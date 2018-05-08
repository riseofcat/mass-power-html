package kotlinx.io.js

import com.riseofcat.lib.*
import kotlinx.io.core.*
import org.khronos.webgl.*
import org.w3c.dom.*
import org.w3c.files.*

fun WebSocket.sendPacket(packet: ByteReadPacket) {
    send(packet.readArrayBuffer())
}

inline fun WebSocket.sendPacket(block: BytePacketBuilder.() -> Unit) {
    sendPacket(buildPacket(block = block))
}

fun MessageEvent.getPacket(callback:(ByteReadPacket)->Unit)  {
//    lib.log.info("data is ArrayBuffer = ${data is ArrayBuffer}")
//    lib.log.info("data: $data")

    if(data is Blob) {
        val fileReader = FileReader()
        fileReader.onload = { event->
//            lib.log.info("fileReader.result: ${fileReader.result}")
            callback(ByteReadPacket(BufferView(fileReader.result as ArrayBuffer, null), BufferView.NoPool))
        }
        fileReader.readAsArrayBuffer(data as Blob)
//        fileReader.result as ArrayBuffer
    } else {
        callback(ByteReadPacket(BufferView(data.asDynamic(), null), BufferView.NoPool))
    }
}




