package controllers

/**
 * Created by Anish on 4/28/2015.
 */

import controllers.Application._
import play.api.data.{Forms, Form}
import play.api.libs.mailer.{MailerPlugin, Email}
import play.api.mvc._
import play.modules.reactivemongo._
import play.twirl.api.Html
import reactivemongo.api.collections.default._
import reactivemongo.bson._
import play.api.Play.current

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.data.Forms._

object Survey extends Controller with MongoController{
  lazy val SurveyCollection = db("surveys")
  lazy val ResponseCollection = db("responses")
  trait Survey

  def partialIndex(form:Form[models.Survey]) = {
    val found = SurveyCollection.find(BSONDocument(
      "$query" -> BSONDocument()
    )).cursor[models.Survey]
    found.collect[List]().map{
      f => Ok
    }.recover {
      case e =>
        e.printStackTrace
        BadRequest(e.getMessage)
    }
  }

  def add = Action.async { implicit request =>
    models.Survey.form.bindFromRequest.fold(
      errors => {
        Future.successful(Redirect(routes.Application.index))},
      survey => {
        //generate a new bson object id
        //val q = BSONObjectID("_id" -> models.Survey.fldId)
        //create a new survey object from the contents of the old one but with new survey id
        Application.generatePage(request,views.html.loginform())
        SurveyCollection.insert(survey).zip(partialIndex(models.Survey.form.fill(survey))).map(_._2)
        //do links.insert with the new survey object id
      }
    )
  }

  def sendSurvey(surveyid:String) = Action.async{ implicit request =>
    val form = Form(single("emails" -> text))
    val emails = form.bindFromRequest().get
    val lstEmails = emails.split(",")
    val count = lstEmails.size
    println(count)
    println("List of emails: " + emails)
    SurveyCollection.find(BSONDocument("_id" -> BSONObjectID(surveyid))).one[models.Survey].map{
      optSurvey => {
        optSurvey match {
          case Some(survey) =>
            val answer = lstEmails.foldLeft(""){
              (result,email) =>
                val responseId = BSONObjectID.generate
                val response = survey.createResponse(responseId)
                ResponseCollection.insert(response).map{
                 a =>
                  val email_bucket = Email(
                    "Simple email",
                    "Mister FROM <mysurveydev@gmail.com>",
                    Seq(email),
                    //      attachments = Seq(
                    //        AttachmentFile("favicon.png", new File(current.classloader.getResource("public/images/favicon.png").getPath)),
                    //        AttachmentData("data.txt", "data".getBytes, "text/plain", Some("Simple data"), Some(EmailAttachment.INLINE))
                    //      ),
                    bodyText = Some("A text message"),
                    bodyHtml = Some("<html><body><p>http://localhost:9000/" +responseId.stringify+ "</p></body></html>")
                  )
                  val id = MailerPlugin.send(email_bucket)
                }
                result.concat(email).concat(responseId.stringify)

            }
            play.api.Logger.debug(answer)
            Ok(Html(answer))
          case None => BadRequest
        }
      }
    }

    Future.successful(Ok)
  }

  def newSurvey = Action.async { implicit request =>

    Application.generatePage(request, views.html.newsurvey())
  }

  def index = Action.async { implicit request =>

    // We need to show for each survey, it's title, how many emails sent, and how many users responded back.
    // We start buy creating an initial List of Combined based on our surveys from the DB.

    // Grab all surveys and return 1 Combined object for every Survey object initialized with sent and received as 0
    val futCombines = SurveyCollection.find(BSONDocument()).cursor[models.Survey].collect[List]().map {
      lstSurveys =>
        // Here lstSurveys is the list of surveys from db. we are using map to "Map from Survey to Combined" and setting
        // initial values for sent and received to 0
        // In this case, the return value will be Future [ List [ Combined ] ] instead of F[L[Survey]] due to the map
        lstSurveys map {
          survey =>
            models.Combined(models.Response.fldSurveyId,survey.id.get.stringify,survey.title,survey.createDate.get,0,0) //This is the return value
        }
    }

    // No biggie - just getting a future with List of Responses
    val futResponses = ResponseCollection.find(BSONDocument()).cursor[models.Response].collect[List]()

    // For comprehension -- allows us to provide instructions on what to do when the futures are done executing and
    // have actual results. combines is List[Combined] is result from futCombined above which was a Future
    // response is List[Response] from futResponses
    // Result of a for comprehension of Futures is itself a Future since we are providing instructions for task
    // that is done in the future when the incoming futures (futCombines, futResponses) have completed and have results.
    // In this case, we want the result to be Future[List[Combined]] which we can then use in the next step
    val futCombined:Future[List[models.Combined]] = for {
      combines <- futCombines     // combines:List[Combined] is placeholder for result from futCombined
      responses <- futResponses   // responses:List[Response] is placeholder for result from futResponses
    } yield {

        // Iterate over responses from right to left, start with combines as seed
        // remember, this combines has sent and received both 0 for all surveys
        responses.foldLeft(combines){
              // lstCombines is placeholder for seed (List[Combined])
              // response is placeholder for Response in each iteration
              // lstCombines may be modified in each iteration and is passed to the following iteration
              // lstCombined with all its modifications is returned as a result of foldLeft operation
          (lstCombines,response) =>

            // Map - For each item in lstCombines, we could create a new Combined
            lstCombines.map{
              combined =>
                // If a particular combined surveyid matched with the response, then we know an email was sent
                // If for that response, we have response.sent == true, then we know, a user responded
                if(combined.surveyid == response.surveyid.stringify)
                {
                  // Response == Email sent to user
                  // Response.sent == User submitted Survey Response
                  // Combined.sent == Email Sent
                  // Combined.received == Received response from User
                  if(response.sent)
                    // User responded to this email, create new Combined with both sent and received incremented
                    combined.incSent.incReceived
                  else
                    // User did not respond to this email yet. Create new Combined with only received incremented
                    combined.incSent
                }
                else {
                  // Response doesn't belong to this Combined. Return as is.
                  combined
                }
            }
        }
    }

    // Here we are using "Map" comprehension on Futures, which like "for" above is used to give instructions on what to
    // do when the future completes. In this case, "combines" is a List[Combined] which is a result from futCombined
    val surveyhtmlfut = futCombined.map{
      combines =>
        // combines which is List[Combined] is our model for survey.scala.html page
        views.html.survey(combines)
    }

    val futauthpage = for{
      user <- Application.getLoggedInUser(request)
      surveypage <- surveyhtmlfut
    //page <- Application.generatePage(request,surveypage,false)
    } yield {
        user match {
          case Application.LoggedInUser(userid,username) => views.html.aggregator(surveypage)(views.html.confirmation())
          case _ => surveypage
        }
      }

    for {
      authorpage <- futauthpage
      page <- Application.generatePage(request,authorpage,false)
    } yield {
      page
    }
    //Ok(views.html.author(List()))
  }




//  def get(authorid: String) = Action.async { implicit request =>
//    val authorhtmlfut = SurveyCollection.find(BSONDocument("_id" -> BSONObjectID(authorid))).cursor[models.Survey].collect[List]().map{
//      list =>
//        views.html.usersurvey(list)
//    }
//
//    for{
//      authorpage <- authorhtmlfut
//      page <- Application.generatePage(request,authorpage)
//    } yield page
//
//  }
}
