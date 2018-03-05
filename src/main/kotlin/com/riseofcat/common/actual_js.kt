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

  }
}