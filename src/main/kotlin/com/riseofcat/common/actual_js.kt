package com.riseofcat.common

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
      return JSON.parse(str)
    }

    actual fun createWebSocket(host:String,port:Int,path:String):LibWebSocket {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    actual val timeMs:Long
      get() = TODO("not implemented") //To change body of created functions use File | Settings | File Templates.

    actual fun getStackTraceString(t:Throwable):String? {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    actual fun getCodeLineInfo(depth:Int):CharSequence {
      TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

  }
}
