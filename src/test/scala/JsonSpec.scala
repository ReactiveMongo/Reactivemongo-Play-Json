import play.api.libs.json.{
  JsError,
  Json,
  JsNumber,
  JsResult,
  JsSuccess,
  JsString,
  JsObject,
  OWrites,
  Reads,
  Writes,
  __
}

import reactivemongo.bson._
import reactivemongo.play.json._

class JsonSpec extends org.specs2.mutable.Specification {
  "JSON" title

  sequential

  val pack = Package(
    Expeditor("Amazon"),
    List(Item("A Game of Thrones", "Book", 1)),
    20
  )

  "ReactiveMongo" should {
    "convert an empty JSON and give an empty BSON doc" in {
      val json = Json.obj()
      val bson = BSONFormats.toBSON(json).get.asInstanceOf[BSONDocument]
      bson.isEmpty must beTrue
      val json2 = BSONFormats.toJSON(bson)
      json2.as[JsObject].value.size mustEqual 0
    }

    "convert a simple JSON to BSON and vice versa" in {
      val json = Json.obj("coucou" -> JsString("jack"))
      val bson = JsObjectWriter.write(json)
      val json2 = JsObjectReader.read(bson)
      json.toString mustEqual json2.toString
    }

    "convert a simple JSON array to BSON and vice versa" in {
      val json = Json.arr(JsString("jack"), JsNumber(9.1))
      val bson = BSONFormats.toBSON(json).get.asInstanceOf[BSONArray]

      json.toString mustEqual BSONFormats.toJSON(bson).toString
    }

    "convert BSONDouble NaN using the extended systen" in {
      val bson = BSONDouble(Double.NaN)
      val json = BSONFormats.toJSON(bson)

      json must_== Json.obj("$double" -> "NaN")
    }

    "convert a JSON doc containing an array and vice versa" in {
      val json = Json.obj(
        "name" -> JsString("jack"),
        "contacts" -> Json.arr(Json.toJsFieldJsValueWrapper(Json.obj("email" -> "jack@jack.com")))
      )
      val bson = JsObjectWriter.write(json)

      json.toString mustEqual JsObjectReader.read(bson).toString
    }

    "format a JsPath for Mongo CRUD" in {
      import play.api.libs.functional.syntax._
      import Writers._

      case class Limit(low: Option[Int], high: Option[Int])
      case class App(limit: Option[Limit])

      val lowWriter = (__ \ "low").writeNullable[Int]
      val highWriter = (__ \ "high").writeNullable[Int]
      val limitWriter = (lowWriter and highWriter)(unlift(Limit.unapply))
      val appWriter = (__ \ "limit").writemongo[Limit](limitWriter)

      appWriter.writes(Limit(Some(1), None)) mustEqual
        Json.obj("limit.low" -> 1)
      appWriter.writes(Limit(Some(1), Some(2))) mustEqual
        Json.obj("limit.low" -> 1, "limit.high" -> 2)

      appWriter.writes(Limit(None, None)) mustEqual Json.obj()
    }

    "provides a Play JSON OWrites for T : BSONDocumentWriter" in {
      implicit val bsonWriter = Macros.writer[Item]
      implicit val jsonOWrites: OWrites[Item] = BSONFormats.jsonOWrites[Item]

      Json.toJson(Item("foo", "bar", 1)) must_== Json.obj(
        "name" -> "foo",
        "description" -> "bar",
        "occurrences" -> 1
      )
    }

    "provides a Play JSON Writes for T : BSONWriter" in {
      implicit val bsonWriter = Macros.writer[Item]
      implicit val jsonWrites: Writes[Item] = BSONFormats.jsonWrites[Item]

      Json.toJson(Item("foo", "bar", 1)) must_== Json.obj(
        "name" -> "foo",
        "description" -> "bar",
        "occurrences" -> 1
      )
    }

    "provides a Play JSON Reads for T : BSONWriter" in {
      implicit val bsonReader = Macros.reader[Item]
      implicit val jsonReads: Reads[Item] = BSONFormats.jsonReads[Item]

      Json.obj(
        "name" -> "foo",
        "description" -> "bar",
        "occurrences" -> 1
      ).validate[Item] must beLike[JsResult[Item]] {
          case JsSuccess(item, _) => item must_== Item("foo", "bar", 1)
        }
    }

    "provides a Play JSON OFormat for T" in {
      implicit val bsonWriter = Macros.writer[Expeditor]
      implicit val bsonReader = Macros.reader[Expeditor]
      val format = BSONFormats.jsonOFormat[Expeditor]

      format.reads(format.writes(Expeditor("foo"))).
        get must_== Expeditor("foo") and {
          format.writes(format.reads(Json.obj("name" -> "foo")).get).
            aka("JSON") must_== Json.obj("name" -> "foo")
        }
    }

    "provides a Play JSON Format for T" in {
      implicit val bsonWriter = Macros.writer[Expeditor]
      implicit val bsonReader = Macros.reader[Expeditor]
      val format = BSONFormats.jsonOFormat[Expeditor]

      format.reads(format.writes(Expeditor("bar"))).
        get must_== Expeditor("bar") and {
          format.writes(format.reads(Json.obj("name" -> "bar")).get).
            aka("JSON") must_== Json.obj("name" -> "bar")
        }
    }
  }
}

case class Expeditor(name: String)
case class Item(name: String, description: String, occurrences: Int)
case class Package(
  expeditor: Expeditor,
  items: List[Item],
  price: Float
)
