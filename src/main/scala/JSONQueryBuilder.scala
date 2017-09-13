package reactivemongo.play.json.collection

import play.api.libs.json.{ JsArray, JsObject }

import reactivemongo.api.{
  Collection,
  FailoverStrategy,
  QueryOpts,
  ReadPreference
}
import reactivemongo.api.collections.{ GenericCollection, GenericQueryBuilder }
import reactivemongo.util.option

import reactivemongo.play.json.JSONSerializationPack

@SerialVersionUID(1)
@SuppressWarnings(Array("FinalModifierOnCaseClass"))
case class JSONQueryBuilder(
    @transient collection: Collection,
    failover: FailoverStrategy,
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

  import play.api.libs.json.{ JsValue, Json }

  type Self = JSONQueryBuilder

  @transient val pack = JSONSerializationPack

  override val readPreference: ReadPreference = collection match {
    case coll: GenericCollection[_] => coll.readPreference
    case _                          => ReadPreference.primary
  }

  def copy(queryOption: Option[JsObject], sortOption: Option[JsObject], projectionOption: Option[JsObject], hintOption: Option[JsObject], explainFlag: Boolean, snapshotFlag: Boolean, commentString: Option[String], options: QueryOpts, failover: FailoverStrategy, maxTimeMsOption: Option[Long]): JSONQueryBuilder =
    JSONQueryBuilder(collection, failover, queryOption, sortOption, projectionOption, hintOption, explainFlag, snapshotFlag, commentString, options, maxTimeMsOption)

  def merge(readPreference: ReadPreference): JsObject = {
    def pref = {
      val mode = readPreference match {
        case ReadPreference.Primary               => "primary"
        case ReadPreference.PrimaryPreferred(_)   => "primaryPreferred"
        case ReadPreference.Secondary(_)          => "secondary"
        case ReadPreference.SecondaryPreferred(_) => "secondaryPreferred"
        case ReadPreference.Nearest(_)            => "nearest"
      }
      val base = Seq[(String, JsValue)]("mode" -> Json.toJson(mode))

      JsObject(readPreference match {
        case ReadPreference.Taggable(tagSet) => base :+ ("tags" -> JsArray(
          tagSet.map(tags => JsObject(tags.toList.map {
            case (k, v) => k -> Json.toJson(v)
          }))
        ))

        case _ => base
      })
    }

    @SuppressWarnings(Array("LooksLikeInterpolatedString"))
    @inline def optional = List[Option[(String, JsValue)]](
      queryOption.map { f"$$query" -> Json.toJson(_) },
      sortOption.map { f"$$orderby" -> Json.toJson(_) },
      hintOption.map { f"$$hint" -> Json.toJson(_) },
      maxTimeMsOption.map { f"$$maxTimeMS" -> Json.toJson(_) },
      commentString.map { f"$$comment" -> Json.toJson(_) },
      option(explainFlag, f"$$explain" -> Json.toJson(true)),
      option(snapshotFlag, f"$$snapshot" -> Json.toJson(true))
    ).flatten

    @SuppressWarnings(Array("LooksLikeInterpolatedString"))
    @inline def merged = JsObject((f"$$readPreference" -> pref) :: optional)

    merged
  }
}
