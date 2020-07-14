import _root_.play.api.libs.json._

import _root_.reactivemongo.api.bson._

import org.specs2.execute._, Typecheck._
import org.specs2.matcher.TypecheckMatchers._

import reactivemongo.ExtendedJsonFixtures
import reactivemongo.play.json.TestCompat.JsonValidationError

final class HandlerUseCaseSpec extends org.specs2.mutable.Specification {
  "Handler use cases" title

  // Global compatibility import:
  import reactivemongo.play.json.compat._

  "User" should {
    import HandlerUseCaseSpec.User
    import ExtendedJsonFixtures.boid

    val user = User(
      boid, "lorem", "ipsum",
      created = BSONTimestamp(987654321L),
      lastModified = BSONDateTime(123456789L),
      sym = Some(BSONSymbol("foo")))

    "not be serializable on JSON without 'bson2json'" in {
      typecheck("Json.toJson(user)") must failWith("No\\ Json\\ serializer\\ found\\ for\\ type\\ HandlerUseCaseSpec\\.User.*")
    }

    "be represented in JSON using 'bson2json' conversions" >> {
      import bson2json._

      {
        val expected = s"""{
          "_id": {"$$oid":"${boid.stringify}"},
          "username": "lorem",
          "role": "ipsum",
          "created": {
            "$$timestamp": {"t":0,"i":987654321}
          },
          "lastModified": {
            "$$date": {"$$numberLong":"123456789"}
          },
          "sym": {
            "$$symbol":"foo"
          }
        }"""

        lazy val jsn = Json.parse(expected)

        s"with JSON extended syntax '${jsn}'" in {
          val userJs = Json.toJson(user)

          userJs must_=== jsn and {
            userJs.validate[User] must_=== JsSuccess(user)
          }
        }
      }

      {
        val expected = s"""{
          "_id": "${boid.stringify}",
          "username": "lorem",
          "role": "ipsum",
          "created": 987654321,
          "lastModified": 123456789,
          "sym": "foo"
        }"""

        lazy val jsn = Json.parse(expected)

        s"with lax syntax '${jsn}' provided by appropriate import" in {
          import lax._ // <-- required import for lax syntax

          val userLaxJs = Json.toJson(user) // via fromDocumentWriter

          userLaxJs must_=== jsn and {
            userLaxJs.validate[User] must beLike[JsResult[User]] {
              case JsError((JsPath, JsonValidationError(
                "Fails to handle _id: BSONString != BSONObjectID" ::
                  Nil) :: Nil) :: Nil) =>

                ok
            }
          } and {
            // Overrides BSONReaders for OID/Timestamp/DateTime
            // so that the BSON representation matches the JSON lax one
            implicit val bsonReader: BSONDocumentReader[User] =
              Macros.reader[User]

            userLaxJs.validate[User] must_=== JsSuccess(user)
          }
        }
      }
    }
  }
}

// Test fixtures
object HandlerUseCaseSpec {
  /*
   Note: Using BSON types in case class is not recommended,
   as it couples the case class with this specific persistence layer.
   */
  case class User(
    _id: BSONObjectID, // Rather use UUID or String
    username: String,
    role: String,
    created: BSONTimestamp, // Rather use Instance
    lastModified: BSONDateTime,
    sym: Option[BSONSymbol]) // Rather use String

  object User {
    implicit val bsonWriter: BSONDocumentWriter[User] = Macros.writer[User]

    implicit val bsonReader: BSONDocumentReader[User] = Macros.reader[User]
  }
}
