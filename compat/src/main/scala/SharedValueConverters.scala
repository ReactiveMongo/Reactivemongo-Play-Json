package reactivemongo.play.json.compat

import java.util.Base64

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import scala.language.implicitConversions

import scala.util.{ Failure, Success }
import scala.util.control.NonFatal

import _root_.play.api.libs.json.{
  JsArray,
  JsBoolean,
  JsNull,
  JsNumber,
  JsObject,
  JsString,
  JsValue
}

import reactivemongo.api.bson.{
  BSONBinary,
  BSONBoolean,
  BSONDateTime,
  BSONDecimal,
  BSONDocument,
  BSONDouble,
  BSONLong,
  BSONInteger,
  BSONJavaScript,
  BSONJavaScriptWS,
  BSONMaxKey,
  BSONMinKey,
  BSONNull,
  BSONObjectID,
  BSONSymbol,
  BSONRegex,
  BSONTimestamp,
  BSONUndefined,
  BSONValue,
  Digest,
  Subtype
}

private[json] trait SharedValueConverters
  extends SharedValueConvertersLowPriority1 { self =>

  import ValueConverters.logger

  implicit final def fromBinary(bin: BSONBinary): JsObject =
    JsObject(Map[String, JsValue](
      f"$$binary" -> JsObject(Map[String, JsValue](
        "base64" -> JsString(base64Enc encodeToString bin.byteArray),
        f"subType" -> JsString(
          Digest.hex2Str(Array(bin.subtype.value.toByte)))))))

  implicit final def fromBoolean(bson: BSONBoolean): JsBoolean =
    if (bson.value) JsTrue else JsFalse

  implicit final def fromDateTime(bson: BSONDateTime): JsObject =
    JsObject(Map[String, JsValue](f"$$date" -> dsl.long(bson.value)))

  implicit final def fromDecimal(bson: BSONDecimal): JsObject =
    JsObject(Map[String, JsValue](
      f"$$numberDecimal" -> JsString(bson.toString)))

  implicit def fromDocument(bson: BSONDocument): JsObject =
    JsObject(bson.elements.map(elem => elem.name -> fromValue(elem.value)))

  implicit final def fromJavaScript(bson: BSONJavaScript): JsObject =
    JsObject(Map[String, JsValue](f"$$code" -> JsString(bson.value)))

  implicit final def fromJavaScriptWS(bson: BSONJavaScriptWS): JsObject =
    JsObject(Map[String, JsValue](
      f"$$code" -> JsString(bson.value),
      f"$$scope" -> fromDocument(bson.scope)))

  implicit final def fromObjectID(bson: BSONObjectID): JsObject =
    dsl.objectID(bson)

  implicit final def fromRegex(rx: BSONRegex): JsObject = {
    val builder = scala.collection.mutable.Map.empty[String, JsValue]

    builder.put("pattern", JsString(rx.value))

    if (rx.flags.nonEmpty) {
      builder.put("options", JsString(rx.flags))
    }

    JsObject(Map[String, JsValue](
      f"$$regularExpression" -> JsObject(builder.toMap)))
  }

  implicit final def fromSymbol(bson: BSONSymbol): JsObject =
    JsObject(Map[String, JsValue](f"$$symbol" -> JsString(bson.value)))

  implicit final def fromTimestamp(ts: BSONTimestamp): JsObject =
    JsObject(Map[String, JsValue](
      f"$$timestamp" -> JsObject(Map[String, JsValue](
        "t" -> JsNumber(ts.time), "i" -> JsNumber(ts.ordinal)))))

  // ---

  implicit final def toNumber(js: JsNumber): BSONValue = {
    val v = js.value

    if (!v.ulp.isWhole) {
      BSONDouble(v.toDouble)
    } else if (v.isValidInt) {
      BSONInteger(v.toInt)
    } else {
      BSONLong(v.toLong)
    }
  }

  // ---

  implicit final def fromObject(js: JsObject): BSONValue = js match {
    case BinaryObject(bin) => bin
    case DateObject(date) => date
    case Decimal128Object(dec) => dec
    case DoubleObject(d) => d

    case JavaScriptWSObject(js) => js
    case JavaScriptObject(js) => js

    case Int32Object(i) => i
    case Int64Object(i) => i
    case MaxKeyObject() => BSONMaxKey
    case MinKeyObject() => BSONMinKey
    case ObjectIdObject(oid) => oid
    case RegexObject(re) => re
    case TimestampObject(ts) => ts
    case UndefinedObject() => BSONUndefined
    case SymbolObject(sym) => sym
    case _ => toDocument(js)
  }

  /** See [[https://github.com/mongodb/specifications/blob/master/source/extended-json.rst#conversion-table syntax]] */
  private[json] object JavaScriptObject {
    def unapply(obj: JsObject): Option[BSONJavaScript] =
      (obj \ f"$$code").asOpt[String].map(BSONJavaScript(_))
  }

  /** See [[https://github.com/mongodb/specifications/blob/master/source/extended-json.rst#conversion-table syntax]] */
  private[json] object JavaScriptWSObject {
    def unapply(obj: JsObject): Option[BSONJavaScriptWS] = for {
      scope <- (obj \ f"$$scope").toOption.collect {
        case obj @ JsObject(_) => toDocument(obj)
      }
      code <- (obj \ f"$$code").asOpt[String]
    } yield BSONJavaScriptWS(code, scope)
  }

  /**
   * See [[https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Binary syntax]]
   */
  private[json] object BinaryObject {
    def unapply(obj: JsObject): Option[BSONBinary] = for {
      bin <- (obj \ f"$$binary").toOption.collect {
        case obj @ JsObject(_) => obj
      }
      hexaValue <- (bin \ "base64").asOpt[String]

      hexaTpe <- (bin \ "subType").asOpt[String]
      tpeByte <- Digest.str2Hex(hexaTpe).headOption

      subtpe <- try {
        Some(Subtype(tpeByte))
      } catch {
        case NonFatal(cause) =>
          logger.debug(s"Invalid Binary 'subType': $hexaTpe; See https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Binary", cause)

          None
      }

      bytes <- try {
        Some(base64Dec decode hexaValue)
      } catch {
        case NonFatal(cause) =>
          logger.debug(s"Invalid Binary 'base64' value; https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Binary", cause)

          None
      }
    } yield BSONBinary(bytes, subtpe)
  }

  /**
   * See [[https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Date syntax]]
   */
  private[json] object DateObject {
    @SuppressWarnings(Array("LooksLikeInterpolatedString"))
    def unapply(obj: JsObject): Option[BSONDateTime] =
      (obj \ f"$$date").toOption.flatMap {
        case JsString(repr) => try {
          val dt = ZonedDateTime.parse(repr, DateTimeFormatter.ISO_DATE_TIME)

          Some(BSONDateTime(dt.toInstant.toEpochMilli))
        } catch {
          case NonFatal(cause) =>
            logger.debug(s"Invalid relaxed Date: $repr; https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Date", cause)

            None
        }

        case Int64Object(ms) =>
          Some(BSONDateTime(ms.value))

        case _ => None
      }
  }

  /**
   * See [[https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Decimal128 syntax]]
   */
  private[json] object Decimal128Object {
    @SuppressWarnings(Array("LooksLikeInterpolatedString"))
    def unapply(obj: JsObject): Option[BSONDecimal] =
      (obj \ f"$$numberDecimal").asOpt[String].flatMap { repr =>
        BSONDecimal.parse(repr) match {
          case Success(v) => Some(v)

          case Failure(cause) => {
            logger.debug(s"Invalid JSON Decimal; See https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Decimal128", cause)

            None
          }
        }
      }
  }

  /**
   * See [[https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Double syntax]]
   */
  private[json] object DoubleObject {
    @SuppressWarnings(Array("LooksLikeInterpolatedString"))
    def unapply(obj: JsObject): Option[BSONDouble] =
      (obj \ f"$$numberDouble").asOpt[String].flatMap {
        case "-Infinity" => Some(BSONDouble(Double.NegativeInfinity))
        case "Infinity" => Some(BSONDouble(Double.PositiveInfinity))
        case "NaN" => Some(BSONDouble(Double.NaN))

        case repr => try {
          Some(BSONDouble(repr.toDouble))
        } catch {
          case NonFatal(cause) =>
            logger.debug(s"Invalid JSON Double; See https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Double", cause)

            None
        }
      }
  }

  /**
   * See [[https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Int32 syntax]]
   */
  private[json] object Int32Object {
    @SuppressWarnings(Array("LooksLikeInterpolatedString"))
    def unapply(obj: JsObject): Option[BSONInteger] =
      (obj \ f"$$numberInt").asOpt[String].flatMap { repr =>
        try {
          Some(BSONInteger(repr.toInt))
        } catch {
          case NonFatal(cause) =>
            logger.debug(s"Invalid JSON Int32; See https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Int32", cause)

            None
        }
      }
  }

  /**
   * See [[https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Int64 syntax]]
   */
  private[json] object Int64Object {
    @SuppressWarnings(Array("LooksLikeInterpolatedString"))
    def unapply(obj: JsObject): Option[BSONLong] =
      (obj \ f"$$numberLong").asOpt[String].flatMap { repr =>
        try {
          Some(BSONLong(repr.toLong))
        } catch {
          case NonFatal(cause) =>
            logger.debug(s"Invalid JSON Int64; See https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Int64", cause)

            None
        }
      }
  }

  private val JsOne = JsNumber(1)

  /**
   * See [[https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.MaxKey syntax]]
   */
  private[json] object MaxKeyObject {
    @SuppressWarnings(Array("LooksLikeInterpolatedString"))
    def unapply(obj: JsObject): Boolean =
      obj.value.get(f"$$maxKey") match {
        case Some(JsOne) => true
        case Some(JsBoolean(true)) => true
        case _ => false
      }
  }

  /**
   * See [[https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.MaxKey syntax]]
   */
  private[json] object MinKeyObject {
    @SuppressWarnings(Array("LooksLikeInterpolatedString"))
    def unapply(obj: JsObject): Boolean =
      obj.value.get(f"$$minKey") match {
        case Some(JsOne) => true
        case Some(JsBoolean(true)) => true
        case _ => false
      }
  }

  /**
   * See [[https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.ObjectId syntax]]
   */
  private[json] object ObjectIdObject {
    @SuppressWarnings(Array("LooksLikeInterpolatedString"))
    def unapply(obj: JsObject): Option[BSONObjectID] = {
      if (obj.fields.size != 1) None
      else (obj \ f"$$oid").asOpt[String].flatMap { repr =>
        BSONObjectID.parse(repr) match {
          case Success(oid) => Some(oid)

          case Failure(cause) => {
            logger.debug(s"Invalid ObjectId: $repr; See https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.ObjectId", cause)

            None
          }
        }
      }
    }
  }

  /**
   * See [[https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Regular-Expression syntax]]
   */
  private[json] object RegexObject {
    @SuppressWarnings(Array("LooksLikeInterpolatedString"))
    def unapply(js: JsObject): Option[BSONRegex] =
      (js \ f"$$regularExpression").toOption.flatMap {
        case obj @ JsObject(_) => (obj \ "pattern").asOpt[String].map { rx =>
          BSONRegex(rx, (obj \ "options").asOpt[String].getOrElse(""))
        }

        case _ => None
      }
  }

  /**
   * See [[https://docs.mongodb.com/manual/reference/mongodb-extended-json/#bson.Timestamp syntax]]
   */
  private[json] object TimestampObject {
    @SuppressWarnings(Array("LooksLikeInterpolatedString"))
    def unapply(js: JsObject): Option[BSONTimestamp] =
      (js \ f"$$timestamp").toOption.flatMap {
        case obj @ JsObject(_) => {
          for {
            time <- (obj \ "t").asOpt[Int]
            ord <- (obj \ "i").asOpt[Int]
          } yield BSONTimestamp(time, ord)
        }

        case _ => None
      }
  }

  private[json] object UndefinedObject {
    @SuppressWarnings(Array("LooksLikeInterpolatedString"))
    def unapply(obj: JsObject): Boolean =
      obj.value.get(f"$$undefined") match {
        case Some(JsOne) => true
        case Some(JsBoolean(true)) => true
        case _ => false
      }
  }

  private[json] object SymbolObject {
    @SuppressWarnings(Array("LooksLikeInterpolatedString"))
    def unapply(obj: JsObject): Option[BSONSymbol] =
      (obj \ f"$$symbol").asOpt[String].map(BSONSymbol(_))
  }

  private lazy val base64Dec = Base64.getDecoder

  private lazy val base64Enc = Base64.getEncoder
}

private[compat] sealed trait SharedValueConvertersLowPriority1 {
  _: SharedValueConverters =>

  implicit final def toDocument(js: JsObject): BSONDocument =
    BSONDocument(js.fields.map {
      case (nme, v) => nme -> toValue(v)
    })

  implicit final def toValue(js: JsValue): BSONValue = js match {
    case arr @ JsArray(_) => toArray(arr)

    case JsFalse => BSONBoolean(false)
    case JsTrue => BSONBoolean(true)
    case JsBoolean(b) => BSONBoolean(b)

    case JsNull => BSONNull

    case num @ JsNumber(_) => toNumber(num)
    case obj @ JsObject(_) => fromObject(obj)
    case str @ JsString(_) => toStr(str)
  }

  def fromValue(bson: BSONValue): JsValue
}
