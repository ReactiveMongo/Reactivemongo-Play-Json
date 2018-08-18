package reactivemongo.play.json

import java.util.UUID

import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal

import play.api.libs.json.{
  JsArray,
  JsBoolean,
  JsError,
  Json,
  JsNumber,
  JsNull,
  JsObject,
  JsResult,
  JsResultException,
  JsString,
  JsSuccess,
  JsValue,
  OWrites,
  Reads
}

import reactivemongo.api.SerializationPack

import reactivemongo.bson.utils.Converters
import reactivemongo.bson.{ BSONDocument, Subtype }
import reactivemongo.bson.buffer.{ ReadableBuffer, WritableBuffer }

import reactivemongo.play.json.commands.JSONCommandError

object JSONSerializationPack extends SerializationPack { self =>
  type Value = JsValue
  type ElementProducer = (String, Json.JsValueWrapper)
  type Document = JsObject
  type Writer[A] = OWrites[A]
  type Reader[A] = Reads[A]
  type NarrowValueReader[A] = Reads[A]
  private[reactivemongo] type WidenValueReader[A] = Reads[A]

  private[reactivemongo] val IsDocument =
    scala.reflect.ClassTag[Document](classOf[JsObject])

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

  override private[reactivemongo] def bsonSize(value: JsValue): Int = {
    @inline def str(s: String) = 5 + s.getBytes.size

    value match {
      case JsNumber(n) if !n.ulp.isWhole => 8 // Double
      case JsNumber(n) if n.isValidInt   => 4 // Int
      case JsNumber(_)                   => 8 // Long
      case JsString(s)                   => str(s)
      case JsBoolean(_)                  => 1
      case JsNull                        => 0

      case JsArray(values) => bsonSize(values.zipWithIndex.map {
        case (v, idx) => idx.toString -> v
      })

      case obj @ JsObject(fields) => obj match {
        case BSONUndefinedFormat.Undefined(_) |
          BSONMaxKeyFormat.MaxKey(_) |
          BSONMinKeyFormat.MinKey(_) => 0

        case BSONSymbolFormat.Symbol(s)         => str(s)
        case BSONJavaScriptFormat.JavaScript(s) => str(s)
        case BSONObjectIDFormat.ObjectID(s)     => str(s)

        case BSONRegexFormat.Regex(r, o) =>
          2 + r.getBytes.size + o.fold(0)(_.getBytes.size)

        case BSONDateTimeFormat.Date(_) | BSONTimestampFormat.Time(_, _) =>
          8 // Long

        case BSONBinaryFormat.Binary(hexa, _) =>
          5 /* header = 4 (value.readable:Int) + 1 (subtype.value.toByte) */ + (
            hexa.getBytes.size / 2 /* max estimated size */ )

        case _ => bsonSize(fields)
      }

      case _ => -1 // unsupported/fallback
    }
  }

  private def bsonSize(elements: Iterable[(String, JsValue)]): Int =
    elements.foldLeft(5) {
      case (sz, (n, v)) => sz + 2 + n.getBytes.size + bsonSize(v)
    }

  override private[reactivemongo] def newBuilder: SerializationPack.Builder[JSONSerializationPack.type] = Builder

  override private[reactivemongo] def newDecoder: SerializationPack.Decoder[JSONSerializationPack.type] = Decoder

  private[reactivemongo] def document(doc: reactivemongo.bson.BSONDocument): JsObject = BSONFormats.toJSON(doc) match {
    case obj @ JsObject(_) => obj
    case _                 => throw new JSONException("error.expected.jsobject")
  }

  private[reactivemongo] def bsonValue(json: JsValue): reactivemongo.bson.BSONValue = BSONFormats.toBSON(json).get

  private[reactivemongo] def reader[A](f: JsObject => A): Reads[A] = Reads[A] {
    case obj @ JsObject(_) => try {
      JsSuccess(f(obj))
    } catch {
      case NonFatal(cause) => JsError(cause.getMessage)
    }

    case _ => JsError("error.expected.jsobject")
  }

  // ---

  /** A builder for serialization simple values (useful for the commands) */
  private object Builder
    extends SerializationPack.Builder[JSONSerializationPack.type] {
    protected[reactivemongo] val pack = self

    def document(elements: Seq[ElementProducer]): Document =
      Json.obj(elements: _*)

    def array(value: Value, values: Seq[Value]): Value =
      JsArray(value +: values)

    // (String, Json.JsValueWrapper)
    def elementProducer(name: String, value: Value): ElementProducer =
      name -> implicitly[Json.JsValueWrapper](value)

    def boolean(b: Boolean): Value = JsBoolean(b)

    def int(i: Int): Value = JsNumber(BigDecimal(i))

    def long(l: Long): Value = JsNumber(BigDecimal(l))

    def double(d: Double): Value = JsNumber(BigDecimal(d.toString))

    def string(s: String): Value = JsString(s)

    def timestamp(value: Long): Value = {
      val time = value >>> 32
      val ordinal = value.toInt

      Json.obj(
        f"$$time" -> time,
        f"$$i" -> ordinal,
        f"$$timestamp" -> Json.obj(
          "t" -> time,
          "i" -> ordinal
        ))
    }

    def uuid(id: UUID): Value = {
      val buf = java.nio.ByteBuffer.wrap(Array.ofDim[Byte](16))

      buf putLong id.getMostSignificantBits
      buf putLong id.getLeastSignificantBits

      Json.obj(
        f"$$binary" -> Converters.hex2Str(buf.array),
        f"$$type" -> Converters.hex2Str(Array(
          Subtype.UuidSubtype.value.toByte))
      )
    }
  }

  private object Decoder
    extends SerializationPack.Decoder[JSONSerializationPack.type] {
    protected[reactivemongo] val pack = self

    def asDocument(value: JsValue): Option[JsObject] = value.asOpt[JsObject]

    def names(document: JsObject): Set[String] = document.keys.toSet

    def array(document: JsObject, name: String): Option[Seq[JsValue]] =
      (document \ name).asOpt[JsArray].map(_.value)

    def get(document: JsObject, name: String): Option[JsValue] =
      document.value.get(name)

    def booleanLike(document: JsObject, name: String): Option[Boolean] =
      document.value.get(name).collect {
        case JsBoolean(b) => b
        case JsNumber(n)  => n.intValue > 0
      }

    def child(document: JsObject, name: String): Option[JsObject] =
      (document \ name).asOpt[JsObject]

    def children(document: JsObject, name: String): List[JsObject] = {
      (document \ name).asOpt[List[JsObject]].
        getOrElse(List.empty[JsObject])
    }

    def double(document: JsObject, name: String): Option[Double] =
      (document \ name).asOpt[Double]

    def int(document: JsObject, name: String): Option[Int] =
      (document \ name).asOpt[Int]

    def long(document: JsObject, name: String): Option[Long] =
      (document \ name).asOpt[Long]

    def string(document: JsObject, name: String): Option[String] =
      (document \ name).asOpt[String]

    def uuid(document: JsObject, name: String): Option[UUID] =
      for {
        doc <- (document \ name).asOpt[JsObject]
        _ <- (doc \ f"$$type").asOpt[String].flatMap { str =>
          Converters.str2Hex(str).headOption.filter { code =>
            code == Subtype.OldUuidSubtype.value ||
              code == Subtype.UuidSubtype.value
          }
        }
        bytes <- (doc \ f"$$binary").asOpt[String].map(Converters.str2Hex)
      } yield UUID.nameUUIDFromBytes(bytes)
  }
}
