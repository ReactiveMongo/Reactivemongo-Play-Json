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
  trait PartialFormat[T <: BSONValue] extends Format[T] {
    def partialReads: PartialFunction[JsValue, JsResult[T]]
    def partialWrites: PartialFunction[BSONValue, JsValue]

    def writes(t: T): JsValue = partialWrites(t)
    def reads(json: JsValue) = partialReads.lift(json).getOrElse(JsError(s"unhandled json value: $json"))
  }

  implicit object BSONDoubleFormat extends PartialFormat[BSONDouble] {
    private val jsNaN = Json.obj(f"$$double" -> "NaN")

    val partialReads: PartialFunction[JsValue, JsResult[BSONDouble]] = {
      case JsNumber(f)        => JsSuccess(BSONDouble(f.toDouble))
      case DoubleValue(value) => JsSuccess(BSONDouble(value))
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      case BSONDouble(v) if v.isNaN => jsNaN
      case BSONDouble(v)            => JsNumber(v)
    }

    private object DoubleValue {
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
      case obj @ JsObject(_) =>
        try {
          JsSuccess(bson(obj))
        } catch {
          case e: Throwable => JsError(e.getMessage)
        }
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
      case arr: JsArray =>
        try {
          JsSuccess(BSONArray(arr.value.map { value =>
            toBSON(value) match {
              case JsSuccess(bson, _) => bson
              case JsError(err)       => throw new JSONException(err.toString)
            }
          }))
        } catch {
          case e: Throwable => JsError(e.getMessage)
        }
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
      case oid: BSONObjectID => Json.obj(f"$$oid" -> oid.stringify)
    }

    private object OidValue {
      def unapply(obj: JsObject): Option[String] =
        if (obj.fields.size != 1) None else (obj \ f"$$oid").asOpt[String]
    }
  }

  implicit object BSONJavaScriptFormat extends PartialFormat[BSONJavaScript] {
    val partialReads: PartialFunction[JsValue, JsResult[BSONJavaScript]] = {
      case JavascriptValue(oid) => JsSuccess(BSONJavaScript(oid))
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      case BSONJavaScript(code) => Json.obj(f"$$javascript" -> code)
    }

    private object JavascriptValue {
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
      case dt: BSONDateTime => Json.obj(f"$$date" -> dt.value)
    }

    private object DateValue {
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
      case ts: BSONTimestamp => Json.obj(
        f"$$time" -> (ts.value >>> 32), f"$$i" -> ts.value.toInt,
        f"$$timestamp" -> Json.obj(
          "t" -> (ts.value >>> 32), "i" -> ts.value.toInt
        )
      )
    }

    private object TimeValue {
      def unapply(obj: JsObject): Option[(Long, Int)] = (for {
        time <- (obj \ f"$$time").asOpt[Long]
        i <- (obj \ f"$$i").asOpt[Int]
      } yield (time, i)).orElse(legacy(obj))

      def legacy(obj: JsObject): Option[(Long, Int)] = for {
        time <- (obj \ f"$$timestamp" \ "t").asOpt[Long]
        i <- (obj \ f"$$timestamp" \ "i").asOpt[Int]
      } yield (time, i)
    }
  }

  implicit object BSONUndefinedFormat
      extends PartialFormat[BSONUndefined.type] {
    private object Undefined {
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
      case BSONUndefined => Json.obj(f"$$undefined" -> true)
    }
  }

  implicit object BSONRegexFormat extends PartialFormat[BSONRegex] {
    val partialReads: PartialFunction[JsValue, JsResult[BSONRegex]] = {
      case js: JsObject if js.values.size == 1 && js.fields.head._1 == f"$$regex" =>
        js.fields.head._2.asOpt[String].
          map(rx => JsSuccess(BSONRegex(rx, ""))).
          getOrElse(JsError(__ \ f"$$regex", "string expected"))
      case js: JsObject if js.value.size == 2 && js.value.exists(_._1 == f"$$regex") && js.value.exists(_._1 == f"$$options") =>
        val rx = (js \ f"$$regex").asOpt[String]
        val opts = (js \ f"$$options").asOpt[String]
        (rx, opts) match {
          case (Some(rx), Some(opts)) => JsSuccess(BSONRegex(rx, opts))
          case (None, Some(_))        => JsError(__ \ f"$$regex", "string expected")
          case (Some(_), None)        => JsError(__ \ f"$$options", "string expected")
          case _                      => JsError(__ \ f"$$regex", "string expected") ++ JsError(__ \ f"$$options", "string expected")
        }
    }
    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      case rx: BSONRegex =>
        if (rx.flags.isEmpty)
          Json.obj(f"$$regex" -> rx.value)
        else Json.obj(f"$$regex" -> rx.value, f"$$options" -> rx.flags)
    }
  }

  implicit object BSONMinKeyFormat
      extends PartialFormat[BSONMinKey.type] {
    private object MinKey {
      private val JsOne = JsNumber(1)
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
      case BSONMinKey => Json.obj(f"$$minKey" -> 1)
    }
  }

  implicit object BSONMaxKeyFormat
      extends PartialFormat[BSONMaxKey.type] {
    private object MaxKey {
      private val JsOne = JsNumber(1)
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
      case BSONMaxKey => Json.obj(f"$$maxKey" -> 1)
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
      case JsNumber(i)     => JsSuccess(BSONInteger(i.toInt))
      case IntValue(value) => JsSuccess(BSONInteger(value))
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      case BSONInteger(i) => JsNumber(i)
    }

    private object IntValue {
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
      def unapply(obj: JsObject): Option[Long] =
        (obj \ f"$$long").asOpt[JsNumber].map(_.value.toLong)
    }
  }

  implicit object BSONBinaryFormat extends PartialFormat[BSONBinary] {
    val partialReads: PartialFunction[JsValue, JsResult[BSONBinary]] = {
      case JsString(str) => try {
        JsSuccess(BSONBinary(Converters.str2Hex(str), Subtype.UserDefinedSubtype))
      } catch {
        case e: Throwable => JsError(s"error deserializing hex ${e.getMessage}")
      }
      case obj: JsObject if obj.fields.exists {
        case (str, _: JsString) if str == f"$$binary" => true
        case _                                        => false
      } => try {
        JsSuccess(BSONBinary(Converters.str2Hex((obj \ f"$$binary").as[String]), Subtype.UserDefinedSubtype))
      } catch {
        case e: Throwable => JsError(s"error deserializing hex ${e.getMessage}")
      }
    }
    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      case binary: BSONBinary =>
        val remaining = binary.value.readable()
        Json.obj(
          f"$$binary" -> Converters.hex2Str(binary.value.slice(remaining).readArray(remaining)),
          f"$$type" -> Converters.hex2Str(Array(binary.subtype.value.toByte))
        )
    }
  }

  implicit object BSONSymbolFormat extends PartialFormat[BSONSymbol] {
    val partialReads: PartialFunction[JsValue, JsResult[BSONSymbol]] = {
      case SymbolValue(value) => JsSuccess(BSONSymbol(value))
    }

    val partialWrites: PartialFunction[BSONValue, JsValue] = {
      case BSONSymbol(s) => Json.obj(f"$$symbol" -> s)
    }

    private object SymbolValue {
      def unapply(obj: JsObject): Option[String] =
        if (obj.fields.size != 1) None else (obj \ f"$$symbol").asOpt[String]
    }
  }

  val numberReads: PartialFunction[JsValue, JsResult[BSONValue]] = {
    case JsNumber(n) if !n.ulp.isWhole => JsSuccess(BSONDouble(n.toDouble))
    case JsNumber(n) if n.isValidInt   => JsSuccess(BSONInteger(n.toInt))
    case JsNumber(n)                   => JsSuccess(BSONLong(n.toLong))
  }

  def toBSON(json: JsValue): JsResult[BSONValue] =
    BSONStringFormat.partialReads.
      orElse(BSONObjectIDFormat.partialReads).
      orElse(BSONJavaScriptFormat.partialReads).
      orElse(BSONDateTimeFormat.partialReads).
      orElse(BSONTimestampFormat.partialReads).
      orElse(BSONBinaryFormat.partialReads).
      orElse(BSONRegexFormat.partialReads).
      orElse(numberReads).
      orElse(BSONBooleanFormat.partialReads).
      orElse(BSONMinKeyFormat.partialReads).
      orElse(BSONMaxKeyFormat.partialReads).
      orElse(BSONNullFormat.partialReads).
      orElse(BSONSymbolFormat.partialReads).
      orElse(BSONArrayFormat.partialReads).
      orElse(BSONDocumentFormat.partialReads).
      orElse(BSONUndefinedFormat.partialReads).
      lift(json).getOrElse(JsError(s"unhandled json value: $json"))

  def toJSON(bson: BSONValue): JsValue = BSONObjectIDFormat.partialWrites.
    orElse(BSONJavaScriptFormat.partialWrites).
    orElse(BSONDateTimeFormat.partialWrites).
    orElse(BSONTimestampFormat.partialWrites).
    orElse(BSONBinaryFormat.partialWrites).
    orElse(BSONRegexFormat.partialWrites).
    orElse(BSONDoubleFormat.partialWrites).
    orElse(BSONIntegerFormat.partialWrites).
    orElse(BSONLongFormat.partialWrites).
    orElse(BSONBooleanFormat.partialWrites).
    orElse(BSONMinKeyFormat.partialWrites).
    orElse(BSONMaxKeyFormat.partialWrites).
    orElse(BSONNullFormat.partialWrites).
    orElse(BSONStringFormat.partialWrites).
    orElse(BSONSymbolFormat.partialWrites).
    orElse(BSONArrayFormat.partialWrites).
    orElse(BSONDocumentFormat.partialWrites).
    orElse(BSONUndefinedFormat.partialWrites).
    lift(bson).getOrElse(throw new JSONException(s"Unhandled json value: $bson"))
}

object Writers {
  implicit class JsPathMongo(val jp: JsPath) extends AnyVal {
    def writemongo[A](implicit writer: Writes[A]): OWrites[A] = {
      OWrites[A] { (o: A) =>
        val newPath = jp.path.flatMap {
          case e: KeyPathNode     => Some(e.key)
          case e: RecursiveSearch => Some(s"$$.${e.key}")
          case e: IdxPathNode     => Some(s"${e.idx}")
        }.mkString(".")

        val orig = writer.writes(o)
        orig match {
          case JsObject(e) =>
            JsObject(e.flatMap {
              case (k, v) => Seq(s"${newPath}.$k" -> v)
            })
          case e: JsValue => JsObject(Seq(newPath -> e))
        }
      }
    }
  }
}

object JSONSerializationPack extends reactivemongo.api.SerializationPack {
  import reactivemongo.bson.buffer.{ ReadableBuffer, WritableBuffer }

  type Value = JsValue
  type ElementProducer = (String, Json.JsValueWrapper)
  type Document = JsObject
  type Writer[A] = OWrites[A]
  type Reader[A] = Reads[A]
  type NarrowValueReader[A] = Reads[A]
  private[reactivemongo]type WidenValueReader[A] = Reads[A]

  object IdentityReader extends Reader[Document] {
    def reads(js: JsValue): JsResult[Document] = js match {
      case o: JsObject => JsSuccess(o)
      case v           => JsError(s"object is expected: $v")
    }
  }

  object IdentityWriter extends Writer[Document] {
    def writes(document: Document): Document = document
  }

  def serialize[A](a: A, writer: Writer[A]): Document = writer.writes(a)

  def deserialize[A](document: Document, reader: Reader[A]): A =
    reader.reads(document) match {
      case JsError(msg)    => sys.error(msg mkString ", ")
      case JsSuccess(v, _) => v
    }

  def writeToBuffer(buffer: WritableBuffer, document: Document): WritableBuffer = BSONFormats.toBSON(document) match {
    case err @ JsError(_) => sys.error(s"fails to write the document: $document: ${Json stringify JsError.toJson(err)}")

    case JsSuccess(d @ BSONDocument(_), _) => {
      BSONDocument.write(d, buffer)
      buffer
    }

    case JsSuccess(v, _) => sys.error(s"fails to write the document: $document; unexpected conversion $v")
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
      case err @ JsError(_) => Failure(new scala.RuntimeException(s"fails to reads the value: ${Json stringify value}; ${Json stringify JsError.toJson(err)}"))

      case JsSuccess(v, _)  => Success(v)
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
  import reactivemongo.bson.{ BSONElement, Producer }

  implicit def jsWriter[A <: JsValue, B <: BSONValue] = new BSONWriter[A, B] {
    def write(js: A): B = BSONFormats.toBSON(js).get.asInstanceOf[B]
  }

  implicit def JsFieldBSONElementProducer[T <: JsValue](jsField: (String, T)): Producer[BSONElement] = Producer.element2Producer(BSONElement(jsField._1, BSONFormats.toBSON(jsField._2).get))

  implicit object BSONValueReads extends Reads[BSONValue] {
    def reads(js: JsValue) = BSONFormats.toBSON(js)
  }

  implicit object BSONValueWrites extends Writes[BSONValue] {
    def writes(bson: BSONValue) = BSONFormats.toJSON(bson)
  }
}
