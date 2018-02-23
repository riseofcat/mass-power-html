package masspower

import common.*
import kuden.*
import org.khronos.webgl.*
import org.w3c.dom.*
import org.w3c.dom.events.*
import util.*
import kotlin.browser.*
import kotlin.js.*
import org.khronos.webgl.WebGLRenderingContext as WGL

@Deprecated("") const val OLD = false
const val DYNAMIC_SHADER = false//default true +1 fps
const val DYNAMIC_TEXTURE = true//default true +2 fps
const val DEBUG_ERROR = false//default true +2 fps
const val BIG_TEXTURE = false//default true +20 fps
const val DYNAMIC_BLEND = true//не влияет на производительность
const val COUNT = 100
const val SCALE = 0.1f

class TextureData(val vMatrix:Matrix4)
data class ImgData(val url:String)
class ImgCache(var texture:MassPower.GameTexture? = null)
data class RenderData(val x:Float,val y:Float,val scale:Float,val imgData:ImgData)
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

class MassPower(val view:View = FixedWidth(1200f,800f,1400f)) {
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
  var mouseX:Float = 0f
  var mouseY:Float = 0f

  init {
    window.onfocus
    window.onblur
    window.onresize = {resize()}
    window.onload = {resize()}
    window.requestAnimationFrame {
      gl.bindBuffer(WGL.ARRAY_BUFFER,gl.createBuffer() ?: JsUtil.error("Unable to create webgl buffer!"))


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
      }


      gl.enable(WGL.BLEND)
      if(!DYNAMIC_BLEND) gl.blendFunc(srcFactor,dstFactor)
      gameLoop(it)
    }
    document.onmousemove = fun(event:Event) {
      if(event is MouseEvent) {
        fun View.screenToGameCoordX(screenX:Float) = (screenX-borderLeft)*gameWidth/windowWidth
        fun View.screenToGameCoordY(screenY:Float) = gameHeight-(screenY-borderTop)*gameHeight/windowHeight
        mouseX = view.screenToGameCoordX(event.getX(html.container).toFloat())
        mouseY = view.screenToGameCoordY(event.getY(html.container).toFloat())
      }
    }
    document.onclick = fun(event:Event) {
      if(false) JsUtil.error("document.onclick")
    }
    document.onkeypress = fun(event:Event) {
      if(event is KeyboardEvent) {
        val code = event.keyCode
        val key = Key.getByCode(code)
        when(key) {
          Key.Q,Key.W,Key.E,Key.R->
            modeKey = key
          else->if(key?.number==true) {
            val result = listOf(WGL.SRC_COLOR,//1
              WGL.ONE_MINUS_SRC_COLOR,//2
              WGL.DST_COLOR,//3
              WGL.ONE_MINUS_DST_COLOR,//4
              WGL.SRC_ALPHA,//5
              WGL.ONE_MINUS_SRC_ALPHA,//6
              WGL.DST_ALPHA,
              WGL.ONE_MINUS_DST_ALPHA,
              WGL.SRC_ALPHA_SATURATE).get(key.numValue-1)
            when(modeKey) {
              Key.Q->srcFactor = result
              Key.W->dstFactor = result
              Key.E->srcFactorGlow = result
              Key.R->dstFactorGlow = result
            }
          }

        }
      }
    }
  }

  enum class Key(val code:Int,val number:Boolean = false) {
    Q(113), W(119), E(101), R(114), T(116), Y(121),
    ZERO(48,number = true), ONE(49,number = true), TWO(50,number = true), THREE(51,number = true), FOUR(52,number = true), FIVE(53,number = true), SIX(54,number = true), SEVEN(55,number = true), EIGHT(56,number = true), NINE(57,number = true)
    ;

    val numValue get() = code-48

    companion object {
      fun getByCode(code:Int):Key? {
        for(value in values()) {
          if(value.code==code) {
            return value
          }
        }
        return null
      }
    }
  }

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

  private var modeKey:Key = Key.Q
  public var srcFactorGlow = WGL.SRC_ALPHA
  public var dstFactorGlow = if(true) WGL.DST_ALPHA else WGL.SRC_ALPHA_SATURATE //todo GL ERROR with false
  public var srcFactor = WGL.SRC_ALPHA
  public var dstFactor = WGL.ONE_MINUS_SRC_ALPHA

  private fun gameLoop(милисекундСоСтараПлюсБездействие:Double):Unit = JsUtil.saveInvoke {
    fps = (fps*averageConst+1f/(time-previousTime)).toFloat()/(averageConst+1)
    previousTime = time
    if(false) resize()
    html.canvas2d.clearRect(0.0,0.0,view.gameWidth.toDouble(),view.gameHeight.toDouble())
    html.canvas2d.fillStyle = "white"
    html.canvas2d.font = "bold 24pt Arial"
    if(false) html.canvas2d.fillText(mem("totalJSHeapSize"),200.0,200.0)
    if(false) html.canvas2d.fillText(mem("usedJSHeapSize"),200.0,300.0)
    if(false) html.canvas2d.fillText(mem("jsHeapSizeLimit"),200.0,400.0)

    html.canvas2d.fillText("fps: $fps",200.0,500.0)
    html.canvas2d.fillText(Gen.date(),200.0,550.0)
    html.canvas2d.fillText(Multiplatform.multiplatform(),200.0,600.0)
    gl.clearColor(0f,0f,0f,1f)//todo потестировать прозрачность fps
    gl.clear(WGL.COLOR_BUFFER_BIT)
    val imgData2 = ImgData(if(BIG_TEXTURE) "img/smiley.png" else "img/smiley_small_rect.png")
    val imgData = ImgData(if(BIG_TEXTURE) "img/smiley.png" else "img/smiley_small_rect_green.png")
    val scale = if(BIG_TEXTURE) SCALE else 8*SCALE
    mutableListOf(RenderData(500f,500f,scale,imgData)).apply {
      addAll(
        List(COUNT) {i->
          val x = (400f+kotlin.math.sin((time*i))*300f).toFloat()
          val y = (view.gameHeight/2.0+kotlin.math.cos((time/2.5*i))*300f).toFloat()
          RenderData(x,y,scale,if(i%2==0) imgData2 else imgData)
        })
      add(RenderData(mouseX,mouseY,scale,imgData))
    }.forEach {
        val cache = imgCache[it.imgData] ?: ImgCache().apply {
          imgCache[it.imgData] = this
          val img = document.createElement("img",HTMLImageElement::class)
          img.onload = {
            val texture = gl.createTexture() ?: JsUtil.error("Couldn't create webgl texture!")
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
          glTexture.renderCircle16(it.x,it.y,0f,0f,0.5f,0.5f,it.scale,0f,0f) {a->
            val cos = kotlin.math.cos(a)
            val sin = kotlin.math.sin(a)
            val DIVIDE = 1.65f
            val glowRadius = 0.75f
            CircleFanStrip(floatArrayOf(it.x,it.y,cos*width/2,sin*height/2,cos*0.5f+0.5f,sin*0.5f+0.5f,it.scale,0f,1f),
              floatArrayOf(it.x,it.y,cos*width*glowRadius,sin*height*glowRadius,0.5f+cos*0.5f,0.5f+sin*0.5f,it.scale,0f,DIVIDE))
          }
        }
        if(false) cache.texture?.apply {
          //Рисует прямоугольники
          glTexture.render(Mode.TRIANGLE,
            it.x,it.y,left,bottom,0f,0f,it.scale,0f,it.x,it.y,left,top,0f,1f,it.scale,0f,it.x,it.y,right,top,1f,1f,it.scale,0f,
            it.x,it.y,right,top,1f,1f,it.scale,0f,it.x,it.y,right,bottom,1f,0f,it.scale,0f,it.x,it.y,left,bottom,0f,0f,it.scale,0f)
        }
      }
    if(false) imgCache[imgData]?.texture?.glTexture?.render(Mode.TRIANGLE,123.45f)//todo сделать рендер треугольника через другой shader
    if(false) {//Раньше было так
      val MAX_MESH = 2000//default 20_000  Не зависит от количества рисуемых объектов
      val mesh = Float32Array(MAX_MESH-(MAX_MESH%(verticesBlockSize*3)))//mesh находился внутри GameTexture. 3 - для треугольника, а у нас может быть FAN
      inline fun render(mode:Mode,txtr:GameTexture,floatArgsCount:Int) = render(mode,mesh,txtr.glTexture,floatArgsCount)
      val argsInVertixCount = 8//коливество аргументов в одной вершине
      val verticesCount = 6//для прямоугольника из двух треугольников
      val objectsCount = 100//количество объектов для рисования
      val floatArgscount:Int = objectsCount*verticesCount*argsInVertixCount//количество переменных типа Float
      imgCache.values.mapNotNull {it.texture}.forEach {render(Mode.TRIANGLE,it,floatArgscount)}
    }
    window.requestAnimationFrame(::gameLoop)
  }

  inline fun angle(i:Int,max:Int):Float = 2*kotlin.math.PI.toFloat()*i/max
  data class CircleFanStrip(val fan:FloatArray,val strip:FloatArray)

  inline fun WebGLTexture.renderCircle16(vararg center:Float,fan:(angle:Float)->CircleFanStrip) {
    val max = 16
    val (f0,s0) = fan(angle(0,max))//todo расчёт до исполнения через companion object
    val (f1,s1) = fan(angle(1,max))
    val (f2,s2) = fan(angle(2,max))
    val (f3,s3) = fan(angle(3,max))
    val (f4,s4) = fan(angle(4,max))
    val (f5,s5) = fan(angle(5,max))
    val (f6,s6) = fan(angle(6,max))
    val (f7,s7) = fan(angle(7,max))
    val (f8,s8) = fan(angle(8,max))
    val (f9,s9) = fan(angle(9,max))
    val (f10,s10) = fan(angle(10,max))
    val (f11,s11) = fan(angle(11,max))
    val (f12,s12) = fan(angle(12,max))
    val (f13,s13) = fan(angle(13,max))
    val (f14,s14) = fan(angle(14,max))
    val (f15,s15) = fan(angle(15,max))
    if(DYNAMIC_BLEND) gl.blendFunc(srcFactor,dstFactor)
    if(DYNAMIC_SHADER) gl.useProgram(shaderProgram)
    render(Mode.TRIANGLE_FAN,*center,*f0,*f1,*f2,*f3,*f4,*f5,*f6,*f7,*f8,*f9,*f10,*f11,*f12,*f13,*f14,*f15,*f0)
    if(DYNAMIC_BLEND) gl.blendFunc(srcFactorGlow,dstFactorGlow)
    if(DYNAMIC_SHADER) gl.useProgram(shaderProgram2)
    render(Mode.TRIANGLE_STRIP,*f0,*s0,*f1,*s1,*f2,*s2,*f3,*s3,*f4,*s4,*f5,*s5,*f6,*s6,*f7,*s7,*f8,*s8,*f9,*s9,*f10,*s10,*f11,*s11,*f12,*s12,*f13,*s13,*f14,*s14,*f15,*s15,*f0,*s0)
  }

  inline fun WebGLTexture.render(mode:Mode,vararg allArgs:Float) = render(mode,allArgs)
  inline fun WebGLTexture.render(mode:Mode,allArgs:FloatArray) = render(mode,if(true) allArgs as Float32Array else Float32Array(allArgs.toTypedArray()),this,allArgs.size)
  inline fun MutableList<Float>.vert(vararg args:Float) = addAll(args.toList())//todo check why asList doesn't working
  inline fun WebGLTexture.render(mode:Mode,lambda:MutableList<Float>.()->Unit) = render(mode,arrayListOf<Float>().also {it.lambda()}.toFloatArray())
  inline fun render(mode:Mode,mesh:Float32Array,glTexture:WebGLTexture,allFloatArgsCount:Int) {
    debugError("allFloatArgsCount<=0") {allFloatArgsCount<=0}
    debugError("Number of vertices not a multiple of the attribute block size!") {allFloatArgsCount%verticesBlockSize!=0}
    gl.activeTexture(WGL.TEXTURE0)
    if(DYNAMIC_TEXTURE) gl.bindTexture(WGL.TEXTURE_2D,glTexture)
    if(OLD&&DYNAMIC_SHADER) gl.useProgram(shaderProgram)
    gl.bufferData(WGL.ARRAY_BUFFER,mesh,WGL.DYNAMIC_DRAW)//todo test STATIC_DRAW fps
    gl.drawArrays(mode.glMode,0,allFloatArgsCount/verticesBlockSize)//todo first, count
    if(OLD&&DYNAMIC_SHADER) gl.useProgram(null)
  }
}

enum class Mode(val glMode:Int) {
  TRIANGLE(WGL.TRIANGLES),
  TRIANGLE_FAN(WGL.TRIANGLE_FAN),
  TRIANGLE_STRIP(WGL.TRIANGLE_STRIP)
}

inline fun debugError(message:String,noinline condition:()->Boolean) {
  if(DEBUG_ERROR&&condition()) JsUtil.error("WebGl error: $message, conditionFunction: $condition")
}

fun mem(v:String):String = "$v ${js("performance.memory")[v]/1024/1024}"