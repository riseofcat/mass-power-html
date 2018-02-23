package util

import org.khronos.webgl.*

val FULLSCREEN_VERTICES = arrayOf(
  -1f,-1f,
  1f,-1f,
  1f,1f,
  1f,1f,
  -1f,1f,
  -1f,-1f
).toVertives()

fun Array<Float>.toVertives():Float32Array {
  val result = Float32Array(size)
  result.set(this,0)
  return result
}
