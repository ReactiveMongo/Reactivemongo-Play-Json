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

import play.api.libs.json.{ JsObject, OWrites }

import reactivemongo.api.commands.{
  FindAndModifyCommand,
  ResolvedCollectionCommand
}
import reactivemongo.play.json.JSONSerializationPack

@deprecated("Will be internal/private", "0.17.0")
object JSONFindAndModifyCommand
  extends FindAndModifyCommand[JSONSerializationPack.type] {
  val pack: JSONSerializationPack.type = JSONSerializationPack

  def UpdateLastError(): FindAndModifyCommand.UpdateLastError.type =
    sys.error("Deprecated/unused")
}

@deprecated("Will be internal/private", "0.17.0")
object JSONFindAndModifyImplicits {
  import JSONFindAndModifyCommand._

  implicit object FindAndModifyResultReader
    extends DealingWithGenericCommandErrorsReader[FindAndModifyResult] {

    def readResult(result: JsObject): FindAndModifyResult =
      sys.error("Deprecated/unused")
  }

  implicit object FindAndModifyWriter
    extends OWrites[ResolvedCollectionCommand[FindAndModify]] {

    def writes(command: ResolvedCollectionCommand[FindAndModify]): JsObject =
      sys.error("Deprecated/unused")
  }
}
