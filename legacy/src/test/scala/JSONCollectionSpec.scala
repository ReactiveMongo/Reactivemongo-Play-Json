import scala.util.{ Failure, Try }

import scala.concurrent.Await

import reactivemongo.api.{
  Cursor,
  FailoverStrategy,
  ReadConcern,
  ReadPreference,
  WriteConcern
}

import reactivemongo.api.commands.{
  CommandError,
  UnitBox,
  WriteResult
}

import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.core.Fragments

final class JSONCollectionSpec(implicit ee: ExecutionEnv)
  extends org.specs2.mutable.Specification {

  "JSON collection" title

  sequential

  import Common._
  import play.api.libs.json._
  import reactivemongo.play.json._
  import reactivemongo.play.json.collection.JSONCollection
  import reactivemongo.bson._
  import reactivemongo.api.bson.compat._

  case class User(
    _id: Option[BSONObjectID] = None, username: String, height: Double)

  implicit val userReads = Json.reads[User]
  implicit val userWrites = Json.writes[User]

  lazy val collectionName = s"test_users_${System identityHashCode this}"
  lazy val bsonCollection = db(collectionName)
  lazy val collection = new JSONCollection(
    db, collectionName, FailoverStrategy.default, ReadPreference.primary)

  "JSONCollection.save" should {
    "add object if there does not exist in database" in {
      // Check current document does not exist
      val query = BSONDocument("username" -> BSONString("John Doe"))

      bsonCollection.find(query, Option.empty[JsObject]).
        one[JsObject] must beNone.await(1, timeout)

      // Add document..
      collection.insert.one(
        User(username = "John Doe", height = 12)) must beLike[WriteResult] {
          case result => result.ok must beTrue and {
            // Check data in mongodb..
            bsonCollection.find(query, Option.empty[BSONDocument]).
              one[BSONDocument] must beSome[BSONDocument].which { d =>
                d.get("_id") must beSome and (
                  d.get("username") must beSome(BSONString("John Doe")))
              }.await(1, timeout)
          }
        }.await(1, timeout)
    }

    "update object there already exists in database" in {
      // Find saved object
      val fetched1 = Await.result(collection.find(
        Json.obj("username" -> "John Doe"), Option.empty[JsObject]).
        one[User], timeout)

      fetched1 must beSome[User].which { u =>
        u._id.isDefined must beTrue and (u.username must_=== "John Doe")
      }

      // Update object..
      val newUser = fetched1.get.copy(username = "Jane Doe")
      val result = Await.result(
        collection.update(ordered = true).one(
          q = Json.obj("username" -> "John Doe"),
          u = newUser,
          upsert = false,
          multi = false), timeout)
      result.ok must beTrue

      // Check data in mongodb..
      val fetched2 = Await.result(bsonCollection.find(
        BSONDocument("username" -> BSONString("John Doe")),
        Option.empty[BSONDocument]).one[BSONDocument], timeout)

      fetched2 must beNone

      val fetched3 = Await.result(bsonCollection.find(
        BSONDocument("username" -> BSONString("Jane Doe")),
        Option.empty[BSONDocument]).one[BSONDocument], timeout)

      fetched3 must beSome[BSONDocument].which { d =>
        d.get("_id") must beSome(fetched1.get._id.get) and (
          d.get("username") must beSome(BSONString("Jane Doe")))
      }
    }

    "add object if does not exist but its field `_id` is set" in {
      // Check current document does not exist
      val query = BSONDocument("username" -> BSONString("Robert Roe"))
      val id = BSONObjectID.generate

      // Add document..
      collection.insert.one(User(
        _id = Some(id), username = "Robert Roe", height = 13)).map(_.ok) aka "saved" must beTrue.await(1, timeout) and {
        // Check data in mongodb..
        bsonCollection.find(query, Option.empty[BSONDocument]).
          one[BSONDocument] must beSome[BSONDocument].which { d =>
            d.get("_id") must beSome(id) and (
              d.get("username") must beSome(BSONString("Robert Roe")))
          }.await(1, timeout)
      }
    }

    "find distinct height" in {
      collection.distinct[Int, Set](
        key = "height",
        selector = None,
        readConcern = ReadConcern.Local,
        collation = None) must contain(atMost(12, 13)).awaitFor(timeout)
    }

    "delete inserted user" in {
      val query = Json.obj("username" -> "To Be Deleted")
      val id = BSONObjectID.generate
      val user = User(
        _id = Some(id), username = "To Be Deleted", height = 13)

      def find() = collection.find(query, Option.empty[JsObject]).one[User]

      collection.insert.one(user).map(_.ok) must beTrue.await(1, timeout) and {
        find() must beSome(user).awaitFor(timeout)
      } and {
        collection.delete.one(query).
          map(_.n) must beTypedEqualTo(1).awaitFor(timeout)
      } and {
        find() must beNone.awaitFor(timeout)
      }
    }
  }

  "JSONCollection.findAndModify" should {
    "be successful" in {
      val id = BSONObjectID.generate

      collection.findAndUpdate(
        selector = Json.obj("_id" -> id),
        update = User(
          _id = Some(id),
          username = "James Joyce", height = 1.264290338792695E+64),
        fetchNewObject = false,
        upsert = true,
        sort = None,
        fields = None,
        bypassDocumentValidation = false,
        writeConcern = WriteConcern.Default,
        maxTime = None,
        collation = None,
        arrayFilters = Seq.empty).
        map(_.result[JsObject]) must beNone.await(1, timeout)
    }
  }

  "GridFS" should {
    "be setup with JSON serialization" in {
      @com.github.ghik.silencer.silent
      implicit def collp = reactivemongo.play.json.collection.JSONCollectionProducer

      val x = reactivemongo.api.gridfs.GridFS(
        JSONSerializationPack, db, "fs")

      x.ensureIndex() must beTrue.awaitFor(timeout) and {
        x.ensureIndex() must beFalse.awaitFor(timeout)
      }
    }
  }

  "JSON collection" should {
    @inline def cursorAll: Cursor[JsObject] =
      collection.withReadPreference(ReadPreference.secondaryPreferred).
        find(Json.obj(), Option.empty[JsObject]).cursor[JsObject]()

    "use read preference from the collection" in {
      import scala.language.reflectiveCalls
      val withPref = cursorAll.asInstanceOf[{ def preference: ReadPreference }]

      withPref.preference must_=== ReadPreference.secondaryPreferred
    }

    "find with empty criteria document" in {
      collection.find(Json.obj(), Option.empty[JsObject]).
        sort(Json.obj("updated" -> -1)).cursor[JsObject]().collect[List](
          Int.MaxValue, Cursor.FailOnError[List[JsObject]]()).
          aka("find with empty document") must not(throwA[Throwable]).
          await(1, timeout)
    }

    "find with selector and projection" in {
      collection.find(
        selector = Json.obj("username" -> "Jane Doe"),
        projection = Option(Json.obj("_id" -> 0))).cursor[JsObject]().headOption must beSome[JsObject].which { json =>
          Json.stringify(json) must beTypedEqualTo(
            "{\"username\":\"Jane Doe\",\"height\":12}")
        }.await(1, timeout)
    }

    "count all matching document" in {
      def count(
        selector: Option[JsObject] = None,
        limit: Option[Int] = None,
        skip: Int = 0) = collection.count(
        selector, limit, skip, None, ReadConcern.Local)

      count() aka "all" must beTypedEqualTo(3L).await(1, timeout) and (
        count(Some(Json.obj("username" -> "Jane Doe"))).
        aka("with query") must beTypedEqualTo(1L).await(1, timeout)) and {
          count(limit = Some(1)) must beTypedEqualTo(1L).await(1, timeout)
        }
    }
  }

  "JSON cursor" should {
    "return result as a JSON array" in {
      import reactivemongo.play.json.collection.JsCursor._

      collection.find(Json.obj(), Option.empty[JsObject]).
        cursor[JsObject]().jsArray().
        map(_.value.map { js => (js \ "username").as[String] }.toSeq).
        aka("extracted JSON array") must beTypedEqualTo(Seq(
          "Jane Doe", "Robert Roe", "James Joyce")).await(1, timeout)
    }
  }

  "JSON documents" should {
    import reactivemongo.play.json.collection.Helpers

    val quiz = db[JSONCollection](s"quiz_${System identityHashCode this}")

    "be imported" in {
      def input = getClass.getResourceAsStream("/quiz.json")
      val expected = List("""{"_id":1,"name":"dave123","quiz":1,"score":85}""", """{"_id":2,"name":"dave2","quiz":1,"score":90}""", """{"_id":3,"name":"ahn","quiz":1,"score":71}""", """{"_id":4,"name":"li","quiz":2,"score":96}""", """{"_id":5,"name":"annT","quiz":2,"score":77}""", """{"_id":6,"name":"ty","quiz":2,"score":82}""")

      Helpers.bulkInsert(quiz, input).map(_.totalN).
        aka("inserted") must beTypedEqualTo(6).await(0, timeout) and {
          quiz.find(Json.obj(), Option.empty[JsObject]).cursor[JsObject]().
            collect[List](Int.MaxValue, Cursor.FailOnError[List[JsObject]]()).
            map(_.map(Json.stringify).sorted) must beTypedEqualTo(expected).
            await(0, timeout)
        }
    }
  }

  "Command result" should {
    import reactivemongo.play.json.commands._

    val mini = Json.obj("ok" -> Json.toJson(0))
    val withCode = mini + ("code" -> Json.toJson(1))
    val withErrmsg = mini + ("errmsg" -> Json.toJson("Foo"))
    val full = withCode ++ withErrmsg

    type Fixture = (JsObject, Boolean, Boolean)

    val pack = JSONSerializationPack
    val reader: pack.Reader[UnitBox.type] = CommonImplicits.UnitBoxReader

    Fragments.foreach(Seq[Fixture](
      (mini, false, false),
      (withCode, true, false),
      (withErrmsg, false, true),
      (full, true, true))) {
      case (origDoc, hasCode, hasErrmsg) =>
        val res = Try(pack.deserialize[UnitBox.type](origDoc, reader))

        val mustHasCode = if (!hasCode) ok else {
          res must beLike {
            case Failure(CommandError.Code(1)) => ok
          }
        }

        val mustHasErrmsg = if (!hasErrmsg) ok else {
          res must beLike {
            case Failure(CommandError.Message("Foo")) => ok
          }
        }

        s"represent CommandError from '${Json.stringify(origDoc)}'" in {
          mustHasCode and mustHasErrmsg
        }
    }
  }
}
