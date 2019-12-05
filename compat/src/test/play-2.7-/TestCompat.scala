package reactivemongo

import java.util.Locale

import java.time.{
  DateTimeException,
  ZoneId,
  Duration => JDuration
}
import java.time.format.DateTimeParseException
import java.time.temporal.{ ChronoUnit, TemporalUnit }

import scala.util.control

import _root_.play.api.libs.json._

object TestCompat {
  implicit object BigIntReads extends Reads[BigInt] {
    def reads(json: JsValue) = json match {
      case JsString(s) =>
        control.Exception.catching(classOf[NumberFormatException]).
          opt(JsSuccess(BigInt(new java.math.BigInteger(s)))).
          getOrElse(JsError("error.expected.numberformatexception"))

      case JsNumber(d) => d.toBigIntExact match {
        case Some(i) => JsSuccess(i)
        case _ => JsError("error.invalid.biginteger")
      }

      case _ =>
        JsError("error.expected.jsnumberorjsstring")
    }
  }

  implicit object BigIntWrites extends Writes[BigInt] {
    def writes(i: BigInt) = JsNumber(BigDecimal(i))
  }

  implicit object BigIntegerReads extends Reads[java.math.BigInteger] {
    def reads(json: JsValue) = json match {
      case JsString(s) =>
        control.Exception.catching(classOf[NumberFormatException]).
          opt(JsSuccess(new java.math.BigInteger(s))).
          getOrElse(JsError("error.expected.numberformatexception"))

      case JsNumber(d) => d.toBigIntExact match {
        case Some(i) => JsSuccess(i.underlying)
        case _ => JsError("error.invalid.biginteger")
      }

      case _ =>
        JsError("error.expected.jsnumberorjsstring")
    }
  }

  implicit object BigIntegerWrites extends Writes[java.math.BigInteger] {
    def writes(i: java.math.BigInteger) = JsNumber(BigDecimal(i))
  }

  implicit val localeReads: Reads[Locale] =
    Reads[Locale] { _.validate[String].map(Locale.forLanguageTag(_)) }

  implicit val localeWrites: Writes[Locale] =
    Writes[Locale] { l => JsString(l.toLanguageTag) }

  implicit val zoneIdReads: Reads[ZoneId] = Reads[ZoneId] {
    case JsString(s) => try {
      JsSuccess(ZoneId.of(s))
    } catch {
      case _: DateTimeException => JsError("error.expected.timezone")
    }

    case _ => JsError("error.expected.jsstring")
  }

  implicit val zoneIdWrites: Writes[ZoneId] =
    Writes[ZoneId](zone => JsString(zone.getId))

  val javaDurationMillisReads: Reads[JDuration] =
    javaDurationNumberReads(ChronoUnit.MILLIS)

  implicit val durationReads: Reads[JDuration] = Reads[JDuration] {
    case JsString(repr) => try {
      JsSuccess(JDuration.parse(repr))
    } catch {
      case _: DateTimeParseException => JsError("error.invalid.duration")
    }

    case js => javaDurationMillisReads.reads(js)
  }

  implicit val javaDurationMillisWrites: Writes[JDuration] =
    Writes[JDuration] { d => JsNumber(d.toMillis) }

  def javaDurationNumberReads(unit: TemporalUnit): Reads[JDuration] =
    jdurationNumberReads(unit)

  private def jdurationNumberReads(unit: TemporalUnit) =
    Reads[JDuration] {
      case n: JsNumber => n.validate[Long].map(l => JDuration.of(l, unit))
      case _ => JsError("error.expected.longDuration")
    }

}
