package util

fun Float.min(other:Float) = kotlin.math.min(this,other)
fun Float.max(other:Float) = kotlin.math.max(this,other)
fun Int.min(other:Float) = kotlin.math.min(toFloat(),other)
fun Int.max(other:Float) = kotlin.math.max(toFloat(),other)
