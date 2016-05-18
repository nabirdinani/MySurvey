package models

import akka.actor.FSM.->
import play.api.data.validation.Constraints._
import play.api.data.format.Formats._
import reactivemongo.bson.{BSONDocumentReader, BSONDocument, BSONDocumentWriter, BSONObjectID}
import play.api.data.{Form, Mapping}
import play.api.data.Forms._
import reactivemongo.bson._

case class User(id:Option[BSONObjectID], user:String,password:String)

object User{
  val fldId = "_id"
  val fldName = "user"
  val fldPassword = "password"

  implicit object UserWriter extends BSONDocumentWriter[User]{
    def write(user:User):BSONDocument = BSONDocument(
      fldId -> user.id.getOrElse(BSONObjectID.generate),
      fldName -> user.user,
      fldPassword -> user.password
    )
  }

  implicit object UserReader extends BSONDocumentReader[User]{
    def read(doc:BSONDocument):User = User(
      doc.getAs[BSONObjectID](fldId),
      doc.getAs[String](fldName).getOrElse(""),
      doc.getAs[String](fldPassword).getOrElse("")
    )
  }

  val form = Form(
    mapping(
      fldId -> optional(of[String] verifying pattern(
        """[a-fA-F0-9]{24}""".r,
        "constraint.objectId",
        "error.objectId")),
      fldName -> nonEmptyText,
      fldPassword -> nonEmptyText){ (id,user,password) =>
      User(
        id.map(BSONObjectID(_)) ,user,password
      )
    }{user => Some(user.id.map(_.stringify), user.user, user.password)}
  )
}