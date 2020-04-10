package reactivemongo

import _root_.play.api.libs.json._
import reactivemongo.api.bson.{
  BSON,
  BSONReader,
  BSONDocumentWriter,
  BSONValue
}

final class ValueConverterSpec extends org.specs2.mutable.Specification {
  "Value converters" title

  "Case class" should {
    "be properly serialized using default Play JSON handlers" >> {
      stopOnFail

      "for Lorem" in valueConversionSpec(PlayFixtures.lorem)

      "for Bar" in valueConversionSpec(PlayFixtures.bar)

      "for Foo" in valueConversionSpec(PlayFixtures.foo)
    }
  }

  // ---

  private def valueConversionSpec[T: Reads: OWrites](value: T) = {
    import _root_.reactivemongo.play.json.compat.ValueConverters._

    val jsIn: JsValue = Json.toJson(value)
    val bsonIn: BSONValue = jsIn
    val jsOut: JsValue = bsonIn

    jsIn aka BSONValue.pretty(bsonIn) must_=== jsOut and {
      val o = Json.obj("bson" -> bsonIn) // Json.JsValueWrapper conversion

      (o \ "bson").get must_=== jsOut
    } and {
      jsOut.validate[T] aka Json.stringify(jsOut) must beLike[JsResult[T]] {
        case JsSuccess(out, _) => out must_=== value
      }
    } and {
      import _root_.reactivemongo.play.json.compat.HandlerConverters._

      val bsonW: BSONDocumentWriter[T] = implicitly[OWrites[T]]
      val bsonR: BSONReader[T] = implicitly[Reads[T]]

      bsonW.writeTry(value) must beSuccessfulTry[BSONValue].like {
        case written => written must_=== bsonIn and {
          bsonR.readTry(written) must beSuccessfulTry(value)
        }
      } and {
        // Not implicit conversion, but implicit derived instances
        BSON.write(value) must beSuccessfulTry[BSONValue].like {
          case written => written must_=== bsonIn and {
            BSON.read(written) must beSuccessfulTry(value)
          }
        }
      }
    }
  }
}

@com.github.ghik.silencer.silent("Unused import" /* TestCompat play-2.6+ */ )
object PlayFixtures {
  import java.util.{ Date, Locale, UUID }
  import java.time.{
    Instant,
    LocalDate,
    LocalDateTime,
    OffsetDateTime,
    ZoneId,
    ZonedDateTime
  }
  import java.time.{ Duration => JDuration }

  import TestCompat._

  case class Foo(
    int: Int,
    short: Short,
    byte: Byte,
    str: String,
    locale: Locale,
    flag: Boolean,
    bar: Bar)

  object Foo {
    implicit val format: OFormat[Foo] = Json.format[Foo]
  }

  case class Bar(
    duration: JDuration,
    long: Long,
    float: Float,
    double: Double,
    bigDec: BigDecimal,
    bigInt: BigInt,
    jbigInt: java.math.BigInteger,
    lorem: Lorem)

  object Bar {
    implicit val writes: OWrites[Bar] = Json.writes[Bar]
    implicit val reads: Reads[Bar] = Json.reads
  }

  case class Lorem(
    id: UUID,
    date: Date,
    instant: Instant,
    localDate: LocalDate,
    localDateTime: LocalDateTime,
    offsetDateTime: OffsetDateTime,
    zid: ZoneId,
    zdt: ZonedDateTime)

  object Lorem {
    implicit val format: OFormat[Lorem] = Json.format
  }

  val lorem = Lorem(
    id = UUID.randomUUID(),
    date = new Date(),
    instant = Instant.now(),
    localDate = LocalDate.now(),
    localDateTime = LocalDateTime.now(),
    offsetDateTime = OffsetDateTime.now(),
    zid = ZoneId.systemDefault(),
    zdt = ZonedDateTime.now())

  val bar = Bar(
    duration = JDuration.ofDays(2),
    long = 2L,
    float = 3.45F,
    double = 67.891D,
    bigDec = BigDecimal("10234.56789"),
    bigInt = BigInt(Long.MaxValue),
    jbigInt = new java.math.BigInteger("9876"),
    lorem = lorem)

  val foo = Foo(
    int = 1,
    short = 2,
    byte = 3,
    str = "value",
    locale = Locale.FRANCE,
    flag = false,
    bar = bar)

}
