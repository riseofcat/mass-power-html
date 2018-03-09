package com.riseofcat.common

import org.w3c.dom.*
import org.w3c.dom.events.*
import kotlin.js.*
import kotlin.reflect.*

actual class Common {
  actual companion object {
    actual fun <T> createConcurrentList():MutableList<T> {
      return arrayListOf()
    }

    actual fun <K,V> createConcurrentHashMap():MutableMap<K,V> {
      return hashMapOf()
    }

    actual fun toJson(obj:Any):String {
      return JSON.stringify(obj)
    }

    actual fun <T:Any> fromJson(str:String,clazz:KClass<T>):T {
      val obj = JSON.parse<T>(str)
      return obj
    }

    actual fun createWebSocket(host:String,port:Int,path:String):LibWebSocket {
      val webSocket = WebSocket("ws://$host:$port/$path")
      webSocket.onopen
      return object:LibWebSocket() {
        override fun addListener(l:Listener) {
          webSocket.onopen = fun(e:Event) {l.onOpen()}
          webSocket.onmessage = fun(e:Event){if(e is MessageEvent)l.onMessage(e.data.toString())}
          webSocket.onclose = fun(e:Event) {l.onClose()}
        }

        override fun connect() {

        }

        override fun close() {
          webSocket.close()
        }

        override fun send(message:String) {
          webSocket.send(message)
        }

        override val state:State
          get() = when(webSocket.readyState) {
            //0->CONNECTING
            1.toShort()->State.OPEN
            //2->CLOSING
            //3->CLOSED
            else -> State.CLOSE
          }

      }
    }

    actual val timeMs:Long
      get() = Date().getTime().toLong()

    actual fun getStackTraceString(t:Throwable):String? {
      return "todo js"
    }

    actual fun getCodeLineInfo(depth:Int):CharSequence {
      return "todo js"
    }

    actual fun <T:MayClone<T>> clone(obj:T):T {
      TODO("kotlinx.serialization")
      return JSON.parse(JSON.stringify(obj))
    }

  }
}
