package com.riseofcat.common

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

    actual inline fun <reified T> fromJson(str:String):T {
      return JSON.parse(str)
    }

  }
}
