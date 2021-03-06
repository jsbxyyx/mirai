package net.mamoe.mirai.qqandroid.io.serialization

import kotlinx.io.core.*
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import net.mamoe.mirai.qqandroid.io.JceStruct
import net.mamoe.mirai.qqandroid.io.ProtoBuf
import net.mamoe.mirai.qqandroid.network.protocol.data.jce.RequestDataVersion2
import net.mamoe.mirai.qqandroid.network.protocol.data.jce.RequestDataVersion3
import net.mamoe.mirai.qqandroid.network.protocol.data.jce.RequestPacket
import net.mamoe.mirai.utils.firstValue
import net.mamoe.mirai.utils.io.read
import net.mamoe.mirai.utils.io.toUHexString


fun <T : JceStruct> ByteArray.loadAs(deserializer: DeserializationStrategy<T>, c: JceCharset = JceCharset.UTF8): T {
    return Jce.byCharSet(c).load(deserializer, this)
}

fun <T : JceStruct> BytePacketBuilder.writeJceStruct(serializer: SerializationStrategy<T>, struct: T, charset: JceCharset = JceCharset.GBK) {
    this.writePacket(Jce.byCharSet(charset).dumpAsPacket(serializer, struct))
}

fun <T : JceStruct> ByteReadPacket.readRemainingAsJceStruct(serializer: DeserializationStrategy<T>, charset: JceCharset = JceCharset.UTF8): T {
    return Jce.byCharSet(charset).load(serializer, this)
}

/**
 * 先解析为 [RequestPacket], 即 `UniRequest`, 再按版本解析 map, 再找出指定数据并反序列化
 */
fun <T : JceStruct> ByteReadPacket.decodeUniPacket(deserializer: DeserializationStrategy<T>, name: String? = null): T {
    return decodeUniRequestPacketAndDeserialize(name) {
        it.read {
            discardExact(1)
            this.readRemainingAsJceStruct(deserializer)
        }
    }
}

/**
 * 先解析为 [RequestPacket], 即 `UniRequest`, 再按版本解析 map, 再找出指定数据并反序列化
 */
fun <T : ProtoBuf> ByteReadPacket.decodeUniPacket(deserializer: DeserializationStrategy<T>, name: String? = null): T {
    return decodeUniRequestPacketAndDeserialize(name) {
        it.read {
            discardExact(1)
            this.readRemainingAsProtoBuf(deserializer)
        }
    }
}


private fun <R> ByteReadPacket.decodeUniRequestPacketAndDeserialize(name: String? = null, block: (ByteArray) -> R): R {
    val request = this.readRemainingAsJceStruct(RequestPacket.serializer())

    return block(if (name == null) when (request.iVersion.toInt()) {
        2 -> request.sBuffer.loadAs(RequestDataVersion2.serializer()).map.firstValue().firstValue()
        3 -> request.sBuffer.loadAs(RequestDataVersion3.serializer()).map.firstValue()
        else -> error("unsupported version ${request.iVersion}")
    } else when (request.iVersion.toInt()) {
        2 -> request.sBuffer.loadAs(RequestDataVersion2.serializer()).map.getOrElse(name) { error("cannot find $name") }.firstValue()
        3 -> request.sBuffer.loadAs(RequestDataVersion3.serializer()).map.getOrElse(name) { error("cannot find $name") }
        else -> error("unsupported version ${request.iVersion}")
    })
}

fun <T : JceStruct> T.toByteArray(serializer: SerializationStrategy<T>, c: JceCharset = JceCharset.GBK): ByteArray = Jce.byCharSet(c).dump(serializer, this)

fun <T : ProtoBuf> BytePacketBuilder.writeProtoBuf(serializer: SerializationStrategy<T>, v: T) {
    this.writeFully(v.toByteArray(serializer).also {
        println("发送 protobuf: ${it.toUHexString()}")
    })
}

/**
 * dump
 */
fun <T : ProtoBuf> T.toByteArray(serializer: SerializationStrategy<T>): ByteArray {
    return ProtoBufWithNullableSupport.dump(serializer, this)
}

/**
 * load
 */
fun <T : ProtoBuf> ByteArray.loadAs(deserializer: DeserializationStrategy<T>): T {
    return ProtoBufWithNullableSupport.load(deserializer, this)
}

/**
 * load
 */
fun <T : ProtoBuf> Input.readRemainingAsProtoBuf(serializer: DeserializationStrategy<T>): T {
    return ProtoBufWithNullableSupport.load(serializer, this.readBytes())
}