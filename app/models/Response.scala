package models

/**
 * Created by Anish on 4/28/2015.
 */

import play.api.data._
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data.validation.Constraints._
import reactivemongo.bson.{BSONDateTime, BSONObjectID, BSONDocumentWriter, BSONDocumentReader, BSONDocument}
import models.BSONProducers._

case class Response (id:BSONObjectID,
                     surveyid:BSONObjectID,
                     finishDate:Option[java.util.Date],
                     title:String,
                     questions:List[String],
                     answers:List[String],
                     sent:Boolean)
object Response{
  val fldId = "_id"
  val fldSurveyId = "surveyid"
  val fldFinishDate = "finishdate"
  val fldTitle = "title"
  val fldQuestions = "questions"
  val fldAnswers = "answers"
  val fldSent = "sent"

  implicit object ResponseWriter extends BSONDocumentWriter[Response]{
    def write(response:Response):BSONDocument = BSONDocument(
      fldId -> response.id,
      fldSurveyId -> response.surveyid,
      fldFinishDate -> response.finishDate.getOrElse(new java.util.Date()),
      fldTitle -> response.title,
      fldQuestions -> response.questions,
      fldAnswers -> response.answers,
      fldSent -> response.sent
    )
  }

  implicit object ResponseReader extends BSONDocumentReader[Response]{
    def read(doc:BSONDocument):Response = Response(
      doc.getAs[BSONObjectID](fldId).get,
      doc.getAs[BSONObjectID](fldSurveyId).get,
      doc.getAs[java.util.Date](fldFinishDate),
      //doc.getAs[java.util.Date](fldSentDate),
      doc.getAs[String](fldTitle).get,
      doc.getAs[List[String]](fldQuestions).getOrElse(List()),
      doc.getAs[List[String]](fldAnswers).getOrElse(List()),
      doc.getAs[Boolean](fldSent).get
    )
  }
  val form = Form(
    mapping(
      //fldId -> nonEmptyText,
      fldAnswers -> list(text)
    )
    { (answers) => answers
    }
    {
      response => Some(response)
    }
  )
}
