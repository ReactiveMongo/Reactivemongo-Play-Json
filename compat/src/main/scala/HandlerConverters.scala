package reactivemongo.play.json
package compat

import scala.language.implicitConversions

import scala.util.{ Failure, Success }

import _root_.play.api.libs.json.{
  Format,
  JsError,
  JsSuccess,
  JsObject,
  JsResultException,
  Json,
  OFormat,
  OWrites,
  Reads,
  Writes
}

import reactivemongo.api.bson.{
  BSONDocument,
  BSONDocumentHandler,
  BSONDocumentReader,
  BSONDocumentWriter,
  BSONHandler,
  BSONReader,
  BSONValue,
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
  implicit final def toDocumentHandler[T](h: OFormat[T]): BSONDocumentHandler[T] = BSONDocumentHandler.provided[T](toDocumentReader(h), toDocumentWriter(h))

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
  implicit final def fromDocumentHandler[T](h: BSONDocumentHandler[T]): OFormat[T] = OFormat[T](fromDocumentReader(h), fromDocumentWriter(h))

  /**
   * Based on the compatibility conversions,
   * provides instances of Play JSON `OWrites` for the new BSON value API.
   */
  implicit def jsonWriterNewDocument[L](implicit conv: L => BSONDocument): OWrites[L] = fromDocumentWriter(BSONDocumentWriter[L](conv))

  /**
   * Based on the compatibility conversions,
   * provides instances of Play JSON `Reads` for the new BSON value API.
   */
  implicit def jsonReaderNewValue[B <: BSONValue, L](implicit r: BSONDocumentReader[B], conv: B => L): Reads[L] = r.afterRead[L](conv)
}

private[json] sealed trait LowPriorityHandlerConverters1
  extends LowPriorityHandlerConverters2 { _: HandlerConverters =>

  implicit final def toHandler[T](h: Format[T]): BSONHandler[T] =
    BSONHandler.provided[T](toReader(h), toWriter(h))

  implicit final def fromHandler[T](h: BSONHandler[T]): Format[T] =
    Format[T](fromReader(h), fromWriter(h))

  /**
   * Based on the compatibility conversions,
   * provides instances of Play JSON `Writes` for the new BSON value API.
   */
  implicit def jsonWriterNewValue[B <: BSONValue, L](implicit conv: L => B): Writes[L] = fromWriter(BSONWriter[L](conv))

}

private[json] sealed trait LowPriorityHandlerConverters2
  extends LowPriorityHandlerConverters3 { _: LowPriorityHandlerConverters1 =>

  import HandlerConverters.logger

  /**
   * Provided there is a Play JSON `OWrites`, resolve a new one.
   */
  implicit final def toDocumentWriter[T](implicit w: OWrites[T]): BSONDocumentWriter[T] = toDocumentWriterConv[T](w)

  implicit final def toReader[T](r: Reads[T]): BSONReader[T] =
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
   * {{{
   * import reactivemongo.play.json.compat.HandlerConverters.fromDocumentWriter
   *
   * def bar[T](lw: reactivemongo.api.bson.BSONDocumentWriter[T]) = {
   *   val w: play.api.libs.json.OWrites[T] = lw
   *   w
   * }
   * }}}
   */
  implicit final def fromDocumentWriter[T](w: BSONDocumentWriter[T]): OWrites[T] = OWrites[T] { t =>
    w.writeTry(t) match {
      case Success(d) => ValueConverters.fromDocument(d)
      case Failure(e) => throw e
    }
  }

  /**
   * {{{
   * import reactivemongo.play.json.compat.HandlerConverters.fromDocumentReader
   *
   * def foo[T](r: reactivemongo.api.bson.BSONDocumentReader[T]) = {
   *   val jr: play.api.libs.json.Reads[T] = r
   *   jr
   * }
   * }}}
   */
  implicit final def fromDocumentReader[T](r: BSONDocumentReader[T]): Reads[T] =
    Reads[T] {
      case obj @ JsObject(_) =>
        r.readTry(ValueConverters toDocument obj) match {
          case Success(t) => JsSuccess(t)

          case Failure(e) => {
            logger.debug(s"Fails to read JSON object: ${Json stringify obj}", e)

            JsError(e.getMessage)
          }
        }

      case _ =>
        JsError("error.expected.jsobject")
    }
}

private[json] sealed trait LowPriorityHandlerConverters3 {
  _: LowPriorityHandlerConverters2 =>

  import HandlerConverters.logger

  implicit final def toWriter[T](w: Writes[T]): BSONWriter[T] = BSONWriter[T] { t => ValueConverters.toValue(w writes t) }

  /**
   * Provided there is a Play JSON `Reads`, resolve a new one.
   */
  implicit final def toDocumentReader[T](implicit r: Reads[T]): BSONDocumentReader[T] = toDocumentReaderConv[T](r)

  /**
   * {{{
   * import reactivemongo.play.json.compat.HandlerConverters.toDocumentReaderConv
   *
   * def lorem[T](jr: play.api.libs.json.Reads[T]) = {
   *   val w: reactivemongo.api.bson.BSONDocumentReader[T] = jr
   *   w
   * }
   * }}}
   */
  implicit final def toDocumentReaderConv[T](r: Reads[T]): BSONDocumentReader[T] = BSONDocumentReader.from[T] { bson =>
    r.reads(ValueConverters fromDocument bson) match {
      case JsSuccess(result, _) => Success(result)

      case JsError(details) =>
        Failure(JsResultException(details))
    }
  }

  implicit final def fromWriter[T](w: BSONWriter[T]): Writes[T] =
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

  implicit final def fromReader[T](r: BSONReader[T]): Reads[T] =
    Reads[T] { js =>
      r.readTry(ValueConverters toValue js) match {
        case Success(t) => JsSuccess(t)

        case Failure(e) => {
          logger.debug(s"Fails to read JSON value: ${Json stringify js}", e)

          JsError(e.getMessage)
        }
      }
    }
}
