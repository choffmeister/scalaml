package de.choffmeister.scalaml

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.softwaremill.sttp._
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NoStackTrace

final case class JiraError(status: Int, message: String) extends RuntimeException with NoStackTrace {
  override def getMessage: String =
    s"Jira request caused error [${status.intValue()}] with message [$message]"
}

object JiraClientProtocol {
  final case class ErrorResponse(errorMessages: Vector[String], warningMessages: Option[Vector[String]])
  sealed trait Page {
    val startAt: Int
    val maxResults: Int
    val total: Int
  }

  final case class IssuesPage(startAt: Int, maxResults: Int, total: Int, issues: Vector[JsObject]) extends Page

  implicit val ErrorResponseJsonFormat: OFormat[ErrorResponse] = Json.format[ErrorResponse]
  implicit val IssuesPageJsonFormat: OFormat[IssuesPage] = Json.format[IssuesPage]
}

class JiraClient(baseUrl: String)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing]) {
  import JiraClientProtocol._

  def searchIssues(jql: String): Source[JsObject, NotUsed] = {
    paging(uri"$baseUrl/rest/api/2/search?jql=$jql", 100, 0)
      .flatMapConcat(page => Source(page.issues))
  }

  private def paging[T <: Page](uri: Uri, elementsPerPage: Int = 100, startIndex: Int = 0)(
    implicit format: OFormat[T]
  ): Source[T, NotUsed] = {
    Source.unfoldAsync[Option[Int], T](Some(0)) {
      case Some(index) =>
        request()
          .get(uri"$uri&startAt=${startIndex + index * elementsPerPage}&maxResults=$elementsPerPage")
          .mapResponse(body => Json.parse(body).as[T])
          .send()
          .map(JiraClient.parseResponse)
          .map {
            case page if page.total > startIndex + index * elementsPerPage + elementsPerPage =>
              Some((Some(index + 1), page))
            case page =>
              Some((None, page))
          }
      case None =>
        Future.successful(None)
    }
  }

  private def request(): RequestT[Empty, String, Nothing] = sttp
}

object JiraClient {
  import JiraClientProtocol._

  private def parseResponse[T](res: Response[T]): T = res match {
    case Response(Right(t), _, _, _, _) =>
      t
    case Response(Left(bytes), code, _, _, _) =>
      val json = Try(Json.parse(bytes).as[ErrorResponse])
        .getOrElse(ErrorResponse(Vector(new String(bytes, "UTF-8")), None))
      val err =
        JiraError(
          status = code,
          message = (json.errorMessages ++ json.warningMessages.getOrElse(Vector.empty)).mkString(" ")
        )
      throw err
  }
}