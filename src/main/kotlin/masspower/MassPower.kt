package masspower

import com.riseofcat.client.*
import com.riseofcat.common.*
import com.riseofcat.lib.*
import com.riseofcat.share.mass.*
import kuden.*
import org.khronos.webgl.*
import org.w3c.dom.*
import org.w3c.dom.events.*
import util.*
import kotlin.browser.*
import kotlin.js.*
import org.khronos.webgl.WebGLRenderingContext as WGL

const val DYNAMIC_SHADER = true//default true +1 fps
const val DYNAMIC_TEXTURE = true//default true +2 fps
const val BLEND = true//не влияет на производительность
const val DYNAMIC_BLEND = true//не влияет на производительность
const val IMG_SIZE_PX = 128//Важно для scale картинки

class TextureData(val vMatrix:Matrix4)
data class ImgData(val url:String)
class ImgCache(var texture:MassPower.GameTexture? = null)
data class RenderData(val x:Float,val y:Float,val gameSize:Float,val imgData:ImgData) {
  val scale:Float get() = gameSize/IMG_SIZE_PX
}
abstract class View {
  abstract fun getWidth(aspectRation:Float):Float
  abstract fun getHeight(aspectRation:Float):Float
  val gameWidth:Float get() = getWidth(window.innerWidth/window.innerHeight.toFloat())
  val gameHeight:Float get() = getHeight(window.innerWidth/window.innerHeight.toFloat())
  val projectionMatrix get() = Matrix4().apply {setOrthographicProjection(0f,gameWidth,0f,gameHeight,-0.1f,-100f)}
  val windowWidth get() = window.innerWidth.min(window.innerHeight*gameWidth/gameHeight)
  val windowHeight get() = window.innerHeight.min(window.innerWidth*gameHeight/gameWidth)
  val borderLeft get() = (window.innerWidth-windowWidth)/2
  val borderTop get() = (window.innerHeight-windowHeight)/2
}

class FixedWidth(val width:Float,val minHeight:Float,val maxHeight:Float):View() {
  override fun getWidth(aspectRation:Float) = width
  override fun getHeight(aspectRation:Float) = (width/aspectRation).max(minHeight).min(maxHeight)
}

data class Attr(val locationName:String,val numElements:Int)
data class IterAttr(val attr:Attr,val location:Int,val offset:Int)

class MassPower(val view:View = FixedWidth(1000f,1000f,1000f)) {
  val html = HTMLElements()
  val gl get() = html.webgl
  val vertex = gl.compileShader(/*language=GLSL*/"""
attribute vec2 a_position;
attribute vec2 a_boundingBox;
attribute vec2 a_texCoord;
attribute float a_scale;
attribute float a_rotation;
attribute float a_divide;
uniform mat4 u_projectionView;
varying vec2 v_textCoord;
varying vec4 myColor;
varying vec4 arr[gl_MaxVaryingVectors-2];//28-29
varying float v_divide;

mat4 scale(float scale) {
  return mat4(
    vec4(scale, 0.0,   0.0,   0.0),
    vec4(0.0,   scale, 0.0,   0.0),
    vec4(0.0,   0.0,   scale, 0.0),
    vec4(0.0,   0.0,   0.0,   1.0)
  );
}
mat4 rotateZ(float angle) {
  return mat4(
    vec4(cos(angle),   sin(angle),  0.0,  0.0),
    vec4(-sin(angle),  cos(angle),  0.0,  0.0),
    vec4(0.0,          0.0,         1.0,  0.0),
    vec4(0.0,          0.0,         0.0,  1.0)
  );
}
void main(void) {
  v_divide = a_divide;
  v_textCoord = a_texCoord;
  vec4 scaledBox = vec4(a_boundingBox, 1.0, 1.0) * scale(a_scale) * rotateZ(a_rotation);
  //myColor = vec4(1.0,0.3,0.3,0.5);
  myColor = vec4(1.0,1.0,1.0,1.0);
  gl_Position = u_projectionView * vec4(a_position + scaledBox.xy, 1.0, 1.0);
  }
""",WGL.VERTEX_SHADER)
  val shaderProgram:WebGLProgram = gl.createWebGLProgram(/*language=GLSL*/
    vertex,
    gl.compileShader(
      /*language=GLSL*/
      """
precision mediump float;
uniform sampler2D u_sampler;
uniform lowp int u_test_array_size;
uniform lowp float u_arr[3];
uniform lowp vec4 u_vec_arr[gl_MaxVertexUniformVectors/2 - 5];
varying vec2 v_textCoord;
varying vec4 myColor;
varying vec4 arr[gl_MaxVaryingVectors-2];//28-29
varying float v_divide;
void main(void) {
  gl_FragColor = myColor * texture2D(u_sampler, v_textCoord);
  gl_FragColor.a = gl_FragColor.a / v_divide/v_divide/v_divide/v_divide/v_divide/v_divide;
  //gl_FragColor.a = gl_FragColor.a * (u_arr[0] + u_arr[1]);
}
""",WGL.FRAGMENT_SHADER))
  val shaderProgram2:WebGLProgram = gl.createWebGLProgram(
    vertex,
    gl.compileShader(/*language=GLSL*/
      """
precision mediump float;
uniform sampler2D u_sampler;
uniform lowp int u_test_array_size;
uniform lowp float u_arr[3];
uniform lowp vec4 u_vec_arr[gl_MaxVertexUniformVectors/2 - 5];
varying vec2 v_textCoord;
varying vec4 myColor;
varying vec4 arr[gl_MaxVaryingVectors-2];//28-29
varying float v_divide;
void main(void) {
  gl_FragColor = myColor;
}
""",WGL.FRAGMENT_SHADER))

  val shaderProgram3:WebGLProgram = gl.createWebGLProgram(
    gl.compileShader(/*language=GLSL*/"""
attribute vec2 a_position;
attribute vec2 a_boundingBox;
attribute vec2 a_texCoord;
attribute float a_scale;
attribute float a_rotation;
attribute float a_divide;
uniform mat4 u_projectionView;

mat4 scale(float scale) {
  return mat4(
    vec4(scale, 0.0,   0.0,   0.0),
    vec4(0.0,   scale, 0.0,   0.0),
    vec4(0.0,   0.0,   scale, 0.0),
    vec4(0.0,   0.0,   0.0,   1.0)
  );
}
mat4 rotateZ(float angle) {
  return mat4(
    vec4(cos(angle),   sin(angle),  0.0,  0.0),
    vec4(-sin(angle),  cos(angle),  0.0,  0.0),
    vec4(0.0,          0.0,         1.0,  0.0),
    vec4(0.0,          0.0,         0.0,  1.0)
  );
}
void main(void) {
  vec4 scaledBox = vec4(a_boundingBox, 1.0, 1.0) * scale(a_scale) * rotateZ(a_rotation);
  gl_Position = u_projectionView * vec4(a_position + scaledBox.xy, 1.0, 1.0);
  }
""",WGL.VERTEX_SHADER),
    gl.compileShader(/*language=GLSL*/
      """
precision mediump float;
uniform sampler2D u_sampler;
uniform lowp int u_test_array_size;
uniform lowp vec4 u_vec_arr[gl_MaxVertexUniformVectors/2 - 5];
void main(void) {
  gl_FragColor = vec4(0.3,0.3,0.3,0.4);
}
""",WGL.FRAGMENT_SHADER))
  val attributes = listOf(Attr("a_position",2),Attr("a_boundingBox",2),Attr("a_texCoord",2),Attr("a_scale",1),Attr("a_rotation",1),Attr("a_divide",1)).run {
    val result = mutableListOf<IterAttr>()
    var currentSize = 0
    forEach {
      result.add(IterAttr(it,gl.getAttribLocation(shaderProgram,it.locationName),currentSize))
      currentSize += it.numElements
    }
    result
  }
  val verticesBlockSize = attributes.sumBy {it.attr.numElements}
  private val imgCache:MutableMap<ImgData,ImgCache> = hashMapOf()
  var mousePos:XY = XY()
  val model:ClientModel? = ClientModel(Conf(5000))
//  val model:ClientModel? = ClientModel(Conf(5000, "192.168.100.7"))
//  val model = Model(Conf(80, "mass-power.herokuapp.com"))

  init {
    window.onfocus
    window.onblur
    window.onresize = {resize()}
    window.onload = {resize()}
    window.requestAnimationFrame {
      gl.bindBuffer(WGL.ARRAY_BUFFER,gl.createBuffer() ?: lib.log.fatalError("Unable to create webgl buffer!"))

      gl.useProgram(shaderProgram)
      attributes.forEach {
        gl.enableVertexAttribArray(it.location)
        gl.vertexAttribPointer(it.location,it.attr.numElements,WGL.FLOAT,false,/*шаг*/verticesBlockSize*4,it.offset*4)//todo попробовать разные типы а также lowp precision
        if(false) gl.disableVertexAttribArray(it.location)//Если нужно после рендера отключить эти атрибуты (вероятно чтобы иметь возможность задать новые атрибуты для другого шейдера)
      }
      if(false) gl.uniform1i(gl.getUniformLocation(shaderProgram,"u_sampler"),0)
      gl.uniformMatrix4fv(gl.getUniformLocation(shaderProgram,"u_projectionView"),false,(TextureData(view.projectionMatrix)).vMatrix.toFloat32Arr())
      gl.uniform1i(gl.getUniformLocation(shaderProgram,"u_test_array_size"),5)
      gl.uniform1fv(gl.getUniformLocation(shaderProgram,"u_arr[0]"),arrayOf(0.1f,0.1f))

      if(DYNAMIC_SHADER) {
        gl.useProgram(shaderProgram2)
        attributes.forEach {
          gl.enableVertexAttribArray(it.location)
          gl.vertexAttribPointer(it.location,it.attr.numElements,WGL.FLOAT,false,/*шаг*/verticesBlockSize*4,it.offset*4)//todo попробовать разные типы а также lowp precision
          if(false) gl.disableVertexAttribArray(it.location)//Если нужно после рендера отключить эти атрибуты (вероятно чтобы иметь возможность задать новые атрибуты для другого шейдера)
        }
        if(false) gl.uniform1i(gl.getUniformLocation(shaderProgram2,"u_sampler"),0)
        gl.uniformMatrix4fv(gl.getUniformLocation(shaderProgram2,"u_projectionView"),false,(TextureData(view.projectionMatrix)).vMatrix.toFloat32Arr())
        gl.uniform1i(gl.getUniformLocation(shaderProgram2,"u_test_array_size"),5)
        gl.uniform1fv(gl.getUniformLocation(shaderProgram2,"u_arr[0]"),arrayOf(0.1f,0.1f))

        gl.useProgram(shaderProgram3)
        attributes.forEach {
          gl.enableVertexAttribArray(it.location)
          gl.vertexAttribPointer(it.location,it.attr.numElements,WGL.FLOAT,false,/*шаг*/verticesBlockSize*4,it.offset*4)
        }
        gl.uniformMatrix4fv(gl.getUniformLocation(shaderProgram3,"u_projectionView"),false,(TextureData(view.projectionMatrix)).vMatrix.toFloat32Arr())
        gl.uniform1i(gl.getUniformLocation(shaderProgram3,"u_test_array_size"),5)
      }

      gl.enable(WGL.BLEND)
      if(BLEND && !DYNAMIC_BLEND) gl.blendFunc(srcFactor,dstFactor)
      gameLoop(it)
    }

    infix fun View.screenToGame(screen:XY) = XY((screen.x-borderLeft)*gameWidth/windowWidth, gameHeight-(screen.y-borderTop)*gameHeight/windowHeight)
    document.onmousemove = fun(event:Event) {
      if(event is MouseEvent) {
        mousePos = view screenToGame event.xy
      }
    }
    document.onclick = fun(event:Event) {
      if(event is MouseEvent) {
        model?.touch(view screenToGame event.xy)
      }
    }
    document.onkeypress = fun(event:Event) {
      if(event is KeyboardEvent) {
        val code = event.keyCode
        val key = Key.getByCode(code)
      }
    }
  }

  inline val MouseEvent.xy get() = XY(getX(html.container), getY(html.container))

  inner class GameTexture(val glTexture:WebGLTexture,val width:Int,val height:Int) {
    val left = -width/2f
    val right = width/2f
    val bottom = -height/2f
    val top = height/2f
  }

  private fun resize() {
    gl.canvas.width = view.gameWidth.toInt()
    gl.canvas.height = view.gameHeight.toInt()
    html.canvas2d.canvas.width = view.gameWidth.toInt()
    html.canvas2d.canvas.height = view.gameHeight.toInt()
    gl.viewport(0,0,view.gameWidth.toInt(),view.gameHeight.toInt())
    gl.canvas.setAttribute("style","position: absolute; left: ${view.borderLeft}px; top: ${view.borderTop}px; z-index: 5; width: ${view.windowWidth}px; height: ${view.windowHeight}px;")
    html.canvas2d.canvas.setAttribute("style","position: absolute; left: ${view.borderLeft}px; top: ${view.borderTop}px; z-index: 10; width: ${view.windowWidth}px; height: ${view.windowHeight}px;")
  }

  val time get() = Date().getTime()/1000f
  var previousTime = time
  val averageConst = 30
  var fps = 60f

  var srcFactorGlow = WGL.SRC_ALPHA
  var dstFactorGlow = if(false) WGL.DST_ALPHA else WGL.ONE_MINUS_SRC_ALPHA
  var srcFactor = WGL.SRC_ALPHA
  var dstFactor = WGL.ONE_MINUS_SRC_ALPHA

  val imgRed = ImgData("img/smiley_small_rect_red.png")
  val imgGreen = ImgData("img/smiley_small_rect_green.png")
  val imgBlue = ImgData("img/smiley_small_rect_blue.png")
  val imgYellow = ImgData("img/smiley_small_rect_yellow.png")
  val imgViolet = ImgData("img/smiley_small_rect_violet.png")
  val imgGray = ImgData("img/smiley_small_rect_gray.png")
  val colors = listOf(imgRed,imgGreen,imgBlue,imgYellow,imgViolet)
  val PlayerId.color get() = colors.let {it[id%it.size]}

  private fun gameLoop(милисекундСоСтараПлюсБездействие:Double):Unit = lib.saveInvoke {
    fps = (fps*averageConst+1f/(time-previousTime)).toFloat()/(averageConst+1)
    previousTime = time
    if(false) resize()
    html.canvas2d.clearRect(0.0,0.0,view.gameWidth.toDouble(),view.gameHeight.toDouble())
    html.canvas2d.fillStyle = "white"
    html.canvas2d.font = "bold 24pt Arial"
    html.canvas2d.fillText("fps: $fps",200.0,500.0)
    html.canvas2d.fillText(Gen.date(),200.0,550.0)
    html.canvas2d.fillText(ServerCommon.test(),200.0,600.0)
    gl.clearColor(0f,0f,0f,1f)
    gl.clear(WGL.COLOR_BUFFER_BIT)

    val state = model?.calcDisplayState()
    if(DYNAMIC_SHADER) gl.useProgram(shaderProgram3)
    if(true)state?.reactive?.forEach {
      it.pos.x
      val scl = 0.1f
      //поехал центр, было: it.x,it.y,0f,0f,0.5f,0.5f,it.scale,0f,0f
      renderCircle8(it.pos.x.toFloat(),it.pos.y.toFloat(),0f,0f,0.5f,0.5f,scl,0f,0f) {cos, sin->
        val size=it.radius*10
        floatArrayOf(it.pos.x.toFloat(),it.pos.y.toFloat(),cos*size/2,sin*size/2,cos*0.5f+0.5f,sin*0.5f+0.5f,scl,0f,1f)
      }
    }
    mutableListOf<RenderData>(/*RenderData(500f,500f,someWdthInGameCoords,imgGreen)*/).apply {
      if(state != null) {
        state.foods.forEach {add(RenderData(it.pos.x.toFloat(),it.pos.y.toFloat(),it.radius*2,imgGray))}
        if(false) state.reactive.forEach {add(RenderData(it.pos.x.toFloat(),it.pos.y.toFloat(),it.radius*2,it.owner.color))}
        state.cars.forEach {add(RenderData(it.pos.x.toFloat(),it.pos.y.toFloat(),it.radius*2,it.owner.color))}
      }
      add(RenderData(mousePos.x.toFloat(),mousePos.y.toFloat(),30f,imgViolet))
    }.forEach {
        val cache = imgCache[it.imgData] ?: ImgCache().apply {
          imgCache[it.imgData] = this
          val img = document.createElement("img",HTMLImageElement::class)
          img.onload = {
            val texture = gl.createTexture() ?: lib.log.fatalError("Couldn't create webgl texture!")
            gl.bindTexture(WGL.TEXTURE_2D,texture)
            gl.pixelStorei(WGL.UNPACK_FLIP_Y_WEBGL,1) // second argument must be an int
            gl.texImage2D(WGL.TEXTURE_2D,0,WGL.RGBA,WGL.RGBA,WGL.UNSIGNED_BYTE,img)
            gl.texParameteri(WGL.TEXTURE_2D,WGL.TEXTURE_MAG_FILTER,WGL.NEAREST)//LINEAR
            gl.texParameteri(WGL.TEXTURE_2D,WGL.TEXTURE_MIN_FILTER,WGL.NEAREST)
            gl.texParameteri(WGL.TEXTURE_2D,WGL.TEXTURE_WRAP_T,WGL.CLAMP_TO_EDGE)
            gl.texParameteri(WGL.TEXTURE_2D,WGL.TEXTURE_WRAP_S,WGL.CLAMP_TO_EDGE)
            if(DYNAMIC_TEXTURE) gl.bindTexture(WGL.TEXTURE_2D,null)//зануляем текстуру чтобы её настройки уже зафиксировать и случайно не изменить
            this.texture = GameTexture(texture,img.width,img.height)
            null
          }
          img.src = it.imgData.url
        }
        cache.texture?.apply {
          glTexture.renderCircle16(it.x,it.y,0f,0f,0.5f,0.5f,it.scale,0f,0f) {cos,sin->
            val DIVIDE = 1.65f
            val glowRadius = 0.75f
            CircleFanStrip(floatArrayOf(it.x,it.y,cos*width/2,sin*height/2,cos*0.5f+0.5f,sin*0.5f+0.5f,it.scale,0f,1f),
              floatArrayOf(it.x,it.y,cos*width*glowRadius,sin*height*glowRadius,0.5f+cos*0.5f,0.5f+sin*0.5f,it.scale,0f,DIVIDE))
          }
        }
        if(true) cache.texture?.apply {
          //Рисует прямоугольники
          glTexture.render(Mode.TRIANGLE,
            it.x,it.y,left,bottom,0f,0f,it.scale,0f, 1f,
            it.x,it.y,left,top,0f,1f,it.scale,0f, 1f,
            it.x,it.y,right,top,1f,1f,it.scale,0f, 1f,

            it.x,it.y,right,top,1f,1f,it.scale,0f, 1f,
            it.x,it.y,right,bottom,1f,0f,it.scale,0f,1f,
            it.x,it.y,left,bottom,0f,0f,it.scale,0f, 1f)
        }
      }
    window.requestAnimationFrame(::gameLoop)
  }

  fun angle(i:Int,max:Int) = 2*kotlin.math.PI.toFloat()*i/max
  val radian8 = (1..8).toList().map {angle(it,8)}
  val cos8 = radian8.map {kotlin.math.cos(it)}.toFloatArray()
  val sin8 = radian8.map {kotlin.math.sin(it)}.toFloatArray()
  val radian16 = (1..16).toList().map {angle(it,16)}
  val cos16 = radian16.map {kotlin.math.cos(it)}.toFloatArray()
  val sin16 = radian16.map {kotlin.math.sin(it)}.toFloatArray()
  data class CircleFanStrip(val fan:FloatArray,val strip:FloatArray)

  fun renderCircle8(vararg center:Float,fan:(cos:Float, sin:Float)->FloatArray) {//noinline better performance
    val f0 = fan(cos8[0], sin8[0])
    val f1 = fan(cos8[1], sin8[1])
    val f2 = fan(cos8[2], sin8[2])
    val f3 = fan(cos8[3], sin8[3])
    val f4 = fan(cos8[4], sin8[4])
    val f5 = fan(cos8[5], sin8[5])
    val f6 = fan(cos8[6], sin8[6])
    val f7 = fan(cos8[7], sin8[7])
    if(BLEND && DYNAMIC_BLEND) gl.blendFunc(srcFactor,dstFactor)
    render(Mode.TRIANGLE_FAN,*center,*f0,*f1,*f2,*f3,*f4,*f5,*f6,*f7,*f0)
  }
  inline fun render(mode:Mode,vararg allArgs:Float) = render(mode,allArgs)
  inline fun render(mode:Mode,allArgs:FloatArray) = render(mode,if(true) allArgs as Float32Array else Float32Array(allArgs.toTypedArray()),null,allArgs.size)

  fun WebGLTexture.renderCircle16(vararg center:Float,fan:(cos:Float, sin:Float)->CircleFanStrip) {//noinline better performance
    val (f0,s0) = fan(cos16[0], sin16[0])
    val (f1,s1) = fan(cos16[1], sin16[1])
    val (f2,s2) = fan(cos16[2], sin16[2])
    val (f3,s3) = fan(cos16[3], sin16[3])
    val (f4,s4) = fan(cos16[4], sin16[4])
    val (f5,s5) = fan(cos16[5], sin16[5])
    val (f6,s6) = fan(cos16[6], sin16[6])
    val (f7,s7) = fan(cos16[7], sin16[7])
    val (f8,s8) = fan(cos16[8], sin16[8])
    val (f9,s9) = fan(cos16[9], sin16[9])
    val (f10,s10) = fan(cos16[10], sin16[10])
    val (f11,s11) = fan(cos16[11], sin16[11])
    val (f12,s12) = fan(cos16[12], sin16[12])
    val (f13,s13) = fan(cos16[13], sin16[13])
    val (f14,s14) = fan(cos16[14], sin16[14])
    val (f15,s15) = fan(cos16[15], sin16[15])
    if(BLEND && DYNAMIC_BLEND) gl.blendFunc(srcFactor,dstFactor)
    if(DYNAMIC_SHADER) gl.useProgram(shaderProgram)
    render(Mode.TRIANGLE_FAN,*center,*f0,*f1,*f2,*f3,*f4,*f5,*f6,*f7,*f8,*f9,*f10,*f11,*f12,*f13,*f14,*f15,*f0)
    if(BLEND && DYNAMIC_BLEND) gl.blendFunc(srcFactorGlow,dstFactorGlow)
    render(Mode.TRIANGLE_STRIP,*f0,*s0,*f1,*s1,*f2,*s2,*f3,*s3,*f4,*s4,*f5,*s5,*f6,*s6,*f7,*s7,*f8,*s8,*f9,*s9,*f10,*s10,*f11,*s11,*f12,*s12,*f13,*s13,*f14,*s14,*f15,*s15,*f0,*s0)
  }

  inline fun WebGLTexture.render(mode:Mode,vararg allArgs:Float) = render(mode,allArgs)
  inline fun WebGLTexture.render(mode:Mode,allArgs:FloatArray) = render(mode,if(true) allArgs as Float32Array else Float32Array(allArgs.toTypedArray()),this,allArgs.size)
  inline fun MutableList<Float>.vert(vararg args:Float) = addAll(args.toList())//todo check why asList doesn't working
  inline fun WebGLTexture.render(mode:Mode,lambda:MutableList<Float>.()->Unit) = render(mode,arrayListOf<Float>().also {it.lambda()}.toFloatArray())
  inline fun render(mode:Mode,mesh:Float32Array,glTexture:WebGLTexture?,allFloatArgsCount:Int) {
    lib.debug {
      if(allFloatArgsCount<=0) lib.log.error("allFloatArgsCount<=0")
      if(allFloatArgsCount%verticesBlockSize!=0) lib.log.error("Number of vertices not a multiple of the attribute block size!")
    }
    if(true) gl.activeTexture(WGL.TEXTURE0)
    if(glTexture != null) if(DYNAMIC_TEXTURE) gl.bindTexture(WGL.TEXTURE_2D,glTexture)
    gl.bufferData(WGL.ARRAY_BUFFER,mesh,WGL.DYNAMIC_DRAW)
    gl.drawArrays(mode.glMode,0,allFloatArgsCount/verticesBlockSize)//todo first, count
  }
}

enum class Mode(val glMode:Int) {
  TRIANGLE(WGL.TRIANGLES),
  TRIANGLE_FAN(WGL.TRIANGLE_FAN),
  TRIANGLE_STRIP(WGL.TRIANGLE_STRIP)
}