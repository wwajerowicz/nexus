package ch.epfl.bluebrain.nexus.delta.plugins.elasticsearch.routes

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.delta.plugins.elasticsearch.model.ElasticSearchViewRejection.{ViewIsDeprecated, ViewNotFound}
import ch.epfl.bluebrain.nexus.delta.plugins.elasticsearch.model.{ElasticSearchViewRejection, ResourcesSearchParams}
import ch.epfl.bluebrain.nexus.delta.plugins.elasticsearch.{ElasticSearchViews, ElasticSearchViewsQuery}
import ch.epfl.bluebrain.nexus.delta.rdf.Vocabulary.nxv
import ch.epfl.bluebrain.nexus.delta.sdk.model.identities.Caller
import ch.epfl.bluebrain.nexus.delta.sdk.model.projects.ProjectRef
import ch.epfl.bluebrain.nexus.delta.sdk.model.search.Pagination.FromPagination
import ch.epfl.bluebrain.nexus.delta.sdk.model.search.{Pagination, SearchResults, SortList}
import ch.epfl.bluebrain.nexus.delta.sdk.model.{BaseUri, IdSegment}
import ch.epfl.bluebrain.nexus.testkit.CirceLiteral
import io.circe.syntax._
import io.circe.{Json, JsonObject}
import monix.bio.IO

private[routes] class DummyElasticSearchViewsQuery(views: ElasticSearchViews)
    extends ElasticSearchViewsQuery
    with CirceLiteral {

  private def toJsonObject(value: Map[String, String]) =
    JsonObject.fromMap(value.map { case (k, v) => k -> v.asJson })

  private val allowedPage                              = FromPagination(0, 5)
  private val allowedSearchParams                      = ResourcesSearchParams(q = Some("something"))
  private val defaultCtx                               = jobj"""{"@context": {"@vocab": "http://localhost/"}}"""

  override def list(
      project: ProjectRef,
      pagination: Pagination,
      params: ResourcesSearchParams,
      sort: SortList
  )(implicit caller: Caller, baseUri: BaseUri): IO[ElasticSearchViewRejection, SearchResults[JsonObject]] =
    if (pagination == allowedPage && params == allowedSearchParams)
      IO.pure(
        SearchResults(
          1,
          List(jobj"""{"project": "$project"}"""" deepMerge defaultCtx)
        )
      )
    else
      IO.raiseError(ViewNotFound(nxv + "id", project))

  override def list(
      project: ProjectRef,
      schema: IdSegment,
      pagination: Pagination,
      params: ResourcesSearchParams,
      sort: SortList
  )(implicit caller: Caller, baseUri: BaseUri): IO[ElasticSearchViewRejection, SearchResults[JsonObject]] =
    if (pagination == allowedPage && params == allowedSearchParams) {
      IO.pure(
        SearchResults(
          1,
          List(
            JsonObject(
              "project" -> project.toString.asJson,
              "schema"  -> schema.asString.asJson
            ) deepMerge defaultCtx
          )
        )
      )
    } else
      IO.raiseError(ViewNotFound(nxv + "id", project))

  override def query(
      id: IdSegment,
      project: ProjectRef,
      query: JsonObject,
      qp: Uri.Query
  )(implicit caller: Caller): IO[ElasticSearchViewRejection, Json] = {
    for {
      view <- views.fetch(id, project)
      _    <- IO.raiseWhen(view.deprecated)(ViewIsDeprecated(view.id))
    } yield json"""{"id": "$id", "project": "$project"}""" deepMerge toJsonObject(
      qp.toMap
    ).asJson deepMerge query.asJson
  }
}
