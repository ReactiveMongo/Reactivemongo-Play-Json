package reactivemongo.play.json.compat

import play.api.libs.json.{ JsFalse => F, JsTrue => T }

import reactivemongo.api.bson.BSONBoolean

private[compat] trait ExtendedJsonCompat {
  implicit final val toFalse: F.type => BSONBoolean = {
    val stable = BSONBoolean(false)
    _ => stable
  }

  implicit final val toTrue: T.type => BSONBoolean = {
    val stable = BSONBoolean(true)
    _ => stable
  }
}
