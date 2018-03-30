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
const val DYNAMIC_BLEND = true//не влияет на производительность

data class ImgData(val url:String, val width:Int, val height:Int = width)
class ImgCache(var texture:MassPower.GameTexture? = null)
data class RenderData(val x:Float,val y:Float,val gameSize:Float,val imgData:ImgData)
abstract class View {
  abstract fun getWidth(aspectRation:Float):Float
  abstract fun getHeight(aspectRation:Float):Float
  val gameWidth:Float get() = getWidth(window.innerWidth/window.innerHeight.toFloat())
  val gameHeight:Float get() = getHeight(window.innerWidth/window.innerHeight.toFloat())

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

class MassPower(val view:View = FixedWidth(1500f,1000f,1000f)) {
  val gameScale:Float = 1.0f
  val RenderData.scale:Float get() = gameScale * gameSize/imgData.width
  val html = HTMLElements()
  val gl get() = html.webgl
  //language=GLSL
  val vertex = gl.compileShader(/*language=GLSL*/"""
//Если атрибут в шейдере не используется, то при компиляции об будет вырезан, и могут возникнуть ошибки "enableVertexAttribArray: index out of range"
attribute vec2 a_center_pos;//игровые координаты центра круга
attribute float a_angle;
//attribute float a_game_radius;//радиус в игровых координатах//todo получается что всегда одинаковый. Тогда можно и в uniform положить и проверить производительность

attribute float a_relative_radius;//относительный радиус от [0 до 1] внутри круга и от (1 до inf) вне круга //todo позиция атрибутов, может lowp //todo можно сделать varying вместо v_textCoord и потестить performance

uniform float u_game_radius;//todo test performance
uniform float u_game_width;
uniform float u_game_height;
//uniform vec2 u_game_camera_x;
//uniform vec2 u_game_camera_y;

varying vec2 v_textCoord;
varying float v_distance;//расстояние до круга относительно a_relative_radius. Если 0 то - в круге , если > 0 то точка на растоянии a_relative_radius * v_distance от края круга

void main(void) {
  v_distance = max(a_relative_radius - 1.0, 0.0);
  v_textCoord = vec2(0.5, 0.5) + vec2(cos(a_angle), sin(a_angle)) * 0.5 * min(a_relative_radius, 1.0);
  float currentRadius = a_relative_radius*u_game_radius;
  mat2 screenScale = mat2(2.0/u_game_width,       0.0,
                                0.0,       2.0/u_game_height);
  vec2 gamePos = a_center_pos + vec2(cos(a_angle)*currentRadius, sin(a_angle)*currentRadius);
  gl_Position = vec4(screenScale*gamePos, 1.0, 1.0) - vec4(1.0, 1.0, 0.0, 0.0);
  }
""",WGL.VERTEX_SHADER)
  val shaderProgram:WebGLProgram = gl.createWebGLProgram(/*language=GLSL*/
    vertex,
    gl.compileShader(
      /*language=GLSL*/
      """
precision mediump float;
uniform sampler2D u_sampler;
varying vec2 v_textCoord;
varying float v_distance;
void main(void) {
  gl_FragColor = texture2D(u_sampler, v_textCoord);
  gl_FragColor.a = gl_FragColor.a / pow(1.0 + v_distance, 6.0);//todo потестировать performance pow() vs деление много раз
}
""",WGL.FRAGMENT_SHADER))
  val shaderProgram3:WebGLProgram = gl.createWebGLProgram(
    vertex,
    gl.compileShader(/*language=GLSL*/
      """
precision mediump float;
uniform sampler2D u_sampler;
void main(void) {
  gl_FragColor = vec4(0.3,0.3,0.3,0.4);
}
""",WGL.FRAGMENT_SHADER))
  val attributes = listOf(Attr("a_relative_radius",1), Attr("a_center_pos",2), Attr("a_angle",1)/*, Attr("a_game_radius",1)*/).run {
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
//  val model:ClientModel? = Model(Conf(80, "mass-power.herokuapp.com"))

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
      gl.uniform1f(gl.getUniformLocation(shaderProgram,"u_game_width"),view.gameWidth)
      gl.uniform1f(gl.getUniformLocation(shaderProgram,"u_game_height"),view.gameHeight)
//      gl.uniformMatrix4fv(gl.getUniformLocation(shaderProgram,"u_transform_matrix"),false,view.transformMatrix)

      gl.useProgram(shaderProgram3)
      attributes.forEach {
        gl.enableVertexAttribArray(it.location)
        gl.vertexAttribPointer(it.location,it.attr.numElements,WGL.FLOAT,false,/*шаг*/verticesBlockSize*4,it.offset*4)
      }
      gl.uniform1f(gl.getUniformLocation(shaderProgram3,"u_game_width"),view.gameWidth)
      gl.uniform1f(gl.getUniformLocation(shaderProgram3,"u_game_height"),view.gameHeight)

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
  val imgBig = ImgData("img/smiley.png",1024)
  val imgRed = ImgData("img/smiley_small_rect_red.png",128)
  val imgGreen = ImgData("img/smiley_small_rect_green.png",128)
  val imgBlue = ImgData("img/smiley_small_rect_blue.png",128)
  val imgYellow = ImgData("img/smiley_small_rect_yellow.png",128)
  val imgViolet = ImgData("img/smiley_small_rect_violet.png",128)
  val imgGray = ImgData("img/smiley_small_rect_gray.png",128)
  val colors = listOf(imgRed,imgGreen,imgBlue,imgYellow,imgViolet)
  val PlayerId.color get() = colors.let {it[id%it.size]}
  var currentShader:WebGLProgram = shaderProgram//todo bad

  private fun gameLoop(милисекундСоСтараПлюсБездействие:Double):Unit = lib.saveInvoke {
    fps30 = (fps30*30+1f/(time-previousTime)).toFloat()/(30+1)
    fps500 = (fps500*200+1f/(time-previousTime)).toFloat()/(200+1)
    previousTime = time
    if(false) resize()
    html.canvas2d.clearRect(0.0,0.0,view.gameWidth.toDouble(),view.gameHeight.toDouble())
    html.canvas2d.fillStyle = "white"
    html.canvas2d.font = "bold 24pt Arial"
    html.canvas2d.fillText("mouse: ${mousePos}",200.0,400.0)//todo протестировать производительность за пределами области рисования
    html.canvas2d.fillText("fps30: $fps30",200.0,450.0)
    html.canvas2d.fillText("fps500: $fps500",200.0,500.0)
    html.canvas2d.fillText(Gen.date(),200.0,550.0)
    html.canvas2d.fillText(ServerCommon.test(),200.0,600.0)
    gl.clearColor(0f,0f,0f,1f)
    gl.clear(WGL.COLOR_BUFFER_BIT)
    val state = model?.calcDisplayState()
    gl.useProgram(shaderProgram3)
    currentShader = shaderProgram3
    if(false)state?.reactive?.forEach {
      val fan = CircleData(defaultBlend){cos, sin, angle->
        floatArrayOf(it.pos.x.toFloat(),it.pos.y.toFloat(),angle/*,it.radius*/)
      }
      renderCircle10(it.radius, null,fan)
    }
    gl.useProgram(shaderProgram)
    currentShader = shaderProgram
    mutableListOf<RenderData>().apply {
      if(state != null) {
        state.foods.forEach {add(RenderData(it.pos.x.toFloat(),it.pos.y.toFloat(),it.radius,imgGray))}
        if(true) state.reactive.forEach {add(RenderData(it.pos.x.toFloat(),it.pos.y.toFloat(),it.radius,it.owner.color))}
        state.cars.forEach {add(RenderData(it.pos.x.toFloat(),it.pos.y.toFloat(),it.radius,it.owner.color))}
      }
      add(RenderData(mousePos.x.toFloat(),mousePos.y.toFloat(),30f,imgViolet))
      add(RenderData(mousePos.x.toFloat(),mousePos.y.toFloat(),30f,imgBig))
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
            gl.bindTexture(WGL.TEXTURE_2D,null)//зануляем текстуру чтобы её настройки уже зафиксировать и случайно не изменить
            this.texture = GameTexture(texture,img.width,img.height)
            null
          }
          img.src = it.imgData.url
        }
        cache.texture?.apply {
          if(false) {
            gl.bindTexture(WGL.TEXTURE_2D,glTexture)//-2fps
            render(Mode.TRIANGLE,
              it.x,it.y,left,bottom,0f,0f,it.scale,1f,
              it.x,it.y,left,top,0f,1f,it.scale,1f,
              it.x,it.y,right,top,1f,1f,it.scale,1f,

              it.x,it.y,right,top,1f,1f,it.scale,1f,
              it.x,it.y,right,bottom,1f,0f,it.scale,1f,
              it.x,it.y,left,bottom,0f,0f,it.scale,1f)
          }
          val fan = CircleData(defaultBlend) {cos,sin, angle-> floatArrayOf(it.x,it.y,angle/*,it.gameSize*/)}
          val strip = CircleData(stripBlend) {cos,sin, angle->
            floatArrayOf(it.x,it.y,angle/*,it.gameSize*/)
          }
          renderCircle10(it.gameSize, glTexture,fan,strip, 0.75f)
        }
      }
    window.requestAnimationFrame(::gameLoop)
  }

  fun angle(i:Int,max:Int) = 2*kotlin.math.PI.toFloat()*i/max
  val radian10 = (1..10).toList().map {angle(it,10)}
  val cos10 = radian10.map {kotlin.math.cos(it)}.toFloatArray()
  val sin10 = radian10.map {kotlin.math.sin(it)}.toFloatArray()

  class CircleData(val blend:Blend, val getArr:(cos:Float, sin:Float, angle:Float)->FloatArray)
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

  fun renderCircle10(radius:Float, texture:WebGLTexture?, fan:CircleData, strip:CircleData? = null, stripRelativeDistance:Float = 0.75f) {//noinline better performance
    if(texture != null) gl.bindTexture(WGL.TEXTURE_2D,texture)//-2fps
    gl.uniform1f(gl.getUniformLocation(currentShader,"u_game_radius"),radius)//todo cache position? //todo если будут проблемы с проивзодительностью, то можно применять uniform для группы объектов с одинаковыми радиусами

    val r1 = 1f//Радиус 1f - окружность
    val r0 = 0f//центр круга
    val center = fan.getArr(0f,0f, 0f)
    val f0 = fan.getArr(cos10[0], sin10[0], radian10[0])
    val f1 = fan.getArr(cos10[1], sin10[1], radian10[1])
    val f2 = fan.getArr(cos10[2], sin10[2], radian10[2])
    val f3 = fan.getArr(cos10[3], sin10[3], radian10[3])
    val f4 = fan.getArr(cos10[4], sin10[4], radian10[4])
    val f5 = fan.getArr(cos10[5], sin10[5], radian10[5])
    val f6 = fan.getArr(cos10[6], sin10[6], radian10[6])
    val f7 = fan.getArr(cos10[7], sin10[7], radian10[7])
    val f8 = fan.getArr(cos10[8], sin10[8], radian10[8])
    val f9 = fan.getArr(cos10[9], sin10[9], radian10[9])
    if(DYNAMIC_BLEND) gl.blendFunc(fan.blend.src.value,fan.blend.dst.value)
    render(Mode.TRIANGLE_FAN,r0,*center,r1,*f0,r1,*f1,r1,*f2,r1,*f3,r1,*f4,r1,*f5,r1,*f6,r1,*f7,r1,*f8,r1,*f9,r1,*f0)
    if(strip != null) {
      val s0 = strip.getArr(cos10[0], sin10[0], radian10[0])
      val s1 = strip.getArr(cos10[1], sin10[1], radian10[1])
      val s2 = strip.getArr(cos10[2], sin10[2], radian10[2])
      val s3 = strip.getArr(cos10[3], sin10[3], radian10[3])
      val s4 = strip.getArr(cos10[4], sin10[4], radian10[4])
      val s5 = strip.getArr(cos10[5], sin10[5], radian10[5])
      val s6 = strip.getArr(cos10[6], sin10[6], radian10[6])
      val s7 = strip.getArr(cos10[7], sin10[7], radian10[7])
      val s8 = strip.getArr(cos10[8], sin10[8], radian10[8])
      val s9 = strip.getArr(cos10[9], sin10[9], radian10[9])
      if(DYNAMIC_BLEND) gl.blendFunc(strip.blend.src.value,strip.blend.dst.value)
      val sr = 1.0f + stripRelativeDistance//за кругом glow radius
      render(Mode.TRIANGLE_STRIP,r1,*f0,sr,*s0,r1,*f1,sr,*s1,r1,*f2,sr,*s2,r1,*f3,sr,*s3,r1,*f4,sr,*s4,r1,*f5,sr,*s5,r1,*f6,sr,*s6,r1,*f7,sr,*s7,r1,*f8,sr,*s8,r1,*f9,sr,*s9,r1,*f0,sr,*s0)
    }
  }

  inline fun render(mode:Mode,vararg allArgs:Float) = render(mode,allArgs)
  inline fun render(mode:Mode,allArgs:FloatArray) = render(mode,if(true) allArgs as Float32Array else Float32Array(allArgs.toTypedArray()),allArgs.size)
  inline fun MutableList<Float>.vert(vararg args:Float) = addAll(args.toList())//todo check why asList doesn't working
  inline fun render(mode:Mode,lambda:MutableList<Float>.()->Unit) = render(mode,arrayListOf<Float>().also {it.lambda()}.toFloatArray())
  inline fun render(mode:Mode,mesh:Float32Array,allFloatArgsCount:Int) {
    lib.debug {
      if(allFloatArgsCount<=0) lib.log.error("allFloatArgsCount<=0")
      if(allFloatArgsCount%verticesBlockSize!=0) lib.log.error("Number of vertices not a multiple of the attribute block size!")
    }
    if(true) gl.activeTexture(WGL.TEXTURE0)
    gl.bufferData(WGL.ARRAY_BUFFER,mesh,WGL.DYNAMIC_DRAW)
    gl.drawArrays(mode.glMode,0,allFloatArgsCount/verticesBlockSize)//todo first, count
  }
}

enum class Mode(val glMode:Int) {
  TRIANGLE(WGL.TRIANGLES),
  TRIANGLE_FAN(WGL.TRIANGLE_FAN),
  TRIANGLE_STRIP(WGL.TRIANGLE_STRIP)
}