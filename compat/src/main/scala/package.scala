package reactivemongo.play.json

import play.api.libs.json.{ JsObject, JsString, JsValue, OWrites }

import reactivemongo.api.bson.BSONObjectID

/**
 * Implicit conversions for handler & value types between
 * `play.api.libs.json` and `reactivemongo.api.bson`,
 * by default using the [[https://docs.mongodb.com/manual/reference/mongodb-extended-json MongoDB Extended JSON]] syntax.
 *
 * {{{
 * import play.api.libs.json.JsValue
 * import reactivemongo.api.bson.BSONValue
 *
 * import reactivemongo.play.json.compat._
 *
 * def foo(v: BSONValue): JsValue = v // ValueConverters.fromValue
 * }}}
 *
 * For more specific imports, see [[ValueConverters]] and handler converters.
 */
package object compat extends PackageCompat with ValueConverters {
  @deprecated("Will be removed when provided by Play-JSON itself", "0.20.6")
  implicit final val jsObjectWrites: OWrites[JsObject] =
    OWrites[JsObject](identity)

  /**
   * Implicit conversions for handler types
   * from `play.api.libs.json` to `reactivemongo.api.bson` .
   *
   * {{{
   * import reactivemongo.play.json.compat.json2bson._
   *
   * def foo[T](jw: play.api.libs.json.OWrites[T]) = {
   *   val w: reactivemongo.api.bson.BSONDocumentWriter[T] = jw
   *   w
   * }
   * }}}
   *
   * '''Note:''' Importing both `json2bson` & `bson2json`
   * can lead to diverging implicits in Scala 2.11
   *  (see `HandlerConverterSpec211`).
   */
  object json2bson extends Json2BsonConverters

  /**
   * Implicit conversions for handler types
   * from `reactivemongo.api.bson` to `play.api.libs.json` .
   *
   * {{{
   * import reactivemongo.play.json.compat.bson2json._
   *
   * def bar[T](br: reactivemongo.api.bson.BSONReader[T]) = {
   *   val r: play.api.libs.json.Reads[T] = br
   *   r
   * }
   * }}}
   *
   * '''Note:''' Importing both `json2bson` & `bson2json`
   * can lead to diverging implicits in Scala 2.11
   *  (see `HandlerConverterSpec211`).
   */
  object bson2json extends Bson2JsonConverters

  /**
   * DSL for [[https://docs.mongodb.com/manual/reference/mongodb-extended-json MongoDB Extended JSON]] syntax (v2).
   *
   * {{{
   * import play.api.libs.json.Json
   * import reactivemongo.play.json.compat.dsl._
   *
   * Json.obj("int" -> int(1), "double" -> double(2.3D))
   * // {
   * //   "int": { "\$numberInt": "1" },
   * //   "double": { "\$numberDouble": "2.3" }
   * // }
   * }}}
   */
  object dsl {
    /**
     * Represents a [[scala.Int]] value using [[https://docs.mongodb.com/manual/reference/mongodb-extended-json MongoDB Extended JSON]] syntax (v2).
     *
     * {{{
     * import play.api.libs.json.Json
     * import reactivemongo.play.json.compat.dsl.int
     *
     * Json.obj("field" -> int(1))
     * // { "field": { "\$numberInt": "1" } }
     * }}}
     */
    @inline def int(i: Int): JsObject =
      JsObject(Map[String, JsValue](f"$$numberInt" -> JsString(i.toString)))

    /**
     * Represents a [[scala.Long]] value using [[https://docs.mongodb.com/manual/reference/mongodb-extended-json MongoDB Extended JSON]] syntax (v2).
     *
     * {{{
     * import play.api.libs.json.Json
     * import reactivemongo.play.json.compat.dsl.long
     *
     * Json.obj("field" -> long(2L))
     * // { "field": { "\$numberLong": "2" } }
     * }}}
     */
    @inline def long(l: Long): JsObject =
      JsObject(Map[String, JsValue](f"$$numberLong" -> JsString(l.toString)))

    /**
     * Represents a [[scala.Double]] value using [[https://docs.mongodb.com/manual/reference/mongodb-extended-json MongoDB Extended JSON]] syntax (v2).
     *
     * {{{
     * import play.api.libs.json.Json
     * import reactivemongo.play.json.compat.dsl.double
     *
     * Json.obj("field" -> double(3.4D))
     * // { "field": { "\$numberDouble": "3.4" } }
     * }}}
     */
    @inline def double(d: Double): JsObject = {
      val repr: String = {
        if (d.isNaN) "NaN"
        else if (d.isNegInfinity) "Infinity"
        else if (d.isInfinity) "-Infinity"
        else d.toString
      }

      JsObject(Map[String, JsValue](f"$$numberDouble" -> JsString(repr)))
    }

    /**
     * Represents a [[scala.BigDecimal]] value using [[https://docs.mongodb.com/manual/reference/mongodb-extended-json MongoDB Extended JSON]] syntax (v2).
     *
     * {{{
     * import play.api.libs.json.Json
     * import reactivemongo.play.json.compat.dsl.decimal
     *
     * Json.obj("field" -> decimal(BigDecimal("4")))
     * // { "field": { "\$numberDecimal": "4" } }
     * }}}
     */
    @inline def decimal(d: BigDecimal): JsObject =
      JsObject(Map[String, JsValue](f"$$numberDecimal" -> JsString(d.toString)))

    /**
     * Represents a symbol using [[https://docs.mongodb.com/manual/reference/mongodb-extended-json MongoDB Extended JSON]] syntax (v2).
     *
     * {{{
     * import play.api.libs.json.Json
     * import reactivemongo.play.json.compat.dsl.symbol
     *
     * Json.obj("field" -> symbol("sym_name"))
     * // { "field": { "\$symbol": "sym_name" } }
     * }}}
     */
    @inline def symbol(name: String): JsObject =
      JsObject(Map[String, JsValue](f"$$symbol" -> JsString(name.toString)))

    /**
     * Represents a object ID using [[https://docs.mongodb.com/manual/reference/mongodb-extended-json MongoDB Extended JSON]] syntax (v2).
     *
     * {{{
     * import play.api.libs.json.Json
     * import reactivemongo.api.bson.BSONObjectID
     * import reactivemongo.play.json.compat.dsl.objectID
     *
     * Json.obj("field" -> objectID(BSONObjectID.generate()))
     * // { "field": { "\$oid": "...binary repr" } }
     * }}}
     */
    @inline def objectID(oid: BSONObjectID): JsObject =
      JsObject(Map[String, JsValue](f"$$oid" -> JsString(oid.stringify)))

  }

  /**
   * @define syntaxDocBaseUrl https://docs.mongodb.com/manual/reference/mongodb-extended-json
   * @define specsUrl https://github.com/mongodb/specifications/blob/master/source/extended-json.rst
   *
   * Implicit conversions for value types between
   * `play.api.libs.json` and `reactivemongo.api.bson`,
   * using [[$syntaxDocBaseUrl MongoDB Extended JSON]] syntax (v2).
   *
   * {{{
   * import play.api.libs.json.JsValue
   * import reactivemongo.api.bson.BSONValue
   * import reactivemongo.play.json.compat.extended._
   *
   * def foo(v: BSONValue): JsValue =
   *   implicitly[JsValue](v) // ExtendedJsonConverters.fromValue
   *
   * def bar(v: JsValue): BSONValue =
   *   implicitly[BSONValue](v) // ExtendedJsonConverters.toValue
   * }}}
   *
   * ''Note:'' Logger `reactivemongo.api.play.json.ValueConverters` can be used to debug.
   *
   * See [[https://github.com/mongodb/specifications/blob/master/source/extended-json.rst#conversion-table specifications]].
   */
  object extended extends ExtendedJsonConverters { converters =>
    @inline implicit def fromValue: FromValue = converters
    @inline implicit def toValue: ToValue = converters

    override def toString = "extended"
  }

  /**
   * {{{
   * import play.api.libs.json._
   * import reactivemongo.api.bson._
   * import reactivemongo.play.json.compat.lax._
   *
   * Json.obj("_id" -> BSONObjectID.generate()) // objectIdWrites
   * // { "_id": "as_string_instead_of_ObjectId" }
   * }}}
   */
  object lax extends LaxValueConverters
    with LaxHandlerWorkarounds { converters =>

    @inline implicit def fromValue: FromValue = converters
    @inline implicit def toValue: ToValue = converters

    override def toString = "lax"
  }

  override def toString = "compat"
}
