package reactivemongo.play.json
package compat

import scala.language.implicitConversions

import scala.util.{ Failure, Success }

import _root_.play.api.libs.json.{
  Format,
  JsError,
  JsSuccess,
  JsResultException,
  Json,
  OFormat,
  OWrites,
  Reads,
  Writes
}

import reactivemongo.api.bson.{
  BSONDocumentHandler,
  BSONDocumentReader,
  BSONDocumentWriter,
  BSONHandler,
  BSONReader,
  BSONWriter,
  SafeBSONWriter,
  exceptions
}

private[compat] object HandlerConverters {
  val logger = org.slf4j.LoggerFactory.getLogger(
    "reactivemongo.play.json.HandlerConverters")

}

private[compat] trait Json2BsonConverters
  extends LowPriority1Json2BsonConverters {

  /**
   * Implicit conversion from Play JSON `OFormat` to the BSON API.
   *
   * {{{
   * import reactivemongo.play.json.compat.
   *   json2bson.toDocumentHandlerConv
   *
   * def foo[T](jh: play.api.libs.json.OFormat[T]) = {
   *   val h: reactivemongo.api.bson.BSONDocumentHandler[T] = jh
   *   h
   * }
   * }}}
   */
  implicit final def toDocumentHandlerConv[T](h: OFormat[T])(implicit conv: FromValue): BSONDocumentHandler[T] = BSONDocumentHandler.provided[T](toDocumentReaderConv(h), toDocumentWriterConv(h))

  implicit final def toHandler[T](h: Format[T])(
    implicit
    from: FromValue, to: ToValue): BSONHandler[T] =
    BSONHandler.provided[T](toReaderConv(h), toWriterConv(h))

}

private[compat] trait Bson2JsonConverters
  extends LowPriority1Bson2JsonConverters {

  /**
   * Implicit conversion from new `BSONDocumentHandler` to Play JSON.
   *
   * {{{
   * import reactivemongo.play.json.compat.
   *   bson2json.fromDocumentHandlerConv
   *
   * def bar[T](bh: reactivemongo.api.bson.BSONDocumentHandler[T]) = {
   *   val h: play.api.libs.json.OFormat[T] = bh
   *   h
   * }
   * }}}
   */
  implicit final def fromDocumentHandlerConv[T](h: BSONDocumentHandler[T])(implicit from: FromValue, to: ToValue): OFormat[T] = OFormat[T](fromReaderConv(h), fromDocumentWriterConv(h))

  implicit final def fromHandler[T](h: BSONHandler[T])(
    implicit
    from: FromValue, to: ToValue): Format[T] =
    Format[T](fromReaderConv(h), fromWriterConv(h))

}

private[compat] sealed trait LowPriority1Json2BsonConverters
  extends LowPriority2Json2BsonConverters { _: Json2BsonConverters =>

  /**
   * Provided there is a Play JSON `OWrites`, resolve a document writer.
   *
   * {{{
   * import play.api.libs.json.OWrites
   * import reactivemongo.api.bson.BSONDocumentWriter
   * import reactivemongo.play.json.compat.json2bson.toDocumentWriter
   *
   * def foo[T : OWrites]: BSONDocumentWriter[T] =
   *   implicitly[BSONDocumentWriter[T]]
   * }}}
   *
   * @see [[toDocumentWriterConv]]
   */
  implicit final def toDocumentWriter[T](implicit w: OWrites[T], conv: ToValue): BSONDocumentWriter[T] = toDocumentWriterConv[T](w)

  /**
   * {{{
   * import reactivemongo.play.json.compat.
   *   json2bson.toDocumentWriterConv
   *
   * def foo[T](jw: play.api.libs.json.OWrites[T]) = {
   *   val w: reactivemongo.api.bson.BSONDocumentWriter[T] = jw
   *   w
   * }
   * }}}
   */
  implicit final def toDocumentWriterConv[T](w: OWrites[T])(implicit conv: ToValue): BSONDocumentWriter[T] = BSONDocumentWriter[T] { t => conv.toDocument(w writes t) }
}

private[compat] sealed trait LowPriority2Json2BsonConverters
  extends LowPriority3Json2BsonConverters {
  _: LowPriority1Json2BsonConverters =>

  /**
   * Converts a Play JSON `Reads` to a BSON reader.
   *
   * {{{
   * import play.api.libs.json.Reads
   * import reactivemongo.api.bson.BSONReader
   * import reactivemongo.play.json.compat.json2bson.toReaderConv
   *
   * def foo[T](implicit r: Reads[T]): BSONReader[T] = r
   * }}}
   *
   * @see [[toDocumentWriterConv]]
   */
  implicit final def toReaderConv[T](r: Reads[T])(
    implicit
    conv: FromValue): BSONReader[T] = BSONReader.from[T] { bson =>
    val js = conv fromValue bson

    r.reads(js) match {
      case JsSuccess(result, _) => Success(result)

      case _ => Failure(exceptions.TypeDoesNotMatchException(
        js.getClass.getSimpleName,
        bson.getClass.getSimpleName))
    }
  }

  /**
   * Provided there is a Play JSON `Reads`, resolve a BSON reader.
   *
   * {{{
   * import play.api.libs.json.Reads
   * import reactivemongo.api.bson.BSONReader
   * import reactivemongo.play.json.compat.json2bson.toReader
   *
   * def foo[T: Reads]: BSONReader[T] = implicitly[BSONReader[T]]
   * }}}
   *
   * @see [[toDocumentWriterConv]]
   */
  implicit final def toReader[T](implicit r: Reads[T], conv: FromValue): BSONReader[T] = toReaderConv(r)

}

private[compat] sealed trait LowPriority1Bson2JsonConverters
  extends LowPriority2Bson2JsonConverters { _: Bson2JsonConverters =>

  /**
   * Resolves a `OWrites` provided a BSON document writer is found.
   *
   * {{{
   * import play.api.libs.json.OWrites
   * import reactivemongo.play.json.compat.bson2json
   *
   * def bar[T: reactivemongo.api.bson.BSONDocumentWriter]: OWrites[T] =
   *   bson2json.fromDocumentWriter[T]
   * }}}
   */
  implicit final def fromDocumentWriter[T](implicit w: BSONDocumentWriter[T], conv: FromValue): OWrites[T] = fromDocumentWriterConv(w)

  /**
   * {{{
   * import reactivemongo.play.json.compat.bson2json.fromDocumentWriterConv
   *
   * def bar[T](lw: reactivemongo.api.bson.BSONDocumentWriter[T]) = {
   *   val w: play.api.libs.json.OWrites[T] = lw
   *   w
   * }
   * }}}
   */
  implicit final def fromDocumentWriterConv[T](
    w: BSONDocumentWriter[T])(implicit conv: FromValue): OWrites[T] =
    OWrites[T] { t =>
      w.writeTry(t) match {
        case Success(d) => conv.fromDocument(d)
        case Failure(e) => throw e
      }
    }

}

private[compat] sealed trait LowPriority3Json2BsonConverters {
  _: LowPriority2Json2BsonConverters =>

  implicit final def toWriterConv[T](w: Writes[T])(implicit conv: ToValue): BSONWriter[T] = BSONWriter[T] { t => conv.toValue(w writes t) }

  /**
   *
   * Raises a `JsError` is the JSON value is not a `JsObject`.
   *
   * {{{
   * import reactivemongo.play.json.compat.json2bson.toDocumentReaderConv
   *
   * def lorem[T](jr: play.api.libs.json.Reads[T]) =
   *   toDocumentReaderConv(jr)
   * }}}
   */
  implicit final def toDocumentReaderConv[T](
    r: Reads[T])(implicit conv: FromValue): BSONDocumentReader[T] =
    BSONDocumentReader.from[T] { bson =>
      r.reads(conv fromDocument bson) match {
        case JsSuccess(result, _) => Success(result)

        case JsError(details) =>
          Failure(JsResultException(details))
      }
    }

}

private[compat] sealed trait LowPriority2Bson2JsonConverters {
  _: LowPriority1Bson2JsonConverters =>

  implicit final def fromWriterConv[T](w: BSONWriter[T])(
    implicit
    conv: FromValue): Writes[T] =
    SafeBSONWriter.unapply(w) match {
      case Some(sw) => Writes { t =>
        conv.fromValue(sw safeWrite t)
      }

      case _ => Writes {
        w.writeTry(_) match {
          case Success(v) => conv.fromValue(v)
          case Failure(e) => throw e
        }
      }
    }

  /**
   * Provided there is a BSON writer, resolves a JSON one.
   *
   *
   * {{{
   * import play.api.libs.json.Writes
   * import reactivemongo.api.bson.BSONWriter
   * import reactivemongo.play.json.compat.bson2json.fromWriter
   *
   * def foo[T: BSONWriter] = implicitly[Writes[T]] // resolve fromWriter
   * }}}
   */
  implicit final def fromWriter[T](implicit w: BSONWriter[T], conv: FromValue): Writes[T] = fromWriterConv(w)

  implicit final def fromReaderConv[T](r: BSONReader[T])(
    implicit
    conv: ToValue): Reads[T] =
    Reads[T] { js =>
      r.readTry(conv toValue js) match {
        case Success(t) => JsSuccess(t)

        case Failure(e) => {
          HandlerConverters.logger.debug(
            s"Fails to read JSON value: ${Json stringify js}", e)

          JsError(e.getMessage)
        }
      }
    }

  /**
   * Provided there is a BSON reader, a JSON one is resolved.
   *
   * {{{
   * import play.api.libs.json.Reads
   * import reactivemongo.api.bson.BSONReader
   * import reactivemongo.play.json.compat.bson2json.fromReader
   *
   * def foo[T: BSONReader]: Reads[T] = implicitly[Reads[T]]
   * }}}
   */
  implicit final def fromReader[T](implicit r: BSONReader[T], conv: ToValue): Reads[T] = fromReaderConv(r)

}
