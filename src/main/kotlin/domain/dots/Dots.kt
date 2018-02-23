package domain.dots

import kuden.*
import org.khronos.webgl.*
import util.*
import kotlin.browser.*
import kotlin.js.*

class Dots(private val html:HTMLElements) {
  private val shaderProgram:WebGLProgram = html.webgl.createWebGLProgram(
    /*language=GLSL*/
    """
attribute vec2 a_position;
varying vec2 v_pos;
uniform vec2 u_scale;
void main(void) {
  v_pos = a_position*u_scale;
  gl_Position = vec4(a_position, 0.0, 1.0);
//  gl_PointSize = 10.0;
}
""",
    /*language=GLSL*/
    """
precision mediump float;
varying vec2 v_pos;
uniform int u_length;
const int max_length = gl_MaxVertexUniformVectors/4;
uniform vec3 u_color[max_length];
uniform vec2 u_dot[max_length];
void main(void) {
  vec3 clr = vec3(0.0,0.0,0.0);
  for(int i=0;i < max_length;i++) {
    vec3 c = u_color[i];
    if(c.r + c.g + c.b == 0.0) break;
     clr = clr + 0.01/distance(v_pos, u_dot[i])*c;
  }
  gl_FragColor = vec4(clr, 1.0);
}
""")
  val dots:List<Dot> = List(10) {Dot()}

  init {
    html.webgl.apply {
      useProgram(shaderProgram)
      bindBuffer(WebGLRenderingContext.ARRAY_BUFFER,createBuffer() ?: JsUtil.error("Unable to create webgl buffer!"))
      val location = getAttribLocation(shaderProgram,"a_position")//0
      enableVertexAttribArray(location)
      vertexAttribPointer(location,2,WebGLRenderingContext.FLOAT,false,/*шаг*/2*4,0)
    }
  }

  fun render() {
    html.resize()
    val x = html.webgl.canvas.width.toFloat()
    val y = html.webgl.canvas.height.toFloat()
    html.webgl.apply {
      clear()
      uniform2f(getUniformLocation(shaderProgram,"u_scale"),if(x/y>1) x/y else 1.0f,if(y/x>1) y/x else 1.0f)
      uniform3fv(getUniformLocation(shaderProgram,"u_color"),dots.map {listOf(it.color.getR(),it.color.getG(),it.color.getB())}.flatten().toTypedArray())
      uniform2fv(getUniformLocation(shaderProgram,"u_dot"),dots.map {listOf(it.pos.getX(),it.pos.getY())}.flatten().toTypedArray())
      uniform1i(getUniformLocation(shaderProgram,"u_length"),1)
      bufferData(WebGLRenderingContext.ARRAY_BUFFER,FULLSCREEN_VERTICES,WebGLRenderingContext.STATIC_DRAW)//todo DYNAMIC OR STREAM
      drawArrays(WebGLRenderingContext.Companion.TRIANGLES,0,6)
    }
    window.requestAnimationFrame {render()}
  }
}

fun WebGLRenderingContext.clear() {
  clearColor(1f,1f,1f,1f)
  clear(WebGLRenderingContext.COLOR_BUFFER_BIT)
}

fun rnd(min:Float,max:Float) = (min+Math.random()*(max-min)).toFloat()
class Pos(val x:Float = rnd(-1f,1f),val y:Float = rnd(-1f,1f),val periodX:Float = rnd(0.1f,5f),val periodY:Float = rnd(0.1f,5f),val dx:Float = rnd(0.0f,0.5f),val dy:Float = rnd(0.0f,0.5f)) {
  fun getX() = x+dx*kotlin.math.sin(time/periodX).toFloat()
  fun getY() = y+dy*kotlin.math.sin(time/periodY).toFloat()
}

class Color(val r:Float = rnd(0.5f,1f),val g:Float = rnd(0.5f,1f),val b:Float = rnd(0.5f,1f),val periodR:Float = rnd(0.1f,5f),val dr:Float = rnd(0f,0.5f)) {
  fun getR() = r+dr*kotlin.math.sin(time/periodR).toFloat()
  fun getG() = g
  fun getB() = b
}

class Dot(val pos:Pos = Pos(),val color:Color = Color())

val time get() = Date().getTime()/1000f