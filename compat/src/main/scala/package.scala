package reactivemongo.play.json

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
package object compat extends PackageCompat
  with ValueConverters with LowPriorityPackageCompat {

  override def toString = "compat"

  // ---

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
}
