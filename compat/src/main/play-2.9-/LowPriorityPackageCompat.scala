package reactivemongo.play.json

import play.api.libs.json.{ JsObject, OWrites }

private[json] trait LowPriorityPackageCompat {
  @deprecated("Will be removed when provided by Play-JSON itself", "0.20.6")
  implicit final val jsObjectWrites: OWrites[JsObject] =
    OWrites[JsObject](identity)
}
