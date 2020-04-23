import reactivemongo.api.bson._

import org.specs2.execute._, Typecheck._
import org.specs2.matcher.TypecheckMatchers._

final class LaxHandlerSpec extends org.specs2.mutable.Specification {
  "Lax handler" title

  import LaxHandlerFixtures._

  val user = User(
    _id = BSONObjectID.generate(),
    username = "lorem",
    role = "ipsum",
    created = BSONTimestamp(987654321L),
    lastModified = BSONDateTime(123456789L),
    sym = Some(BSONSymbol("foo")))

  "BSON handler" should {
    val repr = BSONDocument(
      "_id" -> user._id,
      "username" -> "lorem",
      "role" -> "ipsum",
      "created" -> BSONTimestamp(987654321L),
      "lastModified" -> BSONDateTime(123456789L),
      "sym" -> BSONSymbol("foo"))

    "write as expected representation" in {
      BSON.writeDocument(user) must beSuccessfulTry(repr)
    }

    "read user from representation" in {
      BSON.readDocument[User](repr) must beSuccessfulTry(user)
    }
  }

  "Converted JSON handler" should {
    import _root_.play.api.libs.json._

    "not be resolved by default" in {
      typecheck("Json.toJson(user)") must failWith(
        "No Json serializer found for type LaxHandlerFixtures\\.User")
    }

    {
      import reactivemongo.play.json.compat._

      "write using JSON extended syntax" in {
        def userJs: JsValue = {
          import bson2json._

          Json.toJson(user)
        }

        userJs must_=== JsObject(Map[String, JsValue](
          "_id" -> Json.obj(f"$$oid" -> user._id.stringify),
          "role" -> JsString("ipsum"),
          "username" -> JsString("lorem"),
          "lastModified" -> JsObject(Map[String, JsValue](
            f"$$date" -> Json.obj(
              f"$$numberLong" -> JsString("123456789")))),
          "created" -> JsObject(Map[String, JsValue](
            f"$$timestamp" -> Json.obj(
              "t" -> JsNumber(0),
              "i" -> JsNumber(987654321)))),
          "sym" -> Json.obj(f"$$symbol" -> JsString("foo"))))
      }

      val userLaxJs: JsValue = {
        import bson2json._
        import lax._ // <---

        Json.toJson(user) // via fromDocumentWriter
      }

      "write using lax syntax" in {
        userLaxJs must_=== JsObject(Map[String, JsValue](
          "role" -> JsString("ipsum"),
          "username" -> JsString("lorem"),
          "lastModified" -> JsNumber(123456789),
          "_id" -> JsString(user._id.stringify),
          "sym" -> JsString("foo"),
          "created" -> JsNumber(987654321)))
      }

      "fail to validate" in {
        typecheck("userLaxJs.validate[User]") must failWith(
          "No Json deserializer found for type LaxHandlerFixtures\\.User")
      }

      "validate with bson2json import" in {
        import bson2json._
        import lax._

        userLaxJs.validate[User] must_=== JsSuccess(user)
      }
    }
  }
}

object LaxHandlerFixtures {
  case class User(
    _id: BSONObjectID,
    username: String,
    role: String,
    created: BSONTimestamp,
    lastModified: BSONDateTime,
    sym: Option[BSONSymbol])

  object User {
    implicit val bsonWriter: BSONDocumentWriter[User] = Macros.writer[User]

    implicit val bsonReader: BSONDocumentReader[User] = {
      import reactivemongo.play.json.compat.lax._

      Macros.reader[User]
    }
  }
}
