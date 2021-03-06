package masspower

import com.riseofcat.client.*
import com.riseofcat.lib.*
import com.riseofcat.share.mass.*
import kuden.*
import org.khronos.webgl.*
import org.w3c.dom.*
import org.w3c.dom.events.*
import util.*
import kotlin.browser.*
import kotlin.js.*
import kotlin.reflect.*
import org.khronos.webgl.WebGLRenderingContext as WGL

const val FOOD_SCALE = 1.3f
const val TEXT = true
const val FAKE_PING = false
const val HIDDEN = false
const val SLOW_POKE = false
data class ImgData(val url:String)
class ImgCache(var texture:MassPower.GameTexture? = null)
data class RenderData(val x:Float,val y:Float,val gameSize:Float,val imgData:ImgData)
abstract class View {
  abstract fun getWidth(aspectRation:Float):Float
  abstract fun getHeight(aspectRation:Float):Float
}
class FixedWidth(val width:Float,val minHeight:Float,val maxHeight:Float):View() {
  override fun getWidth(aspectRation:Float) = width
  override fun getHeight(aspectRation:Float) = (width/aspectRation).max(minHeight).min(maxHeight)
}
data class Attr(val locationName:String,val numElements:Int)
data class IterAttr(val attr:Attr,val location:Int,val offset:Int)
data class Color(val r:Float, val g:Float, val b:Float)
class MassPower(val view:View = FixedWidth(1000f,1000f,1000f)) {//todo 1500 width
  val View.gameWidth:Float get() = getWidth(window.innerWidth/window.innerHeight.toFloat())*gameScale.toFloat()
  val View.gameHeight:Float get() = getHeight(window.innerWidth/window.innerHeight.toFloat())*gameScale.toFloat()
  val View.windowWidth get() = window.innerWidth.min(window.innerHeight*gameWidth/gameHeight)
  val View.windowHeight get() = window.innerHeight.min(window.innerWidth*gameHeight/gameWidth)
  val View.borderLeft get() = (window.innerWidth-windowWidth)/2
  val View.borderTop get() = (window.innerHeight-windowHeight)/2

  val gameScale by SmoothByRenderCalls {targetGameScale}
  var targetGameScale = 3.0
  var myCar:Car? = null
  var previousCameraPos = XY()
  inline val cameraGamePos get() = myCar?.pos?.also {previousCameraPos = it} ?: previousCameraPos
  val html = HTMLElements()
  val gl get() = html.webgl
  val textureShader:ShaderFull = ShaderFull(ShaderVertex(MASS_POWER_TEXTURE_VERTEX,listOf(/*Attr("a_center_pos",2),*/ Attr("a_center_x",1),Attr("a_center_y",1),Attr("a_angle",1),Attr("a_game_radius",1),Attr("a_relative_radius",1))),MASS_POWER_TEXTURE_FRAG)
  val foodShader:ShaderFull = ShaderFull(ShaderVertex(MASS_POWER_FOOD_VERTEX,listOf(/*Attr("a_center_pos",2),*/ Attr("a_center_x",1),Attr("a_center_y",1),Attr("a_angle",1),Attr("a_game_radius",1),Attr("a_color",4))),MASS_POWER_FOOD_FRAG)
  val reactiveShader:ShaderFull = ShaderFull(ShaderVertex(MASS_POWER_REACTIVE_VERTEX,listOf(/*Attr("a_center_pos",2),*/ Attr("a_center_x",1),Attr("a_center_y",1),Attr("a_angle",1),Attr("a_game_radius",1),Attr("a_color",4))),MASS_POWER_FOOD_FRAG)
  val backgroundShader = ShaderFull(ShaderVertex(shader_mesh_default_vert, listOf(Attr("aVertexPosition",2))), shader_background_stars_frag)
  private val imgCache:MutableMap<ImgData,ImgCache> = mutableMapOf()
  var mousePos:XY = XY()
  val fakePingClient = FakePingClient<ServerPayload,ClientPayload>(ServerPayload(
    stableTick = Tick(0),
    welcome = Welcome(PlayerId(1),lib.time),
    stable = State(
      cars = mutableListOf(Car(PlayerId(1),GameConst.DEFAULT_CAR_SIZE*3,XY(),XY()))),
      recommendedLatency = Duration(10),
      actions = mutableListOf<AllCommand>().apply {
        for(i in 2..100) {
          val pid = PlayerId(i)
          add(AllCommand(Tick(10+i*1),pid,NewCarCommand(pid)))
        }
    }
  ))
  var model:ClientModel = ClientModel(
    if(FAKE_PING) {
      fakePingClient
    } else {
      confs.current.pingClient()
    }, slowpoke = SLOW_POKE
  )

  var mouseDown = false
  infix fun View.screenToGame(screen:XY) = XY(
    (screen.x-borderLeft)*gameWidth/windowWidth - gameWidth/2 + cameraGamePos.x,
    gameHeight/2-(screen.y-borderTop)*gameHeight/windowHeight + cameraGamePos.y
  )
  init {
    window.onfocus
    window.onblur
    window.onresize = {resize()}
    window.onload = {resize()}
    window.requestAnimationFrame {
      backgroundShader.activate()
      textureShader.activate()
      gl.enable(WGL.BLEND)
      gameLoop(it)
    }

    document.onmousemove = fun(event:Event) {
      if(event is MouseEvent) {
        mousePos = view screenToGame event.xy
      }
    }
    document.onclick = fun(event:Event) {
      if(event is MouseEvent) {
        model._touch(myCar?.pos, view screenToGame event.xy)
      }
    }
    document.onmousedown = fun(event:Event) {
      mouseDown = true
    }
    document.onmouseup = fun(event:Event) {
      mouseDown = false
    }
    document.onkeypress = fun(event:Event) {
      if(event is KeyboardEvent) {
        val code = event.keyCode
        val key = Key.getByCode(code)
        when(key) {
          Key.MINUS->userScale*=1.3
          Key.PLUS->userScale/=1.3
        }

      }
    }
  }

  var userScale = 1.0

  val MouseEvent.xy get() = XY(getX(html.container), getY(html.container))

  inner class GameTexture(val glTexture:WebGLTexture,val width:Int,val height:Int) {
    val left = -width/2f
    val right = width/2f
    val bottom = -height/2f
    val top = height/2f
  }

  private fun resize() {
    gl.canvas.width = view.windowWidth.toInt()
    gl.canvas.height = view.windowWidth.toInt()
    html.canvas2d.canvas.width = view.windowWidth.toInt()
    html.canvas2d.canvas.height = view.windowWidth.toInt()
    gl.viewport(0,0,view.windowWidth.toInt(),view.windowWidth.toInt())
    gl.canvas.setAttribute("style","position: absolute; left: ${view.borderLeft}px; top: ${view.borderTop}px; z-index: 5; width: ${view.windowWidth}px; height: ${view.windowHeight}px;")
    html.canvas2d.canvas.setAttribute("style","position: absolute; left: ${view.borderLeft}px; top: ${view.borderTop}px; z-index: 10; width: ${view.windowWidth}px; height: ${view.windowHeight}px;")
    setUniformf("resolution", view.windowWidth, view.windowHeight)
  }

  val time get() = Date().getTime()/1000f
  var previousTime = time
  var fps30 = 30f
  var fps500 = 30f
  val defaultBlend = Blend(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA)
  val stripBlend = Blend(BlendFactor.SRC_ALPHA, if(true) BlendFactor.ONE_MINUS_SRC_ALPHA else BlendFactor.DST_ALPHA)
  val imgBig = ImgData("img/smiley.png")
  val imgRed = ImgData("img/smiley_small_rect_red.png")
  val imgGreen = ImgData("img/smiley_small_rect_green.png")
  val imgBlue = ImgData("img/smiley_small_rect_blue.png")
  val imgYellow = ImgData("img/smiley_small_rect_yellow.png")
  val imgViolet = ImgData("img/smiley_small_rect_violet.png")
  val imgGray = ImgData("img/smiley_small_rect_gray.png")
  val imgNonQuadrat = ImgData("img/rect_long.png")
  val pngs = listOf(imgRed,imgGreen,imgBlue,imgYellow,imgViolet)
  val PlayerId.png get() = pngs.let {it[id%it.size]}
  val red = Color(1f,0f,0f)
  val green = Color(0f,1f,0f)
  val blue = Color(0f,0f,1f)
  val yellow = Color(0.9f,0.9f,0.5f)
  val violet = Color(0.9f,0.5f,0.9f)
  val colors = listOf(red, green, blue, yellow, violet)
  val PlayerId.color get() = colors.let {it[id%it.size]}

  inner class BackOffset {
    var previousRelativeCameraPos:XY?=null
    var previousResult:XY=XY()
    fun getValue(state:State):XY {
      var result = previousResult
      val relativeCameraPos = cameraGamePos.scale(XY(1/state.width.toDouble(),1/state.height.toDouble()))
      previousRelativeCameraPos?.let {
        val change = relativeCameraPos-it
        if(change.x>0.5) change.x = change.x-1
        else if(change.x<-0.5) change.x = change.x+1
        if(change.y>0.5) change.y = change.y-1
        else if(change.y<-0.5) change.y = change.y+1
        result += change*0.3//скорость задника
      }
      previousResult = result
      previousRelativeCameraPos = relativeCameraPos
      return result
    }
  }
  val backOffset = BackOffset()

  var previousMouseDownHandle = lib.time

  private fun gameLoop(милисекундСоСтараПлюсБездействие:Double):Unit = lib.saveInvoke {
    if(lib.time > previousMouseDownHandle + Duration(300)) {
      previousMouseDownHandle = lib.time
      if(mouseDown) {
        model._touch(myCar?.pos, mousePos)
      }
    }
    if(false)onRender()
    fps30 = (fps30*30+1f/(time-previousTime)).toFloat()/(30+1)
    fps500 = (fps500*200+1f/(time-previousTime)).toFloat()/(200+1)
    previousTime = time

    val state = model.calcDisplayState()

    if(TEXT) {
      val lines:MutableList<String> = mutableListOf()
      if(model.ping is FakePingClient) {
        lib.release {
          lines.add("Сервер не отвечает")
          lines.add("Наверное попал под блокировку")
          lines.add("OFFLINE режим")
        }
      } else {
        lines.add(model.ping.state.toString())
        if(!model.ping.state.good) {
          model.ping.close()
          model = ClientModel(fakePingClient)
        }
      }
      lines.add("fps: ${lib.formatDouble(fps30.toDouble(), 2)}")
      lib.debug {
        if(false) lines.add("mouse: ${mousePos}")
        lines.add(Gen.date())
        if(false)lines.add("realtimeTick: " +model.realtimeTick)
        if(false)lines.add("serverTime: " +model.ping.serverTime.s)
        if(false)lines.add("smartPingDelay: " +model.ping.smartPingDelay)
        lines.add("size: ${state.size}")
        lines.add("foods: ${state.foods.size}")
      }
      html.canvas2d.clearRect(0.0,0.0,view.gameWidth.toDouble(),view.gameHeight.toDouble())//todo why gameWidth?
      html.canvas2d.fillStyle = "white"
      html.canvas2d.font = "bold 24pt Arial"
      for(i in 0 until lines.size) html.canvas2d.fillText(lines[i],10.0,10.0+50*(i+1))
    }
    gl.clearColor(0f,0f,0f,1f)
    gl.clear(WGL.COLOR_BUFFER_BIT)
    myCar = model.welcome?.id?.let{state.getCar(it)}
    onRender()
    myCar?.let {
      setUniformf("u_game_camera_x", cameraGamePos.x.toFloat())
      setUniformf("u_game_camera_y", cameraGamePos.y.toFloat())
    }
    val (offsetX, offsetY) = backOffset.getValue(state)
    setUniformf("mouse", offsetX.toFloat(), offsetY.toFloat())
    setUniformf("u_game_width", view.gameWidth)
    setUniformf("u_game_height", view.gameHeight)
    backgroundShader.activate()
    val pow2in14:Float = 1024f*2*2*2*2
    setUniformf("time", pow2in14 - lib.pillarTimeS(2*pow2in14).toFloat())//lowp от -2.0 до 2.0
    render(Mode.TRIANGLE,-1f,-1f,-1f,1f,1f,-1f,1f,1f,-1f,1f,1f,-1f)
    foodShader.activate()
    val filterRadius = (view.gameWidth+view.gameHeight)/2/1.42
    state.foods.asSequence().filter{state.distance(cameraGamePos,it.pos)<filterRadius}.forEach {
      val xy = calcRenderXY(state,it.pos,cameraGamePos)
      val fan = CircleData(defaultBlend){angle-> floatArrayOf(0f, 0f, 0f, 0f)}
      renderCircle10(xy.x.toFloat(), xy.y.toFloat(), it.radius*FOOD_SCALE, null,floatArrayOf(1.5f, 1.5f, 1.5f, 1f),fan)
    }
    reactiveShader.activate()
    state.reactive.asSequence().filter{state.distance(cameraGamePos,it.pos)<filterRadius}.forEach {
      val clr = it.owner.color
      val xy = calcRenderXY(state,it.pos,cameraGamePos)
      val fan = CircleData(defaultBlend){angle-> floatArrayOf(0f, 0f, 0f, 0f)}
      renderCircle10(xy.x.toFloat(), xy.y.toFloat(), it.radius*1.43f, null,floatArrayOf(clr.r, clr.g, clr.b, 1f),fan)
    }

    textureShader.activate()
    val filtered = lib.measure("filter") {state.cars.filter{state.distance(cameraGamePos,it.pos)-it.radius<filterRadius}}
    lib.measure("sort cars") {
      filtered
        .toMutableList()
        .apply{sortBy{it.size}}//todo сделать более умную сортировку чтобы не каждый кадр
    }.forEach {car->
      val rd = RenderData(car.pos.x.toFloat(),car.pos.y.toFloat(),car.radius,car.owner.png)
      val cache = imgCache[rd.imgData] ?: ImgCache().apply {
        imgCache[rd.imgData] = this
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
        img.src = rd.imgData.url
      }
      cache.texture?.apply {
        val fan = CircleData(defaultBlend) {angle-> floatArrayOf(1f)}
        val strip = CircleData(stripBlend) {angle-> floatArrayOf(1.75f)}
        val (x,y) = calcRenderXY(state,XY(rd.x,rd.y),cameraGamePos)
        renderCircle10(x.toFloat(), y.toFloat(), rd.gameSize, glTexture,floatArrayOf(0f),fan,strip)
      }
    }
    if(HIDDEN) {
      gl.clearColor(1f,1f,1f,1f)
      gl.clear(WGL.COLOR_BUFFER_BIT)
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
  fun renderCircle10(gameX:Float, gameY:Float, gameRadius:Float, texture:WebGLTexture?, center:FloatArray, fan:CircleData, strip:CircleData? = null, stripRelativeWidth:Float = 0.5f) {//noinline better performance
    if(texture != null) gl.bindTexture(WGL.TEXTURE_2D,texture)//-2fps
    val x = gameX
    val y = gameY
    val notUsed = 0f
    val r0 = 0f
    val gr = gameRadius
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
    gl.blendFunc(fan.blend.src.value,fan.blend.dst.value)
    render(Mode.TRIANGLE_FAN,
      x,y,notUsed,r0,*center,
      x,y,radian10[0],gr,*f0,
      x,y,radian10[1],gr,*f1,
      x,y,radian10[2],gr,*f2,
      x,y,radian10[3],gr,*f3,
      x,y,radian10[4],gr,*f4,
      x,y,radian10[5],gr,*f5,
      x,y,radian10[6],gr,*f6,
      x,y,radian10[7],gr,*f7,
      x,y,radian10[8],gr,*f8,
      x,y,radian10[9],gr,*f9,
      x,y,radian10[0],gr,*f0
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
      val stripRadius = (1f+stripRelativeWidth)*gr
      gl.blendFunc(strip.blend.src.value,strip.blend.dst.value)
      render(Mode.TRIANGLE_STRIP,
        x,y,radian10[0],gr,*f0,x,y,radian10[0],stripRadius,*s0,
        x,y,radian10[1],gr,*f1,x,y,radian10[1],stripRadius,*s1,
        x,y,radian10[2],gr,*f2,x,y,radian10[2],stripRadius,*s2,
        x,y,radian10[3],gr,*f3,x,y,radian10[3],stripRadius,*s3,
        x,y,radian10[4],gr,*f4,x,y,radian10[4],stripRadius,*s4,
        x,y,radian10[5],gr,*f5,x,y,radian10[5],stripRadius,*s5,
        x,y,radian10[6],gr,*f6,x,y,radian10[6],stripRadius,*s6,
        x,y,radian10[7],gr,*f7,x,y,radian10[7],stripRadius,*s7,
        x,y,radian10[8],gr,*f8,x,y,radian10[8],stripRadius,*s8,
        x,y,radian10[9],gr,*f9,x,y,radian10[9],stripRadius,*s9,
        x,y,radian10[0],gr,*f0,x,y,radian10[0],stripRadius,*s0
      )
    }
  }

  inline fun render(mode:Mode,vararg allArgs:Float) = render(mode,allArgs)
  inline fun render(mode:Mode,allArgs:FloatArray) = render(mode,if(true) allArgs as Float32Array else Float32Array(allArgs.toTypedArray()),allArgs.size)
  inline fun MutableList<Float>.vert(vararg args:Float) = addAll(args.toList())//todo check why asList doesn't working
  inline fun render(mode:Mode,lambda:MutableList<Float>.()->Unit) = render(mode,mutableListOf<Float>().also {it.lambda()}.toFloatArray())
  inline fun render(mode:Mode,mesh:Float32Array,allFloatArgsCount:Int) {
    lib.debug {
      if(allFloatArgsCount<=0) lib.log.error("allFloatArgsCount<=0")
      if(allFloatArgsCount%currentShader?.blockSize!! !=0) lib.log.error("Number of vertices not a multiple of the attribute block size! allFloatArgsCount: $allFloatArgsCount,  verticesBlockSize: ${currentShader?.blockSize!!}")
    }
    if(true) gl.activeTexture(WGL.TEXTURE0)
    gl.bufferData(WGL.ARRAY_BUFFER,mesh,WGL.DYNAMIC_DRAW)
    gl.drawArrays(mode.glMode,0,allFloatArgsCount/currentShader?.blockSize!!)//todo first, count
  }

  var currentShader:ShaderFull?=null
  val uniforms:MutableMap<String, FloatArray> = mutableMapOf()
  fun setUniformf(name:String, vararg values:Float) {
    uniforms[name] = values
    _setUniform(name, values)
  }
  fun _setUniform(name:String, values:FloatArray) = currentShader?.run {
    val uniformLocation = gl.getUniformLocation(shaderProgram,name)
    when(values.size) {
      1 -> gl.uniform1f(uniformLocation,values[0])
      2 -> gl.uniform2f(uniformLocation,values[0], values[1])
    }
  }
  inner class ShaderVertex(val src:String, val attrList:List<Attr>)
  inner class ShaderFull(val vert:ShaderVertex, frag:String) {
    val shaderProgram = gl.createWebGLProgram(vert.src,frag)//todo test performance с одной текстурой атласом
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
      uniforms.entries.forEach {(k,v)-> _setUniform(k,v)}//todo optimize _setUniform
      attributes.forEach {
        gl.enableVertexAttribArray(it.location)
        val BYTES = Float32Array.BYTES_PER_ELEMENT//4
        gl.vertexAttribPointer(it.location,it.attr.numElements,WGL.FLOAT,false,/*шаг*/blockSize*BYTES,it.offset*BYTES)
        if(false) gl.disableVertexAttribArray(it.location)//Если нужно после рендера отключить эти атрибуты (вероятно чтобы иметь возможность задать новые атрибуты для другого шейдера)
      }
    }
  }

  fun onRender() {
    renderCalls++
    val car = if(true) myCar else model.myCar
    if(car!=null) {
      val result = 1.5f*lib.Fun.arg0toInf(car.size.radius,GameConst.DEFAULT_CAR_SIZE.radius)+3*lib.Fun.arg0toInf(car.speed.len,1000.0)
      targetGameScale = kotlin.math.max(result,1.0) * userScale
    } else {
      targetGameScale = 3.0  * userScale
    }
  }

}

enum class Mode(val glMode:Int) {
  TRIANGLE(WGL.TRIANGLES),
  TRIANGLE_FAN(WGL.TRIANGLE_FAN),
  TRIANGLE_STRIP(WGL.TRIANGLE_STRIP)
}

var renderCalls:Int = 0
class SmoothByRenderCalls(val lambda:()->Double?) {
  var current:Double? = null
  var currentRenderCall:Int?=null
  operator fun getValue(t:Any,property:KProperty<*>):Double {
    if(currentRenderCall != renderCalls) {
      var result = current?:0.0
      val target = lambda()
      if(target != null) {
        result += (target-result)/30
      }
      current = result
      currentRenderCall = renderCalls
    }
    return current?:0.0
  }
}
class CacheByRenderCalls<T,V:Any>(val lambda:()->V) {
  lateinit var cache:V
  var cachedRenderCall:Int?=null
  operator fun getValue(t:T,property:KProperty<*>):V {
    if(!this::cache.isInitialized || cachedRenderCall!=renderCalls) {
      cachedRenderCall=renderCalls
      cache = lambda()
    }
    return cache
  }

}

//TODO ЕСЛИ ТУТ ВСЁ ОТКАТИТЬ ТО НЕ ДЁРГАЕТСЯ