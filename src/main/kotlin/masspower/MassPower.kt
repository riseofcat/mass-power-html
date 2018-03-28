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

const val DYNAMIC_SHADER = false//default true +1 fps
const val DYNAMIC_TEXTURE = true//default true +2 fps
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
varying vec4 arr[gl_MaxVaryingVectors-2];//28-29
varying float v_divide;
void main(void) {
  gl_FragColor = texture2D(u_sampler, v_textCoord);
  gl_FragColor.a = gl_FragColor.a / v_divide/v_divide/v_divide/v_divide/v_divide/v_divide;
  //gl_FragColor.a = gl_FragColor.a * (u_arr[0] + u_arr[1]);
}
""",WGL.FRAGMENT_SHADER))
  val shaderProgram3:WebGLProgram = gl.createWebGLProgram(
    vertex,
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

      gl.useProgram(shaderProgram3)
      attributes.forEach {
        gl.enableVertexAttribArray(it.location)
        gl.vertexAttribPointer(it.location,it.attr.numElements,WGL.FLOAT,false,/*шаг*/verticesBlockSize*4,it.offset*4)
      }
      gl.uniformMatrix4fv(gl.getUniformLocation(shaderProgram3,"u_projectionView"),false,(TextureData(view.projectionMatrix)).vMatrix.toFloat32Arr())
      gl.uniform1i(gl.getUniformLocation(shaderProgram3,"u_test_array_size"),5)

      gl.enable(WGL.BLEND)
      if(!DYNAMIC_BLEND) gl.blendFunc(defaultBlend.src.value,defaultBlend.dst.value)
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
  var fps30 = 30f
  var fps500 = 30f
  val defaultBlend = Blend(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA)
  val stripBlend = Blend(BlendFactor.SRC_ALPHA, if(true) BlendFactor.ONE_MINUS_SRC_ALPHA else BlendFactor.DST_ALPHA)
  val imgRed = ImgData("img/smiley_small_rect_red.png")
  val imgGreen = ImgData("img/smiley_small_rect_green.png")
  val imgBlue = ImgData("img/smiley_small_rect_blue.png")
  val imgYellow = ImgData("img/smiley_small_rect_yellow.png")
  val imgViolet = ImgData("img/smiley_small_rect_violet.png")
  val imgGray = ImgData("img/smiley_small_rect_gray.png")
  val colors = listOf(imgRed,imgGreen,imgBlue,imgYellow,imgViolet)
  val PlayerId.color get() = colors.let {it[id%it.size]}

  private fun gameLoop(милисекундСоСтараПлюсБездействие:Double):Unit = lib.saveInvoke {
    fps30 = (fps30*30+1f/(time-previousTime)).toFloat()/(30+1)
    fps500 = (fps500*200+1f/(time-previousTime)).toFloat()/(200+1)
    previousTime = time
    if(false) resize()
    html.canvas2d.clearRect(0.0,0.0,view.gameWidth.toDouble(),view.gameHeight.toDouble())
    html.canvas2d.fillStyle = "white"
    html.canvas2d.font = "bold 24pt Arial"
    html.canvas2d.fillText("fps30: $fps30",200.0,450.0)
    html.canvas2d.fillText("fps500: $fps500",200.0,500.0)
    html.canvas2d.fillText(Gen.date(),200.0,550.0)
    html.canvas2d.fillText(ServerCommon.test(),200.0,600.0)
    gl.clearColor(0f,0f,0f,1f)
    gl.clear(WGL.COLOR_BUFFER_BIT)
    val state = model?.calcDisplayState()
    gl.useProgram(shaderProgram3)
    if(true)state?.reactive?.forEach {
      val scl = 5f
      val fan = CircleData(defaultBlend){cos, sin->
        val size=it.radius
        floatArrayOf(it.pos.x.toFloat(),it.pos.y.toFloat(),cos*size/2,sin*size/2,0f,0f,scl,0f,1f)
      }
      renderCircle10(null,fan)
    }
    gl.useProgram(shaderProgram)
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
          if(true) render(glTexture,Mode.TRIANGLE,
            it.x,it.y,left,bottom,0f,0f,it.scale,0f,1f,
            it.x,it.y,left,top,0f,1f,it.scale,0f,1f,
            it.x,it.y,right,top,1f,1f,it.scale,0f,1f,

            it.x,it.y,right,top,1f,1f,it.scale,0f,1f,
            it.x,it.y,right,bottom,1f,0f,it.scale,0f,1f,
            it.x,it.y,left,bottom,0f,0f,it.scale,0f,1f)
          val fan = CircleData(defaultBlend) {cos,sin->
            floatArrayOf(it.x,it.y,cos*width/2,sin*height/2,cos*0.5f+0.5f,sin*0.5f+0.5f,it.scale,0f,1f)
          }
          val strip = CircleData(stripBlend) {cos,sin->
            val DIVIDE = 1.65f
            val glowRadius = 0.75f
            floatArrayOf(it.x,it.y,cos*width*glowRadius,sin*height*glowRadius,0.5f+cos*0.5f,0.5f+sin*0.5f,it.scale,0f,DIVIDE)
          }
          renderCircle10(glTexture,fan,strip)
        }
      }
    window.requestAnimationFrame(::gameLoop)
  }

  fun angle(i:Int,max:Int) = 2*kotlin.math.PI.toFloat()*i/max
  val radian10 = (1..10).toList().map {angle(it,10)}
  val cos10 = radian10.map {kotlin.math.cos(it)}.toFloatArray()
  val sin10 = radian10.map {kotlin.math.sin(it)}.toFloatArray()

  class CircleData(val blend:Blend, val getArr:(cos:Float, sin:Float)->FloatArray)
  data class Blend(val src:BlendFactor, val dst:BlendFactor)
  enum class BlendFactor(val value:Int) {
    SRC_COLOR(WGL.SRC_COLOR),
    ONE_MINUS_SRC_COLOR(WGL.ONE_MINUS_SRC_COLOR),
    DST_COLOR(WGL.DST_COLOR),
    ONE_MINUS_DST_COLOR(WGL.ONE_MINUS_DST_COLOR),
    SRC_ALPHA(WGL.SRC_ALPHA),
    ONE_MINUS_SRC_ALPHA(WGL.ONE_MINUS_SRC_ALPHA),
    DST_ALPHA(WGL.DST_ALPHA),
    ONE_MINUS_DST_ALPHA(WGL.ONE_MINUS_DST_ALPHA),
    SRC_ALPHA_SATURATE(WGL.SRC_ALPHA_SATURATE)
  }

  fun renderCircle10(texture:WebGLTexture?, fan:CircleData, strip:CircleData? = null) {//noinline better performance
    val center = fan.getArr(0f,0f)
    val f0 = fan.getArr(cos10[0], sin10[0])
    val f1 = fan.getArr(cos10[1], sin10[1])
    val f2 = fan.getArr(cos10[2], sin10[2])
    val f3 = fan.getArr(cos10[3], sin10[3])
    val f4 = fan.getArr(cos10[4], sin10[4])
    val f5 = fan.getArr(cos10[5], sin10[5])
    val f6 = fan.getArr(cos10[6], sin10[6])
    val f7 = fan.getArr(cos10[7], sin10[7])
    val f8 = fan.getArr(cos10[8], sin10[8])
    val f9 = fan.getArr(cos10[9], sin10[9])
    if(DYNAMIC_BLEND) gl.blendFunc(fan.blend.src.value,fan.blend.dst.value)
    render(texture, Mode.TRIANGLE_FAN,*center,*f0,*f1,*f2,*f3,*f4,*f5,*f6,*f7,*f8,*f9,*f0)
    if(strip != null) {
      val s0 = strip.getArr(cos10[0], sin10[0])
      val s1 = strip.getArr(cos10[1], sin10[1])
      val s2 = strip.getArr(cos10[2], sin10[2])
      val s3 = strip.getArr(cos10[3], sin10[3])
      val s4 = strip.getArr(cos10[4], sin10[4])
      val s5 = strip.getArr(cos10[5], sin10[5])
      val s6 = strip.getArr(cos10[6], sin10[6])
      val s7 = strip.getArr(cos10[7], sin10[7])
      val s8 = strip.getArr(cos10[8], sin10[8])
      val s9 = strip.getArr(cos10[9], sin10[9])
      if(DYNAMIC_BLEND) gl.blendFunc(strip.blend.src.value,strip.blend.dst.value)
      render(texture, Mode.TRIANGLE_STRIP,*f0,*s0,*f1,*s1,*f2,*s2,*f3,*s3,*f4,*s4,*f5,*s5,*f6,*s6,*f7,*s7,*f8,*s8,*f9,*s9,*f0,*s0)
    }
  }

  inline fun render(texture:WebGLTexture?, mode:Mode,vararg allArgs:Float) = render(texture, mode,allArgs)
  inline fun render(texture:WebGLTexture?, mode:Mode,allArgs:FloatArray) = render(mode,if(true) allArgs as Float32Array else Float32Array(allArgs.toTypedArray()),texture,allArgs.size)
  inline fun MutableList<Float>.vert(vararg args:Float) = addAll(args.toList())//todo check why asList doesn't working
  inline fun render(texture:WebGLTexture?, mode:Mode,lambda:MutableList<Float>.()->Unit) = render(texture, mode,arrayListOf<Float>().also {it.lambda()}.toFloatArray())
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