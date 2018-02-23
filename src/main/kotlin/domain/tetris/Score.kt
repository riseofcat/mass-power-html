package domain.tetris

class Score(var score:Int = 0) {

  fun linesRemoved(lines:Int) {
    var linesLeft = lines
    var points = 100
    while(linesLeft>0) {
      score += points
      linesLeft--
      points += 100
    }
  }

  fun tick() {
    score++
  }

  fun formatted():String {
    var result = score.toString()

    while(result.length<5) {
      result = "0"+result
    }

    return result
  }

}
