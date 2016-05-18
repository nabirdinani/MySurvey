package controllers

/**
 * Created by Anish on 4/28/2015.
 */

import controllers.Survey._
import org.joda.time.DateTime
import play.api.data.{Forms, Form}
import play.api.data.Forms._
import play.api.libs.mailer.{MailerPlugin, Email}
import play.api.mvc._
import play.modules.reactivemongo._
import play.twirl.api.Html
import reactivemongo.api.collections.default._
import reactivemongo.bson._
import play.api.Play.current

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Response extends Controller with MongoController{
  lazy val ResponseCollection = db("responses")
  lazy val SurveyCollection = db("surveys")
  trait Response

  def partialIndex(form:Form[models.Response]) = {
    val found = ResponseCollection.find(BSONDocument(
      "$query" -> BSONDocument()
    )).cursor[models.Response]
    found.collect[List]().map{
      f => Ok
    }.recover {
      case e =>
        e.printStackTrace
        BadRequest(e.getMessage)
    }
  }

  def addResponse(responseid:String) = Action.async{ implicit request =>
    val form = Form(tuple("answers" -> list(text), "submit" -> boolean))
    val fromRequest = form.bindFromRequest()
    val receivedResponses = fromRequest.get._1
    val submit = fromRequest.get._2
    ResponseCollection.find(BSONDocument("_id" -> BSONObjectID(responseid))).one[models.Response].map{
      optResponse => {
        optResponse match {
          case Some(response) =>
            val ans = BSONDocument(
              "$set" -> BSONDocument(
                "finishdate" -> BSONDateTime(new DateTime().getMillis),
                "answers" -> receivedResponses,
                "sent" -> submit))
            val selector = BSONDocument("_id" -> BSONObjectID(responseid))
            ResponseCollection.update(selector, ans)
            Ok//Application.generatePage(request, views.html.confirmation())
          case None => BadRequest
        }
      }
    }
  }

  def saveResponse(responseid:String) = Action.async{ implicit request =>
    val form = Form("answers" -> list(text))
    val receivedResponses = form.bindFromRequest().get
    ResponseCollection.find(BSONDocument("_id" -> BSONObjectID(responseid))).one[models.Response].map{
      optResponse => {
        optResponse match {
          case Some(response) =>
            val ans = BSONDocument(
              "$set" -> BSONDocument(
                "answers" -> receivedResponses))
            val selector = BSONDocument("_id" -> BSONObjectID(responseid))
            ResponseCollection.update(selector, ans)
          case None => BadRequest
        }
      }
    }
    Future.successful(Ok)
  }

  def index = Action.async { implicit request =>

    val authorhtmlfut = ResponseCollection.find(BSONDocument()).cursor[models.Response].collect[List]().map{
      list =>
        views.html.response(list)
    }
    val futauthpage = for{
      user <- Application.getLoggedInUser(request)
      authorpage <- authorhtmlfut
    } yield {
        user match {
          case Application.LoggedInUser(userid,username) => views.html.aggregator(authorpage)(views.html.confirmation())
          case _ => authorpage
        }
      }
    for {
      authorpage <- futauthpage
      page <- Application.generatePage(request,authorpage,false)
    } yield {
      page
    }
  }

  def getResponse(responseid: String) = Action.async { implicit request =>
    val authorhtmlfut = ResponseCollection.find(BSONDocument("_id" -> BSONObjectID(responseid))).cursor[models.Response].collect[List]().map{
      list =>
        views.html.usersurvey(list)
    }

    for{
      authorpage <- authorhtmlfut
      page <- Application.generatePage(request,authorpage)
    } yield page

  }
  def confirmation(responsid: String) = Action.async { implicit request =>
    Application.generatePage(request, views.html.confirmation())
  }
}