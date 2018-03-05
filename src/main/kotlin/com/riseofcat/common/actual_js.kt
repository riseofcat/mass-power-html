package com.riseofcat.common

import kotlin.reflect.*

actual fun Any.toJson():String {
  return JSON.stringify(this)
}
actual inline fun <reified T:Any>String.fromJson():T {
  return JSON.parse(this)
}
actual fun <T> createConcurrentList():MutableList<T> {
  return arrayListOf()
}
actual fun <K,V> createConcurrentHashMap():MutableMap<K,V> {
  return hashMapOf()
}