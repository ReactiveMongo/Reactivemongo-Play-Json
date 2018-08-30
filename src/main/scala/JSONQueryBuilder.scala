package reactivemongo.play.json.collection

import play.api.libs.json.JsObject

import reactivemongo.api.{ Collection, FailoverStrategy, QueryOpts }
import reactivemongo.api.collections.GenericQueryBuilder

import reactivemongo.play.json.JSONSerializationPack

@SerialVersionUID(1)
@SuppressWarnings(Array("FinalModifierOnCaseClass"))
@deprecated("Useless, will be remove", "0.16.0")
case class JSONQueryBuilder(
    @transient collection: Collection,
    failoverStrategy: FailoverStrategy,
    queryOption: Option[JsObject] = None,
    sortOption: Option[JsObject] = None,
    projectionOption: Option[JsObject] = None,
    hintOption: Option[JsObject] = None,
    explainFlag: Boolean = false,
    snapshotFlag: Boolean = false,
    commentString: Option[String] = None,
    options: QueryOpts = QueryOpts(),
    maxTimeMsOption: Option[Long] = None
) extends GenericQueryBuilder[JSONSerializationPack.type] {
  type Self = JSONQueryBuilder

  @transient val pack = JSONSerializationPack

  @deprecated("Use `failoverStrategy`", "0.16.0")
  def failover = failoverStrategy

  def copy(queryOption: Option[JsObject], sortOption: Option[JsObject], projectionOption: Option[JsObject], hintOption: Option[JsObject], explainFlag: Boolean, snapshotFlag: Boolean, commentString: Option[String], options: QueryOpts, failover: FailoverStrategy, maxTimeMsOption: Option[Long]): JSONQueryBuilder = JSONQueryBuilder(collection, failover, queryOption, sortOption, projectionOption, hintOption, explainFlag, snapshotFlag, commentString, options, maxTimeMsOption)

}
