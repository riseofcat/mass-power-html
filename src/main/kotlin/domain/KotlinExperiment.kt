package domain

import com.riseofcat.share.*
import kotlinx.serialization.*
import org.khronos.webgl.*
import kotlin.coroutines.experimental.*
import kotlin.js.Math
import kotlin.properties.Delegates
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

fun breakpoint(info:String? = "") {
  println("breakpoint $info")//todo печатать строчку или функцию, но Throwable.stackTrace нету в JS. Поможет кодогенерация
}

class ExecuteMe {
  fun execute(f:()->Unit):ExecuteMe { //todo добавить аргумент title:String=""
    f()
    return this
  }

  fun notExecute(f:()->Unit):ExecuteMe {
    return this
  }

  val e get() = this::execute
  val ne get() = this::notExecute
}

class Log(val s:String) {
  infix fun info(a:Any?) {
    println("[Info] $s: $a")
  }

  infix fun error(a:Any?) {
    println("[Error] $s: $a")
  }
}

infix fun Any?.info(l:Log) {
  l info this
}

infix fun Any?.error(l:Log) {
  l error this
}

fun rnd(min:Int,max:Int) = (min+Math.random()*(max-min+1)).toInt()
fun Int.rnd(max:Int) = rnd(this,max)
val Int.rnd
  get() = rnd(1,this)

fun testFirst() {
  ExecuteMe().ne {
    Log("log1") info 123
    123 info Log("log2")
  }.ne {
    //todo apply with run also let
  }.ne {
    for(i in (1-5..11+2).reversed().step(2)) print("$i,")
    1 until 10 //1..9
    100 downTo 1 step 2
    'A'..'Z'
    3 !in 1..10
    "a" in arrayOf("a","b","c")
    (1..10).forEach {it}
  }.e {
    val a:Any? = null
    when(a) {
      !is String->""
      a.length>10->""//string
      !is Int->""
      in 1..10->""//int
      else->a.length
    }
  }.e {
    class A {
      override fun toString():String {
        return "instanceA"
      }
    }

    fun anyAnswer(value:Any?):String = when(value) {//внутри блока e.{} то определять её нужно до использования
      is Int->"integer"
      is String->"text length ${value.length}" //value:String type here
      is A->"classA"
      else->"unknown or null"
    }
    repeat(5) {
      when(4.rnd) {
        1->123
        2->"Two"
        3->A()
        else->null
      }.let {
        "$it is ${anyAnswer(it)}" info Log("")
      }
    }
  }.e {
    val funRef = {1+1}
    "$funRef = ${funRef()}" info Log("function refference")
  }.e {
    val m:Map<Int,String> = mapOf(Pair(1,"one"),2 to "two")
    m[1]
    for((k,v) in m) {
    }
  }.e {
    class A {
      operator fun component1():String = "str"
      operator fun component2():Int = 1
      operator fun plus(other:A):A {
        return this
      } //работает для += если plusAssign не указан

      operator fun plus(other:Int):A {
        return this
      }

      operator fun times(scale:Float):A {
        return this
      }

      operator fun unaryMinus():A {
        return this
      }

      operator fun unaryPlus():A {
        return this
      }

      operator fun inc():A {
        return this
      }//++

      operator fun get(index:Int):Any? {
        return this
      }//a[0]

      operator fun get(i:Int,j:Int):Any? {
        return this
      }//a[x][y]

      operator fun set(index:Int,value:Any?) {}//a[0] = 123
      operator fun contains(a:Any?):Boolean {
        return true
      }// in  !in

      operator fun iterator():Iterator<String> = object:Iterator<String> {
        //https://try.kotlinlang.org/#/Kotlin%20in%20Action/Chapter%207/7.3/4_IteratorConvention.kt
        override fun hasNext():Boolean = true

        override fun next():String {
          return "str"
        }
      }

    }

    var a = +A()+-A()
    a += A()
    val (v1,v2) = A()
    /*todo поэксперементировать
    += и +
    a + (-b) и a - b
    * */
  }.ne {
    class ClassAndFunction {
      operator fun invoke(vararg args:Any?):Int = 123
    }

    val c = ClassAndFunction()
    val i:Int = c("",1f)
//        val fref:()->Int = c::invoke //don't work in JS https://try.kotlinlang.org/#/Kotlin%20in%20Action/Chapter%2011/11.3/2_TheInvokeConventionAndFunctionalTypes.kt
  }.e {
    class DependencyHandler {
      fun compile(coordinate:String) {
        println("Added dependency on $coordinate")
      }

      operator fun invoke(
        body:DependencyHandler.()->Unit) {
        body()
      }
    }

    val dependencies = DependencyHandler()
    dependencies.compile("org.jetbrains.kotlin:kotlin-stdlib:1.0.0")
    dependencies {
      compile("org.jetbrains.kotlin:kotlin-reflect:1.0.0")
    }
  }.e {
    open class S
    class A():S() {
      var /*val*/ f:String by DelegateA()

      inner class DelegateA {
        operator fun getValue(ref:S,prop:KProperty<*>):String {
          return "get Value from DelegateA"
        }

        operator fun setValue(ref:S,prop:KProperty<*>,value:String) {}
      }
    }
  }.e {
    val list:List<String> = listOf("a","b")+"c"+listOf("d","e")
  }.ne {
    0x0F and 0xF0
    0x0F or 0xF0
    0x1 shl 4
  }.ne {
    fun ж(й:()->Unit):(()->Unit)->Any = null!!
    ж {}() {}
  }.ne {
    val a:Any? = null
    if(a is ()->Any?) a()
  }.ne {
    //Kotlin Night 2017
    fun foo1() {
      println("foo1")
    }

    fun foo2() = {println("foo2")}
    foo1()//correct
    foo2()//nothing to print
    foo2()()//correct
  }.e {
    data class A(val v1:String,val v2:Int)

    val a = A("v1",1)
    val (v1,v2) = a
    a.copy(v2 = 123)
    a.hashCode()
  }.e {
    class A {
      val s:String by lazy {
        println("computed")
        "str value"
      }
    }
    A().s
    A().s
  }.e {
    //Observable property
    class A {
      var v1:String by Delegates.observable("init value") {d,old,new-> println("observable $old -> $new")}
      var v2:String by Delegates.vetoable("init value") {d,old,new-> println("vetoable $old -> $new"); false}
    }

    val a = A()
    a.v1 = "new value1"
    a.v1 = "new value2"
    a.v2 = "new value1"
    a.v2 = "new value2"
  }.e {
    //nonNull delegate. throws exception before init(...)
    class A {
      var v:String by Delegates.notNull()
      fun init(value:String):A {
        v = value
        return this
      }
    }
    A().init("value1").v
  }.e {
    //Delegate to map
    class User(val map:Map<String,Any?>) {
      val name:String by map
      val age:Int     by map
    }
    User(mapOf("name" to "John Doe","age" to 25))
  }.e {
    fun length(s:String) = s.length
    fun goodSize(len:Int) = len>1
    fun <A,B,C> compose(f:(B)->C,g:(A)->B):(A)->C {
      return {x-> f(g(x))}
    }

    val oddLength = compose(::goodSize,::length)
    println(listOf("a","ab","abc").filter(oddLength))
  }.e {
    data class A(val i:Int?,val s:String)
    listOf(A(1,"One"),A(2,"Two"),A(3,"Three"))
      .filter {it.i ?: -1%2!=0}//todo it.i?:-1 заменить на filter по полю
      .maxBy {it.i ?: Int.MIN_VALUE}
  }.e {
    listOf(1,2,3).joinToString("; ",transform = {i-> (i*10).toString()})
    listOf(1,2,3).map {i-> i*i}
    val всеЧётные:Boolean = listOf(2,4,6).all {i-> i%2==0}
    listOf(-1,1,2).any {i-> i<0}
    listOf(1,2,3).groupBy {i-> "key $i"}.forEach {it.key;it.value}
    println(listOf("abc","def").flatMap {it.toList()})//[a, b, c, d, e, f]
  }.e {
    val result = try {
      "123".toInt()
    } catch(e:NumberFormatException) {
      0
    }
  }.e {
    hashMapOf("a" to 123)
    arrayListOf(1,2,3)
    println(hashSetOf(1,1,2).size)//prints 2
    2 in setOf(1,1,2)//1,2
    listOf(1,2,3).average()//2
  }.e {
    fun Any?.hello():CharSequence {
      if(this!=null) {
        return "hello ${this.toString()}"
      } else {
        return "null can't say hello"
      }
    }

    fun Collection<Any?>?.hello():CharSequence {
      if(this!=null) {
        val result = StringBuilder("[");
        for((i,v) in this.withIndex()) {
          if(i>0) result.append(',')
          val csq = v.hello()
          result.append(csq)
        }
        result.append(']')
        return result
      } else if(false) {
        return null.hello()//todo recursion
      } else {
        val a:Any? = null
        return a.hello()
      }
    }

    fun Any?.sayHi() {
      if(this is Collection<Any?>?) {//todo how to override extension?
        println(this.hello())
      } else {
        println(this.hello())
      }
    }
    listOf(1,2,null,"A",'B').sayHi()
    val coll:Collection<Any?>? = null
    coll.sayHi()
  }.ne {
    listOf("яблоки","груши","бананы")
    //.zipWithNext { a, b ->  }
    //zip previous
    //todo print: "Яблоки, груши и бананы."
  }.e {
    fun stringArgs(vararg a:String) {
      val b:Array<out String> = a
      b.forEachIndexed {index,element->
        {
          index
          element.length
        }
      }
    }

    val arr:Array<String> = Array(3,{i-> "str $i"})
    stringArgs(*arr,*arr)//todo
    stringArgs(*listOf("a","b","c").toTypedArray())

    fun floatArgs(vararg f:Float) {}
    floatArgs(*listOf(1f,2f).toFloatArray())

    inline fun getFloat32Arr(vararg f:Float):Float32Array {
      Float32Array(f.toTypedArray())
      val result:Float32Array = f as Float32Array//todo Lol, it works with JS!!!
      return result
    }

    val float32Arr = getFloat32Arr(1.5f,3.14f)
    Log("FloatArray(10, { it.toFloat()+0.5f }) to JS:") info {
      FloatArray(10,{it.toFloat()+0.5f})
    }
    breakpoint("after print function")
    Log("print function with varargs toTypedArray()") info {
      "usage:"
      getFloat32Arr(1f,2f)
    }
    breakpoint("after print function")
  }.ne {
    "12.345-6.A".split("\\.|-".toRegex())
  }.ne {
    fun parsePath1(path:String) {
      val directory = path.substringBeforeLast("/")
      val fullName = path.substringAfterLast("/")
      val fileName = fullName.substringBeforeLast(".")
      val extension = fullName.substringAfterLast(".")
      println("Dir: $directory, name: $fileName, ext: $extension")
    }

    fun parsePath2(path:String) {
      val regex = """(.+)/(.+)\.(.+)""".toRegex()
      val matchResult = regex.matchEntire(path)
      if(matchResult!=null) {
        val (directory,filename,extension) = matchResult.destructured
        println("Dir: $directory, name: $filename, ext: $extension")
      }
    }

    val path:String = "/Users/yole/kotlin-book/chapter.adoc"
    parsePath1(path)
    parsePath2(path)
  }.e {
    val str = """
                йabc
                """.trimMargin("й")
    str.length//3
    println(str)//abc
    val str2 = """
        abc
        def
      """.trimIndent()
    str2.length
    println(str2)//abc\ndef
    breakpoint()
  }.e {
    open class A constructor(arg1:String,open val arg2:String) {
      val value1:String

      init {
        value1 = arg1
      }
    }

    class B(arg1:String,arg2:String):A(arg1,arg2) {
      override val arg2 get() = "overriden value"
    }
    //https://try.kotlinlang.org/#/Kotlin%20in%20Action/Chapter%204/4.2/3_1_ImplementingPropertiesDeclaredInInterfaces.kt
  }.e {
    class A {
      var counter:Int = 0
        private set
    }
  }.e {
    class A(val m:MutableMap<Int,String> = HashMap()):MutableMap<Int,String> by m
    A()[1] = "123"
  }.e {
    class DSL {
      val a = "A"
      fun f(init:DSL.()->Unit):DSL {//важно .()
        this.init()
        return this
      }
    }
    DSL().f {
      this.a
    }
  }.ne {
    abstract class Animated {
      abstract fun animate()
      open fun stopAnimating() {}
      /*final*/ fun animateTwice() {}
      inner class Inner {
        fun getOuterReference():Animated = this@Animated
      }
    }
  }.e {
    //equivalents:
    val a1 = {"some"}()
    val a2 = run {"some"}
    val f1 = {"some"}
    run(f1)
    fun f2():String {
      return "some"
    }
    run(::f2)
  }.e {
    //Ссылка на конструктор
    class A(val str:String) {}

    val refA:(String)->A = ::A
    val a1:A = refA("abc")
  }.e {
    //https://discuss.kotlinlang.org/t/async-await-on-the-client-javascript/2412/3
    //https://www.youtube.com/watch?v=b4mBmi1QNF0
    val block:suspend ()->Unit = {
      for(i in 1..10) {
        testSuspendFun()
      }
    }
    if(false) {
      println(" --- COROUTINES UNDER THE HOOD --- ")
      println(block)
      println(" --- COROUTINES UNDER THE HOOD --- ")
    }
    block.startCoroutine(object:Continuation<Unit> {
      override fun resume(value:Unit) {}
      override val context:CoroutineContext get() = EmptyCoroutineContext
      override fun resumeWithException(e:Throwable) {
        console.log("Coroutine failed: $e")
      }
    })
  }.e {
    var variable1 = "Kotlin variable"//can't used in js string
    val const1 = "Kotlin constant"
    js("console.log('inside js code you can use $const1')")
  }.e {
    val naturalNumbers = generateSequence(0) {it+1}
    val numbersTo100 = naturalNumbers.takeWhile {it<=100}
    println(numbersTo100.sum())
    val b:Boolean = 123 in naturalNumbers
  }.ne {
    //todo адаптировать для JS
//        fun File.isInsideHiddenDirectory() = generateSequence(this) { it.parentFile }.any { it.isHidden }
//        File("/Users/svtk/.HiddenDir/a.txt").isHiddenDirectory()
  }.e {
    class A {
      val p:String = "property"
    }
    with(A()) {
      this.p
      p
    }
    A().apply {
      //inline
      p
    }
    buildString {
      append("this ")
      append("is ")
      append("StringBuilder")
    }
  }.e {
    //as?
    class A {
      val i:Int = 123
    }

    val a:Any? = "wrong value"
    val result1:Int = (a as? A)?.i ?: -1
    breakpoint()
    //(null as A)?.i//exception here
    //https://try.kotlinlang.org/#/Kotlin%20in%20Action/Chapter%206/6.1/5_SafeCastsAs.kt
  }.ne {
    class A {
      lateinit var v:String
    }
    A().v
  }.e {
    " ".isNullOrBlank()
    "small".toUpperCase()
    val coerceIn = 140.coerceIn(1..100) //100
    breakpoint()
  }.e {
    fun nothingFun():Nothing {
      throw Exception("some exception")
    }

    val inferType = {v:Any?->
      if(v is String) {
        v
      } else {
        nothingFun()
      }
    }
    inferType("abc").toUpperCase()//result is String
    try {
      inferType(123).toUpperCase()//String too
    } catch(e:Exception) {
    }
  }.ne {
    val (a,b,c) = listOf("a","b")//throws Index out of bounds
  }.e {
    label@{
      return@label
    }
    label2@ for(i in 1..10) {
      break@label2
    }
    (1..10).forEach {
      return@forEach
    }
    println(StringBuilder().apply sb@{
      listOf(1,2,3).apply {
        this@sb.append(this.toString())
      }
    })
  }.e {
    fun <T:Comparable<T>> max(first:T,second:T):T {
      return if(first>second) first else second
    }
    println(max("kotlin","java"))
    fun <T> ensureTrailingPeriod(seq:T)
      where T:CharSequence,T:Appendable {
      if(!seq.endsWith('.')) {
        seq.append('.')
      }
    }
    println(ensureTrailingPeriod(StringBuilder("Hello World")))
    fun printSum(c:Collection<*>) {
      val intList = c as? List<Int>
        ?: throw IllegalArgumentException("List is expected")
      println(intList.sum())
    }
    printSum(listOf(1,2,3))
    inline fun <reified T> isA(value:Any) = value is T
    println(isA<String>("abc"))
    println(isA<String>(123))
    listOf("one",2,"three").filterIsInstance<String>()
  }.e {
    fun <T:R,R> copyData(source:MutableList<T>,
      destination:MutableList<R>) {
      for(item in source) {
        destination.add(item)
      }
    }

    fun <T> copyData2(source:MutableList<out T>,destination:MutableList<T>) {
      for(item in source) {
        destination.add(item)
      }
    }

    fun <T> copyData3(source:MutableList<T>,destination:MutableList<in T>) {
      for(item in source) {
        destination.add(item)
      }
    }
  }.e {
    val class1:KClass<String> = String::class//https://try.kotlinlang.org/#/Kotlin%20in%20Action/Chapter%209/9.3/6.2_4_StarProjectionUsingInsteadOfATypeArgument3.kt
    val class2:KClass<out String> = "str"::class.js.kotlin//"str".javaClass.kotlin
    class1.simpleName
    //        var i:Int;(::i).setter//https://try.kotlinlang.org/#/Kotlin%20in%20Action/Chapter%2010/10.2/1_3_ReflectionAPI2.kt //todo kotlin 1.2.0 ?
    class A(var b:Int = 123)

    val a = A()
    A::b.get(a)
  }.e {
    val appendExcl:StringBuilder.()->Unit = {this.append("!")}
    StringBuilder("abc").appendExcl()
  }.e {
    open class Tag(val name:String) {
      private val children = mutableListOf<Tag>()
      protected fun <T:Tag> doInit(child:T,init:T.()->Unit) {
        child.init()
        children.add(child)
      }

      override fun toString() =
        "<$name>${children.joinToString("")}</$name>"
    }

    class TD:Tag("td")
    class TR:Tag("tr") {
      fun td(init:TD.()->Unit) = doInit(TD(),init)
    }

    class TABLE:Tag("table") {
      fun tr(init:TR.()->Unit) = doInit(TR(),init)
    }

    fun table(init:TABLE.()->Unit) = TABLE().apply(init)

    table {
      for(i in 1..2) {
        tr {
          td {
          }
        }
      }
    } info Log("DSL")  //https://try.kotlinlang.org/#/Examples/Longer%20examples/HTML%20Builder/HTML%20Builder.kt
  }.e {
    Log("round") info
      1.6f.round //2
  }.e {
    //https://try.kotlinlang.org/#/Examples/Problems/Index%20of%20Maximum/Index%20of%20Maximum.kt
    fun indexOfMax(a:IntArray):Int? {
//            a.forEachIndexed
      val maxBy = a.mapIndexed {i:Int,v:Int-> Pair(i,v)}.maxBy {p-> p.second}
      return maxBy?.first
    }
  }.e {
    fun isPalindrome(s:String):Boolean {//Palindrome значит что в обе стороны строка читается одинаково, например "поп"
      //https://try.kotlinlang.org/#/Examples/Problems/Palindrome/Palindrome.kt
      if(false) {
        return s==s.reversed() //easy way
      } else {
        for(i in s.indices) {
          if(s[i]!=s.get(s.lastIndex-i)) {
            return false
          }
        }
        return true
      }
    }
  }.e {
    listOf(1,2,3,4,5,6,7).sortedWith(object:Comparator<Int> {
      override fun compare(a:Int,b:Int):Int {
        return a%2-b%2
      }
    }) info Log("Comparator")
  }.e {
    class A:ClosedRange<Int> {
      override val start:Int = 1
      override val endInclusive:Int get() = 10
      override fun contains(value:Int):Boolean {
        return super.contains(value)
      }
    }
    (5 in A()) info Log("5 in A()")
    operator fun Int.rangeTo(other:String) = A()
    (5 in 123.."abc") info Log("5 in 123..abc")
  }.ne {
    //https://try.kotlinlang.org/#/Kotlin%20Koans/Conventions/For%20loop/Task.kt
    class MyDate {}

    class DateRange(val start:MyDate,val end:MyDate):Iterable<MyDate> {
      override fun iterator():Iterator<MyDate> = object:Iterator<MyDate> {
        override fun next():MyDate {
          return null!!
        }

        override fun hasNext():Boolean {
          return false
        }
      }
    }
  }.e {
    open class A(val v:String)
    class B:A {
      constructor(v:String):super(v) {
        breakpoint("secondary constructor")//4
      }

      init {
        breakpoint("init block 1")//1
      }

      val a = 123.also {println("val a = 123 also")}//2

      init {
        breakpoint("init block 2")//3
      }
    }
    B("v")
  }.e {
    var increment:Int = 0
    fun printInc(s:String) {
      println("${++increment} + $s")
    }

    open class Parent {
      private val a = printInc("4 Parent.a")                                                                         //   4

      constructor(arg:Unit = printInc("3 Parent primary constructor default argument")) {                             //   3     //miss on js
        printInc("7 Parent primary constructor")                                                                   //   7
      }

      init {
        printInc("5 Parent.init")                                                                                  //   5
      }

      private val b = printInc("6 Parent.b")                                                                         //   6
    }

    class Child:Parent {
      val a = printInc("8 Child.a")                                                                                  //   8

      init {
        printInc("9 Child.init 1")                                                                                 //   9
      }

      constructor(arg:Unit = printInc("2 Child primary constructor default argument")):super() {                    //   2     //miss on js
        printInc("12 Child primary constructor")                                                                   //  12
      }

      val b = printInc("10 Child.b")                                                                                 //  10

      constructor(arg:Int,arg2:Unit = printInc("1 Child secondary constructor default argument")):this() {         //   1
        printInc("13 Child secondary constructor")                                                                 //  13
      }

      init {
        printInc("11 Child.init 2")                                                                                //  11
      }
    }
    Child(1)
    /**
    Child secondary constructor default argument
    Child primary constructor default argument
    Parent primary constructor default argument
    Parent.a
    Parent.init
    Parent.b
    Parent primary constructor
    Child.a
    Child.init 1
    Child.b
    Child.init 2
    Child primary constructor
    Child secondary constructor*/
  }.e {
    val a = setOf(1,2)
    val b = setOf(2,3)
    a union b info Log("union")//123
    a intersect b info Log("intersect")//2
    breakpoint()
  }.e {
    fun noInlineFun(f:()->Unit) {
      f()
    }

    inline fun inlineFun(f:()->Unit) {
      f()
    }

    fun withReturn() {
      noInlineFun {
        return@noInlineFun//return local and works only with label
      }
      inlineFun(fun() {
        return//return local without label
      })
      inlineFun {
        return//return to global function withReturn
      }
      println("never prints")
    }
  }.e {
    data class JsonObj(val a:String,val b:Float)
    val obj:JsonObj = JSON.parse("""{"a":"str", "b": 3.14}""")
    breakpoint("json obj")
  }.e {
    fun longValueMinus1(value:Long):Long {
      return value-1
    }
    inline fun testLong() {
      //ОЧЕНЬ ВАЖНЫЙ ТЕСТ !!! СМОТРЕТЬ НА ПОСЛЕДНИЕ 4 ЦИФРЫ. ОНИ НЕ ПРАВИЛЬНЫЕ LONG - 1 ПЛОХАЯ ОПЕРАЦИЯ !!!
      val longMax:Long = Long.MAX_VALUE
      println("longMax = $longMax")
      println("longMax-1 = ${longValueMinus1(longMax)}")
      val randomLong = Long.MAX_VALUE/6
      println("randomLong = $randomLong")
      println("randomLong-1 = ${longValueMinus1(randomLong)}")
    }
    testLong()
    println(::testLong)
    Int.MAX_VALUE
    breakpoint("Long and Int")
  }.e {
    @Serializable data class Data(val a: Int)
    @Serializable data class Box<T>(val boxed: T)
    val dataSerial:KSerializer<Data> = Data.serializer()
    val boxedDataSerial:KSerializer<Box<Data>> = Box.serializer(dataSerial)
    val boxedData = Box<Data>(Data(123))
    val str = kotlinx.serialization.json.JSON.stringify(boxedDataSerial,boxedData)
    val boxedData2 = kotlinx.serialization.json.JSON.parse(boxedDataSerial,str)
    println("boxedData2.boxed.a = ${boxedData2.boxed.a}")
    if(boxedData == boxedData2) println("boxedData == boxedData2")
  }
}

val Float.round:Int get() = ((this+0.5).toInt())  //https://try.kotlinlang.org/#/Kotlin%20in%20Action/Chapter%2011/11.4/2_DefiningExtensionsOnPrimitiveTypesHandlingDates.kt

suspend fun testSuspendFun() {
  print("inside suspend fun ")
}

//region Interface multi inheritance
interface Clickable {
  fun click()
  fun showOff() = println("I'm clickable!")
}

interface Focusable {
  fun setFocus(b:Boolean) = println("I ${if(b) "got" else "lost"} focus.")
  fun showOff() = println("I'm focusable!")
}

class Button:Clickable,Focusable {
  override fun click() = println("I was clicked")
  override fun showOff() {
    super<Clickable>.showOff()
    super<Focusable>.showOff()
  }
}
//endregion

interface User {
  val email:String
  val nickname:String
    get() = email.substringBefore('@')
}

sealed class Expr {
  class Num(val value:Int):Expr()
  class Sum(val left:Expr,val right:Expr):Expr()

  fun eval(e:Expr):Int = when(e) {
    is Num->e.value
    is Sum->eval(e.right)+eval(e.left)
  }
}

//region singletone
object Singletone /*:SuperClass*/ {
  var a = 1
}

fun Singletone.f() {}
fun _1() {
  Singletone.f()
}
//endregion

//region companion object
class WithCompanion {
  fun f() {
    a
  }

  //Only one companion object in class
  companion object /*Companion*/ {
    val a = "abc"
    operator fun invoke(str:String) = WithCompanion()
  }
}

fun _2() {
  WithCompanion.a
  WithCompanion.a
  WithCompanion()//constructor call
  WithCompanion("companion call")
}
//endregion

//http://youtube.com/watch?v=b4mBmi1QNF0
