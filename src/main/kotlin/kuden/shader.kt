package kuden

import com.riseofcat.lib.*
import org.khronos.webgl.*
import util.*

class VertexShader<TVarying, TAttributes, IUniform>() {

}

class FragmentShader<TVarying, IUniform>() {

}

class ShaderPog<TVarying, TAttributes, TUniform>(vertex:VertexShader<out TVarying, TAttributes, in TUniform>, fragment:FragmentShader<in TVarying, in TUniform>) {

}

fun WebGLRenderingContext.compileShader(source:String,type:Int):WebGLShader = createShader(type)?.also {
  shaderSource(it,source)
  compileShader(it)
  if(getShaderParameter(it,WebGLRenderingContext.COMPILE_STATUS)==false) lib.log.fatalError("Unable to compile shader! $source ${getShaderInfoLog(it)}")
} ?: lib.log.fatalError("Unable to request shader from webgl context!")
fun WebGLRenderingContext.createWebGLProgram(vertexSrc:String,fragmentSrc:String) = createWebGLProgram(compileShader(vertexSrc,WebGLRenderingContext.VERTEX_SHADER), compileShader(fragmentSrc,WebGLRenderingContext.FRAGMENT_SHADER))
fun WebGLRenderingContext.createWebGLProgram(vertex:WebGLShader,fragment:WebGLShader) = createProgram()?.also {
  attachShader(it,vertex)
  attachShader(it,fragment)
  linkProgram(it)
  if(getProgramParameter(it,WebGLRenderingContext.LINK_STATUS)==false) lib.log.fatalError("Unable to compile shader program! ${getProgramInfoLog(it)}")
} ?: lib.log.fatalError("Unable to request shader program from webgl context!")

enum class DType(val glDrawType:Int) {
  TRIANGLE(WebGLRenderingContext.Companion.TRIANGLES)
}

class VertextAttributeInfo(val locationName:String,val numElements:Int) {
  //todo разные типы
  var location:Int = 0
  var offset:Int = 0
}

class ShaderProgram<T>(val webgl:WebGLRenderingContext,val drawType:DType,vertxSrc:String,fragmSrc:String,val vainf:Array<VertextAttributeInfo>,val setter:(shst:SetData<T>)->Unit) {
  val shaderProgram:WebGLProgram = webgl.createWebGLProgram(vertxSrc,fragmSrc)
  val drawLength:Int
  val verticesBlockSize:Int

  init {
    webgl.useProgram(shaderProgram)
    var currentSize = 0
    for(info in vainf) {// set attribute locations
      info.location = webgl.getAttribLocation(shaderProgram,info.locationName)
      info.offset = currentSize
      currentSize += info.numElements
    }
    verticesBlockSize = currentSize
    when(drawType) {
      DType.TRIANGLE->drawLength = verticesBlockSize*3
      else->TODO("NOT USED DRAW TYPE")//drawLength = verticesBlockSize
    }
  }

  fun begin(attribBuffer:WebGLBuffer,userdata:T) {
    webgl.useProgram(shaderProgram)
    webgl.bindBuffer(WebGLRenderingContext.ARRAY_BUFFER,attribBuffer)
    for(info in vainf) {// set attribute locations...
      webgl.enableVertexAttribArray(info.location)
      webgl.vertexAttribPointer(info.location,info.numElements,WebGLRenderingContext.FLOAT,false,/*шаг*/verticesBlockSize*4,info.offset*4)//todo разные типы
    }
    setter(SetData(this,userdata))
  }

  fun end() {
    vainf.forEach {webgl.disableVertexAttribArray(it.location)}
    webgl.useProgram(null)
  }

  fun getAttribLocation(location:String) = webgl.getAttribLocation(shaderProgram,location)
  fun getUniformLocation(location:String) = webgl.getUniformLocation(shaderProgram,location)
  fun setUniform1f(location:String,value:Float) = webgl.uniform1f(getUniformLocation(location),value)
  fun setUniform1fv(location:String,arr:Array<Float>) = webgl.uniform1fv(getUniformLocation(location),arr)
  fun setUniform1f(location:String,value:Double) = setUniform1f(location,value.toFloat())
  fun setUniform2f(location:String,v1:Float,v2:Float) {
    webgl.uniform2f(getUniformLocation(location),v1,v2)
  }

  fun setUniform2f(location:String,v1:Double,v2:Double) = setUniform2f(location,v1.toFloat(),v2.toFloat())
  fun setUniform4f(location:String,v1:Float,v2:Float,v3:Float,v4:Float) = webgl.uniform4f(getUniformLocation(location),v1,v2,v3,v4)
  fun setUniform4f(location:String,v1:Double,v2:Double,v3:Double,v4:Double) = setUniform4f(location,v1.toFloat(),v2.toFloat(),v3.toFloat(),v4.toFloat())
  fun setUniform1i(location:String,value:Int) = webgl.uniform1i(getUniformLocation(location),value)
  fun setUniformMatrix4fv(location:String,value:Float32Array) = webgl.uniformMatrix4fv(getUniformLocation(location),false,value)
  fun WebGLShader.log() = webgl.getShaderInfoLog(this) ?: "no log"
  fun WebGLProgram.log() = webgl.getProgramInfoLog(this) ?: "no log"
  data class SetData<T>(val program:ShaderProgram<T>,val data:T)
  inner class ShaderProgramMesh {
    val verticesBuffer:Float32Array = Float32Array(20_000-(20_000%drawLength))
    val attribBuffer:WebGLBuffer = webgl.createBuffer() ?: lib.log.fatalError("Unable to create webgl buffer!")
    var currentIndex:Int = 0
    fun queue(vararg vertices:Float) = queueArray(vertices.toTypedArray())
    fun remaining() = verticesBuffer.length-currentIndex
    fun bufferFull() = currentIndex>=verticesBuffer.length
    fun queueArray(vertices:Array<Float>) {
      verticesBuffer.set(vertices,currentIndex)
      currentIndex += vertices.size
      if(bufferFull()) {
        println("Skipped draw call, to many values!")
        currentIndex = 0
      }
    }

    fun render(userdata:T) {
      if(currentIndex>0) {
        if(currentIndex%verticesBlockSize!=0) lib.log.fatalError("Number of vertices not a multiple of the attribute block size!")
        begin(attribBuffer,userdata)
        webgl.bufferData(WebGLRenderingContext.ARRAY_BUFFER,verticesBuffer,WebGLRenderingContext.DYNAMIC_DRAW)
        webgl.drawArrays(drawType.glDrawType,0,currentIndex/verticesBlockSize)
        currentIndex = 0
        end()
      }
    }
  }
}

