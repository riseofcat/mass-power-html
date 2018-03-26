package util

import org.w3c.dom.*
import org.w3c.dom.events.*
import kotlin.reflect.*

//todo move to lib.js
fun Any.callApply(functionName:String,vararg args:Any?):Any? = asDynamic()[functionName]?.apply(this,args)//this.asDynamic().call
fun MouseEvent.getX(element:Element) = pageX-element.getBoundingClientRect().left
fun MouseEvent.getY(element:Element) = pageY-element.getBoundingClientRect().top
fun <T:Any> Document.createElement(localName:String,kClass:KClass<T>):T = createElement(localName) as T
fun Document.createCanvas(style:String) = (createElement("canvas", HTMLCanvasElement::class)).apply {setAttribute("style",style)}
