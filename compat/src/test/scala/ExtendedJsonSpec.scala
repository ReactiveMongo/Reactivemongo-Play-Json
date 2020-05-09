package reactivemongo

import _root_.play.api.libs.json.{
  JsArray,
  JsBoolean,
  JsNull,
  JsNumber,
  JsObject,
  JsString,
  JsValue,
  Json
}

import reactivemongo.api.bson.{
  BSONArray,
  BSONBinary,
  BSONBoolean,
  BSONDecimal,
  BSONDocument,
  BSONDouble,
  BSONInteger,
  BSONJavaScript,
  BSONJavaScriptWS,
  BSONLong,
  BSONMaxKey,
  BSONMinKey,
  BSONNull,
  BSONString,
  BSONSymbol,
  BSONUndefined,
  BSONValue,
  Subtype
}

import reactivemongo.play.json.compat.{ JsFalse, JsTrue, dsl }
import reactivemongo.play.json.compat.extended._

import org.specs2.specification.core.Fragment

final class ExtendedJsonSpec extends org.specs2.mutable.Specification {

  "Extended JSON value converters" title

  import ExtendedJsonFixtures._

  "Scalar value converters" should {
    "support binary" >> {
      val bytes = "Test".getBytes("UTF-8")

      Fragment.foreach(Seq[(JsObject, BSONBinary)](
        jsBinUuid -> BSONBinary(uuid),
        jsBinGeneric -> BSONBinary(bytes, Subtype.GenericBinarySubtype))) {
        case (l, n) =>
          s"from JSON $l" in {
            implicitly[BSONValue](l) must_== n
          }

          s"to BSON $n" in {
            implicitly[JsObject](n) must_=== l
          }
      }
    }

    "support boolean" >> {
      "from JSON" in {
        implicitly[BSONBoolean](
          JsBoolean(true)) must_=== BSONBoolean(true) and {
            implicitly[BSONBoolean](JsBoolean(false)) must_=== BSONBoolean(false)
          } and {
            implicitly[BSONBoolean](JsTrue) must_=== BSONBoolean(true)
          } and {
            implicitly[BSONBoolean](JsFalse) must_=== BSONBoolean(false)
          }
      }

      "to BSON" in {
        implicitly[JsBoolean](BSONBoolean(true)) must_=== JsBoolean(true) and {
          implicitly[JsBoolean](BSONBoolean(false)) must_=== JsBoolean(false)
        }
      }
    }

    "support date/time" >> {
      "from JSON" in {
        implicitly[BSONValue](jdt) must_== bdt
      }

      "to BSON" in {
        implicitly[JsObject](bdt) must_=== jdt
      }
    }

    "support decimal" >> {
      lazy val jsInfinity = Json.obj(f"$$numberDecimal" -> "Infinity")
      lazy val jsZero = dsl.decimal(0)

      "from JSON" in {
        implicitly[BSONValue](jsInfinity) must_=== BSONDecimal.PositiveInf and {
          implicitly[BSONValue](jsZero) must_=== BSONDecimal.PositiveZero
        }
      }

      "to BSON" in {
        implicitly[JsObject](BSONDecimal.PositiveInf) must_=== jsInfinity and {
          implicitly[JsObject](BSONDecimal.PositiveZero) must_=== jsZero
        }
      }
    }

    "support double" >> {
      val raw = 1.23D

      "from JSON" in {
        implicitly[BSONValue](dsl.double(raw)) must_=== BSONDouble(raw) and {
          implicitly[BSONValue](JsNumber(raw)) must_=== BSONDouble(raw)
        }
      }

      "to BSON" in {
        implicitly[JsObject](BSONDouble(raw)) must_=== dsl.double(raw)
      }
    }

    "support integer" >> {
      "from JSON" in {
        implicitly[BSONValue](dsl.int(1)) must_=== BSONInteger(1) and {
          implicitly[BSONValue](JsNumber(2)) must_=== BSONInteger(2)
        }
      }

      "to BSON" in {
        implicitly[JsObject](BSONInteger(2)) must_=== dsl.int(2)
      }
    }

    "support JavaScript" >> {
      val raw = "foo()"

      "from JSON" in {
        implicitly[BSONValue](jsJavaScript(raw)) must_=== BSONJavaScript(raw)
      }

      "to BSON" in {
        implicitly[JsObject](BSONJavaScript(raw)) must_=== jsJavaScript(raw)
      }
    }

    "support JavaScript/WS" >> {
      val raw = "bar('lorem')"

      "from JSON" in {
        implicitly[BSONValue](jsJavaScriptWS(raw)) must_=== BSONJavaScriptWS(
          raw, BSONDocument.empty)
      }

      "to BSON" in {
        implicitly[JsObject](BSONJavaScriptWS(
          raw, BSONDocument.empty)) must_=== jsJavaScriptWS(raw)
      }
    }

    "support long" >> {
      "from JSON" in {
        implicitly[BSONValue](dsl.long(1L)) must_=== BSONLong(1L) and {
          implicitly[BSONValue](
            JsNumber(Long.MaxValue)) must_=== BSONLong(Long.MaxValue)
        }
      }

      "to BSON" in {
        implicitly[JsValue](BSONLong(2L)) must_=== dsl.long(2L)
      }
    }

    "support null" >> {
      "from JSON" in {
        implicitly[BSONNull](JsNull) must_=== BSONNull
      }

      "to BSON" in {
        implicitly[JsNull.type](BSONNull) must_=== JsNull
      }
    }

    "support maxKey" >> {
      "from JSON" in {
        implicitly[BSONValue](JsMaxKey) must_=== BSONMaxKey
      }

      "to BSON" in {
        implicitly[JsObject](BSONMaxKey) must_=== JsMaxKey
      }
    }

    "support minKey" >> {
      "from JSON" in {
        implicitly[BSONValue](JsMinKey) must_=== BSONMinKey
      }

      "to BSON" in {
        implicitly[JsObject](BSONMinKey) must_=== JsMinKey
      }
    }

    "support object ID" >> {
      "from JSON" in {
        implicitly[BSONValue](joid) must_=== boid
      }

      "to BSON" in {
        implicitly[JsObject](boid) must_=== joid
      }
    }

    "support string" >> {
      val raw = "Foo"

      "from JSON" in {
        implicitly[BSONValue](JsString(raw)) must_=== BSONString(raw)
      }

      "to BSON" in {
        implicitly[JsString](BSONString(raw)) must_=== JsString(raw)
      }
    }

    "support symbol" >> {
      val raw = "Foo"

      "from JSON" in {
        implicitly[BSONValue](dsl.symbol(raw)) must_=== BSONSymbol(raw)
      }

      "to BSON" in {
        implicitly[JsObject](BSONSymbol(raw)) must_=== dsl.symbol(raw)
      }
    }

    "support timestamp" >> {
      "from JSON" in {
        toValue(jts) must_=== bts and {
          implicitly[BSONValue](jts) must_=== bts
        }
      }

      "to BSON" in {
        implicitly[JsObject](bts) must_=== jts
      }
    }

    "support regexp" >> {
      "from JSON" in {
        implicitly[BSONValue](jre) must_=== bre
      }

      "to BSON" in {
        implicitly[JsObject](bre) must_=== jre
      }
    }

    "support undefined" >> {
      "from JSON" in {
        implicitly[BSONValue](JsUndefined) must_=== BSONUndefined
      }

      "to BSON" in {
        implicitly[JsObject](BSONUndefined) must_=== JsUndefined
      }
    }
  }

  "Non-scalar value converters" should {
    "support array" >> {
      "from JSON" in {
        implicitly[BSONArray](jarr) must_=== barr
      }

      "to BSON" in {
        implicitly[JsArray](barr) must_=== jarr
      }
    }

    "support document" >> {
      "from JSON" in {
        implicitly[BSONDocument](jdoc) must_=== bdoc
      }

      "to BSON" in {
        implicitly[JsObject](bdoc) must_=== jdoc
      }
    }
  }

  "Opaque values" should {
    Fragment.foreach(fixtures) {
      case (json, bson) =>
        s"from JSON $json" in {
          implicitly[BSONValue](json) must_=== bson
        }

        s"$bson to BSON" in {
          implicitly[JsValue](bson) must_=== json
        }
    }
  }
}
