package ch.epfl.bluebrain.nexus.delta.sdk.error

import ch.epfl.bluebrain.nexus.delta.rdf.Vocabulary.contexts
import ch.epfl.bluebrain.nexus.delta.rdf.jsonld.JsonLdEncoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.{Encoder, Json}

import scala.annotation.nowarn

/**
  * Top level error type that represents general errors
  */
sealed abstract class ServiceError(val reason: String) extends SDKError {

  override def getMessage: String = reason
}

object ServiceError {

  /**
    * Signals that the requested resource was not found
    */
  final case object NotFound extends ServiceError("The requested resource could not be found.")

  @nowarn("cat=unused")
  implicit val serviceErrorEncoder: Encoder.AsObject[ServiceError] = {
    implicit val configuration: Configuration = Configuration.default.withDiscriminator("@type")
    val enc                                   = deriveConfiguredEncoder[ServiceError]
    Encoder.AsObject.instance[ServiceError] { r =>
      enc.encodeObject(r).+:("reason" -> Json.fromString(r.reason))
    }
  }

  implicit val serviceErrorJsonLdEncoder: JsonLdEncoder[ServiceError] =
    JsonLdEncoder.compactFromCirce(contexts.error)
}