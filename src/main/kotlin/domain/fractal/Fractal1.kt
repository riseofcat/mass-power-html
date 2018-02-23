package domain.fractal

import kuden.*
import org.khronos.webgl.*
import util.*
import kotlin.browser.*
import kotlin.js.*
import kotlin.math.*

private val SPEED = 1.0f
//language=
private val radius = "4.0"
//language=GLSL //https://www.jetbrains.com/help/idea/using-language-injections.html
private val vertexShader = """
attribute vec2 a_position;
uniform vec4 u_viewWindow;
varying vec2 v_coord;
void main(void) {
  v_coord = a_position * u_viewWindow.zw + u_viewWindow.xy;
  gl_Position = vec4(a_position, 0.0, 1.0);
}
"""
//language=GLSL //https://www.jetbrains.com/help/idea/using-language-injections.html
private val fragmentShader = """
precision mediump float;
uniform vec2 u_julia;
uniform float u_offset;
varying vec2 v_coord;
void main(void) {
  float xx = v_coord.x;
  float yy = v_coord.y;
  float xt = 0.0;
  gl_FragColor = vec4( 0.0, 0.0, 0.0, 1.0);
  if (xx*xx + yy*yy < ${radius}) {
    for (int iteration = 0; iteration < 1000; iteration++) {
      if (xx*xx + yy*yy > ${radius}) {
        float mu = u_offset + float(iteration) + 1.0 - log(log(xx*xx + yy*yy)) / log(2.0);
        //mu = sqrt(mu);
        float it = mod(mu * 23.0, 768.0);

        float red = min(it, 255.0) / 255.0;
        float green = max(0.0, min(it, 511.0) - 256.0) / 255.0;
        float blue = max(0.0, min(it, 767.0) - 512.0) / 255.0;
        gl_FragColor = vec4( blue, green, red, 1.0);
        break;
      }
      xt = xx*xx - yy*yy + u_julia.x;
      yy = 2.0*xx*yy + u_julia.y;
      xx = xt;
    }
  }
}
"""

class Fractal1Data(var x:Double = 0.0,var y:Double = 0.0,var offsetX:Double = 0.0,var offsetY:Double = 0.0,var scaleX:Double = 1.0,var scaleY:Double = 1.0,var offset:Double = 0.0)

class Fractal1(val html:HTMLElements) {
  val start = Date().getTime()
  val webgl = html.webgl
  val attribBuffer = webgl.createBuffer() ?: JsUtil.error("Unable to create webgl buffer!")
  val shaderProgram:ShaderProgram<Fractal1Data> = ShaderProgram(webgl,DType.TRIANGLE,vertexShader,fragmentShader,
    arrayOf(VertextAttributeInfo("a_position",2))) {
    it.program.setUniform2f("u_julia",it.data.x,it.data.y)
    it.program.setUniform4f("u_viewWindow",it.data.offsetX,it.data.offsetY,it.data.scaleX,it.data.scaleY)
    it.program.setUniform1f("u_offset",it.data.offset)
  }

  fun render() {
    html.resize()
    webgl.clearColor(1f,1f,1f,1f)
    webgl.clear(WebGLRenderingContext.COLOR_BUFFER_BIT)
    val time = (start-Date().getTime())/500.0*SPEED
    shaderProgram.begin(attribBuffer,Fractal1Data(
      0.28+(sin(time/31)/100.0),
      0.008+(cos(time/23.07)/100f),
      scaleX = 1.3-sin(time/10.0)*0.5f,
      scaleY = 1.3-sin(time/10.0)*0.5f,
      offset = 0.0//time * 10.0
    ))
    webgl.bufferData(WebGLRenderingContext.ARRAY_BUFFER,FULLSCREEN_VERTICES,WebGLRenderingContext.DYNAMIC_DRAW)
    webgl.drawArrays(shaderProgram.drawType.glDrawType,0,6)
    shaderProgram.end()
    window.requestAnimationFrame {render()}
  }
}
