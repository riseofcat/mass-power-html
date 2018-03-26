package domain.fractal

import com.riseofcat.lib.*
import kuden.*
import org.khronos.webgl.*
import util.*
import kotlin.browser.*
import kotlin.js.*
import kotlin.math.*

//language=GLSL //https://www.jetbrains.com/help/idea/using-language-injections.html
private val vertexShader = """
    attribute vec2 a_position;
    uniform vec4 u_viewWindow;
    varying vec2 v_coord;
    void main(void) {
        v_coord = a_position + u_viewWindow.xy;
        gl_Position = vec4(a_position, 0.0, 1.0);
    }
"""
//language=GLSL //https://www.jetbrains.com/help/idea/using-language-injections.html
private val fragmentShader = """
    precision mediump float;
    varying vec2 v_coord;
    void main(void) {
        float xx = 0.0;
        float yy = 0.0;
        float xt = 0.0;
        gl_FragColor = vec4( 0.0, 0.0, 0.0, 1.0);
        for (int iteration = 0; iteration < 1000; iteration++) {
            if (xx*xx + yy*yy > 4.0) {
              float it = mod(float(iteration) * 13.0, 768.0);
              float red = min(it, 255.0) / 255.0;
              float green = max(0.0, min(it, 511.0) - 256.0);
              float blue = max(0.0, min(it, 767.0) - 512.0);
              gl_FragColor = vec4( red, green, blue, 1.0);
              break;
            }
            xt = xx*xx - yy*yy + v_coord.x;
            yy = 2.0*xx*yy + v_coord.y;
            xx = xt;
        }
    }
"""

class Fractal2Data(var offsetX:Float = 0f,var offsetY:Float = 0f)

class Fractal2(val html:HTMLElements) {
  val start = Date().getTime()
  val webgl = html.webgl
  val shaderProgram:ShaderProgram<Fractal2Data> = ShaderProgram(webgl,DType.TRIANGLE,vertexShader,fragmentShader,
    arrayOf(VertextAttributeInfo("a_position",2))) {
    it.program.setUniform4f("u_viewWindow",it.data.offsetX,it.data.offsetY,0f,0f)
  }
  val attribBuffer = webgl.createBuffer() ?: lib.log.fatalError("Unable to create webgl buffer!")

  fun render() {
    html.resize()
    webgl.clearColor(1f,1f,1f,1f)
    webgl.clear(WebGLRenderingContext.COLOR_BUFFER_BIT)
    val time = (start-(Date().getTime()))/1000.0
    shaderProgram.begin(attribBuffer,Fractal2Data(sin(time).toFloat(),cos(time).toFloat()))
    webgl.bufferData(WebGLRenderingContext.ARRAY_BUFFER,FULLSCREEN_VERTICES,WebGLRenderingContext.DYNAMIC_DRAW)
    webgl.drawArrays(shaderProgram.drawType.glDrawType,0,6)
    shaderProgram.end()
    window.requestAnimationFrame {render()}
  }
}
