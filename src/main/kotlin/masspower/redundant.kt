package masspower

fun mem(v:String):String {
  return "$v ${js("performance.memory")[v]/1024/1024}"
}

enum class Key(val code:Int,val number:Boolean = false) {
  Q(113), W(119), E(101), R(114), T(116), Y(121),
  ZERO(48,number = true), ONE(49,number = true), TWO(50,number = true), THREE(51,number = true), FOUR(52,number = true), FIVE(53,number = true), SIX(54,number = true), SEVEN(55,number = true), EIGHT(56,number = true), NINE(57,number = true)
  ;
  val numValue get() = code-48
  companion object {
    fun getByCode(code:Int):Key? {
      for(value in values()) {
        if(value.code==code) {
          return value
        }
      }
      return null
    }
  }
}