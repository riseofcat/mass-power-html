package domain

import domain.dots.*
import domain.empty.*
import domain.fractal.*
import domain.tetris.*
import kuden.*
import masspower.*
import performance.*
import kotlin.browser.*

enum class Mode {
  MASS_POWER,
  PERFORMANCE,
  DOT,
  EXPERIMENTS,
  EMPTY,
  TETRIS,
  FRACTAL1,
  FRACTAL2
}

fun main(args:Array<String>) {
  when(Mode.PERFORMANCE) {
    Mode.MASS_POWER->MassPower()
    Mode.EXPERIMENTS-> {
      testFirst()
      console.info("hi console")
    }
    Mode.EMPTY-> {
      document.body?.style?.backgroundColor = "#242"
      EmptyGameScreen()
    }
    Mode.TETRIS->TetriesScreen()
    Mode.FRACTAL1->Fractal1(HTMLElements()).render()
    Mode.FRACTAL2->Fractal2(HTMLElements()).render()
    Mode.DOT->Dots(HTMLElements()).render()
    Mode.PERFORMANCE->PerformanceTest()
  }
}
