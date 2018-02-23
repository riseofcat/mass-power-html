package domain.tetris

import kuden.*
import org.w3c.dom.*
import kotlin.browser.*

class TetriesScreen:Game(SizeType.FixedWidth(800f,1600f,1600f)) {
  override fun keyPressed(charCode:Int) {}
  override fun keyDown(keyCode:Int) {}
  override fun keyUp(keyCode:Int) {}
  override fun pointerClick(pointer:Int,x:Float,y:Float) {
    if(x>720&&x<800&&y>1520&&y<1600) view.switchFullscreen()
    else {
      if(y<400) moveDown()
      else if(y>1200) turn()
      else if(x<400) moveLeft()
      else moveRight()
    }
  }

  var sprites = SpriteBatch()
  var playfield = Array(22,{Array(10,{" "})})
  var blocks = mapOf(
    "I" to GenSprite(GenTexData(8,8,0.5f,Block::create)),
    "J" to GenSprite(GenTexData(8,8,0.625f,Block::create)),
    "L" to GenSprite(GenTexData(8,8,0.0625f,Block::create)),
    "O" to GenSprite(GenTexData(8,8,0.125f,Block::create)),
    "S" to GenSprite(GenTexData(8,8,0.25f,Block::create)),
    "T" to GenSprite(GenTexData(8,8,0.75f,Block::create)),
    "Z" to GenSprite(GenTexData(8,8,0f,Block::create))
  )
  var fullscreen = ImgSprite(ImgTexData("img/fullscreen.png"))
  var windowed = ImgSprite(ImgTexData("img/windowed.png"))

  var timePerTick = 1f
  var timeTillNextTick = timePerTick
  var score = Score()
  var shadowColor = Color.hslToRgb(0.125f,SATURATION,SHADOW)
  var textColor = Color.hslToRgb(0.125f,SATURATION,LIGHT)
  var gameOver = false

  var music:HTMLAudioElement? = null

  val greySize = 10
  var greyBlocks:Array<Sprite> = Array(greySize) {GenSprite(GenTexData(8,8,it.toFloat()/greySize,Block::createGrey))}
  var deltaY = 0

  var piece = Piece()

  override fun loadResources() {
    GameSounds.loadAll()
    music = Music.play("music/Tetris.mp3",0.1,looping = true)
  }

  override fun unloadResources() {
    textures.clear()
  }

  private fun moveDown() {
    if(piece.canMoveDown(playfield)) {
      piece.moveDown()
      score.tick()
      GameSounds.TICK.play()
    }
  }

  private fun turn() {
    if(piece.canTurn(playfield)) {
      piece.turn()
      GameSounds.ROTATE.play()
    } else {
      GameSounds.ROTATE_FAIL.play()
    }
  }

  private fun moveLeft() {
    if(piece.canMoveLeft(playfield)) {
      piece.moveLeft()
      GameSounds.TICK.play()
    }
  }

  private fun moveRight() {
    if(piece.canMoveRight(playfield)) {
      piece.moveRight()
      GameSounds.TICK.play()
    }
  }

  private fun checkInput(delta:Float) {
    if(keys.wasPressed(KeyCode.DOWN.keyCode,(delta*1000).toDouble())) {
      moveDown()
    } else if(keys.wasPressed(KeyCode.UP.keyCode,(delta*1000).toDouble())) {
      turn()
    } else if(keys.wasPressed(KeyCode.LEFT.keyCode,(delta*1000).toDouble())) {
      moveLeft()
    } else if(keys.wasPressed(KeyCode.RIGHT.keyCode,(delta*1000).toDouble())) {
      moveRight()
    }
  }

  override fun update(time:Float,delta:Float) {
    document.body?.style?.backgroundColor = "#ddd"
    var tick = false
    timeTillNextTick -= delta
    while(timeTillNextTick<0f) {
      timeTillNextTick += timePerTick
      tick = true
    }

    if(gameOver) {
      return
    }

    checkInput(delta)

    if(tick) {
      if(!piece.canMoveDown(playfield)) {
        if(piece.y==21) {
          gameOver = true
        } else {
          nextPiece()
        }
      }

      tick()
    }

    deltaY = deltaY+(delta*80f).toInt()
    while(deltaY>320) {
      deltaY -= 320
    }
  }

  private fun nextPiece() {
    piece.nextPiece(playfield)

    removeFilledLines()
  }

  fun tick() {
    if(piece.canMoveDown(playfield)) {
      piece.moveDown()
    }
    score.tick()
  }

  private fun drawGrey(x:Int,y:Int,c:Int) {
    sprites.draw(greyBlocks[c],40f+x*80f,-deltaY+40f+y*80f,scale = 10f)
  }

  override fun render() {
    for(x in 0..2) {
      for(y in 0..5) {
        val xx = x*4
        val yy = y*4

        drawGrey(xx+0,yy+0,4)
        drawGrey(xx+1,yy+0,4)
        drawGrey(xx+1,yy+1,4)
        drawGrey(xx+2,yy+0,4)
        drawGrey(xx+0,yy+1,3)
        drawGrey(xx+0,yy+2,3)
        drawGrey(xx+1,yy+2,3)
        drawGrey(xx+0,yy+3,3)
        drawGrey(xx+1,yy+3,1)
        drawGrey(xx+2,yy+3,1)
        drawGrey(xx+2,yy+2,1)
        drawGrey(xx+3,yy+3,1)
        drawGrey(xx+3,yy+0,2)
        drawGrey(xx+3,yy+1,2)
        drawGrey(xx+2,yy+1,2)
        drawGrey(xx+3,yy+2,2)
      }
    }

    sprites.render()

    var y = 40f
    for(line in 0..playfield.size-1) {
      var x = 40f
      for(char in playfield[line]) {
        val block = blocks[char]
        if(block!=null) {
          sprites.draw(block,x,y,scale = 10f)
        }
        x += 80f
      }
      y += 80f
    }

    val px = piece.x*80f+40f
    val py = piece.y*80f+40f
    val block = blocks[piece.type.name];
    if(block!=null) {
      for(position in piece.type.getPositions(piece.orientation)) {
        sprites.draw(block,px+position.first*80f,py+position.second*80f,scale = 10f)
      }
    }

    if(view.isFullscreen()) {
      sprites.draw(windowed,760f,1560f,0.25f)
    } else {
      sprites.draw(fullscreen,760f,1560f,0.25f)
    }

    sprites.render()

    val fs = score.formatted()
    drawText(10f,-58f,fs,font = "bold 48pt Arial",fillStyle = "rgba(${shadowColor[0]}, ${shadowColor[1]}, ${shadowColor[2]}, 1)")
    drawText(7f,-55f,fs,font = "bold 48pt Arial",fillStyle = "rgba(${textColor[0]}, ${textColor[1]}, ${textColor[2]}, 0.9)")

    if(gameOver) {
      drawText(53f,803f,"GAME OVER!",font = "bold 80pt Arial",fillStyle = "rgba(55,0,0,1)")
      drawText(50f,800f,"GAME OVER!",font = "bold 80pt Arial",fillStyle = "rgba(255,0,0,0.9)")
    }
  }

  private fun removeFilledLines() {
    val toRemove = ArrayList<Int>()

    for(y in 0..21) {
      var empty = false
      for(x in 0..9) {
        if(playfield[y][x]==" ") {
          empty = true
          break
        }
      }
      if(!empty) {
        toRemove.add(y)
      }
    }

    var linesRemoved = 0
    while(!toRemove.isEmpty()) {
      val line = toRemove.removeAt(0)-linesRemoved

      for(y in line..20) {
        playfield[y] = playfield[y+1]
      }

      playfield[21] = Array(10,{" "})
      linesRemoved++
      timePerTick *= 0.95f
    }

    score.linesRemoved(linesRemoved)
    when(linesRemoved) {
      0-> {
      }
      1-> {
        GameSounds.SINGLE.play()
        document.body?.style?.backgroundColor = "#ff0"
      }
      2-> {
        GameSounds.DOUBLE.play()
        document.body?.style?.backgroundColor = "#f80"
      }
      else-> {
        GameSounds.TRIPLE.play()
        document.body?.style?.backgroundColor = "#f00"
      }
    }
  }

}
