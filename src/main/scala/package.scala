/*
 * Copyright 2012-2013 Stephane Godbillon (@sgodbillon)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package reactivemongo.play.json

import scala.util.{ Failure, Success, Try }

import play.api.libs.json.{
  Format,
  IdxPathNode,
  JsArray,
  JsBoolean,
  JsError,
  JsNumber,
  JsNull,
  JsLookupResult,
  JsObject,
  JsResult,
  JsSuccess,
  JsString,
  JsPath,
  JsValue,
  Json,
  KeyPathNode,
  OFormat,
  OWrites,
  Reads,
  RecursiveSearch,
  Writes,
  JsResultException,
  __
}
import reactivemongo.bson.{
  BSONArray,
  BSONBinary,
  BSONBoolean,
  BSONDateTime,
  BSONDocument,
  BSONDocumentReader,
  BSONDocumentWriter,
  BSONDouble,
  BSONHandler,
  BSONInteger,
  BSONJavaScript,
  BSONLong,
  BSONMaxKey,
  BSONMinKey,
  BSONNull,
  BSONSymbol,
  BSONObjectID,
  BSONRegex,
  BSONString,
  BSONTimestamp,
  BSONUndefined,
  BSONValue,
  BSONWriter,
  BSONReader,
  Subtype
}
import reactivemongo.bson.utils.Converters

import scala.math.BigDecimal.{
  double2bigDecimal,
  int2bigDecimal,
  long2bigDecimal
}

object `package` extends ImplicitBSONHandlers {
  object readOpt {
    implicit def optionReads[T](implicit r: Reads[T]): Reads[Option[T]] =
      Reads.optionWithNull[T]

    def apply[T](lookup: JsLookupResult)(implicit r: Reads[T]): JsResult[Option[T]] = lookup.toOption.fold[JsResult[Option[T]]](JsSuccess(None))(_.validate[Option[T]])
  }
}

@SuppressWarnings(Array("NullAssignment"))
class JSONException(message: String, cause: Throwable = null)
  extends RuntimeException(message, cause)

object BSONFormats extends BSONFormats {
  def jsonOWrites[T](implicit bsonWriter: BSONDocumentWriter[T]): OWrites[T] =
    OWrites[T] { in: T => BSONDocumentFormat.json(bsonWriter.write(in)) }

  def jsonWrites[T](implicit bsonWriter: BSONWriter[T, _ <: BSONValue]): Writes[T] = Writes[T] { in: T => BSONFormats.toJSON(bsonWriter.write(in)) }

  def jsonReads[T](implicit bsonReader: BSONReader[_ <: BSONValue, T]): Reads[T] = Reads[T] { json =>
    val r = bsonReader.widenReader[T]

    BSONFormats.toBSON(json).flatMap(r.readTry(_) match {
      case Success(v)      => JsSuccess(v)
      case Failure(reason) => JsError(reason.getMessage)
    })
  }

  def jsonOFormat[T: BSONDocumentWriter: BSONDocumentReader]: OFormat[T] =
    OFormat(jsonReads[T], jsonOWrites[T])

  def jsonFormat[T](implicit h: BSONHandler[_ <: BSONValue, T]): Format[T] =
    Format(jsonReads[T](h), jsonWrites[T](h))
}

/**
 * JSON Formats for BSONValues.
 */
sealed trait BSONFormats extends LowerImplicitBSONHandlers {
  trait PartialReads[T <: BSONValue] extends Reads[T] {
    def partialReads: PartialFunction[JsValue, JsResult[T]]

    def reads(json: JsValue) = partialReads.lift(json).
      getOrElse(JsError(s"unhandled json value: $json"))
  }

  trait PartialWrites[T <: BSONValue] extends Writes[T] {
    def partialWrites: PartialFunction[BSONValue, JsValue]

    def writes(t: T): JsValue = partialWrites(t)
  }

  trait PartialFormat[T <: BSONValue]
    extends Format[T] with PartialReads[T] with PartialWrites[T]

  implicit object BSONDoubleFormat extends PartialFormat[BSONDouble] {
    private val jsNaN = {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      @inline def json = Json.obj(f"$$double" -> "NaN")

      json
    }

    val partialReads: PartialFunction[JsValue, JsResult[BSONDouble]] = {
      case JsNumber(n) if !n.ulp.isWhole =>
        JsSuccess(BSONDouble(n.toDouble))

      case DoubleValue(value) => JsSuccess(BSONDouble(value))
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      case BSONDouble(v) if v.isNaN => jsNaN
      case BSONDouble(v)            => JsNumber(v)
    }

    private object DoubleValue {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      def unapply(obj: JsObject): Option[Double] =
        (obj \ f"$$double").asOpt[JsNumber].map(_.value.toDouble)
    }
  }

  implicit object BSONStringFormat extends PartialFormat[BSONString] {
    val partialReads: PartialFunction[JsValue, JsResult[BSONString]] = {
      case JsString(str) => JsSuccess(BSONString(str))
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      case str: BSONString => JsString(str.value)
    }
  }

  class BSONDocumentFormat(toBSON: JsValue => JsResult[BSONValue], toJSON: BSONValue => JsValue) extends PartialFormat[BSONDocument] {
    val partialReads: PartialFunction[JsValue, JsResult[BSONDocument]] = {
      @SuppressWarnings(Array("CatchException"))
      @inline def reads: PartialFunction[JsValue, JsResult[BSONDocument]] = {
        case obj @ JsObject(_) => try {
          JsSuccess(bson(obj))
        } catch {
          case e: Exception => JsError(e.getMessage)
        }
      }

      reads
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      case doc: BSONDocument => json(doc)
    }

    // UNSAFE - FOR INTERNAL USE
    private[json] def bson(obj: JsObject): BSONDocument = BSONDocument(
      obj.fields.map { tuple =>
        tuple._1 -> (toBSON(tuple._2) match {
          case JsSuccess(bson, _) => bson
          case JsError(err)       => throw new JSONException(err.toString)
        })
      }
    )

    // UNSAFE - FOR INTERNAL USE
    private[json] def json(bson: BSONDocument): JsObject =
      JsObject(bson.elements.map(elem => elem.name -> toJSON(elem.value)))
  }

  implicit object BSONDocumentFormat extends BSONDocumentFormat(toBSON, toJSON)
  class BSONArrayFormat(toBSON: JsValue => JsResult[BSONValue], toJSON: BSONValue => JsValue) extends PartialFormat[BSONArray] {
    val partialReads: PartialFunction[JsValue, JsResult[BSONArray]] = {
      @SuppressWarnings(Array("CatchException", "LooksLikeInterpolatedString"))
      @inline def bson: PartialFunction[JsValue, JsResult[BSONArray]] = {
        case arr @ JsArray(_) =>
          try {
            JsSuccess(BSONArray(arr.value.map { value =>
              toBSON(value) match {
                case JsSuccess(bson, _) => bson
                case JsError(err)       => throw new JSONException(err.toString)
              }
            }))
          } catch {
            case e: Exception => JsError(e.getMessage)
          }
      }

      bson
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      case array: BSONArray => JsArray(array.values.map(toJSON))
    }
  }

  implicit object BSONArrayFormat extends BSONArrayFormat(toBSON, toJSON)

  implicit object BSONObjectIDFormat extends PartialFormat[BSONObjectID] {
    val partialReads: PartialFunction[JsValue, JsResult[BSONObjectID]] = {
      case OidValue(oid) => BSONObjectID.parse(oid) match {
        case Success(id) => JsSuccess(id)
        case Failure(er) => JsError(er.getMessage)
      }
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      @inline def json: PartialFunction[BSONValue, JsValue] = {
        case oid: BSONObjectID => Json.obj(f"$$oid" -> oid.stringify)
      }

      json
    }

    private object OidValue {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      def unapply(obj: JsObject): Option[String] =
        if (obj.fields.size != 1) None else (obj \ f"$$oid").asOpt[String]
    }
  }

  implicit object BSONJavaScriptFormat extends PartialFormat[BSONJavaScript] {
    val partialReads: PartialFunction[JsValue, JsResult[BSONJavaScript]] = {
      case JavascriptValue(oid) => JsSuccess(BSONJavaScript(oid))
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      @inline def json: PartialFunction[BSONValue, JsValue] = {
        case BSONJavaScript(code) => Json.obj(f"$$javascript" -> code)
      }

      json
    }

    private object JavascriptValue {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      def unapply(obj: JsObject): Option[String] =
        if (obj.fields.size != 1) None else (obj \ f"$$javascript").asOpt[String]
    }
  }

  implicit object BSONBooleanFormat extends PartialFormat[BSONBoolean] {
    val partialReads: PartialFunction[JsValue, JsResult[BSONBoolean]] = {
      case JsBoolean(v) => JsSuccess(BSONBoolean(v))
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      case boolean: BSONBoolean => JsBoolean(boolean.value)
    }
  }

  implicit object BSONDateTimeFormat extends PartialFormat[BSONDateTime] {
    val partialReads: PartialFunction[JsValue, JsResult[BSONDateTime]] = {
      case DateValue(value) => JsSuccess(BSONDateTime(value))
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      @inline def json: PartialFunction[BSONValue, JsValue] = {
        case dt: BSONDateTime => Json.obj(f"$$date" -> dt.value)
      }

      json
    }

    private object DateValue {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      def unapply(obj: JsObject): Option[Long] =
        (obj \ f"$$date").asOpt[JsValue].flatMap {
          case n @ JsNumber(_) => n.asOpt[Long]
          case o @ JsObject(_) => (o \ f"$$numberLong").asOpt[Long]
          case _               => None
        }
    }
  }

  implicit object BSONTimestampFormat extends PartialFormat[BSONTimestamp] {
    val partialReads: PartialFunction[JsValue, JsResult[BSONTimestamp]] = {
      case TimeValue((time, i)) => JsSuccess(BSONTimestamp(time, i))
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      def json: PartialFunction[BSONValue, JsValue] = {
        case ts: BSONTimestamp => Json.obj(
          f"$$time" -> (ts.value >>> 32), f"$$i" -> ts.value.toInt,
          f"$$timestamp" -> Json.obj(
            "t" -> (ts.value >>> 32), "i" -> ts.value.toInt
          )
        )
      }

      json
    }

    private object TimeValue {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      def unapply(obj: JsObject): Option[(Long, Int)] = (for {
        time <- (obj \ f"$$time").asOpt[Long]
        i <- (obj \ f"$$i").asOpt[Int]
      } yield (time, i)).orElse(legacy(obj))

      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      def legacy(obj: JsObject): Option[(Long, Int)] = for {
        time <- (obj \ f"$$timestamp" \ "t").asOpt[Long]
        i <- (obj \ f"$$timestamp" \ "i").asOpt[Int]
      } yield (time, i)
    }
  }

  implicit object BSONUndefinedFormat
    extends PartialFormat[BSONUndefined.type] {
    private object Undefined {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      def unapply(obj: JsObject): Option[BSONUndefined.type] =
        obj.value.get(f"$$undefined") match {
          case Some(JsBoolean(true)) => Some(BSONUndefined)
          case _                     => None
        }
    }

    val partialReads: PartialFunction[JsValue, JsResult[BSONUndefined.type]] = {
      case Undefined(bson) => JsSuccess(bson)
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      @inline def json: PartialFunction[BSONValue, JsValue] = {
        case BSONUndefined => Json.obj(f"$$undefined" -> true)
      }

      json
    }
  }

  implicit object BSONRegexFormat extends PartialFormat[BSONRegex] {
    val partialReads: PartialFunction[JsValue, JsResult[BSONRegex]] = {
      @SuppressWarnings(Array(
        "ExistsSimplifableToContains", "LooksLikeInterpolatedString"
      ))
      @inline def bson: PartialFunction[JsValue, JsResult[BSONRegex]] = {
        case js @ JsObject(fields) if (
          js.values.size == 1 && fields.headOption.exists(_._1 == f"$$regex")
        ) => {
          fields.headOption.flatMap(_._2.asOpt[String]).
            map(rx => JsSuccess(BSONRegex(rx, ""))).
            getOrElse(JsError(__ \ f"$$regex", "string expected"))
        }

        case js @ JsObject(_) if (
          js.value.size == 2 && js.value.exists(_._1 == f"$$regex") &&
          js.value.exists(_._1 == f"$$options")
        ) => {
          val rx = (js \ f"$$regex").asOpt[String]
          val opts = (js \ f"$$options").asOpt[String]

          (rx, opts) match {
            case (Some(rx), Some(opts)) => JsSuccess(BSONRegex(rx, opts))
            case (None, Some(_))        => JsError(__ \ f"$$regex", "string expected")
            case (Some(_), None)        => JsError(__ \ f"$$options", "string expected")
            case _                      => JsError(__ \ f"$$regex", "string expected") ++ JsError(__ \ f"$$options", "string expected")
          }
        }
      }

      bson
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      @inline def json: PartialFunction[BSONValue, JsValue] = {
        case rx: BSONRegex =>
          if (rx.flags.isEmpty)
            Json.obj(f"$$regex" -> rx.value)
          else Json.obj(f"$$regex" -> rx.value, f"$$options" -> rx.flags)
      }

      json
    }
  }

  implicit object BSONMinKeyFormat
    extends PartialFormat[BSONMinKey.type] {
    private object MinKey {
      private val JsOne = JsNumber(1)

      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      def unapply(obj: JsObject): Option[BSONMinKey.type] =
        obj.value.get(f"$$minKey") match {
          case Some(JsOne)           => Some(BSONMinKey)
          case Some(JsBoolean(true)) => Some(BSONMinKey)
          case _                     => None
        }
    }

    val partialReads: PartialFunction[JsValue, JsResult[BSONMinKey.type]] = {
      case MinKey(bson) => JsSuccess(bson)
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      @inline def json: PartialFunction[BSONValue, JsValue] = {
        case BSONMinKey => Json.obj(f"$$minKey" -> 1)
      }

      json
    }
  }

  implicit object BSONMaxKeyFormat
    extends PartialFormat[BSONMaxKey.type] {
    private object MaxKey {
      private val JsOne = JsNumber(1)

      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      def unapply(obj: JsObject): Option[BSONMaxKey.type] =
        obj.value.get(f"$$maxKey") match {
          case Some(JsOne)           => Some(BSONMaxKey)
          case Some(JsBoolean(true)) => Some(BSONMaxKey)
          case _                     => None
        }
    }

    val partialReads: PartialFunction[JsValue, JsResult[BSONMaxKey.type]] = {
      case MaxKey(bson) => JsSuccess(bson)
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      @inline def json: PartialFunction[BSONValue, JsValue] = {
        case BSONMaxKey => Json.obj(f"$$maxKey" -> 1)
      }

      json
    }
  }

  implicit object BSONNullFormat extends PartialFormat[BSONNull.type] {
    val partialReads: PartialFunction[JsValue, JsResult[BSONNull.type]] = {
      case JsNull => JsSuccess(BSONNull)
    }
    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      case BSONNull => JsNull
    }
  }

  implicit object BSONIntegerFormat extends PartialFormat[BSONInteger] {
    val partialReads: PartialFunction[JsValue, JsResult[BSONInteger]] = {
      case JsNumber(n) if n.isValidInt => JsSuccess(BSONInteger(n.toInt))
      case IntValue(value)             => JsSuccess(BSONInteger(value))
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      case BSONInteger(i) => JsNumber(i)
    }

    private object IntValue {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      def unapply(obj: JsObject): Option[Int] =
        (obj \ f"$$int").asOpt[JsNumber].map(_.value.toInt)
    }
  }

  implicit object BSONLongFormat extends PartialFormat[BSONLong] {
    val partialReads: PartialFunction[JsValue, JsResult[BSONLong]] = {
      case JsNumber(long)   => JsSuccess(BSONLong(long.toLong))
      case LongValue(value) => JsSuccess(BSONLong(value))
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      case BSONLong(l) => JsNumber(l)
    }

    private object LongValue {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      def unapply(obj: JsObject): Option[Long] =
        (obj \ f"$$long").asOpt[JsNumber].map(_.value.toLong)
    }
  }

  implicit object BSONBinaryFormat extends PartialFormat[BSONBinary] {
    val partialReads: PartialFunction[JsValue, JsResult[BSONBinary]] = {
      @SuppressWarnings(Array("CatchException", "LooksLikeInterpolatedString"))
      @inline def bson: PartialFunction[JsValue, JsResult[BSONBinary]] = {
        case JsString(str) => try {
          JsSuccess(BSONBinary(
            Converters.str2Hex(str), Subtype.UserDefinedSubtype
          ))
        } catch {
          case e: Exception => JsError(
            s"error deserializing hex ${e.getMessage}"
          )
        }

        case obj @ JsObject(fields) if fields.exists {
          case (str, _: JsString) if str == f"$$binary" => true
          case _                                        => false
        } => try {
          JsSuccess(BSONBinary(Converters.str2Hex((obj \ f"$$binary").as[String]), Subtype.UserDefinedSubtype))
        } catch {
          case e: Exception => JsError(
            s"error deserializing hex ${e.getMessage}"
          )
        }
      }

      bson
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      @inline def json: PartialFunction[BSONValue, JsValue] = {
        case binary: BSONBinary =>
          val remaining = binary.value.readable()

          Json.obj(
            f"$$binary" -> Converters.hex2Str(
              binary.value.slice(remaining).readArray(remaining)
            ),
            f"$$type" -> Converters.hex2Str(Array(binary.subtype.value.toByte))
          )
      }

      json
    }
  }

  implicit object BSONSymbolFormat extends PartialFormat[BSONSymbol] {
    val partialReads: PartialFunction[JsValue, JsResult[BSONSymbol]] = {
      case SymbolValue(value) => JsSuccess(BSONSymbol(value))
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      @inline def json: PartialFunction[BSONValue, JsValue] = {
        case BSONSymbol(s) => Json.obj(f"$$symbol" -> s)
      }

      json
    }

    private object SymbolValue {
      @SuppressWarnings(Array("LooksLikeInterpolatedString"))
      def unapply(obj: JsObject): Option[String] =
        if (obj.fields.size != 1) None else (obj \ f"$$symbol").asOpt[String]
    }
  }

  val numberReads: PartialFunction[JsValue, JsResult[BSONValue]] = {
    case JsNumber(n) if !n.ulp.isWhole => JsSuccess(BSONDouble(n.toDouble))
    case JsNumber(n) if n.isValidInt   => JsSuccess(BSONInteger(n.toInt))
    case JsNumber(n)                   => JsSuccess(BSONLong(n.toLong))
  }

  def toBSON(json: JsValue): JsResult[BSONValue] = readAsBSONValue(json)

  @SuppressWarnings(Array("MaxParameters"))
  def readAsBSONValue(json: JsValue)(
    implicit
    string: PartialReads[BSONString],
    objectID: PartialReads[BSONObjectID],
    javascript: PartialReads[BSONJavaScript],
    dateTime: PartialReads[BSONDateTime],
    timestamp: PartialReads[BSONTimestamp],
    binary: PartialReads[BSONBinary],
    regex: PartialReads[BSONRegex],
    double: PartialReads[BSONDouble],
    integer: PartialReads[BSONInteger],
    long: PartialReads[BSONLong],
    boolean: PartialReads[BSONBoolean],
    minKey: PartialReads[BSONMinKey.type],
    maxKey: PartialReads[BSONMaxKey.type],
    bnull: PartialReads[BSONNull.type],
    symbol: PartialReads[BSONSymbol],
    array: PartialReads[BSONArray],
    doc: PartialReads[BSONDocument],
    undef: PartialReads[BSONUndefined.type]
  ): JsResult[BSONValue] =
    string.partialReads.
      orElse(objectID.partialReads).
      orElse(javascript.partialReads).
      orElse(dateTime.partialReads).
      orElse(timestamp.partialReads).
      orElse(binary.partialReads).
      orElse(regex.partialReads).
      orElse(double.partialReads).
      orElse(integer.partialReads).
      orElse(long.partialReads).
      orElse(boolean.partialReads).
      orElse(minKey.partialReads).
      orElse(maxKey.partialReads).
      orElse(bnull.partialReads).
      orElse(symbol.partialReads).
      orElse(array.partialReads).
      orElse(doc.partialReads).
      orElse(undef.partialReads).
      lift(json).getOrElse(JsError(s"unhandled json value: $json"))

  def toJSON(bson: BSONValue): JsValue = writeAsJsValue(bson)

  @SuppressWarnings(Array("MaxParameters"))
  def writeAsJsValue(bson: BSONValue)(
    implicit
    string: PartialWrites[BSONString],
    objectID: PartialWrites[BSONObjectID],
    javascript: PartialWrites[BSONJavaScript],
    dateTime: PartialWrites[BSONDateTime],
    timestamp: PartialWrites[BSONTimestamp],
    binary: PartialWrites[BSONBinary],
    regex: PartialWrites[BSONRegex],
    double: PartialWrites[BSONDouble],
    integer: PartialWrites[BSONInteger],
    long: PartialWrites[BSONLong],
    boolean: PartialWrites[BSONBoolean],
    minKey: PartialWrites[BSONMinKey.type],
    maxKey: PartialWrites[BSONMaxKey.type],
    bnull: PartialWrites[BSONNull.type],
    symbol: PartialWrites[BSONSymbol],
    array: PartialWrites[BSONArray],
    doc: PartialWrites[BSONDocument],
    undef: PartialWrites[BSONUndefined.type]
  ): JsValue = string.partialWrites.
    orElse(objectID.partialWrites).
    orElse(javascript.partialWrites).
    orElse(dateTime.partialWrites).
    orElse(timestamp.partialWrites).
    orElse(binary.partialWrites).
    orElse(regex.partialWrites).
    orElse(double.partialWrites).
    orElse(integer.partialWrites).
    orElse(long.partialWrites).
    orElse(boolean.partialWrites).
    orElse(minKey.partialWrites).
    orElse(maxKey.partialWrites).
    orElse(bnull.partialWrites).
    orElse(symbol.partialWrites).
    orElse(array.partialWrites).
    orElse(doc.partialWrites).
    orElse(undef.partialWrites).
    lift(bson).getOrElse(
      throw new JSONException(s"Unhandled json value: $bson")
    )
}

object Writers {
  implicit class JsPathMongo(val jp: JsPath) extends AnyVal {
    def writemongo[A](implicit writer: Writes[A]): OWrites[A] =
      OWrites[A] { o =>
        val newPath = jp.path.flatMap {
          case e: KeyPathNode     => Some(e.key)
          case e: RecursiveSearch => Some(s"$$.${e.key}")
          case e: IdxPathNode     => Some(s"${e.idx}")
        }.mkString(".")

        val orig = writer.writes(o)
        orig match {
          case JsObject(e) => JsObject(e.flatMap {
            case (k, v) => Seq(s"${newPath}.$k" -> v)
          })

          case e: JsValue => JsObject(Seq(newPath -> e))
        }
      }
  }
}

object JSONSerializationPack extends reactivemongo.api.SerializationPack {
  import reactivemongo.bson.buffer.{ ReadableBuffer, WritableBuffer }
  import reactivemongo.play.json.commands.JSONCommandError

  type Value = JsValue
  type ElementProducer = (String, Json.JsValueWrapper)
  type Document = JsObject
  type Writer[A] = OWrites[A]
  type Reader[A] = Reads[A]
  type NarrowValueReader[A] = Reads[A]
  private[reactivemongo] type WidenValueReader[A] = Reads[A]

  object IdentityReader extends Reader[Document] {
    def reads(js: JsValue): JsResult[Document] = js match {
      case o @ JsObject(_) => JsSuccess(o)
      case v               => JsError(s"object is expected: $v")
    }
  }

  object IdentityWriter extends Writer[Document] {
    def writes(document: Document): Document = document
  }

  def serialize[A](a: A, writer: Writer[A]): Document = writer.writes(a)

  def deserialize[A](document: Document, reader: Reader[A]): A =
    reader.reads(document) match {
      case JSONCommandError(err) => throw err
      case JsError(errors)       => throw JsResultException(errors)
      case JsSuccess(v, _)       => v
    }

  def writeToBuffer(buffer: WritableBuffer, document: Document): WritableBuffer = BSONFormats.toBSON(document) match {
    case JSONCommandError(err) => throw err
    case JsError(errors)       => throw JsResultException(errors)

    case JsSuccess(d @ BSONDocument(_), _) => {
      BSONDocument.write(d, buffer)
      buffer
    }

    case JsSuccess(v, _) => sys.error(
      s"fails to write the document: $document; unexpected conversion $v"
    )
  }

  def readFromBuffer(buffer: ReadableBuffer): Document =
    BSONFormats.toJSON(BSONDocument.read(buffer)).as[Document]

  def writer[A](f: A => Document): Writer[A] = new OWrites[A] {
    def writes(input: A): Document = f(input)
  }

  def isEmpty(document: Document): Boolean = document.values.isEmpty

  def widenReader[T](r: NarrowValueReader[T]): WidenValueReader[T] = r

  def readValue[A](value: Value, reader: WidenValueReader[A]): Try[A] =
    reader.reads(value) match {
      case JSONCommandError(err) => Failure(err)
      case JsError(errors)       => Failure(JsResultException(errors))

      case JsSuccess(v, _)       => Success(v)
    }

}

import play.api.libs.json.{ JsObject, JsValue }

object ImplicitBSONHandlers extends ImplicitBSONHandlers

/**
 * Implicit BSON Handlers (BSONDocumentReader/BSONDocumentWriter for JsObject)
 */
sealed trait ImplicitBSONHandlers extends BSONFormats {
  implicit object JsObjectWriter extends BSONDocumentWriter[JsObject] {
    def write(obj: JsObject): BSONDocument =
      BSONFormats.BSONDocumentFormat.bson(obj)
  }

  implicit object JsObjectReader extends BSONDocumentReader[JsObject] {
    def read(document: BSONDocument) =
      BSONFormats.BSONDocumentFormat.writes(document).as[JsObject]
  }

  implicit object BSONDocumentWrites
    extends JSONSerializationPack.Writer[BSONDocument] {
    def writes(bson: BSONDocument): JsObject =
      BSONFormats.BSONDocumentFormat.json(bson)
  }

  implicit object JsObjectDocumentWriter // Identity writer
    extends JSONSerializationPack.Writer[JsObject] {
    def writes(obj: JsObject): JSONSerializationPack.Document = obj
  }
}

sealed trait LowerImplicitBSONHandlers {
  import scala.language.implicitConversions
  import reactivemongo.bson.{ BSONElement, Producer }

  implicit def jsWriter[A <: JsValue, B <: BSONValue] = BSONWriter[A, B] { js =>
    BSONFormats.toBSON(js) match {
      case JsSuccess(b: B @unchecked, _) => b
      case res                           => sys.error(s"fails to convert to BSON: $res")
    }
  }

  @SuppressWarnings(Array("MethodNames"))
  implicit def JsFieldBSONElementProducer[T <: JsValue](jsField: (String, T)): Producer[BSONElement] = Producer.element2Producer(BSONElement(jsField._1, BSONFormats.toBSON(jsField._2).get))

  implicit object BSONValueReads extends Reads[BSONValue] {
    def reads(js: JsValue) = BSONFormats.toBSON(js)
  }

  implicit object BSONValueWrites extends Writes[BSONValue] {
    def writes(bson: BSONValue) = BSONFormats.toJSON(bson)
  }
}
