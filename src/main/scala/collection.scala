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

import scala.concurrent.{ ExecutionContext, Future }

import com.github.ghik.silencer.silent

import play.api.libs.json.{
  Json,
  JsArray,
  JsObject,
  JsValue,
  OWrites,
  Reads,
  Writes
}

import reactivemongo.api.{
  CollectionMetaCommands,
  DB,
  FailoverStrategy,
  ReadPreference
}
import reactivemongo.api.collections.{
  BatchCommands,
  GenericCollection,
  GenericCollectionProducer
}
import reactivemongo.api.commands.WriteConcern

import reactivemongo.play.json.{ JSONException, JSONSerializationPack }

/**
 * A Collection that interacts with the Play JSON library, using `Reads` and `Writes`.
 */
object `package` {
  implicit object JSONCollectionProducer extends GenericCollectionProducer[JSONSerializationPack.type, JSONCollection] {
    def apply(db: DB, name: String, failoverStrategy: FailoverStrategy) = new JSONCollection(db, name, failoverStrategy, db.defaultReadPreference)
  }
}

@deprecated("BatchCommands will be removed from the API", "0.17.0")
object JSONBatchCommands
  extends BatchCommands[JSONSerializationPack.type] { commands =>

  import reactivemongo.api.commands.{
    CountCommand => CC,
    DeleteCommand => DC,
    InsertCommand => IC,
    DistinctCommand => DistC,
    ResolvedCollectionCommand,
    UpdateCommand => UC
  }

  val pack = JSONSerializationPack

  object JSONCountCommand extends CC[JSONSerializationPack.type] {
    val pack = commands.pack
  }
  val CountCommand = JSONCountCommand

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

  def CountWriter: OWrites[ResolvedCollectionCommand[CountCommand.Count]] = sys.error("Deprecated/unused")

  def CountResultReader: Reads[CountCommand.CountResult] = sys.error("Deprecated/unused")

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

/**
 * A Collection that interacts with the Play JSON library,
 * using `Reads` and `Writes`.
 */
final class JSONCollection(
    val db: DB,
    val name: String,
    val failoverStrategy: FailoverStrategy,
    override val readPreference: ReadPreference
) extends GenericCollection[JSONSerializationPack.type]
  with CollectionMetaCommands {

  @deprecated("Use the constructor with a ReadPreference", "0.12-RC5")
  def this(db: DB, name: String, failoverStrategy: FailoverStrategy) =
    this(db, name, failoverStrategy, db.defaultReadPreference)

  val pack = JSONSerializationPack

  @silent
  val BatchCommands = JSONBatchCommands

  def withReadPreference(pref: ReadPreference): JSONCollection =
    new JSONCollection(db, name, failoverStrategy, pref)
}

// JSON extension for cursors

import reactivemongo.api.{
  Cursor,
  FlattenedCursor,
  WrappedCursor
}

sealed trait JsCursor[T] extends Cursor[T] {
  /**
   * Returns the result of cursor as a JSON array.
   *
   * @param maxDocs Maximum number of documents to be retrieved
   */
  def jsArray(maxDocs: Int = Int.MaxValue)(implicit ec: ExecutionContext): Future[JsArray]

}

class JsCursorImpl[T: Writes](val wrappee: Cursor[T])
  extends JsCursor[T] with WrappedCursor[T] {
  import Cursor.{ Cont, Fail }

  private val writes = implicitly[Writes[T]]

  def jsArray(maxDocs: Int = Int.MaxValue)(implicit ec: ExecutionContext): Future[JsArray] = wrappee.foldWhile(Json.arr(), maxDocs)(
    (arr, res) => Cont(arr :+ writes.writes(res)),
    (_, error) => Fail(error)
  )

}

class JsFlattenedCursor[T](val future: Future[JsCursor[T]])
  extends FlattenedCursor[T](future) with JsCursor[T] {

  def jsArray(maxDocs: Int = Int.MaxValue)(implicit ec: ExecutionContext): Future[JsArray] = future.flatMap(_.jsArray(maxDocs))

}

/** Implicits of the JSON extensions for cursors. */
object JsCursor {
  import reactivemongo.api.{ CursorFlattener, CursorProducer }

  /** Provides JSON instances for CursorProducer typeclass. */
  implicit def cursorProducer[T: Writes] = new CursorProducer[T] {
    type ProducedCursor = JsCursor[T]

    // Returns a cursor with JSON operations.
    def produce(base: Cursor.WithOps[T]): JsCursor[T] =
      new JsCursorImpl[T](base)
  }

  /** Provides flattener for JSON cursor. */
  implicit object cursorFlattener extends CursorFlattener[JsCursor] {
    def flatten[T](future: Future[JsCursor[T]]): JsCursor[T] =
      new JsFlattenedCursor(future)
  }
}

/** Some JSON helpers. */
object Helpers {
  import java.io.InputStream
  import play.api.libs.json.{ JsError, JsSuccess }
  import reactivemongo.api.commands.MultiBulkWriteResult

  implicit val idWrites = OWrites[JsObject](identity[JsObject])

  /**
   * Inserts the documents from a JSON source.
   *
   * @param documents the source that can be parsed as an array of JSON objects
   * @param ordered true if to insert the document in order
   * @param bulkSize the maximum size for each insert bulk
   * @param wc the write concern
   */
  @deprecated("Use `bulkInsert` without `bulkSize` and `bulkByteSize` (resolved from the metadata)", "0.12.7")
  @SuppressWarnings(Array("UnusedMethodParameter"))
  def bulkInsert(collection: JSONCollection, documents: => InputStream, ordered: Boolean, bulkSize: Int, bulkByteSize: Int)(implicit ec: ExecutionContext, wc: WriteConcern): Future[MultiBulkWriteResult] =
    documentProducer(collection, documents).flatMap { producer =>
      collection.insert(ordered, wc).many(producer.map(_.produce))
    }

  /**
   * Inserts the documents from a JSON source.
   *
   * @param documents the source that can be parsed as an array of JSON objects
   * @param ordered true if to insert the document in order
   * @param wc the write concern
   */
  def bulkInsert(collection: JSONCollection, documents: => InputStream, ordered: Boolean = true)(implicit ec: ExecutionContext, @silent wc: WriteConcern = collection.db.connection.options.writeConcern): Future[MultiBulkWriteResult] =
    documentProducer(collection, documents).flatMap { producer =>
      collection.insert(ordered, wc).many(producer.map(_.produce))
    }

  private def documentProducer(collection: JSONCollection, documents: => InputStream)(implicit ec: ExecutionContext): Future[List[collection.ImplicitlyDocumentProducer]] = {
    lazy val in = documents

    @SuppressWarnings(Array("CatchException"))
    def docs: Future[List[JsObject]] = try {
      Json.parse(in).validate[List[JsObject]] match {
        case JsSuccess(os, _) => Future.successful(os)
        case err @ JsError(_) => Future.failed(new JSONException(
          Json.stringify(JsError toJson err)
        ))
      }
    } catch {
      case reason: Exception => Future.failed(reason)
    } finally {
      try {
        in.close()
      } catch {
        case _: Exception => ()
      }
    }

    docs.map {
      _.map { implicitly[collection.ImplicitlyDocumentProducer](_) }
    }
  }
}
