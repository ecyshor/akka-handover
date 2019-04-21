package dev.nicu.untyped

import akka.actor.{ActorRef, ActorSystem, FSM, Props, Stash}
import akka.cluster.sharding.ShardCoordinator.LeastShardAllocationStrategy
import akka.cluster.sharding.ShardRegion.GracefulShutdown
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import dev.nicu.untyped.ShardedActor.Data.{NoData, StatefulData}
import dev.nicu.untyped.ShardedActor.State.{Loaded, NotInitialized}
import dev.nicu.untyped.ShardedActor._
import spray.json._
import DefaultJsonProtocol._
import scala.concurrent.duration._

class ShardedActor extends FSM[State, Data] with Stash {

  startWith(NotInitialized, NoData)
  private val timeoutTimer = "receiveHandoverTimeout"

  when(NotInitialized) {
    case Event(data: StatefulData, NoData) =>
      log.info(s"Received handover data $data")
      cancelTimer(timeoutTimer)
      goto(Loaded) using data
    case Event(element: ShardedMessage, NoData) =>
      stash()
      if (!isTimerActive(timeoutTimer)) {
        log.info("Setting timer for handover data receive.")
        setTimer(timeoutTimer, InitTimeout(element), 2.seconds)
      }
      stay()
    case Event(InitTimeout(message), NoData) =>
      log.info(
        "Did not receive handover data in the expected time, using default data.")
      goto(Loaded) using StatefulData("timeout-loaded",
                                      message.entityId,
                                      message.shardId)
  }

  when(Loaded) {
    case Event(receivedData: StatefulData, _: StatefulData) =>
      log.warning(s"Got data $receivedData after the timeout.")
      stay()
    case Event(_: GetData, statefulData: StatefulData) =>
      sender() ! statefulData
      stay()
    case Event(GracefulShutdown, data: StatefulData) =>
      log.info(s"Shutting down entity with $data. Sending handover.")
      ClusterSharding(context.system)
        .shardRegion(ShardedActor.ShardingTypeName) ! data
      stop()
  }

  onTransition {
    case NotInitialized -> Loaded =>
      log.info("Loaded data, unstashing previous messages.")
      unstashAll()
  }

  initialize()
}

object ShardedActor {

  def startShard()(implicit system: ActorSystem): ActorRef = {
    val settings = ClusterShardingSettings(system)
    val allocationStrategy = new LeastShardAllocationStrategy(
      settings.tuningParameters.leastShardAllocationRebalanceThreshold,
      settings.tuningParameters.leastShardAllocationMaxSimultaneousRebalance
    )
    ClusterSharding(system).start(
      typeName = ShardingTypeName,
      entityProps = Props[ShardedActor],
      settings = ClusterShardingSettings(system),
      extractEntityId = {
        case msg: ShardedMessage => (msg.entityId, msg)
      },
      extractShardId = {
        case msg: ShardedMessage => msg.shardId.toString
      },
      allocationStrategy,
      GracefulShutdown
    )
  }

  val ShardingTypeName = "ExampleSharding"
  case class InitTimeout(message: ShardedMessage)
  trait ShardedMessage {
    val entityId: String
    val shardId: String
  }

  case class GetData(entityId: String, shardId: String) extends ShardedMessage
  sealed trait State
  object State {
    case object NotInitialized extends State
    case object Loaded extends State
  }
  sealed trait Data
  object Data {
    case object NoData extends Data
    case class StatefulData(importantData: String,
                            entityId: String,
                            shardId: String)
        extends Data
        with ShardedMessage
    object StatefulData {
      implicit val format: RootJsonFormat[StatefulData] = jsonFormat3(
        StatefulData.apply)
    }
  }
}
