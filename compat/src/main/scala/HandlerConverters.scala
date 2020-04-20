package reactivemongo.play.json
package compat

import scala.language.implicitConversions

import scala.util.{ Failure, Success }

import _root_.play.api.libs.json.{
  Format,
  JsError,
  JsObject,
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

/**
 * See [[compat$]] and [[HandlerConverters]]
 */
object HandlerConverters extends HandlerConverters {
  private[compat] val logger =
    org.slf4j.LoggerFactory.getLogger(classOf[HandlerConverters])

}

/**
 * Implicit conversions for handler types
 * between `play.api.libs.json` and `reactivemongo.api.bson` .
 *
 * {{{
 * import reactivemongo.play.json.compat.HandlerConverters._
 *
 * def foo[T](jw: play.api.libs.json.OWrites[T]) = {
 *   val w: reactivemongo.api.bson.BSONDocumentWriter[T] = jw
 *   w
 * }
 *
 * def bar[T](br: reactivemongo.api.bson.BSONReader[T]) = {
 *   val r: play.api.libs.json.Reads[T] = br
 *   r
 * }
 * }}}
 *
 * ''Note:'' Logger `reactivemongo.api.play.json.HandlerConverters` can be used to debug.
 */
trait HandlerConverters extends LowPriorityHandlerConverters1 {

  @deprecated("Will be removed when provided by Play-JSON itself", "0.20.6")
  implicit final val jsObjectWrites: OWrites[JsObject] =
    OWrites[JsObject](identity)

  /**
   * Implicit conversion from Play JSON `OFormat` to the BSON API.
   *
   * {{{
   * import reactivemongo.play.json.compat.HandlerConverters.toDocumentHandler
   *
   * def foo[T](jh: play.api.libs.json.OFormat[T]) = {
   *   val h: reactivemongo.api.bson.BSONDocumentHandler[T] = jh
   *   h
   * }
   * }}}
   */
  implicit final def toDocumentHandler[T](h: OFormat[T]): BSONDocumentHandler[T] = BSONDocumentHandler.provided[T](toDocumentReaderConv(h), toDocumentWriterConv(h))

  /**
   * Implicit conversion from new `BSONDocumentHandler` to Play JSON.
   *
   * {{{
   * import reactivemongo.play.json.compat.HandlerConverters.fromDocumentHandler
   *
   * def bar[T](bh: reactivemongo.api.bson.BSONDocumentHandler[T]) = {
   *   val h: play.api.libs.json.OFormat[T] = bh
   *   h
   * }
   * }}}
   */
  implicit final def fromDocumentHandler[T](h: BSONDocumentHandler[T]): OFormat[T] = OFormat[T](fromReaderConv(h), fromDocumentWriter(h))
}

private[json] sealed trait LowPriorityHandlerConverters1
  extends LowPriorityHandlerConverters2 { _: HandlerConverters =>

  implicit final def toHandler[T](h: Format[T]): BSONHandler[T] =
    BSONHandler.provided[T](toReaderConv(h), toWriter(h))

  implicit final def fromHandler[T](h: BSONHandler[T]): Format[T] =
    Format[T](fromReaderConv(h), fromWriterConv(h))

}

private[json] sealed trait LowPriorityHandlerConverters2
  extends LowPriorityHandlerConverters3 { _: LowPriorityHandlerConverters1 =>

  /**
   * Provided there is a Play JSON `OWrites`, resolve a document writer.
   *
   * {{{
   * import play.api.libs.json.OWrites
   * import reactivemongo.api.bson.BSONDocumentWriter
   * import reactivemongo.play.json.compat.toDocumentWriter
   *
   * def foo[T : OWrites]: BSONDocumentWriter[T] =
   *   implicitly[BSONDocumentWriter[T]]
   * }}}
   *
   * @see [[toDocumentWriterConv]]
   */
  implicit final def toDocumentWriter[T](implicit w: OWrites[T]): BSONDocumentWriter[T] = toDocumentWriterConv[T](w)

  /**
   * {{{
   * import reactivemongo.play.json.compat.HandlerConverters.toDocumentWriterConv
   *
   * def foo[T](jw: play.api.libs.json.OWrites[T]) = {
   *   val w: reactivemongo.api.bson.BSONDocumentWriter[T] = jw
   *   w
   * }
   * }}}
   */
  implicit final def toDocumentWriterConv[T](w: OWrites[T]): BSONDocumentWriter[T] = BSONDocumentWriter[T] { t => ValueConverters.toDocument(w writes t) }

  /**
   * Converts a Play JSON `Reads` to a BSON reader.
   *
   * {{{
   * import play.api.libs.json.Reads
   * import reactivemongo.api.bson.BSONReader
   * import reactivemongo.play.json.compat.toReaderConv
   *
   * def foo[T](implicit r: Reads[T]): BSONReader[T] = r
   * }}}
   *
   * @see [[toDocumentWriterConv]]
   */
  implicit final def toReaderConv[T](r: Reads[T]): BSONReader[T] =
    BSONReader.from[T] { bson =>
      val js = ValueConverters fromValue bson

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
   * import reactivemongo.play.json.compat.toReader
   *
   * def foo[T: Reads]: BSONReader[T] = implicitly[BSONReader[T]]
   * }}}
   *
   * @see [[toDocumentWriterConv]]
   */
  implicit final def toReader[T](implicit r: Reads[T]): BSONReader[T] =
    toReaderConv(r)

  /**
   * Resolves a `OWrites` provided a BSON document writer is found.
   *
   * {{{
   * import play.api.libs.json.OWrites
   * import reactivemongo.play.json.compat.HandlerConverters
   *
   * def bar[T: reactivemongo.api.bson.BSONDocumentWriter]: OWrites[T] =
   *   HandlerConverters.fromDocumentWriter[T]
   * }}}
   */
  implicit final def fromDocumentWriter[T](implicit w: BSONDocumentWriter[T]): OWrites[T] = fromDocumentWriterConv(w)

  /**
   * {{{
   * import reactivemongo.play.json.compat.fromDocumentWriterConv
   *
   * def bar[T](lw: reactivemongo.api.bson.BSONDocumentWriter[T]) = {
   *   val w: play.api.libs.json.OWrites[T] = lw
   *   w
   * }
   * }}}
   */
  implicit final def fromDocumentWriterConv[T](w: BSONDocumentWriter[T]): OWrites[T] = OWrites[T] { t =>
    w.writeTry(t) match {
      case Success(d) => ValueConverters.fromDocument(d)
      case Failure(e) => throw e
    }
  }
}

private[json] sealed trait LowPriorityHandlerConverters3 {
  _: LowPriorityHandlerConverters2 =>

  import HandlerConverters.logger

  implicit final def toWriter[T](w: Writes[T]): BSONWriter[T] = BSONWriter[T] { t => ValueConverters.toValue(w writes t) }

  /**
   *
   * Raises a `JsError` is the JSON value is not a `JsObject`.
   *
   * {{{
   * import reactivemongo.play.json.compat.toDocumentReaderConv
   *
   * def lorem[T](jr: play.api.libs.json.Reads[T]) =
   *   toDocumentReaderConv(jr)
   * }}}
   */
  implicit final def toDocumentReaderConv[T](
    r: Reads[T]): BSONDocumentReader[T] = BSONDocumentReader.from[T] { bson =>
    r.reads(ValueConverters fromDocument bson) match {
      case JsSuccess(result, _) => Success(result)

      case JsError(details) =>
        Failure(JsResultException(details))
    }
  }

  implicit final def fromWriterConv[T](w: BSONWriter[T]): Writes[T] =
    SafeBSONWriter.unapply(w) match {
      case Some(sw) => Writes { t =>
        ValueConverters.fromValue(sw safeWrite t)
      }

      case _ => Writes {
        w.writeTry(_) match {
          case Success(v) => ValueConverters.fromValue(v)
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
   * import reactivemongo.play.json.compat.fromWriter
   *
   * def foo[T: BSONWriter] = implicitly[Writes[T]] // resolve fromWriter
   * }}}
   */
  implicit final def fromWriter[T](implicit w: BSONWriter[T]): Writes[T] =
    fromWriterConv(w)

  implicit final def fromReaderConv[T](r: BSONReader[T]): Reads[T] =
    Reads[T] { js =>
      r.readTry(ValueConverters toValue js) match {
        case Success(t) => JsSuccess(t)

        case Failure(e) => {
          logger.debug(s"Fails to read JSON value: ${Json stringify js}", e)

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
   * import reactivemongo.play.json.compat.fromReader
   *
   * def foo[T: BSONReader]: Reads[T] = implicitly[Reads[T]]
   * }}}
   */
  implicit final def fromReader[T](implicit r: BSONReader[T]): Reads[T] =
    fromReaderConv(r)

}
