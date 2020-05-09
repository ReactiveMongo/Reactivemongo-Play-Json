package reactivemongo.play.json.compat

import scala.language.implicitConversions

import play.api.libs.json.{ JsNull, JsNumber, JsString, JsValue }

import reactivemongo.api.bson.{
  BSONArray,
  BSONBinary,
  BSONBoolean,
  BSONDateTime,
  BSONDecimal,
  BSONDocument,
  BSONDouble,
  BSONInteger,
  BSONJavaScript,
  BSONJavaScriptWS,
  BSONLong,
  BSONMaxKey,
  BSONMinKey,
  BSONNull,
  BSONObjectID,
  BSONRegex,
  BSONString,
  BSONSymbol,
  BSONTimestamp,
  BSONValue
}

/**
 * Implicit conversions for value types between
 * `play.api.libs.json` and `reactivemongo.api.bson`,
 * using lax conversions.
 */
object LaxValueConverters extends LaxValueConverters

private[compat] trait LaxValueConverters
  extends FromToValue with SharedValueConverters
  with LaxValueConvertersLowPriority1 {

  final type JsonNumber = JsNumber
  final type JsonTime = JsNumber
  final type JsonJavaScript = JsString
  final type JsonObjectID = JsString
  final type JsonSymbol = JsString

  /** See `BSONDateTime.value` */
  override implicit final def fromDateTime(bson: BSONDateTime): JsNumber =
    JsNumber(bson.value)

  implicit final def fromDouble(bson: BSONDouble): JsNumber =
    JsNumber(bson.value)

  implicit final def fromInteger(bson: BSONInteger): JsNumber =
    JsNumber(bson.value)

  /** See `BSONJavaScript.value` */
  override implicit final def fromJavaScript(bson: BSONJavaScript): JsString =
    JsString(bson.value)

  implicit final def fromLong(bson: BSONLong): JsNumber = JsNumber(bson.value)

  implicit final def fromObjectID(bson: BSONObjectID): JsString =
    JsString(bson.stringify)

  implicit final def fromSymbol(bson: BSONSymbol): JsString =
    JsString(bson.value)

  override implicit final def fromTimestamp(bson: BSONTimestamp): JsNumber =
    JsNumber(bson.value)

  override def toString = "LaxValueConverters"
}

private[json] sealed trait LaxValueConvertersLowPriority1 {
  _: LaxValueConverters =>

  implicit final def fromValue(bson: BSONValue): JsValue = bson match {
    case arr: BSONArray => fromArray(arr)
    case bin: BSONBinary => fromBinary(bin)

    case BSONBoolean(true) => JsTrue
    case BSONBoolean(_) => JsFalse

    case dt: BSONDateTime => fromDateTime(dt)
    case dec: BSONDecimal => fromDecimal(dec)
    case doc: BSONDocument => fromDocument(doc)
    case d: BSONDouble => fromDouble(d)
    case i: BSONInteger => fromInteger(i)

    case js: BSONJavaScript => fromJavaScript(js)
    case jsw: BSONJavaScriptWS => fromJavaScriptWS(jsw)

    case l: BSONLong => fromLong(l)

    case BSONMaxKey => JsMaxKey
    case BSONMinKey => JsMinKey
    case BSONNull => JsNull

    case oid: BSONObjectID => fromObjectID(oid)
    case re: BSONRegex => fromRegex(re)
    case str: BSONString => fromStr(str)
    case sym: BSONSymbol => fromSymbol(sym)
    case ts: BSONTimestamp => fromTimestamp(ts)

    case _ => JsUndefined
  }
}
