package kuden

import com.riseofcat.lib.*
import org.khronos.webgl.*
import org.w3c.dom.*
import org.w3c.dom.events.*
import util.*
import kotlin.browser.*
import kotlin.collections.set
import kotlin.js.*

enum class DrawMode { LINEAR, NEAREST }
class HTMLElements {
  val container = document.createElement("div",HTMLElement::class).apply {setAttribute("style","position: absolute; left: 0px; top: 0px;")}
  val canvas2d:CanvasRenderingContext2D
  val webgl:WebGLRenderingContext
  private val webGlCanvas = document.createCanvas("position: absolute; left: 0px; top: 0px;")

  init {
    document.body!!.appendChild(container)
    container.appendChild(webGlCanvas)
    val webglcanvas = webGlCanvas.getContext("webgl") ?: webGlCanvas.getContext("experimental-webgl")
    if(webglcanvas!=null) webgl = webglcanvas as WebGLRenderingContext
    else lib.log.fatalError("Your browser doesn't seem to support webgl!$webglcanvas")
    canvas2d = document.createCanvas(
      "position: absolute; left: 0px; top: 0px; z-index: 10; width: 1000px; height: 500px;")
      .apply {container.appendChild(this)}//todo resize
      .getContext("2d") as CanvasRenderingContext2D
  }

  fun resize() {
    if(webGlCanvas.width!=window.innerWidth||webGlCanvas.height!=window.innerHeight) {
      webGlCanvas.width = window.innerWidth//.setAttribute("width", "${width}px")
      webGlCanvas.height = window.innerHeight//.setAttribute("height", "${height}px")
//            canvas.setAttribute("style", "position: absolute; left: 0px; top: 0px; z-index: 5; width: ${windowWidth}px; height: ${windowHeight}px;")
      webgl.viewport(0,0,webGlCanvas.width,webGlCanvas.height)
    }
  }
}

interface InputProcessor {
  fun keyPressed(charCode:Int)
  fun keyDown(keyCode:Int)
  fun keyUp(keyCode:Int)
  fun pointerClick(pointer:Int,x:Float,y:Float)
  //fun mouseMove(x: Float, y: Float)
}

open class EmptyInputProcessor:InputProcessor {
  override fun pointerClick(pointer:Int,x:Float,y:Float) {}
  override fun keyDown(keyCode:Int) {}
  override fun keyPressed(charCode:Int) {}
  override fun keyUp(keyCode:Int) {}
  //override fun mouseMove(x: Float, y: Float) { }
}

data class RGBA(var r:Float,var g:Float,var b:Float,var a:Float = 1.0f)
abstract class Game(sizeType:SizeType,val clearColor:RGBA? = null):InputProcessor {
  private val html by lazy {HTMLElements()}//todo порядок имеет значение
  val view = View(sizeType)
  val textures:Textures = Textures()//todo порядок имеет значение
  private var start = Date().getTime()
  private var currentTime = start
  private var currentDelta = 0f
  private var pause:Boolean = false
  private var fps = 0
  private var fpsCount = 0
  private var fpsCountTime = 0f
  private var focus = true
  val keys:Keys = Keys(this)

  init {
    window.onfocus = {focus = true;""}
    window.onblur = {focus = false;""}
    window.onresize = {resize()}
    window.onload = {resize()}
    loadResources()
    window.requestAnimationFrame(::gameLoop)
  }

  abstract fun loadResources()
  abstract fun unloadResources()
  abstract fun update(time:Float,delta:Float)
  abstract fun render()
  val gl get() = html.webgl
  private fun resize() {
    view.updateView()//todo//
    gl.canvas.width = view.gameWidth.toInt()
    gl.canvas.height = view.gameHeight.toInt()
    html.canvas2d.canvas.width = view.gameWidth.toInt()
    html.canvas2d.canvas.height = view.gameHeight.toInt()
    gl.viewport(0,0,view.gameWidth.toInt(),view.gameHeight.toInt())
    gl.canvas.setAttribute("style","position: absolute; left: ${view.borderLeft}px; top: ${view.borderTop}px; z-index: 5; width: ${view.windowWidth}px; height: ${view.windowHeight}px;")//todo change?
    html.canvas2d.canvas.setAttribute("style","position: absolute; left: ${view.borderLeft}px; top: ${view.borderTop}px; z-index: 10; width: ${view.windowWidth}px; height: ${view.windowHeight}px;")//todo change?
  }

  private fun gameLoop(наверноеDeltaTime:Double):Unit = lib.saveInvoke {
    if(false) resize()
    if(!pause) {
      html.canvas2d.clearRect(0.0,0.0,view.gameWidth.toDouble(),view.gameHeight.toDouble())
      if(clearColor!=null) {
        gl.clearColor(clearColor.r,clearColor.g,clearColor.b,clearColor.a)
        gl.clear(WebGLRenderingContext.COLOR_BUFFER_BIT)
      }
      gl.enable(WebGLRenderingContext.BLEND)
      gl.blendFunc(WebGLRenderingContext.SRC_ALPHA,WebGLRenderingContext.ONE_MINUS_SRC_ALPHA) //ONE_MINUS_DST_ALPHA);
      val time = Date().getTime()
      currentDelta = ((time-currentTime)/1000f).toFloat()
      currentTime = time
      val timeInSeconds = (currentTime-start)/1000f
      fpsCountTime += currentDelta
      fpsCount++
      while(fpsCountTime>1f) {
        fps = fpsCount
        fpsCountTime -= 1f
        fpsCount = 0
      }
      if(focus) update(timeInSeconds.toFloat(),currentDelta)
      render()
    }
    window.requestAnimationFrame(::gameLoop)//todo Если убрать requestAnimationFrame то ломается инициализация
  }

  fun drawText(x:Float,y:Float,message:String,font:String = "bold 24pt Arial",fillStyle:String = "white") {
    // TODO: use same coords for webgl and canvas 2d
    var yy = /* view.height -*/ y
    var xx = x
    if(yy<0) yy += view.gameHeight
    if(xx<0) xx += view.gameWidth
    yy = view.gameHeight-yy
    html.canvas2d.fillStyle = fillStyle
    html.canvas2d.font = font
    html.canvas2d.fillText(message,x.toDouble(),yy.toDouble())
  }

  abstract class Sprite {
    abstract val texture:Texture?
  }

  private val imgCache:MutableMap<ImgTexData,Texture> = hashMapOf()
  private val imgLoading:MutableSet<ImgTexData> = hashSetOf()

  inner class ImgSprite(private val imgTex:ImgTexData):Sprite() {
    override val texture
      get() =
        imgCache[imgTex].apply {
          if(this==null) {
            if(!imgLoading.contains(imgTex)) {
              imgLoading.add(imgTex)
              textures.get(imgTex) {imgCache[imgTex] = it}
            }
          }
        }
  }

  private val genCache:MutableMap<GenTexData<*>,Texture> = hashMapOf()

  inner class GenSprite<T>(private val genTex:GenTexData<T>):Sprite() {
    override val texture get() = genCache[genTex] ?: textures[genTex].apply {genCache[genTex] = this}
  }

  inner class SpriteBatch {
    fun draw(sprite:Sprite,x:Float,y:Float,scale:Float = 1f,rotation:Float = 0f) = sprite.texture?.queueDraw(x,y,scale,rotation)
    fun render() = textures.render()
  }

  class View(private val sizeType:SizeType,var drawMode:DrawMode = DrawMode.LINEAR) {
    val gameWidth:Float get() = sizeType.getWidth(window.innerWidth/window.innerHeight.toFloat())
    val gameHeight:Float get() = sizeType.getHeight(window.innerWidth/window.innerHeight.toFloat())
    val proectionMatrix = Matrix4()

    init {
      updateView()
    }

    fun requestFullscreen() {
      document.body?.callApply("requestFullscreen")
      document.body?.callApply("webkitRequestFullscreen")//chrome
      document.body?.callApply("mozRequestFullScreen")
      document.body?.callApply("msRequestFullscreen")
    }

    fun exitFullscreen() {
      document.callApply("exitFullscreen")
      document.callApply("mozCancelFullScreen")
      document.callApply("webkitExitFullscreen")
    }

    fun switchFullscreen() = if(isFullscreen()) exitFullscreen() else requestFullscreen()
    fun isFullscreen():Boolean = undefined!= /*language=JavaScript*/js("document.fullscreenElement || document.mozFullScreenElement || document.webkitFullscreenElement || document.msFullscreenElement")
    fun updateView() = proectionMatrix.setOrthographicProjection(0f,gameWidth,0f,gameHeight,-0.1f,-100f)
    val windowWidth get() = window.innerWidth.min(window.innerHeight*gameWidth/gameHeight)
    val windowHeight get() = window.innerHeight.min(window.innerWidth*gameHeight/gameWidth)
    val borderLeft get() = (window.innerWidth-windowWidth)/2
    val borderTop get() = (window.innerHeight-windowHeight)/2
    fun screenToGameCoordX(screenX:Float) = (screenX-borderLeft)*gameWidth/windowWidth
    fun screenToGameCoordY(screenY:Float) = gameHeight-(screenY-borderTop)*gameHeight/windowHeight
    fun gameToScreenCoordX(gameX:Float) = (gameX/gameWidth*windowWidth)+borderLeft
    fun gameToScreenCoordY(gameY:Float) = gameHeight-(gameY/gameHeight*windowHeight)+borderTop
  }

  inner class Textures {
    private var textures:MutableList<Texture> = arrayListOf()
    private val shaderProgram:ShaderProgram<TextureData> = ShaderProgram(gl,DType.TRIANGLE,vertxSrc,fragmSrc,
      arrayOf(
        VertextAttributeInfo("a_position",2),
        VertextAttributeInfo("a_boundingBox",2),
        VertextAttributeInfo("a_texCoord",2),
        VertextAttributeInfo("a_scale",1),
        VertextAttributeInfo("a_rotation",1))) {
      it.program.setUniform1i("u_sampler",0)
      it.program.setUniformMatrix4fv("u_projectionView",it.data.vMatrix.toFloat32Arr())
      it.program.setUniform1i("u_test_array_size",5)
      it.program.setUniform1fv("u_arr[0]",arrayOf(0.1f,0.1f))
    }

    private fun load(filename:String,callback:(t:Texture)->Unit) =
      document.createElement("img",HTMLImageElement::class).let /*todo createElement("img") is Bad*/{img->
          img.onload = {callback(temp1_1(gl.createTexture() ?: lib.log.fatalError("Couldn't create webgl texture!"),img))}
          img.src = filename
        }

    fun create(image:HTMLImageElement) = temp1_1(gl.createTexture() ?: lib.log.fatalError("Couldn't create webgl texture!"),image)

    private fun temp0(texture:WebGLTexture) {
      gl.bindTexture(WebGLRenderingContext.TEXTURE_2D,texture)
      gl.pixelStorei(WebGLRenderingContext.UNPACK_FLIP_Y_WEBGL,1) // second argument must be an int
    }

    private fun temp1_1(texture:WebGLTexture,image:HTMLImageElement) = run {
      temp0(texture)
      gl.texImage2D(WebGLRenderingContext.TEXTURE_2D,0,WebGLRenderingContext.RGBA,WebGLRenderingContext.RGBA,WebGLRenderingContext.UNSIGNED_BYTE,image)
      temp2(texture,image.width,image.height)
    }

    private fun temp1_2(texture:WebGLTexture,width:Int,height:Int,imageBuf:ArrayBufferView):Texture {
      temp0(texture)
      gl.texImage2D(WebGLRenderingContext.TEXTURE_2D,0,WebGLRenderingContext.RGBA,width,height,0,WebGLRenderingContext.RGBA,WebGLRenderingContext.UNSIGNED_BYTE,imageBuf)
      return temp2(texture,width,height)
    }

    private fun temp2(webGlTexture:WebGLTexture,width:Int,height:Int):Texture {
      val param = if(view.drawMode==DrawMode.NEAREST) WebGLRenderingContext.NEAREST else WebGLRenderingContext.LINEAR
      gl.texParameteri(WebGLRenderingContext.TEXTURE_2D,WebGLRenderingContext.TEXTURE_MAG_FILTER,param)
      gl.texParameteri(WebGLRenderingContext.TEXTURE_2D,WebGLRenderingContext.TEXTURE_MIN_FILTER,param)
      gl.texParameteri(WebGLRenderingContext.TEXTURE_2D,WebGLRenderingContext.TEXTURE_WRAP_T,WebGLRenderingContext.CLAMP_TO_EDGE)
      gl.texParameteri(WebGLRenderingContext.TEXTURE_2D,WebGLRenderingContext.TEXTURE_WRAP_S,WebGLRenderingContext.CLAMP_TO_EDGE)
      gl.bindTexture(WebGLRenderingContext.TEXTURE_2D,null)
      return Texture(webGlTexture,shaderProgram,width,height).apply {textures.add(this)}
    }

    private fun create(width:Int,height:Int,imageBuf:ArrayBufferView) = temp1_2(gl.createTexture() ?: lib.log.fatalError("Couldn't create webgl texture!"),width,height,imageBuf)
    operator fun get(imgTex:ImgTexData,f:(t:Texture)->Unit) = load(imgTex.url,f)
    operator fun <T> get(genTex:GenTexData<T>):Texture = create(genTex.width,genTex.height,genTex.f(genTex.t))
    fun render() = textures.forEach {it.render()}
    fun dispose() = textures.forEach {gl.deleteTexture(it.glTexture)}
    fun clear():Unit = TODO("not implemented")
  }

  data class ImgTexData(val url:String)
  data class GenTexData<T>(val width:Int,val height:Int,val t:T,val f:(t:T)->ArrayBufferView)
  inner class Texture(val glTexture:WebGLTexture,shaderProgram:ShaderProgram<TextureData>,val width:Int,val height:Int) {
    val shaderProgramMesh = shaderProgram.ShaderProgramMesh()
    val left = -width/2f
    val right = width/2f
    val bottom = -height/2f
    val top = height/2f
    fun queueDraw(x:Float,y:Float,scale:Float = 1f,rotation:Float = 0f) {
      shaderProgramMesh.queue(x,y,left,bottom,0f,0f,scale,rotation)
      shaderProgramMesh.queue(x,y,left,top,0f,1f,scale,rotation)
      shaderProgramMesh.queue(x,y,right,top,1f,1f,scale,rotation)
      shaderProgramMesh.queue(x,y,right,top,1f,1f,scale,rotation)
      shaderProgramMesh.queue(x,y,right,bottom,1f,0f,scale,rotation)
      shaderProgramMesh.queue(x,y,left,bottom,0f,0f,scale,rotation)
      if(shaderProgramMesh.remaining()<36) render()
    }

    fun queueTileDraw(x:Float,y:Float,tcLeft:Float,tcTop:Float,tcRight:Float,tcBottom:Float,scale:Float = 1f,rotation:Float = 0f) {
      shaderProgramMesh.queue(x,y,left,bottom,tcLeft,tcBottom,scale,rotation)
      shaderProgramMesh.queue(x,y,left,top,tcLeft,tcTop,scale,rotation)
      shaderProgramMesh.queue(x,y,right,top,tcRight,tcTop,scale,rotation)
      shaderProgramMesh.queue(x,y,right,top,tcRight,tcTop,scale,rotation)
      shaderProgramMesh.queue(x,y,right,bottom,tcRight,tcBottom,scale,rotation)
      shaderProgramMesh.queue(x,y,left,bottom,tcLeft,tcBottom,scale,rotation)
      if(shaderProgramMesh.remaining()<36) render()
    }

    fun queueTileDraw(x:Float,y:Float,horCount:Int,verCount:Int,frame:Int,scale:Float = 1f,rotation:Float = 0f) {
      val tcw = 1f/horCount
      val tch = 1f/verCount
      val tcx = frame%horCount*tcw
      val tcy = 1f-tch*frame/horCount
      val left = -(width/horCount)/2f
      val right = (width/horCount)/2f
      val bottom = -(height/verCount)/2f
      val top = (height/verCount)/2f
      shaderProgramMesh.queue(x,y,left,bottom,tcx,tcy-tch,scale,rotation)
      shaderProgramMesh.queue(x,y,left,top,tcx,tcy,scale,rotation)
      shaderProgramMesh.queue(x,y,right,top,tcx+tcw,tcy,scale,rotation)
      shaderProgramMesh.queue(x,y,right,top,tcx+tcw,tcy,scale,rotation)
      shaderProgramMesh.queue(x,y,right,bottom,tcx+tcw,tcy-tch,scale,rotation)
      shaderProgramMesh.queue(x,y,left,bottom,tcx,tcy-tch,scale,rotation)
      if(shaderProgramMesh.remaining()<36) render()
    }

    fun render() {
      gl.activeTexture(WebGLRenderingContext.TEXTURE0)
      gl.bindTexture(WebGLRenderingContext.TEXTURE_2D,glTexture)
      shaderProgramMesh.render(TextureData(view.proectionMatrix,glTexture))
    }
  }

  inner class Keys(val inputProcesser:InputProcessor) {
    private val keys:MutableMap<Int,Double> = HashMap()

    init {
      val body = document.body ?: lib.log.fatalError("Can't register key events, document.body is null!?")
      body.onkeydown = ::keyDown
      body.onkeyup = ::keyUp
      body.onkeypress = ::keyPress
      body.onclick = ::mouseClick
      body.onmousedown = ::mouseMove//todo
      body.onmouseup = ::mouseMove//todo
      body.onmousemove = ::mouseMove
    }

    private fun keyDown(key:Event) {
      if(key is KeyboardEvent) {
        keys.put(key.keyCode,Date().getTime())
        inputProcesser.keyDown(key.keyCode)
      }
    }

    private fun keyUp(key:Event) {
      if(key is KeyboardEvent) {
        inputProcesser.keyUp(key.keyCode)
        keys.remove(key.keyCode)
      }
    }

    private fun keyPress(key:Event) {
      if(key is KeyboardEvent) {
        inputProcesser.keyPressed(key.charCode)
      }
    }

    private fun mouseClick(event:Event) {
      if(event is MouseEvent) {
        val vx:Float = view.screenToGameCoordX(event.getX(html.container).toFloat())
        val vy:Float = view.screenToGameCoordY(event.getY(html.container).toFloat())
        inputProcesser.pointerClick(event.button.toInt(),vx,vy)
      }
    }

    private fun mouseMove(event:Event) {
      if(event is MouseEvent) {
        val vx:Float = view.screenToGameCoordX(event.getX(html.container).toFloat())
        val vy:Float = view.screenToGameCoordY(event.getY(html.container).toFloat())
      }
    }

    fun wasPressed(keyCode:Int,delta:Double) = keys[keyCode]?.let {it>(Date().getTime()-delta)} ?: false
    fun isDown(keyCode:Int) = keys.containsKey(keyCode)
    fun isDown(keyCode:KeyCode) = keys.containsKey(keyCode.keyCode)
  }
}

sealed class SizeType {
  class FixedWidth(val width:Float,val minHeight:Float,val maxHeight:Float):SizeType() {
    override fun getWidth(aspectRation:Float) = width
    override fun getHeight(aspectRation:Float) = (width/aspectRation).max(minHeight).min(maxHeight)
//        override fun getAspectRation(aspectRation: Float) = aspectRation.max(width/maxHeight).min(width/minHeight)
  }

  class FixedHeight(val height:Float,val minWidth:Float,val maxWidth:Float):SizeType() {
    override fun getWidth(aspectRation:Float) = (height*aspectRation).max(minWidth).min(maxWidth)
    override fun getHeight(aspectRation:Float) = height
//        override fun getAspectRation(aspectRation: Float) = aspectRation.max(minWidth/height).min(maxWidth/height)
  }

  abstract fun getWidth(aspectRation:Float):Float
  abstract fun getHeight(aspectRation:Float):Float
}

class TextureData(val vMatrix:Matrix4,val texture:WebGLTexture)
enum class KeyCode(val keyCode:Int) { SHIFT(16), ESC(27), LEFT(37), UP(38), DOWN(40), RIGHT(39), SPACE(32), MINUS(109), PLUS(107) }

//language=GLSL //https://www.jetbrains.com/help/idea/using-language-injections.html
val vertxSrc = """
  attribute vec2 a_position;
  attribute vec2 a_boundingBox;
  attribute vec2 a_texCoord;
  attribute float a_scale;
  attribute float a_rotation;
  uniform mat4 u_projectionView;
  varying vec2 v_textCoord;
  varying vec4 myColor;
  varying vec4 arr[gl_MaxVaryingVectors-2];//28-29

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
    v_textCoord = a_texCoord;
    vec4 scaledBox = vec4(a_boundingBox, 1.0, 1.0) * scale(a_scale) * rotateZ(a_rotation);
    //myColor = vec4(1.0,0.3,0.3,0.5);
    myColor = vec4(1.0,1.0,1.0,1.0);
    gl_Position = u_projectionView * vec4(a_position + scaledBox.xy, 1.0, 1.0);
  }
"""
//language=GLSL //https://www.jetbrains.com/help/idea/using-language-injections.html
val fragmSrc = """
  precision mediump float;
  uniform sampler2D u_sampler;
  uniform lowp int u_test_array_size;
  uniform lowp float u_arr[3];
  uniform lowp vec4 u_vec_arr[gl_MaxVertexUniformVectors - 5];
  varying vec2 v_textCoord;
  varying vec4 myColor;
  varying vec4 arr[gl_MaxVaryingVectors-2];//28-29

  void main(void) {
    gl_FragColor = myColor * texture2D(u_sampler, v_textCoord);
    //gl_FragColor.a = gl_FragColor.a * (u_arr[0] + u_arr[1]);
  }
"""
