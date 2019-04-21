package dev.nicu

import akka.Done
import akka.actor.{ActorRef, ActorSystem, CoordinatedShutdown}
import akka.cluster.Cluster
import akka.cluster.sharding.ShardRegion.{
  CurrentShardRegionState,
  GetShardRegionState,
  GracefulShutdown
}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import dev.nicu.untyped.ShardedActor
import dev.nicu.untyped.ShardedActor.Data.StatefulData
import dev.nicu.untyped.ShardedActor.GetData
import spray.json.DefaultJsonProtocol._

import scala.concurrent.Future
import scala.concurrent.duration._

object ShardingHandoverApp extends App {
  private implicit val system: ActorSystem = ActorSystem("akka-handover")

  // Akka Management hosts the HTTP routes used by bootstrap
  AkkaManagement(system).start()

  // Starting the bootstrap process needs to be done explicitly
  ClusterBootstrap(system).start()

  implicit val timeout: Timeout = 1.second

  import system.{dispatcher, log}
  private implicit val materializer: ActorMaterializer = ActorMaterializer()
  private val actor: ActorRef = ShardedActor.startShard()

  CoordinatedShutdown(system)

  system.registerOnTermination({
    log.info("Terminating actor system")
  })

  Http().bindAndHandle(
    pathPrefix("shards" / Segment) { shard =>
      pathPrefix("entities" / Segment) { entity =>
        get {
          complete(getData(shard, entity))
        }
      }
    } ~ pathPrefix("shards") {
      pathEnd {
        put {
          entity(as[StatefulData]) { data =>
            actor ! data
            complete(getData(data.shardId, data.entityId))
          }
        } ~ delete {
          actor ! GracefulShutdown
          complete(StatusCodes.NoContent)
        } ~ get {
          complete(
            (actor ? GetShardRegionState)
              .mapTo[CurrentShardRegionState]
              .flatMap(regionState => {
                Future.traverse(regionState.shards) { shard =>
                  {
                    Future.traverse(shard.entityIds) { entity =>
                      getData(shard.shardId, entity)
                    }
                  }
                }
              }.map(_.flatten)))
        }
      }
    },
    "0.0.0.0",
    8000
  )

  private def getData(shard: String, entity: _root_.java.lang.String) = {
    (actor ? GetData(entity, shard)).mapTo[StatefulData]
  }
}
