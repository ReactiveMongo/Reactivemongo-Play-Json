package reactivemongo.play.json.compat

import scala.language.implicitConversions

import play.api.libs.json.{ JsNull, JsObject, JsValue }

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
 * @define syntaxDocBaseUrl https://docs.mongodb.com/manual/reference/mongodb-extended-json
 * @define specsUrl https://github.com/mongodb/specifications/blob/master/source/extended-json.rst
 *
 * Implicit conversions for value types between
 * `play.api.libs.json` and `reactivemongo.api.bson`,
 * using [[$syntaxDocBaseUrl MongoDB Extended JSON]] syntax (v2).
 *
 * {{{
 * import play.api.libs.json.JsValue
 * import reactivemongo.api.bson.BSONValue
 * import reactivemongo.play.json.compat.ExtendedJsonConverters._
 *
 * def foo(v: BSONValue): JsValue =
 *   implicitly[JsValue](v) // ExtendedJsonConverters.fromValue
 *
 * def bar(v: JsValue): BSONValue =
 *   implicitly[BSONValue](v) // ExtendedJsonConverters.toValue
 * }}}
 *
 * ''Note:'' Logger `reactivemongo.api.play.json.ValueConverters` can be used to debug.
 *
 * See [[https://github.com/mongodb/specifications/blob/master/source/extended-json.rst#conversion-table specifications]].
 */
object ExtendedJsonConverters extends ExtendedJsonCompat
  with SharedValueConverters with LowPriority1ExtendedJson {

  /**
   * See [[$syntaxDocBaseUrl/#bson.Double syntax]];
   *
   * - For finite numbers: `{ "\$numberDouble": "<decimal string>" }`
   * - For other numbers: `{ "\$numberDouble": <"Infinity"|"-Infinity"|"NaN"> }`
   */
  implicit final def fromDouble(bson: BSONDouble): JsObject =
    dsl.double(bson.value)

  /**
   * See [[$syntaxDocBaseUrl/#bson.Int32 syntax]]:
   *
   * `{ "\$numberInt": "<number>" }`
   */
  implicit final def fromInteger(bson: BSONInteger): JsObject =
    dsl.int(bson.value)

  /**
   * See [[$syntaxDocBaseUrl/#bson.Int64 syntax]]:
   *
   * `{ "\$numberLong": "<number>" }`
   */
  implicit final def fromLong(bson: BSONLong): JsObject =
    dsl.long(bson.value)

}

private[json] sealed trait LowPriority1ExtendedJson {
  _: ExtendedJsonConverters.type =>

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
