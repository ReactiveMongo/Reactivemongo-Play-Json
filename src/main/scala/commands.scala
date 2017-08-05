/*
 * Copyright 2012-2013 Stephane Godbillon (@sgodbillon)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package reactivemongo.play.json.commands

import play.api.libs.json.{
  Json,
  JsError,
  JsNumber,
  JsObject,
  JsResult,
  JsSuccess,
  JsValue,
  OWrites,
  Reads
}

import reactivemongo.play.json.ValidationError

import reactivemongo.api.ReadConcern
import reactivemongo.api.commands.{ CommandError, UnitBox }

object CommonImplicits {
  implicit object UnitBoxReader
      extends DealingWithGenericCommandErrorsReader[UnitBox.type] {
    def readResult(doc: JsObject): UnitBox.type = UnitBox
  }

  implicit object ReadConcernWriter extends OWrites[ReadConcern] {
    def writes(concern: ReadConcern) = Json.obj("level" -> concern.level)
  }
}

@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
trait JSONCommandError extends CommandError {
  def originalDocument: JsObject
}

@SuppressWarnings(Array("IncorrectlyNamedExceptions"))
case class DefaultJSONCommandError(
    code: Option[Int],
    errmsg: Option[String],
    originalDocument: JsObject
) extends JSONCommandError {
  override def getMessage = s"CommandError[code=${code.getOrElse("<unknown>")}, errmsg=${errmsg.getOrElse("<unknown>")}, doc: ${originalDocument}]"
}

object JSONCommandError {
  def unapply(error: JsError): Option[JSONCommandError] = error match {
    case JsError(Seq((_, Seq(ValidationError(
      ("errmsg" +: errmsg), args))))) => args match {
      case Seq((doc @ JsObject(_)), code: Int) =>
        Some(DefaultJSONCommandError(Some(code), errmsg.headOption, doc))

      case Seq(doc @ JsObject(_)) =>
        Some(DefaultJSONCommandError(None, errmsg.headOption, doc))
    }

    case _ => None
  }
}

private[commands] trait DealingWithGenericCommandErrorsReader[A]
    extends Reads[A] {

  def readResult(doc: JsObject): A

  final def reads(json: JsValue): JsResult[A] = json match {
    case doc: JsObject => {
      if (!(doc \ "ok").asOpt[JsNumber].exists(_.value.toInt == 1)) {
        val cause = ValidationError(
          messages = "errmsg" +: (doc \ "errmsg").asOpt[String].toSeq,
          args = doc +: (doc \ "code").asOpt[Int].toSeq
        )

        JsError(cause)
      } else JsSuccess(readResult(doc))
    }

    case v => JsError(s"Expecting a ReactiveMongo document, found: $v")
  }
}
