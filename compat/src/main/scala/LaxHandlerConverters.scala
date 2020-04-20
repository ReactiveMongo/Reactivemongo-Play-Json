package reactivemongo.play.json
package compat

import scala.util.{ Failure, Success }

import play.api.libs.json.{
  JsError,
  JsNumber,
  JsSuccess,
  JsString,
  Reads,
  Writes
}

import reactivemongo.api.bson.{
  BSONDateTime,
  BSONJavaScript,
  BSONObjectID,
  BSONSymbol,
  BSONTimestamp
}

/**
 * {{{
 * import play.api.libs.json._
 * import reactivemongo.api.bson._
 * import reactivemongo.play.json.compat.LaxHandlerConverters._
 *
 * Json.toJson(BSONObjectID.generate()) // objectIdWrites
 * }}}
 */
object LaxHandlerConverters extends LaxHandlerConverters

private[compat] trait LaxHandlerConverters {
  /**
   * Writes a `BSONDateTime` as a JSON number.
   */
  implicit val dateTimeWrites: Writes[BSONDateTime] =
    Writes[BSONDateTime] { dt => JsNumber(dt.value) }

  /**
   * Reads a `BSONDateTime` from a JSON number.
   */
  implicit val dateTimerReads: Reads[BSONDateTime] =
    Reads[BSONDateTime] {
      _.validate[Long].map(BSONDateTime(_))
    }

  /**
   * Writes a `BSONJavaScript` as a JSON string.
   */
  implicit val javaScriptWrites: Writes[BSONJavaScript] =
    Writes[BSONJavaScript] { js => JsString(js.value) }

  /**
   * Reads a `BSONJavaScript` from a JSON string.
   */
  implicit val javaScriptReads: Reads[BSONJavaScript] =
    Reads[BSONJavaScript] {
      _.validate[String].map(BSONJavaScript(_))
    }

  /**
   * Writes a `BSONObjectID` as a JSON string.
   */
  implicit val objectIdWrites: Writes[BSONObjectID] =
    Writes[BSONObjectID] { oid => JsString(oid.stringify) }

  /**
   * Reads a `BSONObjectID` from a JSON string.
   */
  implicit val objectIdReads: Reads[BSONObjectID] =
    Reads[BSONObjectID] {
      _.validate[String].flatMap {
        BSONObjectID.parse(_) match {
          case Failure(cause) =>
            JsError(cause.getMessage)

          case Success(oid) =>
            JsSuccess(oid)
        }
      }
    }

  /**
   * Writes a `BSONSymbol` as a JSON string.
   */
  implicit val symbolWrites: Writes[BSONSymbol] =
    Writes[BSONSymbol] { js => JsString(js.value) }

  /**
   * Reads a `BSONSymbol` from a JSON string.
   */
  implicit val symbolReads: Reads[BSONSymbol] =
    Reads[BSONSymbol] {
      _.validate[String].map(BSONSymbol(_))
    }

  /**
   * Writes a `BSONTimestamp` as a JSON number.
   */
  implicit val timestampLaxWrites: Writes[BSONTimestamp] =
    Writes[BSONTimestamp] { ts => JsNumber(ts.value) }

  /**
   * Reads a `BSONTimestamp` from a JSON number.
   */
  implicit val timestampLaxReads: Reads[BSONTimestamp] =
    Reads[BSONTimestamp] {
      _.validate[Long].map(BSONTimestamp(_))
    }

}
