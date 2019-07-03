import play.api.libs.json.{
  JsError,
  Json,
  JsResult,
  JsSuccess,
  __
}

import reactivemongo.bson._
import reactivemongo.play.json.BSONFormats._

object BSONFormatsSpec extends org.specs2.mutable.Specification {
  "BSON/JSON formats" title

  "BSONFormats" should {
    "handle object ID ($oid) as a separate value" in {
      val oid = BSONObjectID.generate
      val oidAgain = Json.fromJson[BSONObjectID](Json.toJson(oid))

      oidAgain must beLike[JsResult[BSONObjectID]] {
        case JsSuccess(v, _) => v must_=== oid
      }
    }

    "should convert special ObjectID notation only if there is only one field named $oid of type String" in {
      val joid = Json.obj(
        f"$$oid" -> "5150806842b329bae81de713", "truc" -> "plop"
      )

      Json.fromJson[BSONObjectID](joid) match {
        case JsError(_) => success
        case s          => failure(s"should not be a JsSuccess $s")
      }
    }

    "write BSONObjectID as JSON" in {
      val oid = BSONObjectID.generate

      toJSON(oid) must_=== Json.toJson(oid)
    }

    // ---

    "handle BSON binary" >> {
      import reactivemongo.bson.utils.Converters

      val bytes = scala.util.Random.shuffle(1 to 129).map(_.toByte).toArray
      val userDefinedBin = BSONBinary(bytes, Subtype.UserDefinedSubtype)
      val genericBin = BSONBinary(bytes, Subtype.GenericBinarySubtype)
      val hexaValue = Converters.hex2Str(bytes)

      "read from simple string representation" in {
        Json.toJson(hexaValue).
          validate[BSONBinary] must beLike[JsResult[BSONBinary]] {
            case JsSuccess(bin, _) => bin must_=== userDefinedBin
          }
      }

      "handle with extended JSON syntax" >> {
        val baseObj = Json.obj(f"$$binary" -> hexaValue)

        "without explicit subtype" in {
          baseObj.validate[BSONBinary] must beLike[JsResult[BSONBinary]] {
            case JsSuccess(bin, _) => bin must_=== userDefinedBin and {
              // Even if default the subtype on read, always write it

              val explicitJsSubtpe = Json.toJson(Converters.hex2Str(
                Array(Subtype.UserDefinedSubtype.value)))

              Json.toJson(bin) must_=== (baseObj + (
                f"$$type" -> explicitJsSubtpe))
            }
          }
        }

        s"with explicit subtype ${Subtype.GenericBinarySubtype}" in {
          val hexaGeneric = Converters.hex2Str(
            Array(Subtype.GenericBinarySubtype.value))

          val withTpe = baseObj + (f"$$type" -> Json.toJson(hexaGeneric))

          withTpe.validate[BSONBinary] must beLike[JsResult[BSONBinary]] {
            case JsSuccess(bin, _) => bin must_=== genericBin and {
              Json.toJson(bin) must_=== withTpe
            }
          }
        }
      }
    }

    // ---

    "handle JavaScript with extended JSON syntax" >> {
      f"""from JSON { "$$javascript": "bar()" }""" in {
        Json.fromJson[BSONJavaScript](Json.obj(f"$$javascript" -> "bar()")).
          aka("from JSON") must beLike[JsResult[BSONJavaScript]] {
            case JsSuccess(BSONJavaScript("bar()"), _) => ok
          }
      }

      val code2 = "lorem();ipsum('bar')"
      s"""from BSONJavaScript("$code2")""" in {
        Json.toJson(BSONJavaScript(code2)).
          aka("from BSON") must_=== Json.obj(f"$$javascript" -> code2)
      }

      s"from BSONValue" in {
        val bson: BSONValue = BSONJavaScript("foo()")
        Json.toJson(bson) must_=== Json.obj(f"$$javascript" -> "foo()")
      }
    }

    // ---

    "handle BSONTimestamp" in {
      val bsonTs = BSONTimestamp(6065270725701271558L)
      val legacyJson = Json.obj(f"$$i" -> 6, f"$$time" -> 1412180887L)
      val strictJson = Json.obj(f"$$timestamp" -> Json.obj(
        "i" -> 6, "t" -> 1412180887L
      ))
      val expectedJson = legacyJson ++ strictJson

      toJSON(bsonTs) must_=== expectedJson and (
        Json.toJson(bsonTs) must_=== expectedJson
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
      val expectedJson = Json.obj(f"$$undefined" -> true)

      toJSON(BSONUndefined) must_=== expectedJson and (
        Json.toJson(BSONUndefined) must_=== expectedJson
      )
    }

    "handle BSONMinKey" in {
      val expectedJson = Json.obj(f"$$minKey" -> 1)

      toJSON(BSONMinKey) must_=== expectedJson and (
        Json.toJson(BSONMinKey) must_=== expectedJson
      )
    }

    "handle BSONMaxKey" in {
      val expectedJson = Json.obj(f"$$maxKey" -> 1)

      toJSON(BSONMaxKey) must_=== expectedJson and (
        Json.toJson(BSONMaxKey) must_=== expectedJson
      )
    }

    "handle BSONDateTime from strict extended syntax" in {
      val timestamp = System.currentTimeMillis()
      val dt = BSONDateTime(timestamp)
      val jdt = Json.obj(f"$$date" -> timestamp)

      Json.fromJson[BSONDateTime](jdt) must beLike {
        case JsSuccess(res, _) => res must_=== dt
      }
    }

    "handle BSONDateTime from legacy extended syntax" in {
      val timestamp = System.currentTimeMillis()
      val dt = BSONDateTime(timestamp)
      val jdt = Json.obj(f"$$date" -> Json.obj(f"$$numberLong" -> timestamp))

      Json.fromJson[BSONDateTime](jdt) must beLike {
        case JsSuccess(res, _) => res must_=== dt
      }
    }

    "handle BSONDecimal from strict extended syntax" in {
      val decimal = BigDecimal(0.7)
      val bs = BSONDecimal.fromBigDecimal(decimal).get
      val jbs = Json.obj(f"$$decimal" -> decimal)
      val jbs1 = Json.obj(f"$$decimal" -> "0.7")

      Json.fromJson[BSONDecimal](jbs) must beLike {
        case JsSuccess(res, _) => res must_=== bs
      } and {
        Json.fromJson[BSONDecimal](jbs1) must beLike {
          case JsSuccess(res, _) => res must_=== bs
        }
      }
    }

    "handle BSONDecimal from legacy extended syntax" in {
      val decimal = BigDecimal(0.7)
      val bs = BSONDecimal.fromBigDecimal(decimal).get
      val jbs = Json.obj(f"$$decimal" -> Json.obj(f"$$numberDecimal" -> decimal))
      val jbs1 = Json.obj(f"$$decimal" -> Json.obj(f"$$numberDecimal" -> 0.7))

      Json.fromJson[BSONDecimal](jbs) must beLike {
        case JsSuccess(res, _) => res must_=== bs
      } and {
        Json.fromJson[BSONDecimal](jbs1) must beLike {
          case JsSuccess(res, _) => res must_=== bs
        }
      }
    }

    "handle BSONDocument" in {
      val json = Json.obj(
        "age" -> 4,
        "name" -> "Jack",
        "_id" -> (Json.obj(
          f"$$oid" -> "5150806842b329bae81de713"
        ): Json.JsValueWrapper),
        "nested" -> Json.arr("plop", 6, Json.obj("toto" -> "titi"))
      )
      val doc = Json.fromJson[BSONDocument](json)

      Json.toJson(doc.get) must_=== json
    }

    "handle BSONSymbol" in {
      val symbol = Symbol("sss")
      val bsymbol = BSONSymbol(symbol.toString())
      val jsymbol = Json.toJson(bsymbol)
      val bsymbolAgain = Json.fromJson[BSONSymbol](jsymbol)
      bsymbol mustEqual bsymbolAgain.get
    }

    "convert special Symbol notation" in {
      val symbol = Symbol("sss")
      val bsymbol = BSONSymbol(symbol.toString())
      val jsymbol = Json.obj(f"$$symbol" -> symbol.toString)
      Json.fromJson[BSONSymbol](jsymbol).get mustEqual bsymbol
    }

    "convert special Symbol notation only if there is only one field named $symbol of type String" in {
      val jsymbol = Json.obj(f"$$symbol" -> "sym", "truc" -> "plop")
      Json.fromJson[BSONSymbol](jsymbol) match {
        case JsError(_) => success
        case s          => failure(s"not be a JsSuccess $s")
      }
    }

    f"""convert JSON regex { "$$regex": "^toto", "$$options": "i" }""" in {
      val js = Json.obj(f"$$regex" -> "^toto", f"$$options" -> "i")
      val bson = Json.fromJson[BSONRegex](js).get

      bson must_=== BSONRegex("^toto", "i") and {
        js must_== Json.toJson(bson)
      }
    }

    f"""convert JSON regex { "$$options": "i", "$$regex": "^toto" }""" in {
      val js = Json.obj(f"$$options" -> "i", f"$$regex" -> "^toto")
      val bson = Json.fromJson[BSONRegex](js).get

      bson must_=== BSONRegex("^toto", "i") and {
        Json.toJson(bson) must_== Json.obj(
          f"$$regex" -> "^toto", f"$$options" -> "i")
      }
    }

    f"""convert JSON regex { "$$regex": "^toto" }""" in {
      val js = Json.obj(f"$$regex" -> "^toto")
      val bson = Json.fromJson[BSONRegex](js).get

      bson must_=== BSONRegex("^toto", "") and {
        js must_== Json.toJson(bson)
      }
    }

    f"""fail converting json regex { "$$options": "i", "$$regex": 98 }""" in {
      val js = Json.obj(f"$$options" -> "i", f"$$regex" -> 98)

      Json.fromJson[BSONRegex](js).asEither.left.
        map(_.map(_._1)).left.toOption must beSome(List((__ \ f"$$regex")))
    }

    f"""convert legacy timestamp { "$$time": 1412180887, "$$i": 6 }""" in {
      val jsonTs = Json.parse(f"""{ "$$time": 1412180887, "$$i": 6 }""")

      Json.fromJson[BSONTimestamp](jsonTs) must beLike {
        case JsSuccess(ts, _) => ts must_=== BSONTimestamp(6065270725701271558L)
      }
    }

    f"""convert strict timestamp { "$$time": 1412180887, "$$i": 6 }""" in {
      val jsonTs = Json.parse(f"""{ "$$timestamp": {"t":1412180887,"i":6} }""")

      Json.fromJson[BSONTimestamp](jsonTs) must beLike {
        case JsSuccess(ts, _) => ts must_=== BSONTimestamp(6065270725701271558L)
      }
    }

    f"""convert extended JSON { "$$undefined": true }""" in {
      val jsonTs = Json.parse(f"""{ "$$undefined": true }""")

      Json.fromJson[BSONUndefined.type](jsonTs) must beLike {
        case JsSuccess(ts, _) => ts must_=== BSONUndefined
      }
    }

    f"""convert extended JSON { "$$minKey": 1 }""" in {
      val jsonTs = Json.parse(f"""{ "$$minKey": 1 }""")

      Json.fromJson[BSONMinKey.type](jsonTs) must beLike {
        case JsSuccess(mk, _) => mk must_=== BSONMinKey
      }
    }

    f"""convert extended JSON { "$$maxKey": 1 }""" in {
      val jsonTs = Json.parse(f"""{ "$$maxKey": 1 }""")

      Json.fromJson[BSONMaxKey.type](jsonTs) must beLike {
        case JsSuccess(mk, _) => mk must_=== BSONMaxKey
      }
    }
  }
}
