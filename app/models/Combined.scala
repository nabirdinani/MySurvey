package models

/**
 * Created by NabirDinani on 8/14/15.
 */

case class Combined (responsesurveyid:String,
                      surveyid:String,
                     title:String,
                     createDate:java.util.Date,
                     sent:Int,
                     received:Int) {
  def incSent = Combined(responsesurveyid,surveyid,title,createDate,sent+1,received)
  def incReceived = Combined(responsesurveyid,surveyid,title,createDate,sent,received+1)
}



