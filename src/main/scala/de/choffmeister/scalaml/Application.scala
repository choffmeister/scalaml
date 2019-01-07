package de.choffmeister.scalaml

import java.io.FileWriter
import java.nio.file.Paths
import java.time.{Instant, ZonedDateTime}

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, Framing, Sink, Source}
import akka.stream.{ActorMaterializer, IOResult, Materializer}
import akka.util.ByteString
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits
import scala.util.{Failure, Success, Try}

object Application extends App {
  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val executionContext: ExecutionContext = Implicits.global
  implicit val materializer: Materializer = ActorMaterializer()
  implicit val sttpBackend: SttpBackend[Future, Nothing] = AkkaHttpBackend.usingActorSystem(actorSystem)

  val issuesPath = "data/issues.json"
  val plotPath = "data/plot.png"

//   run(writeIssues("https://jira.mongodb.org"))
  run {
    readIssues()
      .collect { case Success(issue) => issue }
      .map { issue =>
        val key = (issue \ "key").get.as[JsString].value
        val description = (issue \ "fields" \ "description").toOption.filter(_.isInstanceOf[JsString]).map(_.as[String]).getOrElse("")
        val created = ZonedDateTime.parse((issue \ "fields" \ "created").get.as[String].replaceFirst("""(\+|\-)(\d{2})(\d{2})""", """$1$2:$3""")).toInstant
        val resolutionDate = (issue \ "fields" \ "resolutiondate").toOption.filter(_.isInstanceOf[JsString]).map(_.as[String]).map(s => ZonedDateTime.parse(s.replaceFirst("""(\+|\-)(\d{2})(\d{2})""", """$1$2:$3""")).toInstant)
        (key, description.length, resolutionDate.map(rd => rd.getEpochSecond - created.getEpochSecond))
      }
      .collect { case (key, descriptionLength, Some(resolutionTime)) if descriptionLength < 5000 => (key, descriptionLength, resolutionTime) }
      .runWith(Sink.collection)
      .map { items =>
        import breeze.linalg._
        import breeze.plot._
        val f = Figure()
        val p = f.subplot(0)
        val x = DenseVector(items.map(_._2.toLong).toArray)
        val y = DenseVector(items.map(_._3).toArray)
        p += scatter(x, y, _ => 25)
        p.xlabel = "description length"
        p.ylabel = "resolution time"
        f.saveas(plotPath, 600)
      }
  }

  def run(fn: => Future[Any]): Unit = fn.onComplete {
    case Success(_) =>
      System.exit(0)
    case Failure(err) =>
      println(err)
      System.exit(1)
  }

  def readIssues(): Source[Try[JsObject], Future[IOResult]] = {
    FileIO.fromPath(Paths.get(issuesPath))
      .via(Framing.delimiter(ByteString("\n"), 10 * 1024 * 1024, true)
      .map(_.utf8String))
      .map(line => Try(Json.parse(line).as[JsObject]))
  }

  def writeIssues(baseUrl: String): Future[Done] = {
    val writer = new FileWriter(issuesPath)
    val jiraClient = new JiraClient(baseUrl)
    jiraClient
      .searchIssues("")
      .zipWithIndex
      .map { case (issue, index) =>
        println(index)
        writer.write(issue.toString + "\n")
        issue
      }
      .runWith(Sink.ignore)
      .andThen { case _ =>
        writer.close()
      }
  }
}
