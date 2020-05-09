package reactivemongo.play.json.compat

import scala.language.implicitConversions

import play.api.libs.json.{ JsObject, JsValue }

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
 * @define syntaxDocBaseUrl https://docs.mongodb.com/manual/reference/mongodb-extended-json
 * @define specsUrl https://github.com/mongodb/specifications/blob/master/source/extended-json.rst
 *
 * Implicit conversions for value types between
 * `play.api.libs.json` and `reactivemongo.api.bson`,
 * using [[$syntaxDocBaseUrl MongoDB Extended JSON]] syntax (v2).
 */
object ExtendedJsonConverters extends ExtendedJsonConverters { converters =>
  @inline implicit def fromValue: FromValue = converters
  @inline implicit def toValue: ToValue = converters
}

private[json] trait ExtendedJsonConverters
  extends FromToValue with ExtendedJsonCompat with SharedValueConverters
  with LowPriority1ExtendedJson with TemporalObjectConverters {

  final type JsonNumber = JsObject
  final type JsonJavaScript = JsObject
  final type JsonObjectID = JsObject
  final type JsonSymbol = JsObject

  /**
   * See [[$syntaxDocBaseUrl/#bson.Double syntax]];
   *
   * - For finite numbers: `{ "\$numberDouble": "<decimal string>" }`
   * - For other numbers: `{ "\$numberDouble": <"Infinity"|"-Infinity"|"NaN"> }`
   *
   * @see [[dsl.double]]
   */
  implicit final def fromDouble(bson: BSONDouble): JsObject =
    dsl.double(bson.value)

  /**
   * See [[$syntaxDocBaseUrl/#bson.Int32 syntax]]:
   *
   * `{ "\$numberInt": "<number>" }`
   *
   * @see [[dsl.int]]
   */
  implicit final def fromInteger(bson: BSONInteger): JsObject =
    dsl.int(bson.value)

  /**
   * See [[https://github.com/mongodb/specifications/blob/master/source/extended-json.rst syntax]]:
   *
   * `{ "\$code": "<javascript>" }`
   */
  @inline implicit def fromJavaScript(bson: BSONJavaScript): JsObject = jsonJavaScript(bson)

  /**
   * See [[$syntaxDocBaseUrl/#bson.Int64 syntax]]:
   *
   * `{ "\$numberLong": "<number>" }`
   *
   * @see [[dsl.long]]
   */
  implicit final def fromLong(bson: BSONLong): JsObject =
    dsl.long(bson.value)

  /**
   * See [[$syntaxDocBaseUrl/#bson.ObjectId syntax]]:
   *
   * `{ "\$oid": "<ObjectId bytes>" }`
   *
   * @see [[dsl.objectID]]
   */
  implicit final def fromObjectID(bson: BSONObjectID): JsObject =
    dsl.objectID(bson)

  /**
   * See [[https://github.com/mongodb/specifications/blob/master/source/extended-json.rst syntax]]:
   *
   * `{ "\$symbol": "<name>" }`
   *
   * @see [[dsl.symbol]]
   */
  @inline implicit final def fromSymbol(bson: BSONSymbol): JsObject =
    dsl.symbol(bson.value)

}

private[json] sealed trait LowPriority1ExtendedJson {
  self: ExtendedJsonConverters =>

  @inline implicit final def fromValue(bson: BSONValue): JsValue =
    jsonValue(bson)(self)
}
