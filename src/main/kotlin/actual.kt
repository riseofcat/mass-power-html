actual fun Any.toJson():String {
  return JSON.stringify(this)
}
actual fun <T>String.fromJson():T {
  return JSON.parse(this)
}