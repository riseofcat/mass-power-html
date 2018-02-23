package util

import kuden.*
import org.w3c.dom.*
import org.w3c.dom.events.*
import org.w3c.xhr.*
import kotlin.reflect.*

object JsUtil {
  fun urlGet(url:String):String {
    val req = XMLHttpRequest()
    req.open("GET",url,false)
    req.send(null)
    return req.responseText
  }

  inline fun saveInvoke(lambda:()->Unit) {
    return saveInvoke<Unit>(lambda)?:Unit
  }

  inline fun <T> saveInvoke(lambda:()->T):T? {
    try {
      return lambda()
    } catch(e:Exception) {
      console.log(e.message,e)
      return null
    }
  }

  fun error(vararg args:Any?):Nothing {
    val errStr = "error: "+args.map {it.toString()}.joinToString(", ")
    HTMLElements().container.innerText = errStr
    args.forEach {
      console.log(it)
    }
    if(args.size>0) {
      kotlin.error(args[0] ?: "Unknown error")
    } else {
      kotlin.error("Unknown error")
    }
  }
}

fun Any.callApply(functionName:String,vararg args:Any?):Any? = asDynamic()[functionName]?.apply(this,args)//this.asDynamic().call
fun MouseEvent.getX(element:Element) = pageX-element.getBoundingClientRect().left
fun MouseEvent.getY(element:Element) = pageY-element.getBoundingClientRect().top
fun <T:Any> Document.createElement(localName:String,kClass:KClass<T>):T = createElement(localName) as T
fun Document.createCanvas(style:String) = (createElement("canvas", HTMLCanvasElement::class)).apply {setAttribute("style",style)}
