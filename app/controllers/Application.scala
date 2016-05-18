package controllers

import models._
import play.api.data.Form
import play.api.mvc._
import play.modules.reactivemongo._
import play.twirl.api.Html
import reactivemongo.api.collections.default._
import reactivemongo.bson._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.libs.mailer._
import play.api.libs.mailer.MailerPlugin
import play.api.Play.current


object Application extends Controller with MongoController{

  lazy val LoginCollection = db("users")
  trait User
  case class InvalidUser() extends User
  case class LoggedInUser(userid:String,username:String) extends User
  case class NoLoggedInUser() extends User
  case class UserSignUp(username:String, password:String)
  // Shows the login screen and empties the session:

  def generatePage(request:Request[AnyContent],content:Html,authNeeded:Boolean = true): Future[Result] = {
    generatePage(request, () => {Future.successful(content)},authNeeded)
  }

  def generatePage(request:Request[AnyContent],func:(() => Future[Html]), authNeeded:Boolean): Future[Result] = {
    val step1 = for {
      user <- getLoggedInUser(request)
      loginHtml <- getLoginHtmlFuture(request)
    }yield {
        val result = views.html.index(loginHtml)_
        (user -> result)
      }

    for{
      partial <- step1
      content <- func()
    } yield {
      val (user,result) = partial
      if(authNeeded){
        user match {
          case LoggedInUser(userid, username) => Ok(result(content))
          case _ => Ok(result(Html("Authentication Required!!!"))).withNewSession
        }
      }
      else {
        Ok(result(content))
      }
    }
  }

  def index = Action.async {implicit request =>
    generatePage(request,Html(""),false)
  }

  def getLoggedInUser = {request:Request[AnyContent] =>
    request.session.get("bsonid").map{
      bsonid =>
        if(bsonid =="badid"){
          Future successful InvalidUser()
        }
        else{
          LoginCollection.find(BSONDocument("$query" -> BSONDocument("_id" -> BSONObjectID(bsonid)))).
            one[models.User].map {
            user =>
              user match {
                case Some(a) => LoggedInUser(bsonid, a.user)
                case None => InvalidUser()
              }
          }
        }
    }.getOrElse{
      Future successful NoLoggedInUser()
    }
  }

  def getLoginHtmlFuture = { request:Request[AnyContent] =>
    getLoggedInUser(request).map {
      user =>
        user match {
          case InvalidUser() => views.html.badlogin("Invalid Credentials")
          case NoLoggedInUser() => views.html.loginform()
          case LoggedInUser(userid, username) => views.html.authenticated(username)
        }
    }
  }

  def partialIndex(form:Form[models.User]) = {
    val found = LoginCollection.find(BSONDocument(
      "$query" -> BSONDocument()
    )).cursor[models.User]

    found.collect[List]().map{
      f => Ok
    }.recover {
      case e =>
        e.printStackTrace
        BadRequest(e.getMessage)
    }
  }

  def add = Action.async { implicit request =>
    Redirect(routes.Login.logout())
    println("user created")
    User.form.bindFromRequest.fold(
      errors => Future.successful(Redirect(routes.Application.index)),
      user =>
        LoginCollection.insert(user).zip(partialIndex(models.User.form.fill(user))).map(_._2)
    )}
//
  def send(id: String) = Action {
    val email = Email(
      "Simple email",
      "Mister FROM <mysurveydev@gmail.com>",
      Seq("Miss TO <nabirdinani@gmail.com>"),
      bodyText = Some("A text message"),
      bodyHtml = Some("<html><body><p>localhost:9000/ <b>html</b></p></body></html>")
    )
    val id = MailerPlugin.send(email)
    Ok("Email sent!")
  }
}
