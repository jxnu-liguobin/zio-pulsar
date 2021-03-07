package examples

import org.apache.pulsar.client.api.PulsarClientException
import zio._
import zio.blocking._
import zio.clock._
import zio.console._
import zio.pulsar._
import zio.stream._

object StreamingExample extends App {

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    app.provideCustomLayer(layer).useNow.exitCode

  val pulsarClient = PulsarClient.live("localhost", 6650)

  val layer = ((Console.live ++ Clock.live)) >+> pulsarClient

  val topic = "my-topic"

  import zio.pulsar.given

  val producer: ZManaged[PulsarClient, PulsarClientException, Unit] = 
    for
      sink   <- Producer.make(topic).map(_.asSink)
      stream =  Stream.fromIterable(0 to 100).map(i => s"Message $i".getBytes())
      _      <- stream.run(sink).toManaged_
    yield ()

  val consumer: ZManaged[PulsarClient with Blocking, PulsarClientException, Unit] =
    for
      builder  <- ConsumerBuilder.make[String].toManaged_
      consumer <- builder
                    .withSubscription(Subscription("my-subscription", SubscriptionType.Exclusive))
                    .withTopic(topic)
                    .build
      _        <- consumer.receiveStream.take(10).foreach { a => 
                    consumer.acknowledge(a.id)
                  }.toManaged_
    yield ()

  val app =
    for
      f <- consumer.fork
      _ <- producer
      _ <- f.join.toManaged_
    yield ()
}
