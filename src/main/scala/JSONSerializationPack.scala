package reactivemongo.play.json

import scala.util.{ Failure, Success, Try }

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
import reactivemongo.bson.BSONDocument
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
      case (sz, (n, v)) =>
        sz + 2 + n.getBytes.size + bsonSize(v)
    }

  override private[reactivemongo] def newBuilder: SerializationPack.Builder[JSONSerializationPack.type] = Builder

  // ---

  /** A builder for serialization simple values (useful for the commands) */
  private object Builder
    extends SerializationPack.Builder[JSONSerializationPack.type] {
    protected val pack = self

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
  }
}
