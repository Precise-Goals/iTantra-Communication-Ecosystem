package com.itantra

import com.itantra.core.telemetry.Telemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T11: the NTP-style offset/RTT formula used to correct cross-device latency
 * (SocketTransport's ping/ACK exchange, Telemetry.computeOffsetAndRtt).
 */
class ClockOffsetUnitTest {

    @Test
    fun `synchronised clocks, symmetric delay yields zero offset`() {
        // A sends at t0=1000, B (same clock) replies at t1=1050, A receives at t2=1100.
        val (offset, rtt) = Telemetry.computeOffsetAndRtt(t0 = 1000L, t1 = 1050L, t2 = 1100L)
        assertEquals(0L, offset)
        assertEquals(100L, rtt)
    }

    @Test
    fun `positive clock offset is recovered even with asymmetric delay`() {
        // B's clock runs 500ms ahead of A. A sends at t0=1000 (A's clock). The one-way trip
        // to B takes 30ms, so B receives at 1000+500+30=1530 and replies instantly: t1=1530.
        // The return trip takes 70ms, so A receives at t2=1000+30+70=1100.
        val (offset, rtt) = Telemetry.computeOffsetAndRtt(t0 = 1000L, t1 = 1530L, t2 = 1100L)
        assertEquals(480L, offset) // 1530 - (1000+1100)/2 = 1530 - 1050 = 480
        assertEquals(100L, rtt)
    }

    @Test
    fun `negative clock offset`() {
        // B's clock runs 200ms behind A.
        val (offset, rtt) = Telemetry.computeOffsetAndRtt(t0 = 5000L, t1 = 4850L, t2 = 5100L)
        assertEquals(-200L, offset) // 4850 - (5000+5100)/2 = 4850 - 5050 = -200
        assertEquals(100L, rtt)
    }

    @Test
    fun `nsToEpochMs converts a recent monotonic timestamp to a plausible epoch`() {
        val beforeEpoch = System.currentTimeMillis()
        val ns = System.nanoTime()
        val epoch = Telemetry.nsToEpochMs(ns)
        val afterEpoch = System.currentTimeMillis()
        assertTrue("expected $beforeEpoch <= $epoch <= $afterEpoch", epoch in beforeEpoch..afterEpoch)
    }
}
