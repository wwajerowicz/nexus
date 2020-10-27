package ch.epfl.bluebrain.nexus.delta.rdf

import java.util.UUID

import ch.epfl.bluebrain.nexus.delta.rdf.IriOrBNode.Iri.unsafe
import ch.epfl.bluebrain.nexus.delta.rdf.IriOrBNode.{BNode, Iri}
import io.circe.{Decoder, Encoder}
import org.apache.jena.iri.{IRI, IRIFactory}

/**
  * Represents an [[Iri]] or a [[BNode]]
  */
sealed trait IriOrBNode extends Product with Serializable {

  /**
    * @return true if the current value is an [[Iri]], false otherwise
    */
  def isIri: Boolean

  /**
    * @return true if the current value is an [[BNode]], false otherwise
    */
  def isBNode: Boolean

  /**
    * @return Some(iri) if the current value is an [[Iri]], None otherwise
    */
  def asIri: Option[Iri]

  /**
    * @return Some(bnode) if the current value is a [[BNode]], None otherwise
    */
  def asBNode: Option[BNode]

  /**
    * The rdf string representation of the [[Iri]] or [[BNode]]
    */
  def rdfFormat: String
}

object IriOrBNode {

  /**
    * A simple [[Iri]] representation backed up by Jena [[IRI]].
    *
    * @param value the underlying Jena [[IRI]]
    */
  final case class Iri private (private val value: IRI) extends IriOrBNode {

    /**
      * Is valid according tot he IRI rfc
      *
      * @param includeWarnings If true then warnings are reported as well as errors.
      */
    def isValid(includeWarnings: Boolean): Boolean =
      value.hasViolation(includeWarnings)

    /**
      * Does this Iri specify a scheme.
      *
      * @return true if this IRI has a scheme specified, false otherwise
      */
    def isAbsolute: Boolean =
      value.isAbsolute

    /**
      * Is this Iri a relative reference without a scheme specified.
      *
      * @return true if the Iri is a relative reference, false otherwise
      */
    def isRelative: Boolean =
      value.isRelative

    /**
      * @return true if the current ''iri'' starts with the passed ''other'' iri, false otherwise
      */
    def startsWith(other: Iri): Boolean =
      toString.startsWith(other.toString)

    /**
      * @return the resulting string from stripping the passed ''iri'' to the current iri.
      */
    def stripPrefix(iri: Iri): String =
      stripPrefix(iri.toString)

    /**
      * @return the resulting string from stripping the passed ''prefix'' to the current iri.
      */
    def stripPrefix(prefix: String): String =
      toString.stripPrefix(prefix)

    /**
      * An Iri is a prefix mapping if it ends with `/` or `#`
      */
    def isPrefixMapping: Boolean =
      toString.endsWith("/") || toString.endsWith("#")

    /**
      * @return true if the Iri is empty, false otherwise
      */
    def isEmpty: Boolean =
      toString.isEmpty

    /**
      * @return true if the Iri is not empty, false otherwise
      */
    def nonEmpty: Boolean =
      toString.nonEmpty

    /**
      * Adds a segment to the end of the Iri
      */
    def /(segment: String): Iri = {
      lazy val segmentStartsWithSlash = segment.startsWith("/")
      lazy val iriEndsWithSlash       = toString.endsWith("/")
      if (iriEndsWithSlash && segmentStartsWithSlash)
        unsafe(s"$value${segment.drop(1)}")
      else if (iriEndsWithSlash || segmentStartsWithSlash)
        unsafe(s"$value$segment")
      else unsafe(s"$value/$segment")
    }

    override def toString: String = value.toString

    override val rdfFormat: String = s"<$toString>"

    override val isIri: Boolean = true

    override val isBNode: Boolean = false

    override val asIri: Option[Iri] = Some(this)

    override val asBNode: Option[BNode] = None
  }

  object Iri {

    private val iriFactory = IRIFactory.iriImplementation()

    /**
      * Construct an [[Iri]] safely.
      *
      * @param string the string from which to construct an [[Iri]]
      */
    def apply(string: String): Either[String, Iri] = {
      val iri = unsafe(string)
      Option.when(!iri.isValid(includeWarnings = false))(iri).toRight(s"'$string' is not an IRI")
    }

    /**
      * Construct an absolute [[Iri]] safely.
      *
      * @param string the string from which to construct an [[Iri]]
      */
    def absolute(string: String): Either[String, Iri] =
      apply(string).flatMap(iri => Option.when(iri.isAbsolute)(iri).toRight(s"'$string' is not an absolute IRI"))

    /**
      * Construct an IRI without checking the validity of the format.
      */
    def unsafe(string: String): Iri =
      new Iri(iriFactory.create(string))

    implicit final val iriDecoder: Decoder[Iri] = Decoder.decodeString.emap(apply)
    implicit final val iriEncoder: Encoder[Iri] = Encoder.encodeString.contramap(_.toString)

  }

  /**
    * A [[BNode]] representation holding its label value
    */
  final case class BNode private (value: String) extends IriOrBNode {

    override def toString: String = value

    override val rdfFormat: String = s"_:B$toString"

    override val isIri: Boolean = false

    override val isBNode: Boolean = true

    override val asIri: Option[Iri] = None

    override val asBNode: Option[BNode] = Some(this)
  }

  object BNode {

    /**
      * Creates a random blank node
      */
    def random: BNode = BNode(UUID.randomUUID().toString.replaceAll("-", ""))

    /**
      * Unsafely creates a [[BNode]]
      *
      * @param anonId the string value of the bnode
      */
    def unsafe(anonId: String): BNode =
      BNode(anonId)

    implicit final val bNodeDecoder: Decoder[BNode] = Decoder.decodeString.map(BNode.apply)
    implicit final val bNodeEncoder: Encoder[BNode] = Encoder.encodeString.contramap(_.toString)
  }

  implicit final val iriOrBNodeDecoder: Decoder[IriOrBNode] =
    Decoder.decodeString.emap(Iri.absolute) or Decoder.decodeString.map(BNode.unsafe)

  implicit final val iriOrBNodeEncoder: Encoder[IriOrBNode] =
    Encoder.encodeString.contramap(_.toString)

}