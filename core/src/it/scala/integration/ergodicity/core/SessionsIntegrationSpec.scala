package integration.ergodicity.core

import java.io.File
import java.util.concurrent.TimeUnit
import akka.actor.{Actor, Props, ActorSystem}
import akka.actor.FSM.{Transition, SubscribeTransitionCallBack}
import akka.util.duration._
import integration.ergodicity.core.AkkaIntegrationConfigurations._
import com.ergodicity.core.Sessions
import org.scalatest.{BeforeAndAfterAll, WordSpec}
import akka.event.Logging
import akka.testkit.{ImplicitSender, TestActorRef, TestFSMRef, TestKit}
import com.ergodicity.cgate.config.ConnectionConfig.Tcp
import com.ergodicity.cgate.Connection.StartMessageProcessing
import com.ergodicity.cgate.config.Replication._
import com.ergodicity.cgate._
import config.{Replication, CGateConfig}
import ru.micexrts.cgate.{P2TypeParser, CGate, Connection => CGConnection, Listener => CGListener}


class SessionsIntegrationSpec extends TestKit(ActorSystem("SessionsIntegrationSpec", ConfigWithDetailedLogging)) with ImplicitSender with WordSpec with BeforeAndAfterAll {
  val log = Logging(system, self)

  val Host = "localhost"
  val Port = 4001

  val RouterConnection = Tcp(Host, Port, system.name)

  override def beforeAll() {
    val props = CGateConfig(new File("cgate/scheme/cgate_dev.ini"), "11111111")
    CGate.open(props())
    P2TypeParser.setCharset("windows-1251")
  }

  override def afterAll() {
    system.shutdown()
    CGate.close()
  }

  "Sessions" must {
    "should work" in {
      val underlyingConnection = new CGConnection(RouterConnection())

      val connection = TestFSMRef(new Connection(underlyingConnection), "Connection")

      val FutInfoDataStream = TestFSMRef(new DataStream, "FutInfoDataStream")
      val OptInfoDataStream = TestFSMRef(new DataStream, "OptInfoDataStream")

      // Listeners
      val futInfoListenerConfig = Replication("FORTS_FUTINFO_REPL", new File("cgate/scheme/fut_info.ini"), "CustReplScheme")
      val underlyingFutInfoListener = new CGListener(underlyingConnection, futInfoListenerConfig(), new DataStreamSubscriber(FutInfoDataStream))
      val futInfoListener = TestFSMRef(new Listener(BindListener(underlyingFutInfoListener) to connection), "FutInfoListener")

      val optInfoListenerConfig = Replication("FORTS_OPTINFO_REPL", new File("cgate/scheme/opt_info.ini"), "CustReplScheme")
      val underlyingOptInfoListener = new CGListener(underlyingConnection, optInfoListenerConfig(), new DataStreamSubscriber(OptInfoDataStream))
      val optInfoListener = TestFSMRef(new Listener(BindListener(underlyingOptInfoListener) to connection), "OptInfoListener")

      val sessions = TestActorRef(new Sessions(FutInfoDataStream, OptInfoDataStream), "Sessions")

      Thread.sleep(1000)

      // On connection Activated open listeners etc
      connection ! SubscribeTransitionCallBack(system.actorOf(Props(new Actor {
        protected def receive = {
          case Transition(_, _, Active) =>
            // Open Listeners in Combined mode
            futInfoListener ! Listener.Open(ReplicationParams(ReplicationMode.Combined))
            optInfoListener ! Listener.Open(ReplicationParams(ReplicationMode.Combined))

            // Process messages
            connection ! StartMessageProcessing(500.millis)
        }
      })))

      // Open connections and track it's status
      connection ! Connection.Open
      connection ! com.ergodicity.cgate.TrackUnderlyingStatus(500.millis)

      Thread.sleep(TimeUnit.DAYS.toMillis(10))
    }
  }
}