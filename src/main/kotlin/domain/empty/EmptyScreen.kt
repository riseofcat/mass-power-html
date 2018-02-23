package domain.empty

import kuden.*

class EmptyGameScreen:InputProcessor by EmptyInputProcessor(),Game(SizeType.FixedWidth(1200f,800f,1400f),RGBA(0f,0f,0.5f,0.5f)) {
  private var sprites = SpriteBatch()
  var x = 0f
  var y = 0f
  override fun loadResources() {}
  override fun unloadResources() = textures.dispose()
  override fun update(time:Float,delta:Float) {
    clearColor?.r = kotlin.math.sin((time/3).toDouble()).toFloat()
    clearColor?.g = kotlin.math.sin((time/5).toDouble()).toFloat()
    x = (400f+kotlin.math.sin((time*1.5))*300f).toFloat()
    y = (view.gameHeight/2.0+kotlin.math.cos((time*2.5))*300f).toFloat()
  }

  override fun render() {
    sprites.draw(ImgSprite(ImgTexData("img/smiley.png")),x,y,scale = 0.3f)
    sprites.render()
    drawText(300f,view.gameHeight/2f,"Hello!"+Gen.date(),font = "bold 30pt Arial",fillStyle = "rgba(255,255,0,0.75)")
  }
}