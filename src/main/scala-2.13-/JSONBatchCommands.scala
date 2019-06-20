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
package reactivemongo.play.json.collection

import com.github.ghik.silencer.silent

import play.api.libs.json.{ JsObject, JsValue, OWrites, Reads }

import reactivemongo.api.collections.BatchCommands

import reactivemongo.api.commands.{
  CountCommand => CC,
  DeleteCommand => DC,
  DistinctCommand => DistC,
  InsertCommand => IC,
  ResolvedCollectionCommand,
  UpdateCommand => UC
}

import reactivemongo.play.json.JSONSerializationPack

@deprecated("BatchCommands will be removed from the API", "0.17.0")
object JSONBatchCommands
  extends BatchCommands[JSONSerializationPack.type] { commands =>

  val pack = JSONSerializationPack

  object JSONCountCommand extends CC[JSONSerializationPack.type] {
    val pack = commands.pack
  }
  val CountCommand = JSONCountCommand

  def CountWriter: OWrites[ResolvedCollectionCommand[CountCommand.Count]] = sys.error("Deprecated/unused")

  def CountResultReader: Reads[CountCommand.CountResult] = sys.error("Deprecated/unused")

  @silent
  object JSONDistinctCommand extends DistC[JSONSerializationPack.type] {
    val pack = commands.pack
  }
  val DistinctCommand = JSONDistinctCommand

  object DistinctWriter
    extends OWrites[ResolvedCollectionCommand[DistinctCommand.Distinct]] {
    def writes(cmd: ResolvedCollectionCommand[DistinctCommand.Distinct]): JsObject = sys.error("Deprecated/unused")
  }

  object DistinctResultReader extends Reads[DistinctCommand.DistinctResult] {
    def reads(js: JsValue) = sys.error("Deprecated/unused")
  }

  object JSONInsertCommand extends IC[JSONSerializationPack.type] {
    val pack = commands.pack
  }
  val InsertCommand = JSONInsertCommand

  type ResolvedInsert = ResolvedCollectionCommand[InsertCommand.Insert]

  implicit def InsertWriter: OWrites[ResolvedInsert] = sys.error("Deprecated/unused")

  object JSONUpdateCommand extends UC[JSONSerializationPack.type] {
    val pack = commands.pack
  }
  val UpdateCommand = JSONUpdateCommand

  type ResolvedUpdate = ResolvedCollectionCommand[UpdateCommand.Update]

  implicit def UpdateWriter: OWrites[ResolvedUpdate] = sys.error("Deprecated/unused")

  implicit def UpdateReader: Reads[UpdateCommand.UpdateResult] = sys.error("Deprecated/unused")

  object JSONDeleteCommand extends DC[JSONSerializationPack.type] {
    val pack = commands.pack
  }
  val DeleteCommand = JSONDeleteCommand

  type ResolvedDelete = ResolvedCollectionCommand[DeleteCommand.Delete]

  implicit def DeleteWriter: OWrites[ResolvedDelete] = sys.error("Deprecated/unused")

  import reactivemongo.play.json.commands.{
    JSONFindAndModifyCommand,
    JSONFindAndModifyImplicits
  }

  val FindAndModifyCommand = JSONFindAndModifyCommand

  @silent implicit def FindAndModifyWriter: JSONFindAndModifyImplicits.FindAndModifyWriter.type = sys.error("Deprecated/unused")

  @silent implicit def FindAndModifyReader: JSONFindAndModifyImplicits.FindAndModifyResultReader.type = sys.error("Deprecated/unused")

  import reactivemongo.play.json.commands.{
    JSONAggregationFramework,
    JSONAggregationImplicits
  }
  val AggregationFramework = JSONAggregationFramework

  implicit val AggregateWriter = JSONAggregationImplicits.AggregateWriter
  implicit val AggregateReader =
    JSONAggregationImplicits.AggregationResultReader

}
