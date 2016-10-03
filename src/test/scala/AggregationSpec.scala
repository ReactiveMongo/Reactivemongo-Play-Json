import scala.concurrent.Future

import play.api.libs.json.{
  JsArray,
  Json,
  JsObject
}, Json.{ obj => document, toJson }

import org.specs2.concurrent.{ ExecutionEnv => EE }

import reactivemongo.play.json._, collection._

object AggregationSpec extends org.specs2.mutable.Specification {
  "Aggregation framework" title

  import Common._

  sequential

  val collection = db.collection[JSONCollection]("zipcodes")
  import collection.BatchCommands.AggregationFramework
  import AggregationFramework.{
    Cursor,
    FirstField,
    Group,
    LastField,
    Match,
    Project,
    Sort,
    Ascending,
    Sample,
    SumField
  }

  case class Location(lon: Double, lat: Double)

  case class ZipCode(_id: String, city: String, state: String,
    population: Long, location: Location)

  implicit val locationHandler = Json.format[Location]
  implicit val zipCodeHandler = Json.format[ZipCode]

  private val zipCodes = List(
    ZipCode("10280", "NEW YORK", "NY", 19746227L,
      Location(-74.016323, 40.710537)),
    ZipCode("72000", "LE MANS", "FR", 148169L, Location(48.0077, 0.1984)),
    ZipCode("JP-13", "TOKYO", "JP", 13185502L,
      Location(35.683333, 139.683333)),
    ZipCode("AO", "AOGASHIMA", "JP", 200L, Location(32.457, 139.767))
  )

  "Zip codes" should {
    "be inserted" in { implicit ee: EE =>
      def insert(data: List[ZipCode]): Future[Unit] = data.headOption match {
        case Some(zip) => collection.insert(zip).flatMap(_ => insert(data.tail))
        case _         => Future.successful({})
      }

      insert(zipCodes) must beEqualTo({}).await(1, timeout)
    } tag ("foo")

    "return states with populations above 10000000" in { implicit ee: EE =>
      // http://docs.mongodb.org/manual/tutorial/aggregation-zip-code-data-set/#return-states-with-populations-above-10-million
      val expected = List(
        document("_id" -> "JP", "totalPop" -> 13185702L),
        document("_id" -> "NY", "totalPop" -> 19746227L)
      )

      collection.aggregate(Group(toJson("$state"))(
        "totalPop" -> SumField("population")
      ), List(Match(document("totalPop" -> document("$gte" -> 10000000L))))).
        map(_.firstBatch) must beEqualTo(expected).await(1, timeout)
    }

    "explain simple result" in { implicit ee: EE =>
      val expected = List(
        document("_id" -> "JP", "totalPop" -> 13185702L),
        document("_id" -> "NY", "totalPop" -> 19746227L)
      )

      collection.aggregate(Group(toJson("$state"))(
        "totalPop" -> SumField("population")
      ), List(Match(document("totalPop" -> document("$gte" -> 10000000L)))),
        explain = true).map(_.firstBatch) must beLike[List[JsObject]] {
        case explainResult :: Nil =>
          (explainResult \ "stages").asOpt[List[JsObject]] must beSome

      }.await(1, timeout)
    }

    "return average city population by state" >> {
      // See http://docs.mongodb.org/manual/tutorial/aggregation-zip-code-data-set/#return-average-city-population-by-state
      val expected = List(
        document("_id" -> "NY", "avgCityPop" -> 19746227D),
        document("_id" -> "FR", "avgCityPop" -> 148169D),
        document("_id" -> "JP", "avgCityPop" -> 6592851D)
      )

      val firstOp = Group(document("state" -> "$state", "city" -> "$city"))(
        "pop" -> SumField("population")
      )

      val pipeline = List(
        Group(toJson("$_id.state"))("avgCityPop" ->
          AggregationFramework.AvgField("pop"))
      )

      "successfully as a single batch" in { implicit ee: EE =>
        collection.aggregate(firstOp, pipeline).map(_.firstBatch).
          aka("results") must beEqualTo(expected).await(1, timeout)
      }

      "with cursor" >> {
        def collect(upTo: Int = Int.MaxValue) =
          collection.aggregate1[JsObject](firstOp, pipeline,
            batchSize = Some(1)).collect[List](upTo)

        "without limit (maxDocs)" in { implicit ee: EE =>
          collect() aka "cursor result" must beEqualTo(expected).
            await(1, timeout)
        }

        "with limit (maxDocs)" in { implicit ee: EE =>
          collect(2) aka "cursor result" must beEqualTo(expected take 2).
            await(1, timeout)
        }
      }
    }

    "return largest and smallest cities by state" in { implicit ee: EE =>
      // See http://docs.mongodb.org/manual/tutorial/aggregation-zip-code-data-set/#return-largest-and-smallest-cities-by-state
      val expected = List(
        document(
          "biggestCity" -> document(
            "name" -> "NEW YORK", "population" -> 19746227L
          ),
          "smallestCity" -> document(
            "name" -> "NEW YORK", "population" -> 19746227L
          ),
          "state" -> "NY"
        ),
        document(
          "biggestCity" -> document(
            "name" -> "LE MANS", "population" -> 148169L
          ),
          "smallestCity" -> document(
            "name" -> "LE MANS", "population" -> 148169L
          ),
          "state" -> "FR"
        ), document(
          "biggestCity" -> document(
            "name" -> "TOKYO", "population" -> 13185502L
          ),
          "smallestCity" -> document(
            "name" -> "AOGASHIMA", "population" -> 200L
          ),
          "state" -> "JP"
        )
      )

      collection.aggregate(
        Group(document("state" -> "$state", "city" -> "$city"))(
          "pop" -> SumField("population")
        ),
        List(
          Sort(Ascending("population")),
          Group(toJson("$_id.state"))(
            "biggestCity" -> LastField("_id.city"),
            "biggestPop" -> LastField("pop"),
            "smallestCity" -> FirstField("_id.city"),
            "smallestPop" -> FirstField("pop")
          ),
          Project(document("_id" -> 0, "state" -> "$_id",
            "biggestCity" -> document(
              "name" -> "$biggestCity", "population" -> "$biggestPop"
            ),
            "smallestCity" -> document(
              "name" -> "$smallestCity", "population" -> "$smallestPop"
            )))
        )
      ).
        map(_.firstBatch) aka "results" must beEqualTo(expected).
        await(1, timeout)
    }

    "return distinct states" in { implicit ee: EE =>
      collection.distinct[String, Set]("state").
        aka("states") must beEqualTo(Set("NY", "FR", "JP")).
        await(1, timeout)
    }

    "return a random sample" in { implicit ee: EE =>
      collection.aggregate(Sample(2)).map(_.head[ZipCode].
        filter(zipCodes.contains).size) must beEqualTo(2).await(1, timeout)
    } tag "not_mongo26"
  }
}
