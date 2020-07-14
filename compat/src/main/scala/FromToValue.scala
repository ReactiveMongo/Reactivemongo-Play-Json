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

  implicit final def fromArray(arr: BSONArray): JsArray =
    JsArray(arr.values.map(fromValue))

  def fromBinary(bin: BSONBinary): JsObject

  def fromBoolean(bson: BSONBoolean): JsBoolean

  /** JSON representation for temporal types */
  type JsonTime <: JsValue

  def fromDateTime(bson: BSONDateTime): JsonTime

  /**
   * See [[https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Decimal128 syntax]]:
   *
   * `{ "\$numberDecimal": "<number>" }`
   */
  def fromDecimal(bson: BSONDecimal): JsObject

  /** Converts to a JSON object */
  def fromDocument(bson: BSONDocument)(implicit conv: FromValue): JsObject

  type JsonJavaScript <: JsValue

  def fromJavaScript(bson: BSONJavaScript): JsonJavaScript

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

  type JsonObjectID <: JsValue

  def fromObjectID(bson: BSONObjectID): JsonObjectID

  def fromRegex(rx: BSONRegex): JsObject

  implicit final def fromStr(bson: BSONString): JsString = JsString(bson.value)

  type JsonSymbol <: JsValue

  def fromSymbol(bson: BSONSymbol): JsonSymbol

  def fromTimestamp(ts: BSONTimestamp): JsonTime

  private[reactivemongo] val JsUndefined =
    JsObject(Map[String, JsValue](f"$$undefined" -> JsTrue))

  /**
   * See [[https://github.com/mongodb/specifications/blob/master/source/extended-json.rst syntax]]:
   *
   * `{ "\$undefined": true }`
   */
  implicit final val fromUndefined: BSONUndefined => JsObject = _ => JsUndefined

  def fromValue(bson: BSONValue): JsValue

  final protected def jsonValue(bson: BSONValue)(
    implicit
    conv: FromValue): JsValue = bson match {
    case arr: BSONArray => conv.fromArray(arr)
    case bin: BSONBinary => conv.fromBinary(bin)

    case BSONBoolean(true) => JsTrue
    case BSONBoolean(_) => JsFalse

    case dt: BSONDateTime => conv.fromDateTime(dt)
    case dec: BSONDecimal => conv.fromDecimal(dec)
    case doc: BSONDocument => conv.fromDocument(doc)
    case d: BSONDouble => conv.fromDouble(d)
    case i: BSONInteger => conv.fromInteger(i)

    case js: BSONJavaScript => conv.fromJavaScript(js)
    case jsw: BSONJavaScriptWS => conv.fromJavaScriptWS(jsw)

    case l: BSONLong => conv.fromLong(l)

    case BSONMaxKey => JsMaxKey
    case BSONMinKey => JsMinKey
    case BSONNull => JsNull

    case oid: BSONObjectID => conv.fromObjectID(oid)
    case re: BSONRegex => conv.fromRegex(re)
    case str: BSONString => conv.fromStr(str)
    case sym: BSONSymbol => conv.fromSymbol(sym)
    case ts: BSONTimestamp => conv.fromTimestamp(ts)

    case _ => JsUndefined
  }

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

  implicit final def toArray(arr: JsArray): BSONArray =
    BSONArray(arr.value.map(toValue))

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

  @SuppressWarnings(Array("NullParameter"))
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
