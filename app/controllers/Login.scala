package controllers

import play.api.data._
import play.api.data.Forms._
import play.api.data.validation._
import play.api.mvc.{Action,Controller}
import play.modules.reactivemongo._
import play.twirl.api.Html
import reactivemongo.api.collections.default._
import reactivemongo.bson._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

object Login extends Controller with MongoController {

  lazy val LoginCollection = db("users")

  def authenticate = Action.async { implicit request =>
    val referer = request.headers.get("referer").get
    val futLogin = models.User.form.bindFromRequest.fold(
      errors => {
        Future.successful(None)
      },
      login => {
        val futLog = LoginCollection.find(BSONDocument("user" -> login.user, "password" -> login.password)).one
        for {
          log <- futLog
        }yield log match {
          case Some(l) => {
            log match {
              case Some(a) => {
                val user = models.User.UserReader.read(a)
                Some(user.id.get.stringify)
              }
              case None => None
            }
          }
          case None => None
        }
      }
    )
    futLogin.map{
      login => {
        val result = Redirect(referer, null)
        login match {
          case Some(userid) => result.withSession("bsonid" -> userid)
          case None => result.withSession("bsonid" -> "badid")
        }
      }
    }
  }

  def logout = Action { implicit request =>
    val referer = request.headers.get("referer").get
    Redirect(referer,null).withNewSession
  }

  def signup = Action {implicit request =>
    Ok(views.html.signup())
  }

  def tryagain = Action {implicit request =>
    Ok(views.html.loginform())
  }
}
