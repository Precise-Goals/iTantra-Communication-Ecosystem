package com.itantra.core.proto

import com.itantra.core.proto.TransceiverProto.TransceiverMessage as ProtoMessage
import com.itantra.domain.model.Direction
import com.itantra.domain.model.MessageType
import com.itantra.domain.model.TransceiverMessage

/**
 * Serializes and deserializes TransceiverMessage domain objects to/from Protobuf binary.
 *
 * Every message transmitted over Wi-Fi Direct TCP or Bluetooth RFCOMM is framed as:
 * [4-byte big-endian Int length][protobuf binary payload]
 *
 * This length-prefix framing allows reliable stream-based deserialization over TCP.
 */
object ProtobufSerializer {

    /**
     * Encode a domain [TransceiverMessage] to a length-prefixed [ByteArray].
     * @return ByteArray ready for socket transmission.
     */
    fun encode(message: TransceiverMessage): ByteArray {
        val proto = ProtoMessage.newBuilder()
            .setType(message.type.toProtoType())
            .setText(message.text)
            .setSrcLang(message.srcLang)
            .setDstLang(message.dstLang)
            .setSenderId(message.senderId)
            .setTimestamp(message.timestamp)
            .setConfidence(message.confidence)
            .setSequence(message.sequence)
            .build()

        val bytes = proto.toByteArray()
        val lengthPrefix = ByteArray(4)
        lengthPrefix[0] = (bytes.size shr 24 and 0xFF).toByte()
        lengthPrefix[1] = (bytes.size shr 16 and 0xFF).toByte()
        lengthPrefix[2] = (bytes.size shr 8 and 0xFF).toByte()
        lengthPrefix[3] = (bytes.size and 0xFF).toByte()

        return lengthPrefix + bytes
    }

    /**
     * Decode a raw Protobuf [ByteArray] (without length prefix) to a domain [TransceiverMessage].
     * The length prefix must be stripped by the socket reader before calling this.
     *
     * @param bytes Raw protobuf payload (no length prefix).
     * @param direction UI-only flag indicating if this was sent or received by this device.
     * @return Decoded [TransceiverMessage].
     */
    fun decode(bytes: ByteArray, direction: Direction = Direction.RECEIVED): TransceiverMessage {
        val proto = ProtoMessage.parseFrom(bytes)
        return TransceiverMessage(
            type = proto.type.toDomainType(),
            text = proto.text,
            srcLang = proto.srcLang,
            dstLang = proto.dstLang,
            senderId = proto.senderId,
            timestamp = proto.timestamp,
            confidence = proto.confidence,
            sequence = proto.sequence,
            direction = direction
        )
    }

    /**
     * Read the 4-byte length prefix from a socket stream.
     * @return Message payload length in bytes, or -1 if stream closed.
     */
    fun readLengthPrefix(buffer: ByteArray): Int {
        if (buffer.size < 4) return -1
        return ((buffer[0].toInt() and 0xFF) shl 24) or
               ((buffer[1].toInt() and 0xFF) shl 16) or
               ((buffer[2].toInt() and 0xFF) shl 8) or
               (buffer[3].toInt() and 0xFF)
    }

    // --- Type conversion helpers ---

    private fun MessageType.toProtoType(): ProtoMessage.MessageType = when (this) {
        MessageType.SPEECH -> ProtoMessage.MessageType.SPEECH
        MessageType.ALERT  -> ProtoMessage.MessageType.ALERT
        MessageType.ACK    -> ProtoMessage.MessageType.ACK
        MessageType.PING   -> ProtoMessage.MessageType.PING
    }

    private fun ProtoMessage.MessageType.toDomainType(): MessageType = when (this) {
        ProtoMessage.MessageType.SPEECH -> MessageType.SPEECH
        ProtoMessage.MessageType.ALERT  -> MessageType.ALERT
        ProtoMessage.MessageType.ACK    -> MessageType.ACK
        ProtoMessage.MessageType.PING   -> MessageType.PING
        else                            -> MessageType.SPEECH
    }
}
