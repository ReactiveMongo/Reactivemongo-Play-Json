package reactivemongo.play.json

import play.api.libs.json.{ JsObject, OWrites }

object TestCompat {
  object JsonValidationError {
    @inline def unapply(that: Any) = that match {
      case play.api.libs.json.JsonValidationError(messages) =>
        Some(messages)

      case _ => None
    }
  }

  @inline def toJsObject[T](v: T)(implicit w: OWrites[T]): JsObject =
    w.writes(v)
}
