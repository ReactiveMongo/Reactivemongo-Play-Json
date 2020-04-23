package reactivemongo.play.json
package compat

import scala.util.{ Failure, Success }

import play.api.libs.json.{ JsError, JsSuccess, Reads }

import reactivemongo.api.bson.{
  BSONDateTime,
  BSONInteger,
  BSONJavaScript,
  BSONLong,
  BSONObjectID,
  BSONReader,
  BSONString,
  BSONSymbol,
  BSONTimestamp,
  exceptions
}, exceptions.TypeDoesNotMatchException

private[compat] trait LaxHandlerWorkarounds {

  /**
   * Reads a `BSONDateTime` from a JSON number.
   */
  implicit val dateTimeReads: Reads[BSONDateTime] =
    Reads[BSONDateTime] { v =>
      v.validate[Long].map(BSONDateTime(_))
    }

  /**
   * Reads a `BSONDateTime` from a `BSONLong`.
   */
  implicit val bsonDateTimeReader: BSONReader[BSONDateTime] =
    BSONReader.collect[BSONDateTime] {
      case d: BSONDateTime => d
      case BSONLong(time) => BSONDateTime(time)
      case BSONInteger(time) => BSONDateTime(time.toLong)
    }

  /**
   * Reads a `BSONJavaScript` from a JSON string.
   */
  implicit val javaScriptReads: Reads[BSONJavaScript] =
    Reads[BSONJavaScript] {
      _.validate[String].map(BSONJavaScript(_))
    }

  /**
   * Reads a `BSONJavaScript` from a `BSONString`.
   */
  implicit val javaScriptBSONReader: BSONReader[BSONJavaScript] =
    BSONReader.collect[BSONJavaScript] {
      case BSONString(code) => BSONJavaScript(code)
    }

  /**
   * Reads a `BSONObjectID` from a JSON string.
   */
  implicit val objectIDReads: Reads[BSONObjectID] = Reads[BSONObjectID] {
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
   * Reads a `BSONObjectID` from a JSON string.
   */
  implicit val bsonObjectIDReader: BSONReader[BSONObjectID] =
    BSONReader.from[BSONObjectID] {
      case oid: BSONObjectID =>
        Success(oid)

      case BSONString(repr) =>
        BSONObjectID.parse(repr)

      case bson =>
        Failure(TypeDoesNotMatchException(
          "BSONString", bson.getClass.getSimpleName))
    }

  /**
   * Reads a `BSONSymbol` from a JSON string.
   */
  implicit val symbolReads: Reads[BSONSymbol] =
    Reads[BSONSymbol] {
      _.validate[String].map(BSONSymbol(_))
    }

  /**
   * Reads a `BSONSymbol` from a JSON string.
   */
  implicit val bsonSymbolReader: BSONReader[BSONSymbol] =
    BSONReader.collect[BSONSymbol] {
      case s: BSONSymbol => s
      case BSONString(name) => BSONSymbol(name)
    }

  /**
   * Reads a `BSONTimestamp` from a JSON number.
   */
  implicit val timestampReads: Reads[BSONTimestamp] =
    Reads[BSONTimestamp] {
      _.validate[Long].map(BSONTimestamp(_))
    }

  /**
   * Reads a `BSONTimestamp` from a JSON number.
   */
  implicit val bsonTimestampReader: BSONReader[BSONTimestamp] =
    BSONReader.collect[BSONTimestamp] {
      case t: BSONTimestamp => t
      case BSONInteger(time) => BSONTimestamp(time.toLong)
      case BSONLong(time) => BSONTimestamp(time)
    }

}
