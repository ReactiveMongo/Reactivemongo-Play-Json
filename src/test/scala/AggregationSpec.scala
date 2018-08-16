import scala.concurrent.Future

import play.api.libs.json.{ Json, JsObject }, Json.{ obj => document, toJson }

import org.specs2.concurrent.ExecutionEnv

import reactivemongo.api.Cursor

import reactivemongo.play.json._, collection._

class AggregationSpec(implicit ee: ExecutionEnv)
  extends org.specs2.mutable.Specification {

  "Aggregation framework" title

  import Common._

  sequential

  val collection = db.collection[JSONCollection]("zipcodes")

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
    "be inserted" in {
      def insert(data: List[ZipCode]): Future[Unit] = data.headOption match {
        case Some(zip) => collection.insert.one(zip).
          flatMap(_ => insert(data.tail))

        case _ => Future.successful({})
      }

      insert(zipCodes) must beTypedEqualTo({}).await(1, timeout)
    }

    "return states with populations above 10000000" in {
      // http://docs.mongodb.org/manual/tutorial/aggregation-zip-code-data-set/#return-states-with-populations-above-10-million
      val expected = List(
        document("_id" -> "JP", "totalPop" -> 13185702L),
        document("_id" -> "NY", "totalPop" -> 19746227L)
      )

      collection.aggregateWith[JsObject]() { agg =>
        import agg.{ Group, Match, SumField }

        Group(toJson("$state"))(
          "totalPop" -> SumField("population")
        ) -> List(Match(document("totalPop" -> document("$gte" -> 10000000L))))
      }.collect(3, Cursor.FailOnError[List[JsObject]]()).
        aka("aggregated") must beTypedEqualTo(expected).await(1, timeout)
    }

    "explain simple result" in {
      collection.aggregateWith[JsObject](explain = true) { agg =>
        import agg.{ Group, Match, SumField }

        Group(toJson("$state"))(
          "totalPop" -> SumField("population")
        ) -> List(Match(document("totalPop" -> document("$gte" -> 10000000L))))
      }.collect(Int.MaxValue, Cursor.FailOnError[List[JsObject]]()).
        aka("aggregated") must beLike[List[JsObject]] {
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

      def collect(upTo: Int = Int.MaxValue) =
        collection.aggregateWith[JsObject](batchSize = Some(upTo)) { agg =>
          import agg.{ AvgField, Group, SumField }

          Group(document("state" -> "$state", "city" -> "$city"))(
            "pop" -> SumField("population")
          ) -> List(
              Group(toJson("$_id.state"))("avgCityPop" ->
                AvgField("pop"))
            )
        }.collect(upTo, Cursor.FailOnError[List[JsObject]]())

      "successfully as a single batch" in {
        collect(4) must beTypedEqualTo(expected).await(1, timeout)
      }

      "with cursor" >> {
        "without limit (maxDocs)" in {
          collect() aka "cursor result" must beTypedEqualTo(expected).
            await(1, timeout)
        }

        "with limit (maxDocs)" in {
          collect(2) aka "cursor result" must beTypedEqualTo(expected take 2).
            await(1, timeout)
        }
      }
    }

    "return largest and smallest cities by state" in {
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

      collection.aggregateWith[JsObject]() { agg =>
        import agg.{
          Ascending,
          FirstField,
          Group,
          LastField,
          Project,
          SumField,
          Sort
        }

        Group(document("state" -> "$state", "city" -> "$city"))(
          "pop" -> SumField("population")
        ) -> List(
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
      }.collect(Int.MaxValue, Cursor.FailOnError[List[JsObject]]()).
        aka("results") must beTypedEqualTo(expected).await(1, timeout)
    }

    "return distinct states" in {
      collection.distinct[String, Set]("state").
        aka("states") must beTypedEqualTo(Set("NY", "FR", "JP")).
        await(1, timeout)
    }

    "return a random sample" in {
      collection.aggregateWith[ZipCode]() { agg =>
        agg.Sample(2) -> List.empty
      }.collect(Int.MaxValue, Cursor.FailOnError[List[ZipCode]]()).
        map(_.filter(zipCodes.contains).size).
        aka("aggregated") must beTypedEqualTo(2).await(1, timeout)
    } tag "gt_mongo32"
  }
}
