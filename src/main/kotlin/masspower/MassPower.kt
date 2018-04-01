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

class MassPower(val view:View = FixedWidth(1000f,1000f,1000f)) {//todo 1500 width

  val gameScale:Float = 1.0f
  val RenderData.scale:Float get() = gameScale * gameSize/imgData.width
  val html = HTMLElements()
  val gl get() = html.webgl
//language=GLSL
  val vertex = """
//Если атрибут в шейдере не используется, то при компиляции он будет вырезан, и могут возникнуть ошибки "enableVertexAttribArray: index out of range"
//attribute vec2 a_center_pos;//игровые координаты центра круга //todo позиция атрибутов //попробовать lowp
attribute float a_center_x;
attribute float a_center_y;
attribute float a_angle;
attribute float a_game_radius;//Радиус объекта в игровых координатах. Всегда одинаковый для одного объекта.//так быстрее (+2fps), чем через uniform float u_game_radius;
attribute float a_relative_radius;//относительный радиус от [0 до 1] внутри круга и от (1 до inf) вне круга

uniform float u_game_width;
uniform float u_game_height;
uniform float u_game_camera_x;
uniform float u_game_camera_y;

varying vec2 v_textCoord;
varying float v_distance;//расстояние до круга относительно a_relative_radius. Если 0 то - в круге , если > 0 то точка на растоянии a_relative_radius * v_distance от края круга

void main(void) {
  v_distance = max(a_relative_radius - 1.0, 0.0);//todo попробовать не квадратную, а прямоугольную текстуру
  //сейчас из png вырезается элипс, а ещё можно попробовать натягивать прямоугольник, чтобы попадали уголки png
  v_textCoord = vec2(0.5, 0.5) + vec2(cos(a_angle), sin(a_angle)) * 0.5 * min(a_relative_radius, 1.0);
  float currentRadius = a_relative_radius*a_game_radius;
  mat2 screenScale = mat2(2.0/u_game_width,       0.0,
                                0.0,       2.0/u_game_height);
  vec2 gamePos = /*a_center_pos*/vec2(a_center_x, a_center_y) + vec2(cos(a_angle)*currentRadius, sin(a_angle)*currentRadius);
  gl_Position = vec4(screenScale*(gamePos - vec2(u_game_camera_x, u_game_camera_y)), 1.0, 1.0);
  }
"""
  val textureShader:ShaderFull = ShaderFull(
    ShaderVertex(
      vertex,
      listOf(/*Attr("a_center_pos",2),*/ Attr("a_center_x",1),Attr("a_center_y",1),Attr("a_angle",1),Attr("a_game_radius",1),Attr("a_relative_radius",1))
    ),
//language=GLSL
"""
precision mediump float;
uniform sampler2D u_sampler;
varying vec2 v_textCoord;
varying float v_distance;
void main(void) {
  gl_FragColor = texture2D(u_sampler, v_textCoord);
  gl_FragColor.a = gl_FragColor.a / pow(1.0 + v_distance, 6.0);//todo потестировать performance pow() vs деление много раз
}
""")
  val colorShader:ShaderFull = ShaderFull(
    ShaderVertex(
      vertex,
      listOf(/*Attr("a_center_pos",2),*/ Attr("a_center_x",1),Attr("a_center_y",1), Attr("a_angle",1), Attr("a_game_radius",1), Attr("a_relative_radius",1))
    ),
//language=GLSL
"""
precision mediump float;
void main(void) {
  gl_FragColor = vec4(0.3,0.3,0.3,0.4);
}
""")
  val backgroundShader = ShaderFull(ShaderVertex(shader_mesh_default_vert, listOf(Attr("aVertexPosition",2))), shader_background_stars_frag)
  private val imgCache:MutableMap<ImgData,ImgCache> = hashMapOf()
  var mousePos:XY = XY()
  var model:ClientModel = ClientModel(Conf(5000))
//  val model:ClientModel? = ClientModel(Conf(5000, "192.168.100.7"))
//  val model:ClientModel? = Model(Conf(80, "mass-power.herokuapp.com"))

  init {
    window.onfocus
    window.onblur
    window.onresize = {resize()}
    window.onload = {resize()}
    window.requestAnimationFrame {
      backgroundShader.activate()
      textureShader.activate()
      if(false) setUniform1i("u_sampler", 0)
      setUniform1f("u_game_width", view.gameWidth)
      setUniform1f("u_game_height", view.gameHeight)

      colorShader.activate()

      gl.enable(WGL.BLEND)
      if(!DYNAMIC_BLEND) gl.blendFunc(defaultBlend.src.value,defaultBlend.dst.value)
      gameLoop(it)
    }

    infix fun View.screenToGame(screen:XY) = XY(
      (screen.x-borderLeft)*gameWidth/windowWidth - gameWidth/2 + cameraGamePos.x,
      gameHeight/2-(screen.y-borderTop)*gameHeight/windowHeight + cameraGamePos.y
    )
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

  val MouseEvent.xy get() = XY(getX(html.container), getY(html.container))

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
  var cameraGamePos = XY(0f,0f)

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

    backgroundShader.activate()
//      gl.uniform1f(gl.getUniformLocation(backgroundShader,"resolution"),width,height)
    setUniform1f("time", lib.pillarTimeS(10_000f).toFloat())
//      gl.uniform1f(gl.getUniformLocation(backgroundShader,"mouse"),backgroundOffset.xf,backgroundOffset.yf)
    render(Mode.TRIANGLE,
      -1f,-1f,  -1f,1f,  1f,-1f,
      1f,1f,  -1f,1f,  1f,-1f)

    colorShader.activate()
    state?.reactive?.forEach {
      val fan = CircleData(defaultBlend){angle->
        floatArrayOf(/*it.pos.x.toFloat(),it.pos.y.toFloat()*/)
      }
      renderCircle10(it.pos.x.toFloat(), it.pos.y.toFloat(), it.radius*1.3f, null,fan)
    }

    textureShader.activate()
    mutableListOf<RenderData>().apply {
      if(state != null) {
        state.foods.forEach {add(RenderData(it.pos.x.toFloat(),it.pos.y.toFloat(),it.radius,imgGray))}
        if(true) state.reactive.forEach {add(RenderData(it.pos.x.toFloat(),it.pos.y.toFloat(),it.radius,it.owner.color))}
        state.cars.forEach {
          if(it.owner == model?.welcome?.id) {
            cameraGamePos = it.pos.copy()
            setUniform1f("u_game_camera_x", it.pos.x.toFloat())
            setUniform1f("u_game_camera_y", it.pos.y.toFloat())
          }
          add(RenderData(it.pos.x.toFloat(),it.pos.y.toFloat(),it.radius,it.owner.color))
        }
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
          val fan = CircleData(defaultBlend) {angle-> floatArrayOf(/*it.x,it.y*/)}
          val strip = CircleData(stripBlend) {angle->
            floatArrayOf(/*it.x,it.y*/)
          }
          if(state != null) {//todo redundant state!=null
            val (x,y) = model.calcRenderXY(state,XY(it.x,it.y),cameraGamePos)
            renderCircle10(x.toFloat(), y.toFloat(), it.gameSize, glTexture,fan,strip, 0.75f)
          }
        }
      }
    window.requestAnimationFrame(::gameLoop)
  }

  class CircleData(val blend:Blend, val getArr:(angle:Float)->FloatArray)
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

  fun angle(i:Int,max:Int) = 2*kotlin.math.PI.toFloat()*i/max
  val radian10 = (0..9).toList().map {angle(it,10)}.toFloatArray()//todo попробовать закэшировать значения радиан в шейдерах (на лету генерить string шейдера) и передавать индексы
  fun renderCircle10(gameX:Float, gameY:Float, gameRadius:Float, texture:WebGLTexture?, fan:CircleData, strip:CircleData? = null, stripRelativeDistance:Float = 0.75f) {//noinline better performance
    if(texture != null) gl.bindTexture(WGL.TEXTURE_2D,texture)//-2fps
    val x = gameX
    val y = gameY
    val notUsed = 0f
    val gr = gameRadius
    val r1 = 1f//Радиус 1f - окружность
    val r0 = 0f//центр круга
    val center = fan.getArr(0f)
    val f0 = fan.getArr(radian10[0])
    val f1 = fan.getArr(radian10[1])
    val f2 = fan.getArr(radian10[2])
    val f3 = fan.getArr(radian10[3])
    val f4 = fan.getArr(radian10[4])
    val f5 = fan.getArr(radian10[5])
    val f6 = fan.getArr(radian10[6])
    val f7 = fan.getArr(radian10[7])
    val f8 = fan.getArr(radian10[8])
    val f9 = fan.getArr(radian10[9])
    if(DYNAMIC_BLEND) gl.blendFunc(fan.blend.src.value,fan.blend.dst.value)
    render(Mode.TRIANGLE_FAN,
      x,y,notUsed,gr,r0,*center,
      x,y,radian10[0],gr,r1,*f0,
      x,y,radian10[1],gr,r1,*f1,
      x,y,radian10[2],gr,r1,*f2,
      x,y,radian10[3],gr,r1,*f3,
      x,y,radian10[4],gr,r1,*f4,
      x,y,radian10[5],gr,r1,*f5,
      x,y,radian10[6],gr,r1,*f6,
      x,y,radian10[7],gr,r1,*f7,
      x,y,radian10[8],gr,r1,*f8,
      x,y,radian10[9],gr,r1,*f9,
      x,y,radian10[0],gr,r1,*f0
    )
    if(strip != null) {
      val s0 = strip.getArr(radian10[0])
      val s1 = strip.getArr(radian10[1])
      val s2 = strip.getArr(radian10[2])
      val s3 = strip.getArr(radian10[3])
      val s4 = strip.getArr(radian10[4])
      val s5 = strip.getArr(radian10[5])
      val s6 = strip.getArr(radian10[6])
      val s7 = strip.getArr(radian10[7])
      val s8 = strip.getArr(radian10[8])
      val s9 = strip.getArr(radian10[9])
      if(DYNAMIC_BLEND) gl.blendFunc(strip.blend.src.value,strip.blend.dst.value)
      val rs = 1.0f + stripRelativeDistance//за кругом glow radius
      render(Mode.TRIANGLE_STRIP,
        x,y,radian10[0],gr,r1,*f0,x,y,radian10[0],gr,rs,*s0,
        x,y,radian10[1],gr,r1,*f1,x,y,radian10[1],gr,rs,*s1,
        x,y,radian10[2],gr,r1,*f2,x,y,radian10[2],gr,rs,*s2,
        x,y,radian10[3],gr,r1,*f3,x,y,radian10[3],gr,rs,*s3,
        x,y,radian10[4],gr,r1,*f4,x,y,radian10[4],gr,rs,*s4,
        x,y,radian10[5],gr,r1,*f5,x,y,radian10[5],gr,rs,*s5,
        x,y,radian10[6],gr,r1,*f6,x,y,radian10[6],gr,rs,*s6,
        x,y,radian10[7],gr,r1,*f7,x,y,radian10[7],gr,rs,*s7,
        x,y,radian10[8],gr,r1,*f8,x,y,radian10[8],gr,rs,*s8,
        x,y,radian10[9],gr,r1,*f9,x,y,radian10[9],gr,rs,*s9,
        x,y,radian10[0],gr,r1,*f0,x,y,radian10[0],gr,rs,*s0
      )
    }
  }

  inline fun render(mode:Mode,vararg allArgs:Float) = render(mode,allArgs)
  inline fun render(mode:Mode,allArgs:FloatArray) = render(mode,if(true) allArgs as Float32Array else Float32Array(allArgs.toTypedArray()),allArgs.size)
  inline fun MutableList<Float>.vert(vararg args:Float) = addAll(args.toList())//todo check why asList doesn't working
  inline fun render(mode:Mode,lambda:MutableList<Float>.()->Unit) = render(mode,arrayListOf<Float>().also {it.lambda()}.toFloatArray())
  inline fun render(mode:Mode,mesh:Float32Array,allFloatArgsCount:Int) {
    lib.debug {
      if(allFloatArgsCount<=0) lib.log.error("allFloatArgsCount<=0")
      if(allFloatArgsCount%currentShader?.blockSize!! !=0) lib.log.error("Number of vertices not a multiple of the attribute block size! allFloatArgsCount: $allFloatArgsCount,  verticesBlockSize: ${currentShader?.blockSize!!}")
    }
    if(true) gl.activeTexture(WGL.TEXTURE0)
    gl.bufferData(WGL.ARRAY_BUFFER,mesh,WGL.DYNAMIC_DRAW)
    gl.drawArrays(mode.glMode,0,allFloatArgsCount/currentShader?.blockSize!!)//todo first, count
  }

  lateinit var currentShader:ShaderFull
  val uniforms:MutableMap<String, Float> = mutableMapOf()
  fun setUniform1f(name:String, value:Float) {//todo vararg and int
    uniforms[name] = value
    gl.uniform1f(gl.getUniformLocation(currentShader.shaderProgram,name),value)
  }
  fun setUniform1i(s:String,i:Int) {TODO("setUniform1i")}
  inner class ShaderVertex(val src:String, val attrList:List<Attr>)
  inner class ShaderFull(val vert:ShaderVertex, frag:String) {
    val shaderProgram = gl.createWebGLProgram(vert.src,frag)
    val attributes = vert.attrList.run {
      val result = mutableListOf<IterAttr>()
      var currentSize = 0
      forEach {
        result.add(IterAttr(it,gl.getAttribLocation(shaderProgram,it.locationName),currentSize))
        currentSize += it.numElements
      }
      result
    }
    val blockSize = attributes.sumBy {it.attr.numElements}
    val buffer = gl.createBuffer() ?: lib.log.fatalError("Unable to create webgl buffer!")
    fun activate() {
      currentShader = this
      gl.bindBuffer(WGL.ARRAY_BUFFER,buffer)
      gl.useProgram(shaderProgram)
      uniforms.entries.forEach {(k,v)-> gl.uniform1f(gl.getUniformLocation(currentShader.shaderProgram,k),v)}//todo optimize
      attributes.forEach {
        gl.enableVertexAttribArray(it.location)
        gl.vertexAttribPointer(it.location,it.attr.numElements,WGL.FLOAT,false,/*шаг*/blockSize*4,it.offset*4)
        if(false) gl.disableVertexAttribArray(it.location)//Если нужно после рендера отключить эти атрибуты (вероятно чтобы иметь возможность задать новые атрибуты для другого шейдера)
      }
    }
  }
}

enum class Mode(val glMode:Int) {
  TRIANGLE(WGL.TRIANGLES),
  TRIANGLE_FAN(WGL.TRIANGLE_FAN),
  TRIANGLE_STRIP(WGL.TRIANGLE_STRIP)
}