package domain.tetris

import kuden.*

internal class MapData {
  var version:Int = 1
  var properties:MutableMap<String,String> = HashMap()
  var layers:Array<MapLayer>? = null
  var tilesets:Array<MapTileset>? = null

  var height:Int = 0
  var width:Int = 0

  var nextobjectid:Int = 0
  var orientation:String = "orthogonal"
  var renderorder:String = "right-down"
  var tileheight:Int = 0
  var tilewidth:Int = 0
}

internal class MapLayer {
  var properties:MutableMap<String,String> = HashMap()

  var data:Array<Int>? = null
  var encoding:String = ""
  var x:Int = 0
  var y:Int = 0
  var width:Int = 0
  var height:Int = 0
  var name:String = ""
  var opacity:Float = 1f
  var type:String = ""
  var visible:Boolean = true
  var draworder:String = ""
}

internal class MapTileset {
  var properties:MutableMap<String,String> = HashMap()
  var firstgid:Int = 0
  var image:String = ""
  var imageheight:Int = 0
  var imagewidth:Int = 0
  var margin:Int = 0
  var name:String = ""
  var spacing:Int = 0
  var tilecount:Int = 0
  var tileheight:Int = 0
  var tilewidth:Int = 0
  var tileproperties:MutableMap<String,MutableMap<String,String>> = HashMap()
}

internal class TilesetIndex(val texture:Game.Texture?,val tcLeft:Float,val tcTop:Float,val tcRight:Float,val tcBottom:Float,val scale:Float) {
  constructor():this(null,0f,0f,0f,0f,0f)

  fun render(x:Float,y:Float) = texture?.queueTileDraw(x,y,tcLeft,tcTop,tcRight,tcBottom,scale)
}

