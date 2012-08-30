package com.ergodicity.engine.strategy

import akka.actor.{ActorLogging, Props, Actor}
import com.ergodicity.core.Isin
import com.ergodicity.engine.Services.ServiceResolver
import com.ergodicity.engine.StrategyEngine

object CloseAllPositions {

  implicit case object CloseAllPositions extends StrategyId

  def apply() = new StrategiesFactory {
    def apply() = Strategy(Props(new CloseAllPositions)) :: Nil
  }
}

class CloseAllPositions extends Actor with ActorLogging {

  override def preStart() {
    log.info("Started CloseAllPositions")
  }

  protected def receive = null
}