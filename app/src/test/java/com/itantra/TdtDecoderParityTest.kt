package com.itantra

import com.itantra.core.audio.TdtDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.regex.Pattern

/**
 * T78 Step 3 — feeds the Step 1 encoder-output fixture through [TdtDecoder.decode] with a
 * decoder_joint stub that replays a recorded call-by-call trace of the real Python reference
 * (`sravaani_onnx_infer.py::decode_rnnt`, run on the actual INT8 encoder+decoder_joint pair over
 * a real FLEURS hi_in clip — the same clip validated end-to-end in T78 Step 1). The resulting
 * token ids must match `decode_rnnt`'s exactly.
 *
 * Stronger than a final-answer-only check: the stub asserts, at every step, that [TdtDecoder]
 * requests the same encoder time index and feeds back the same last-emitted-token as the
 * reference did — catching an off-by-one in the duration/frame-skip logic or the max_symbols
 * guard even if it happened to still produce the right final transcript by coincidence.
 *
 * Fixtures (app/src/test/resources/), from a real decode_rnnt run recorded step-by-step in
 * Colab (docs/evaluation/sravaani/tdt/README.md):
 * - parity_encoder_out.npy: encoder output, shape [1024, encoderLen]
 * - parity_encoder_len.txt: encoderLen
 * - parity_trace_t.txt / parity_trace_last_token.txt: per-step (encoder time index, last token)
 * - parity_trace_h_in.npy / parity_trace_c_in.npy: per-step LSTM state fed into decoder_joint
 * - parity_trace_logits.npy: per-step 5006-wide decoder_joint output logits
 * - parity_trace_h_out.npy / parity_trace_c_out.npy: per-step updated LSTM state
 * - parity_reference_tokens.txt: the final emitted token ids
 */
class TdtDecoderParityTest {

    @Test
    fun testDecodeMatchesPythonReferenceStepByStep() {
        val encoderLen = resourceFile("parity_encoder_len.txt").readText().trim().toInt()
        val (encShape, encoderOut) = readNpyFloat32(resourceFile("parity_encoder_out.npy"))
        assertEquals("encoder output should be [1024, encoderLen]", 1024, encShape[0])
        assertEquals("encoder output should be [1024, encoderLen]", encoderLen, encShape[1])

        val traceT = readIntLines(resourceFile("parity_trace_t.txt"))
        val traceLastToken = readIntLines(resourceFile("parity_trace_last_token.txt"))
        val (hInShape, hIn) = readNpyFloat32(resourceFile("parity_trace_h_in.npy"))
        val (cInShape, cIn) = readNpyFloat32(resourceFile("parity_trace_c_in.npy"))
        val (logitsShape, logits) = readNpyFloat32(resourceFile("parity_trace_logits.npy"))
        val (hOutShape, hOut) = readNpyFloat32(resourceFile("parity_trace_h_out.npy"))
        val (cOutShape, cOut) = readNpyFloat32(resourceFile("parity_trace_c_out.npy"))

        val numSteps = traceT.size
        assertEquals(numSteps, traceLastToken.size)
        assertEquals(numSteps, hInShape[0])
        assertEquals(numSteps, cInShape[0])
        assertEquals(numSteps, logitsShape[0])
        assertEquals(numSteps, hOutShape[0])
        assertEquals(numSteps, cOutShape[0])
        assertEquals(TdtDecoder.PRED_HIDDEN, hInShape[1])
        assertEquals(TdtDecoder.PRED_HIDDEN, cInShape[1])
        assertEquals(5006, logitsShape[1])

        val referenceTokens = resourceFile("parity_reference_tokens.txt").readText().trim()
            .split(",").filter { it.isNotEmpty() }.map { it.toInt() }

        var stepIndex = 0
        val stub = TdtDecoder.DecoderJointCall { encoderFrame, lastToken, h, c ->
            if (stepIndex >= numSteps) {
                fail("decoder_joint called more times ($stepIndex+) than the recorded trace has ($numSteps)")
            }

            val expectedT = traceT[stepIndex]
            val expectedFrame = FloatArray(1024) { d -> encoderOut[d * encoderLen + expectedT] }
            assertTrue(
                "Step $stepIndex: encoder frame passed to decoder_joint doesn't match the " +
                    "reference's frame at t=$expectedT",
                expectedFrame.contentEquals(encoderFrame)
            )
            assertEquals(
                "Step $stepIndex: last-emitted-token fed back doesn't match the reference",
                traceLastToken[stepIndex], lastToken
            )

            val rowH = hIn.copyOfRange(stepIndex * TdtDecoder.PRED_HIDDEN, (stepIndex + 1) * TdtDecoder.PRED_HIDDEN)
            val rowC = cIn.copyOfRange(stepIndex * TdtDecoder.PRED_HIDDEN, (stepIndex + 1) * TdtDecoder.PRED_HIDDEN)
            assertTrue("Step $stepIndex: LSTM h state doesn't match the reference", rowH.contentEquals(h))
            assertTrue("Step $stepIndex: LSTM c state doesn't match the reference", rowC.contentEquals(c))

            val stepLogits = logits.copyOfRange(stepIndex * 5006, (stepIndex + 1) * 5006)
            val stepHOut = hOut.copyOfRange(stepIndex * TdtDecoder.PRED_HIDDEN, (stepIndex + 1) * TdtDecoder.PRED_HIDDEN)
            val stepCOut = cOut.copyOfRange(stepIndex * TdtDecoder.PRED_HIDDEN, (stepIndex + 1) * TdtDecoder.PRED_HIDDEN)
            stepIndex++
            TdtDecoder.StepResult(stepLogits, stepHOut, stepCOut)
        }

        val decoded = TdtDecoder.decode(encoderOut, encoderLen, stub)

        assertEquals(
            "Total decoder_joint calls should exactly match the recorded trace",
            numSteps, stepIndex
        )
        assertEquals(
            "Decoded token ids should match decode_rnnt's exactly",
            referenceTokens, decoded
        )
    }

    private fun resourceFile(name: String): File {
        val url = javaClass.classLoader?.getResource(name)
            ?: error("Test resource '$name' not found on the classpath")
        return File(url.toURI())
    }

    private fun readIntLines(file: File): List<Int> =
        file.readText().split("\n").map { it.trim() }.filter { it.isNotEmpty() }.map { it.toInt() }

    /** Reads a float32, C-contiguous, 2D .npy array (as produced by numpy.save). */
    private fun readNpyFloat32(file: File): Pair<IntArray, FloatArray> {
        val bytes = file.readBytes()
        require(bytes.size > 10 && String(bytes, 1, 5, Charsets.US_ASCII) == "NUMPY") {
            "Not a .npy file: $file"
        }
        val majorVersion = bytes[6].toInt()
        val headerLenBytes = if (majorVersion >= 2) 4 else 2
        val headerLenOffset = 8
        val headerLen = if (majorVersion >= 2) {
            ByteBuffer.wrap(bytes, headerLenOffset, 4).order(ByteOrder.LITTLE_ENDIAN).int
        } else {
            ByteBuffer.wrap(bytes, headerLenOffset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        }
        val headerStart = headerLenOffset + headerLenBytes
        val header = String(bytes, headerStart, headerLen, Charsets.US_ASCII)

        require(header.contains("'descr': '<f4'")) { "Expected little-endian float32 .npy, header: $header" }
        require(header.contains("'fortran_order': False")) { "Expected C-order .npy, header: $header" }

        val shapeMatcher = Pattern.compile("'shape':\\s*\\(([^)]*)\\)").matcher(header)
        require(shapeMatcher.find()) { "Could not parse shape from .npy header: $header" }
        val shapeGroup = requireNotNull(shapeMatcher.group(1)) { "Empty shape group in header: $header" }
        val shape = shapeGroup.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { it.toInt() }
        require(shape.size == 2) { "Expected a 2D array, got shape $shape" }

        val dataStart = headerStart + headerLen
        val count = shape[0] * shape[1]
        val data = FloatArray(count)
        val dataBuf = ByteBuffer.wrap(bytes, dataStart, count * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) data[i] = dataBuf.getFloat(dataStart + i * 4)
        return shape.toIntArray() to data
    }
}
