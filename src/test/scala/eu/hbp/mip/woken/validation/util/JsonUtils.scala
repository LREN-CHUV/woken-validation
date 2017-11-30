package eu.hbp.mip.woken.validation.util

import spray.json.JsValue

import scala.io.Source

trait JsonUtils {

  def loadJson(path: String): JsValue = {
    import spray.json._
    import spray.json.DefaultJsonProtocol.RootJsObjectFormat

    val source = Source.fromURL(getClass.getResource(path))
    jsonReader
    source.mkString.parseJson
  }
}
