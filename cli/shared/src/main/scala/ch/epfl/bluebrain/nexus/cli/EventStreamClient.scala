package ch.epfl.bluebrain.nexus.cli

import cats.data.EitherT
import cats.data.EitherT._
import cats.effect.Concurrent
import ch.epfl.bluebrain.nexus.cli.ClientError.SerializationError
import ch.epfl.bluebrain.nexus.cli.config.{NexusConfig, NexusEndpoints}
import ch.epfl.bluebrain.nexus.cli.types.{Event, EventEnvelope, Label, Offset}
import fs2.Stream
import org.http4s.ServerSentEvent.EventId
import org.http4s._
import org.http4s.client.Client
import org.http4s.headers._

trait EventStreamClient[F[_]] {

  /**
    * Fetch the event stream for all Nexus resources.
    *
    * @param lastEventId the optional starting event offset
    */
  def apply(lastEventId: Option[Offset]): Stream[F, EventEnvelope]

  /**
    * Fetch the event stream for all Nexus resources in the passed ''organization''.
    *
    * @param organization the organization label
    * @param lastEventId the optional starting event offset
    */
  def apply(organization: Label, lastEventId: Option[Offset]): Stream[F, EventEnvelope]

  /**
    * Fetch the event stream for all Nexus resources in the passed ''organization'' and ''project''.
    *
    * @param organization the organization label
    * @param lastEventId the optional starting event offset
    */
  def apply(organization: Label, project: Label, lastEventId: Option[Offset]): Stream[F, EventEnvelope]
}

object EventStreamClient {

  /**
    * Construct an [[EventStreamClient]] to read the SSE from Nexus.
    *
    * @param client        the underlying HTTP client
    * @param projectClient the project client to convert UUIDs into Labels
    * @param config        the Nexus configuration
    * @tparam F the effect type
    */
  final def apply[F[_]](
      client: Client[F],
      projectClient: ProjectClient[F],
      config: NexusConfig
  )(implicit F: Concurrent[F]): EventStreamClient[F] = new EventStreamClient[F] {

    private val endpoints = NexusEndpoints(config)

    private def buildStream(uri: Uri, lastEventId: Option[Offset]): Stream[F, EventEnvelope] = {
      val lastEventIdH = lastEventId.map[Header](id => `Last-Event-Id`(EventId(id.asString)))
      val req          = Request[F](uri = uri, headers = Headers(lastEventIdH.toList ++ config.authorizationHeader.toList))
      client
        .stream(req)
        .flatMap { resp =>
          resp.body.through(ServerSentEvent.decoder[F])
        }
        .mapAsync(1) { sse =>
          (for {
            offset <- fromEither[F](sse.id.flatMap(v => Offset(v.value)).toRight(SerializationError("Missing offset")))
            event  <- EitherT(Event(sse, projectClient))
          } yield EventEnvelope(offset, event)).value
        }
        // TODO: log errors
        .collect { case Right(event) => event }
    }

    def apply(lastEventId: Option[Offset]): Stream[F, EventEnvelope] =
      buildStream(endpoints.eventsUri, lastEventId)

    def apply(organization: Label, lastEventId: Option[Offset]): Stream[F, EventEnvelope] =
      buildStream(endpoints.eventsUri(organization), lastEventId)

    def apply(organization: Label, project: Label, lastEventId: Option[Offset]): Stream[F, EventEnvelope] =
      buildStream(endpoints.eventsUri(organization, project), lastEventId)

  }
}