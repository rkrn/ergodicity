package com.ergodicity.core.position

import org.scalatest.{BeforeAndAfterAll, WordSpec}
import akka.event.Logging
import akka.actor.ActorSystem
import com.ergodicity.core._
import AkkaConfigurations._
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}

class PositionActorSpec extends TestKit(ActorSystem("PositionActorSpec", ConfigWithDetailedLogging)) with ImplicitSender with WordSpec with BeforeAndAfterAll {
  val log = Logging(system, self)

  override def afterAll() {
    system.shutdown()
  }

  val futureContract = FutureContract(IsinId(111), Isin("RTS-9.12"), ShortIsin("RIU2"), "Future contract")

  "PositionActor" must {

    import PositionActor._

    "initialied in Position.flat state" in {
      val position = TestActorRef(new PositionActor(futureContract))
      assert(position.underlyingActor.position == Position.flat)
    }

    "stay in Position.flat position on update with position = 0" in {
      val position = TestActorRef(new PositionActor(futureContract))
      position ! UpdatePosition(Position.flat, PositionDynamics.empty)
      assert(position.underlyingActor.position == Position.flat)
    }

    "go to Long position on update" in {
      val position = TestActorRef(new PositionActor(futureContract))
      position ! UpdatePosition(Position(10), PositionDynamics(buys = 10))
      assert(position.underlyingActor.position == Position(10))
    }

    "handle position updates" in {
      val position = TestActorRef(new PositionActor(futureContract))
      position ! SubscribePositionUpdates(self)
      expectMsg(CurrentPosition(futureContract, Position.flat, PositionDynamics.empty))

      val data1 = Position(10)
      val dynamics1 = PositionDynamics(buys = 10)
      position ! UpdatePosition(data1, dynamics1)
      assert(position.underlyingActor.position == data1)
      expectMsg(PositionTransition(futureContract, (Position.flat, PositionDynamics.empty), (data1, dynamics1)))

      val data2 = Position(-2)
      val dynamics2 = PositionDynamics(open = 0, buys = 10, sells = 12)
      position ! UpdatePosition(data2, dynamics2)
      assert(position.underlyingActor.position == data2)
      expectMsg(PositionTransition(futureContract, (data1, dynamics1), (data2, dynamics2)))

      val data3 = Position.flat
      val dynamics3 = PositionDynamics(open = 0, buys = 12, sells = 12)
      position ! UpdatePosition(Position.flat, dynamics3)
      assert(position.underlyingActor.position == Position.flat)
      expectMsg(PositionTransition(futureContract, (data2, dynamics2), (data3, dynamics3)))
    }
  }
}
