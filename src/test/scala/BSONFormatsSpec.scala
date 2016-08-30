import play.api.libs.json.{ JsError, Json, JsResult, JsSuccess, __ }
import reactivemongo.bson._
import reactivemongo.play.json.BSONFormats._

object BSONFormatsSpec extends org.specs2.mutable.Specification {
  "BSON/JSON formats" title

  "BSONFormats" should {
    "handle object ID ($oid) as a separate value" in {
      val oid = BSONObjectID.generate
      val oidAgain = Json.fromJson[BSONObjectID](Json.toJson(oid))
      oid mustEqual oidAgain.get
    }

    "should convert special ObjectID notation only if there is only one field named $oid of type String" in {
      val joid = Json.obj(
        "$oid" -> "5150806842b329bae81de713", "truc" -> "plop"
      )

      Json.fromJson[BSONObjectID](joid) match {
        case JsError(_) => success
        case s          => failure(s"should not be a JsSuccess $s")
      }
    }

    "write BSONObjectID as JSON" in {
      val joid2 = Json.obj(
        "$oid" -> "5150806842b329bae81de713", "truc" -> "plop"
      )
      val oid = BSONObjectID.generate

      toJSON(oid) mustEqual Json.toJson(oid)
    }

    // ---

    "handle JavaScript with extended JSON syntax" >> {
      """from JSON { "$javascript": "bar()" }""" in {
        Json.fromJson[BSONJavaScript](Json.obj("$javascript" -> "bar()")).
          aka("from JSON") must beLike[JsResult[BSONJavaScript]] {
            case JsSuccess(BSONJavaScript("bar()"), _) => ok
          }
      }

      val code2 = "lorem();ipsum('bar')"
      s"""from BSONJavaScript("$code2")""" in {
        Json.toJson(BSONJavaScript(code2)).
          aka("from BSON") must_== Json.obj("$javascript" -> code2)
      }

      s"from BSONValue" in {
        val bson: BSONValue = BSONJavaScript("foo()")
        Json.toJson(bson) must_== Json.obj("$javascript" -> "foo()")
      }
    }

    // ---

    "handle BSONTimestamp" in {
      val bsonTs = BSONTimestamp(6065270725701271558L)
      val legacyJson = Json.obj("$i" -> 6, "$time" -> 1412180887L)
      val strictJson = Json.obj("$timestamp" -> Json.obj(
        "i" -> 6, "t" -> 1412180887L
      ))
      val expectedJson = legacyJson ++ strictJson

      toJSON(bsonTs) must_== expectedJson and (
        Json.toJson(bsonTs) must_== expectedJson
      ) and (
          Json.fromJson[BSONTimestamp](legacyJson) must beLike[JsResult[_]] {
            case JsSuccess(ts, _) => ts must_== bsonTs
          }
        ) and (
            Json.fromJson[BSONTimestamp](strictJson) must beLike[JsResult[_]] {
              case JsSuccess(ts, _) => ts must_== bsonTs
            }
          )
    }

    "handle BSONUndefined" in {
      val expectedJson = Json.obj("$undefined" -> true)

      toJSON(BSONUndefined) must_== expectedJson and (
        Json.toJson(BSONUndefined) must_== expectedJson
      )
    }

    "handle BSONMinKey" in {
      val expectedJson = Json.obj("$minKey" -> 1)

      toJSON(BSONMinKey) must_== expectedJson and (
        Json.toJson(BSONMinKey) must_== expectedJson
      )
    }

    "handle BSONMaxKey" in {
      val expectedJson = Json.obj("$maxKey" -> 1)

      toJSON(BSONMaxKey) must_== expectedJson and (
        Json.toJson(BSONMaxKey) must_== expectedJson
      )
    }

    "handle BSONDateTime from strict extended syntax" in {
      val timestamp = System.currentTimeMillis()
      val dt = BSONDateTime(timestamp)
      val jdt = Json.obj("$date" -> timestamp)

      Json.fromJson[BSONDateTime](jdt) must beLike {
        case JsSuccess(res, _) => res must_== dt
      }
    }

    "handle BSONDateTime from legacy extended syntax" in {
      val timestamp = System.currentTimeMillis()
      val dt = BSONDateTime(timestamp)
      val jdt = Json.obj("$date" -> Json.obj("$numberLong" -> timestamp))

      Json.fromJson[BSONDateTime](jdt) must beLike {
        case JsSuccess(res, _) => res must_== dt
      }
    }

    "handle BSONDocument" in {
      val json = Json.obj(
        "age" -> 4,
        "name" -> "Jack",
        "_id" -> (Json.obj(
          "$oid" -> "5150806842b329bae81de713"
        ): Json.JsValueWrapper),
        "nested" -> Json.arr("plop", 6, Json.obj("toto" -> "titi"))
      )
      val doc = Json.fromJson[BSONDocument](json)

      Json.toJson(doc.get) must_== json
    }

    "handle BSONSymbol" in {
      val symbol = 'sss
      val bsymbol = BSONSymbol(symbol.toString())
      val jsymbol = Json.toJson(bsymbol)
      val bsymbolAgain = Json.fromJson[BSONSymbol](jsymbol)
      bsymbol mustEqual bsymbolAgain.get
    }

    "convert special Symbol notation" in {
      val symbol = 'sss
      val bsymbol = BSONSymbol(symbol.toString())
      val jsymbol = Json.obj("$symbol" -> symbol.toString)
      Json.fromJson[BSONSymbol](jsymbol).get mustEqual bsymbol
    }

    "convert special Symbol notation only if there is only one field named $symbol of type String" in {
      val jsymbol = Json.obj("$symbol" -> "sym", "truc" -> "plop")
      Json.fromJson[BSONSymbol](jsymbol) match {
        case JsError(_) => success
        case s          => failure(s"not be a JsSuccess $s")
      }
    }

    """convert JSON regex { "$regex": "^toto", "$options": "i" }""" in {
      val js = Json.obj("$regex" -> "^toto", "$options" -> "i")
      val bson = Json.fromJson[BSONRegex](js).get
      bson mustEqual BSONRegex("^toto", "i")
      val deser = Json.toJson(bson)
      js mustEqual deser
    }

    """convert JSON regex { "$options": "i", "$regex": "^toto" }""" in {
      val js = Json.obj("$options" -> "i", "$regex" -> "^toto")
      val bson = Json.fromJson[BSONRegex](js).get
      bson mustEqual BSONRegex("^toto", "i")
      Json.toJson(bson) must_== Json.obj("$regex" -> "^toto", "$options" -> "i")
    }

    """convert JSON regex { "$regex": "^toto" }""" in {
      val js = Json.obj("$regex" -> "^toto")
      val bson = Json.fromJson[BSONRegex](js).get

      bson mustEqual BSONRegex("^toto", "") and (js mustEqual Json.toJson(bson))
    }

    """fail converting json regex { "$options": "i", "$regex": 98 }""" in {
      val js = Json.obj("$options" -> "i", "$regex" -> 98)
      val result = Json.fromJson[BSONRegex](js)
      result.fold(
        x => {
          x.head._1 mustEqual (__ \ "$regex")
        }, x => failure(s"got a JsSuccess = $result instead of a JsError")
      )
      ok
    }

    """convert legacy timestamp { "$time": 1412180887, "$i": 6 }""" in {
      val jsonTs = Json.parse("""{ "$time": 1412180887, "$i": 6 }""")

      Json.fromJson[BSONTimestamp](jsonTs) must beLike {
        case JsSuccess(ts, _) => ts must_== BSONTimestamp(6065270725701271558L)
      }
    }

    """convert strict timestamp { "$time": 1412180887, "$i": 6 }""" in {
      val jsonTs = Json.parse("""{ "$timestamp": {"t":1412180887,"i":6} }""")

      Json.fromJson[BSONTimestamp](jsonTs) must beLike {
        case JsSuccess(ts, _) => ts must_== BSONTimestamp(6065270725701271558L)
      }
    }

    """convert extended JSON { "$undefined": true }""" in {
      val jsonTs = Json.parse("""{ "$undefined": true }""")

      Json.fromJson[BSONUndefined.type](jsonTs) must beLike {
        case JsSuccess(ts, _) => ts must_== BSONUndefined
      }
    }

    """convert extended JSON { "$minKey": 1 }""" in {
      val jsonTs = Json.parse("""{ "$minKey": 1 }""")

      Json.fromJson[BSONMinKey.type](jsonTs) must beLike {
        case JsSuccess(mk, _) => mk must_== BSONMinKey
      }
    }

    """convert extended JSON { "$maxKey": 1 }""" in {
      val jsonTs = Json.parse("""{ "$maxKey": 1 }""")

      Json.fromJson[BSONMaxKey.type](jsonTs) must beLike {
        case JsSuccess(mk, _) => mk must_== BSONMaxKey
      }
    }
  }
}
