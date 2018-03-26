package com.riseofcat.common

import com.riseofcat.lib.*
import org.w3c.dom.*
import org.w3c.dom.events.*
import org.w3c.xhr.*
import kotlin.js.*

actual class Common {
  actual companion object {
    actual fun <T> createConcurrentList():MutableList<T> {
      return arrayListOf()
    }

    actual fun <K,V> createConcurrentHashMap():MutableMap<K,V> {
      return hashMapOf()
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
      return "[js] ${t.message}"
    }

    actual fun getCodeLineInfo(depth:Int):CharSequence {
      return "[js]"
    }

    actual fun measureNanoTime(block:()->Unit):Long {
      lib.debug {
        val start = permormanceNowMs()
        block()
        val end:Double = permormanceNowMs()
        val result = (end-start)*1_000_000
        return result.toLong()
      }
      lib.releae {
        val start = lib.time
        block()
        return (lib.time - start).ms*1_000_000
      }
      lib.log.fatalError("debug or release")
    }

    fun permormanceNowMs():Double {
      return js("performance.now()")//todo только в debug
    }

    actual fun urlGet(url:String):String {
      val req = XMLHttpRequest()
      req.open("GET",url,false)
      req.send(null)
      return req.responseText
    }

    fun Any.callApply(functionName:String,vararg args:Any?):Any? = asDynamic()[functionName]?.apply(this,args)//this.asDynamic().call
  }
}
