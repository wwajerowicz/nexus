package ch.epfl.bluebrain.nexus.delta.plugins.elasticsearch

import ch.epfl.bluebrain.nexus.testkit.TestHelpers
import io.gatling.core.Predef.{atOnceUsers, _}
import io.gatling.http.Predef._

import scala.util.Random

class AggregationSimulation extends Simulation with TestHelpers {

  val httpProtocol = http // 4

  val hosts = (1 to 8).map(i => s"es$i")

  val query = jsonContentOf("/aggregate-query.json").spaces2

  val queryForType = jsonContentOf("/properties-query.json").spaces2

  val random = new Random()

  val types = Seq(
    "http://schema.org/Dataset",
    "http://www.w3.org/ns/prov#Entity",
    "https://neuroshapes.org/NeuronMorphology",
    "https://neuroshapes.org/ReconstructedCell",
    "https://neuroshapes.org/ReconstructedWholeBrainCell",
    "https://neuroshapes.org/Trace",
    "http://schema.org/Person",
    "http://www.w3.org/ns/prov#Agent"
  )
  val multipliers = Seq(1, 2, 4)//, 10, 20, 40, 60)

  val scn= scenario("Queries simulation").foreach(multipliers, "multiplier") {
      repeat(10) {
        val host = hosts(random.nextInt(hosts.size))
        exec(
          http("${multiplier}00k clear cache") // 8
            .post(s"http://$host:9200/statistics-benchmark-$${multiplier}00k/_cache/clear")
        ).exec(
          http("${multiplier}00k query relationships") // 8
            .post(s"http://$host:9200/statistics-benchmark-$${multiplier}00k/_search")
            .body(StringBody(query))
            .asJson
        )
          .foreach(types, "type") {
            exec(
              http("${multiplier}00k query properties ${type}") // 8
                .post(s"http://$host:9200/statistics-benchmark-$${multiplier}00k/_search")
                .body(StringBody(queryForType))
                .asJson
            )
          }
      }
    }

  setUp(                       // 11
    scn.inject(atOnceUsers(1)) // 12
  ).protocols(httpProtocol)
}
