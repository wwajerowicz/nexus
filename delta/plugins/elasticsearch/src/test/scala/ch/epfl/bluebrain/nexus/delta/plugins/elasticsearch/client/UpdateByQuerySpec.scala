package ch.epfl.bluebrain.nexus.delta.plugins.elasticsearch.client

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.testkit.TestKit
import ch.epfl.bluebrain.nexus.delta.kernel.RetryStrategyConfig.ExponentialStrategyConfig
import ch.epfl.bluebrain.nexus.delta.rdf.jsonld.context.JsonLdContext.keywords
import ch.epfl.bluebrain.nexus.delta.sdk.http.{HttpClient, HttpClientConfig, HttpClientWorthRetry}
import ch.epfl.bluebrain.nexus.testkit.{CirceLiteral, EitherValuable, IOValues, TestHelpers}
import fs2.Stream
import io.circe.Json
import io.circe.syntax._
import monix.bio.Task
import monix.execution.Scheduler
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._
import scala.util.Random

class UpdateByQuerySpec
    extends TestKit(ActorSystem("UpdateByQuerySpec"))
    with AnyWordSpecLike
    with Matchers
    with CirceLiteral
    with TestHelpers
    with IOValues
    with EitherValuable {
  implicit private val sc: Scheduler                      = Scheduler.global
  private val retry                                       = ExponentialStrategyConfig(500.millis, 10.seconds, 10)
  implicit private val httpClientConfig: HttpClientConfig = HttpClientConfig(retry, HttpClientWorthRetry.onServerError)

  val httpClient = HttpClient()


  val endpoints = (1 to 8).map(i => Uri(s"http://es$i:9200"))


  private val clients   = endpoints.map(new ElasticSearchClient(httpClient, _))

  "An UpdateByQuerySpec" should {
    "ingest data" in {
      val maxConcurrent = sys.env.getOrElse("PARALLEL_REQUESTS", "8").toInt
      val bulkSize = sys.env.getOrElse("BULK_SIZE", "100").toInt
      // TODO: Get the progress from some file
      val progress = 0L

      // multiple of 100.000
      // 60 % reconstructedCells
      // 35 % traces
      // 5 % users
      val multiplier = sys.env.getOrElse("MULTIPLIER", "2").toInt
      val index    = IndexLabel(s"statistics-benchmark-${multiplier}00k").rightValue
      val reconstructedCellsCount = 60000 * multiplier
      val tracesCount = 35000 * multiplier
      val usersCount = 5000 * multiplier

      val reconstructedCellJson = jsonContentOf("/update-query/reconstructed-cell-elasticsearch.json")
      val traceJson = jsonContentOf("/update-query/trace-elasticsearch.json")
      val userJson = jsonContentOf("/update-query/person-elasticsearch.json")
      clients.head.createIndex(index, jsonObjectContentOf("/update-query/mapping.json")).accepted
      val reconstructedCells = Stream
        .range[Task](0, reconstructedCellsCount)
        .map(idx => idx -> reconstructedCellJson.deepMerge(Json.obj(keywords.id -> s"https://bbp.epfl.ch/neurosciencegraph/data/cell/$idx".asJson)))

      val traces = Stream
        .range[Task](reconstructedCellsCount, reconstructedCellsCount + tracesCount)
        .map(idx => idx -> traceJson.deepMerge(Json.obj(keywords.id -> s"https://bbp.epfl.ch/neurosciencegraph/data/trace/$idx".asJson)))

      val users = Stream
        .range[Task](reconstructedCellsCount + tracesCount, reconstructedCellsCount + tracesCount + usersCount)
        .map(idx => idx -> userJson.deepMerge(Json.obj(keywords.id -> s"https://bbp.epfl.ch/nexus/v1/realms/bbp/users/$idx".asJson)))

      val random = new Random()
      val stream = (reconstructedCells ++ traces ++ users).drop(progress).groupWithin(bulkSize, 2.minute).parEvalMap(maxConcurrent) { chunks =>
        val progress = chunks.head.fold(0)(_._1) + chunks.size
        val indexDocuments = chunks.map { case (idx, doc) => ElasticSearchBulk.Index(index, idx.toString, doc) }
        val client = clients(random.nextInt(clients.size))
        client.bulk(indexDocuments.toList) >> Task.pure(progress)
      }.evalTap { progress =>
        Task.delay(
          println(s"Progress: ${100 * progress / (reconstructedCellsCount + tracesCount + usersCount).toDouble}% ($progress)")
        )
        // TODO: Store progress variable in some file
      }
      stream.compile.drain.runSyncUnsafe()
      println("END")

    }
  }

}
