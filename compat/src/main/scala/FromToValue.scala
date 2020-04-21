package reactivemongo.play.json.compat

import scala.language.implicitConversions

import _root_.play.api.libs.json.{
  JsArray,
  JsBoolean,
  JsNull,
  JsNumber,
  JsObject,
  JsString,
  JsValue,
  Json
}

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
  BSONUndefined,
  BSONValue
}

private[json] trait FromToValue extends FromValue with ToValue

/** Conversion API from BSON to JSON values */
sealed trait FromValue {
  /** JSON representation for numbers */
  type JsonNumber <: JsValue

  def fromDouble(bson: BSONDouble): JsonNumber

  def fromInteger(bson: BSONInteger): JsonNumber

  def fromLong(bson: BSONLong): JsonNumber

  implicit final def fromArray(bson: BSONArray): JsArray =
    JsArray(bson.values.map(fromValue))

  /**
   * See [[https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Binary syntax]]:
   *
   * `{ "\$binary":
   *    {
   *       "base64": "<payload>",
   *       "subType": "<t>"
   *    }
   * }`
   */
  def fromBinary(bin: BSONBinary): JsObject

  def fromBoolean(bson: BSONBoolean): JsBoolean

  /**
   * See [[https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Date syntax]]:
   *
   * `{ "\$date": { "\$numberLong": "<millis>" } }`
   */
  def fromDateTime(bson: BSONDateTime): JsObject

  /**
   * See [[https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Decimal128 syntax]]:
   *
   * `{ "\$numberDecimal": "<number>" }`
   */
  def fromDecimal(bson: BSONDecimal): JsObject

  /** Converts to a JSON object */
  def fromDocument(bson: BSONDocument): JsObject

  /**
   * See [[https://github.com/mongodb/specifications/blob/master/source/extended-json.rst syntax]]:
   *
   * `{ "\$code": "<javascript>" }`
   */
  def fromJavaScript(bson: BSONJavaScript): JsObject

  /**
   * See [[https://github.com/mongodb/specifications/blob/master/source/extended-json.rst syntax]]:
   *
   * `{
   *   "\$code": "<javascript>",
   *   "\$scope": { }
   * }`
   */
  def fromJavaScriptWS(bson: BSONJavaScriptWS): JsObject

  private[reactivemongo] val JsMaxKey =
    JsObject(Map[String, JsValue](f"$$maxKey" -> JsNumber(1)))

  /**
   * See [[https://github.com/mongodb/specifications/blob/master/source/extended-json.rst syntax]]:
   *
   * `{ "\$maxKey": 1 }`
   */
  implicit final val fromMaxKey: BSONMaxKey => JsObject = _ => JsMaxKey

  private[reactivemongo] val JsMinKey =
    JsObject(Map[String, JsValue](f"$$minKey" -> JsNumber(1)))

  /**
   * See [[https://github.com/mongodb/specifications/blob/master/source/extended-json.rst syntax]]:
   *
   * `{ "\$minKey": 1 }`
   */
  implicit final val fromMinKey: BSONMinKey => JsObject = _ => JsMinKey

  implicit val fromNull: BSONNull => JsNull.type = _ => JsNull

  /**
   * See [[https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.ObjectId syntax]]:
   *
   * `{ "\$oid": "<ObjectId bytes>" }`
   */
  def fromObjectID(bson: BSONObjectID): JsObject

  /**
   * See [[https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Regular-Expression syntax]]:
   *
   * `{ "\$regularExpression":
   *    {
   *       "pattern": "<regexPattern>",
   *       "options": "<options>"
   *   }
   * }`
   */
  def fromRegex(rx: BSONRegex): JsObject

  implicit final def fromStr(bson: BSONString): JsString = JsString(bson.value)

  /**
   * See [[https://github.com/mongodb/specifications/blob/master/source/extended-json.rst syntax]]:
   *
   * `{ "\$symbol": "<name>" }`
   */
  def fromSymbol(bson: BSONSymbol): JsObject

  /**
   * See [[https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Timestamp syntax]]:
   *
   * `{ "\$timestamp": {"t": <t>, "i": <i>} }`
   */
  def fromTimestamp(ts: BSONTimestamp): JsObject

  private[reactivemongo] val JsUndefined =
    JsObject(Map[String, JsValue](f"$$undefined" -> JsTrue))

  /**
   * See [[https://github.com/mongodb/specifications/blob/master/source/extended-json.rst syntax]]:
   *
   * `{ "\$undefined": true }`
   */
  implicit final val fromUndefined: BSONUndefined => JsObject = _ => JsUndefined

  def fromValue(bson: BSONValue): JsValue

  /**
   * First checks whether an explicit type (e.g. `\$binary`) is specified,
   * otherwise converts to a BSON document.
   */
  def fromObject(js: JsObject): BSONValue
}

object FromValue {
  @inline implicit def defaultFromValue: FromValue = ValueConverters
}

/** Conversion API from BSON to JSON values */
sealed trait ToValue {
  implicit final def toJsValueWrapper[T <: BSONValue](value: T): Json.JsValueWrapper = implicitly[Json.JsValueWrapper](fromValue(value))

  implicit final def toArray(js: JsArray): BSONArray =
    BSONArray(js.value.map(toValue))

  implicit final def toBoolean(js: JsBoolean): BSONBoolean =
    BSONBoolean(js.value)

  /**
   * If the number:
   *
   * - is not whole then it's converted to BSON double,
   * - is a valid integer then it's converted to a BSON integer (int32),
   * - otherwise it's converted to a BSON long integer (int64).
   */
  def toNumber(js: JsNumber): BSONValue

  implicit final val toNull: JsNull.type => BSONNull = _ => BSONNull

  implicit final def toStr(js: JsString): BSONValue = {
    if (js.value == null) BSONNull
    else BSONString(js.value)
  }

  /** See [[toValue]] */
  def toDocument(js: JsObject): BSONDocument

  def toValue(js: JsValue): BSONValue
}

object ToValue {
  @inline implicit def defaultToValue: ToValue = ValueConverters
}
