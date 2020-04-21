package reactivemongo.play.json.compat

import scala.language.implicitConversions

import play.api.libs.json.{ JsNull, JsNumber, JsValue }

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
 * See [[compat$]] and [[ValueConverters]].
 *
 * Note that as there is not a JSON equivalent for each BSON value
 * (`BSONDateTime`, or even distinction between BSON long/int).
 *
 * So for example, using the default JSON handlers,
 * a same `Long` property can be written in some documents
 * as BSON long (`NumberLong`), and in some other as BSON integer
 * (see [[ValueConverters.toNumber]]), which is ok to read all these documents,
 * but can impact the MongoDB queries (same for date/time values that
 * will be serialized as BSON string, rather than BSON date/time or timestamp).
 */
object ValueConverters extends ValueConverters {
  private[compat] val logger =
    org.slf4j.LoggerFactory.getLogger(classOf[ValueConverters])
}

/**
 * Implicit conversions for value types between
 * `play.api.libs.json` and `reactivemongo.api.bson`.
 *
 * {{{
 * import play.api.libs.json.JsValue
 * import reactivemongo.api.bson.BSONValue
 * import reactivemongo.play.json.compat.ValueConverters._
 *
 * def foo(v: BSONValue): JsValue =
 *   implicitly[JsValue](v) // ValueConverters.fromValue
 *
 * def bar(v: JsValue): BSONValue =
 *   implicitly[BSONValue](v) // ValueConverters.toValue
 * }}}
 *
 * ''Note:'' Logger `reactivemongo.api.play.json.ValueConverters` can be used to debug.
 */
trait ValueConverters extends FromToValue
  with SharedValueConverters with LowPriority1Converters {

  final override type JsonNumber = JsNumber

  implicit final def fromDouble(bson: BSONDouble): JsNumber =
    JsNumber(bson.value)

  implicit final def fromInteger(bson: BSONInteger): JsNumber =
    JsNumber(bson.value)

  implicit final def fromLong(bson: BSONLong): JsNumber = JsNumber(bson.value)
}

private[json] sealed trait LowPriority1Converters { _: ValueConverters =>

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
