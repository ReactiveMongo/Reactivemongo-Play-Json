package reactivemongo

import _root_.play.api.libs.json.Json

import reactivemongo.api.bson.{ BSONDocumentReader, Macros }

import com.github.ghik.silencer.silent

import org.specs2.execute._, Typecheck._
import org.specs2.matcher.TypecheckMatchers._

final class HandlerConverterSpec211 extends org.specs2.mutable.Specification {
  "Handler converters (Scala 2.11)" title

  import reactivemongo.play.json.compat._

  "bson2json & json2bson converters" should {
    "not be imported in a same scope" in {
      import HandlerFixtures.FooDateTime

      import bson2json._
      import json2bson._

      // Check implicits integration
      @silent implicit def br: BSONDocumentReader[FooDateTime] = {
        import lax.bsonDateTimeReader

        Macros.reader[FooDateTime]
      }

      @silent def fooJs = Json.obj("bar" -> "lorem", "v" -> 123456789L)

      typecheck("fooJs.validate[FooDateTime]") must failWith(
        "diverging implicit expansion for type reactivemongo\\.api\\.bson\\.BSONReader\\[reactivemongo\\.HandlerFixtures\\.FooDateTime\\].*starting with method toReader in trait LowPriority2Json2BsonConverters")
    }
  }
}
