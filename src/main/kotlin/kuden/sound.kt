package kuden

import org.w3c.dom.*
import util.*
import kotlin.browser.*

object Music {
  private val playing:MutableSet<HTMLAudioElement> = HashSet()
  fun play(url:String,volume:Double = 0.75,looping:Boolean = false) =
    document.createElement("audio",HTMLAudioElement::class).apply {
        src = url
        this.volume = volume
        play()
        onended = {
          if(looping) {
            currentTime = 0.0
            play()
          } else {
            remove()
            playing.remove(this)
          }
        }
      }
}

class Sound(url:String,private val defaultVolume:Double = 0.75,numberOfChannels:Int) {
  private var channels = Array(numberOfChannels) {document.createElement("audio",HTMLAudioElement::class)}
  private var nextChannel:Int = 0

  init {
    channels.forEach {
      it.src = url
      it.pause()
      it.load()
      it.volume = defaultVolume
    }
  }

  fun pause() = channels.forEach {it.pause()}
  fun play(volume:Double = defaultVolume) = channels[nextChannel].let {
    it.volume = volume
    it.currentTime = 0.0
    it.play()
    nextChannel = (nextChannel+1)%channels.size
  }
}

object Sounds {
  private val sounds:MutableMap<String,Sound> = HashMap()
  fun load(name:String,url:String,volume:Double = 0.75,channels:Int = 1) = sounds.put(name,Sound(url,volume,channels))
  fun play(name:String,volume:Double? = null) = sounds[name]?.apply {
    play()
    if(volume!=null) play(volume)
    else play()
  } ?: throw IllegalArgumentException("Sound '$name' not found, load it first!")

  fun pause(name:String) = sounds[name]?.pause() ?: throw IllegalArgumentException("Sound '$name' not found, load it first!")
}
