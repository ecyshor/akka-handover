package dev.nicu.untyped

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import dev.nicu.untyped.ShardedActor.Data.StatefulData
import dev.nicu.untyped.ShardedActor.GetData
import org.scalatest.FlatSpecLike

class ShardedActorTest
    extends TestKit(ActorSystem())
    with FlatSpecLike
    with ImplicitSender {

  private val actor = ShardedActor.startShard()

  private val entityId = "some-entity"
  private val shardId = "1"

  "Sharded FSM" should "start with handover data" in {
    val data = StatefulData("preloaded", entityId, shardId)
    actor ! data
    actor ! GetData(entityId, shardId)
    expectMsg(data)
  }

  it should "use default data when not receiving the handover during the timoeut" in {
    val entity = "other-entity"
    actor ! GetData(entity, shardId)
    expectMsg(StatefulData("timeout-loaded", entity, shardId))
  }

}
