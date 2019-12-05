package reactivemongo

import _root_.play.api.libs.json.{
  JsArray,
  JsNull,
  JsObject,
  JsString,
  JsNumber,
  JsValue
}

import reactivemongo.api.bson.{
  BSONArray,
  BSONBinary,
  BSONBoolean,
  BSONDateTime,
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
  BSONObjectID,
  BSONRegex,
  BSONString,
  BSONSymbol,
  BSONTimestamp,
  BSONUndefined,
  BSONValue
}

import reactivemongo.play.json.compat._

object ExtendedJsonFixtures {
  val time = 1574884443000L

  val joid = JsObject(Map(f"$$oid" -> JsString("5dded45b0000000000000000")))
  val boid = BSONObjectID.fromTime(time, true)

  val uuidBytes = Array[Byte](-118, 110, 125, -36, 67, -38, 70, -93, -113, 18, 35, -53, -69, -15, 49, 66)

  val uuid = java.util.UUID.fromString("ef4afb50-3ff6-3bdf-8ce0-d1c3f4fb1b34")

  val bdt = BSONDateTime(time)
  val jdt = JsObject(Map(f"$$date" -> dsl.long(time)))

  val bts = BSONTimestamp(time)

  val jts = JsObject(Map(f"$$timestamp" -> JsObject(Map(
    "t" -> JsNumber(bts.time), "i" -> JsNumber(bts.ordinal)))))

  val jre = JsObject(Map(f"$$regularExpression" -> JsObject(Map(
    "pattern" -> JsString("foo[A-Z]+"),
    "options" -> JsString("i")))))

  val bre = BSONRegex("foo[A-Z]+", "i")

  @inline def jsJavaScript(code: String) =
    JsObject(Map(f"$$code" -> JsString(code)))

  @inline def jsJavaScriptWS(
    code: String,
    scope: JsObject = JsObject(Map.empty[String, JsValue])) = JsObject(Map(
    f"$$code" -> JsString(code), f"$$scope" -> scope))

  val jarr = JsArray(Seq(joid, JsString("foo"), jdt, dsl.symbol("bar"), jts, jsJavaScript("lorem()"), jre, JsArray(Seq(dsl.int(1), dsl.long(2L))), dsl.double(3.4D)))

  val barr = BSONArray(boid, BSONString("foo"), bdt, BSONSymbol("bar"), bts, BSONJavaScript("lorem()"), bre, BSONArray(BSONInteger(1), BSONLong(2L)), BSONDouble(3.4D))

  val jdoc = JsObject(Map[String, JsValue]("oid" -> joid, "str" -> JsString("foo"), "dt" -> jdt, "sym" -> dsl.symbol("bar"), "ts" -> jts, "nested" -> JsObject(Map[String, JsValue]("foo" -> JsString("bar"), "lorem" -> dsl.long(1L))), "js" -> jsJavaScript("lorem()"), "re" -> jre, "array" -> jarr, "double" -> dsl.double(3.4D)))

  val bdoc = BSONDocument("oid" -> boid, "str" -> BSONString("foo"), "dt" -> bdt, "sym" -> BSONSymbol("bar"), "ts" -> bts, "nested" -> BSONDocument("foo" -> "bar", "lorem" -> 1L), "js" -> BSONJavaScript("lorem()"), "re" -> bre, "array" -> barr, "double" -> BSONDouble(3.4D))

  val jsBinUuid = JsObject(Map(f"$$binary" -> JsObject(Map(
    "base64" -> JsString("70r7UD/2O9+M4NHD9PsbNA=="),
    "subType" -> JsString("04")))))

  val jsBinGeneric = JsObject(Map(f"$$binary" -> JsObject(Map(
    "base64" -> JsString("VGVzdA==" /* "Test" */ ),
    "subType" -> JsString("00")))))

  val fixtures = Seq[(JsValue, BSONValue)](
    jsBinUuid -> BSONBinary(uuid),
    JsTrue -> BSONBoolean(true),
    dsl.double(1.23D) -> BSONDouble(1.23D),
    JsString("Foo") -> BSONString("Foo"),
    dsl.int(1) -> BSONInteger(1),
    dsl.long(1L) -> BSONLong(1L),
    joid -> boid,
    jdt -> bdt,
    jts -> bts,
    dsl.decimal(BigDecimal("0")) -> BSONDecimal.PositiveZero,
    jre -> bre,
    jsJavaScript("foo()") -> BSONJavaScript("foo()"),
    jsJavaScriptWS("bar()") -> BSONJavaScriptWS("bar()", BSONDocument.empty),
    dsl.symbol("sym") -> BSONSymbol("sym"),
    ValueConverters.JsUndefined -> BSONUndefined,
    JsNull -> BSONNull,
    ValueConverters.JsMaxKey -> BSONMaxKey,
    ValueConverters.JsMinKey -> BSONMinKey,
    jarr -> barr,
    jdoc -> bdoc)

}
