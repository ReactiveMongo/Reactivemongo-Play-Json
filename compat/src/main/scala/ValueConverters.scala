package reactivemongo.play.json.compat

import scala.language.implicitConversions

import play.api.libs.json.{ JsNumber, JsObject, JsValue }

import reactivemongo.api.bson.{
  BSONDouble,
  BSONInteger,
  BSONJavaScript,
  BSONLong,
  BSONObjectID,
  BSONSymbol,
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
object ValueConverters extends ValueConverters { converters =>
  @inline implicit def fromValue: FromValue = converters
  @inline implicit def toValue: ToValue = converters

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
trait ValueConverters extends FromToValue with SharedValueConverters
  with LowPriority1Converters with TemporalObjectConverters {

  final type JsonNumber = JsNumber
  final type JsonJavaScript = JsObject
  final type JsonObjectID = JsObject
  final type JsonSymbol = JsObject

  implicit final def fromDouble(bson: BSONDouble): JsNumber =
    JsNumber(bson.value)

  implicit final def fromInteger(bson: BSONInteger): JsNumber =
    JsNumber(bson.value)

  /**
   * See [[https://github.com/mongodb/specifications/blob/master/source/extended-json.rst syntax]]:
   *
   * `{ "\$code": "<javascript>" }`
   */
  @inline implicit def fromJavaScript(bson: BSONJavaScript): JsObject = jsonJavaScript(bson)

  implicit final def fromLong(bson: BSONLong): JsNumber = JsNumber(bson.value)

  /**
   * See [[https://github.com/mongodb/specifications/blob/master/source/extended-json.rst syntax]]:
   *
   * `{ "\$symbol": "<name>" }`
   *
   * @see [[dsl.symbol]]
   */
  @inline implicit final def fromSymbol(bson: BSONSymbol): JsObject =
    dsl.symbol(bson.value)

  /**
   * See [[https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.ObjectId syntax]]:
   *
   * `{ "\$oid": "<ObjectId bytes>" }`
   *
   * @see [[dsl.objectID]]
   */
  @inline implicit final def fromObjectID(bson: BSONObjectID): JsObject =
    dsl.objectID(bson)

}

private[json] sealed trait LowPriority1Converters { self: ValueConverters =>
  @inline implicit final def fromValue(bson: BSONValue): JsValue =
    jsonValue(bson)(self)
}
