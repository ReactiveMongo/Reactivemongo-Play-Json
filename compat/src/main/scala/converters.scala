package reactivemongo.play.json.compat

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
package object json2bson extends Json2BsonConverters

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
package object bson2json extends Bson2JsonConverters
