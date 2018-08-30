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
  JsBoolean,
  JsObject,
  JsPath,
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
  //GenericQueryBuilder
}
import reactivemongo.api.commands.WriteConcern
import reactivemongo.util.option

import reactivemongo.play.json.{
  BSONFormats,
  JSONException,
  JSONSerializationPack
}

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

  import play.api.libs.json.{
    JsError,
    JsNumber,
    JsValue,
    JsString,
    JsResult,
    JsSuccess
  }
  import reactivemongo.bson.BSONValue
  import reactivemongo.api.commands.{
    CountCommand => CC,
    DefaultWriteResult,
    DeleteCommand => DC,
    GetLastError => GLE,
    InsertCommand => IC,
    DistinctCommand => DistC,
    LastError,
    ResolvedCollectionCommand,
    UpdateCommand => UC,
    Upserted,
    UpdateWriteResult,
    WriteError,
    WriteConcernError
  }
  import reactivemongo.core.protocol.MongoWireVersion
  import reactivemongo.play.json.readOpt
  import reactivemongo.play.json.commands.CommonImplicits

  val pack = JSONSerializationPack

  object JSONCountCommand extends CC[JSONSerializationPack.type] {
    val pack = commands.pack
  }
  val CountCommand = JSONCountCommand

  object JSONDistinctCommand extends DistC[JSONSerializationPack.type] {
    val pack = commands.pack
  }
  val DistinctCommand = JSONDistinctCommand

  implicit object DistinctWriter
    extends pack.Writer[ResolvedCollectionCommand[DistinctCommand.Distinct]] {

    import CommonImplicits.ReadConcernWriter

    def writes(cmd: ResolvedCollectionCommand[DistinctCommand.Distinct]): pack.Document = {
      val c = Json.obj(
        "distinct" -> cmd.collection,
        "key" -> cmd.command.keyString,
        "query" -> cmd.command.query
      )

      if (cmd.command.version < MongoWireVersion.V32) c else {
        c + ("readConcern" -> ReadConcernWriter.writes(cmd.command.readConcern))
      }
    }
  }

  implicit object DistinctResultReader
    extends pack.Reader[DistinctCommand.DistinctResult] {

    private val path = JsPath \ "values"

    def reads(js: JsValue): JsResult[DistinctCommand.DistinctResult] =
      (js \ "values").toEither match {
        case Right(JsArray(values)) =>
          JsSuccess(DistinctCommand.DistinctResult(values.toList))

        case Right(v)    => JsError(path, s"invalid JSON: $v")
        case Left(error) => JsError(Seq(path -> Seq(error)))
      }
  }

  implicit object HintWriter extends Writes[CountCommand.Hint] {
    import CountCommand.{ HintString, HintDocument }

    def writes(hint: CountCommand.Hint): JsValue = hint match {
      case HintString(s)     => JsString(s)
      case HintDocument(obj) => obj
    }
  }

  implicit object CountWriter
    extends pack.Writer[ResolvedCollectionCommand[CountCommand.Count]] {

    def writes(count: ResolvedCollectionCommand[CountCommand.Count]): pack.Document = {
      val fields = Seq[Option[(String, Json.JsValueWrapper)]](
        Some("count" -> count.collection),
        count.command.query.map("query" -> _),
        option(count.command.limit != 0, count.command.limit).map("limit" -> _),
        option(count.command.skip != 0, count.command.skip).map("skip" -> _),
        count.command.hint.map("hint" -> _)
      ).flatten

      Json.obj(fields: _*)
    }
  }

  implicit object CountResultReader
    extends pack.Reader[CountCommand.CountResult] {
    def reads(js: JsValue): JsResult[CountCommand.CountResult] =
      (js \ "n").validate[Int].map(CountCommand.CountResult(_))
  }

  object JSONInsertCommand extends IC[JSONSerializationPack.type] {
    val pack = commands.pack
  }
  val InsertCommand = JSONInsertCommand
  type ResolvedInsert = ResolvedCollectionCommand[InsertCommand.Insert]

  implicit object WriteConcernWriter extends pack.Writer[WriteConcern] {
    def writes(wc: WriteConcern): pack.Document = {
      val obj = Json.obj(
        "w" -> ((wc.w match {
          case GLE.Majority                  => JsString("majority")
          case GLE.TagSet(tagSet)            => JsString(tagSet)
          case GLE.WaitForAknowledgments(n)  => JsNumber(n)
          case GLE.WaitForAcknowledgments(n) => JsNumber(n)
        }): JsValue),
        "wtimeout" -> wc.wtimeout
      )

      if (!wc.j) obj else obj + ("j" -> JsBoolean(true))
    }
  }

  implicit object InsertWriter extends pack.Writer[ResolvedInsert] {
    private val underlying = reactivemongo.api.commands.InsertCommand.
      writer(JSONSerializationPack)(JSONInsertCommand)(None)

    def writes(cmd: ResolvedInsert): pack.Document = underlying(cmd)
  }

  object JSONUpdateCommand extends UC[JSONSerializationPack.type] {
    val pack = commands.pack
  }

  val UpdateCommand = JSONUpdateCommand
  type ResolvedUpdate = ResolvedCollectionCommand[UpdateCommand.Update]

  implicit object UpdateElementWriter
    extends pack.Writer[UpdateCommand.UpdateElement] {

    def writes(element: UpdateCommand.UpdateElement): pack.Document = Json.obj(
      "q" -> element.q,
      "u" -> element.u,
      "upsert" -> element.upsert,
      "multi" -> element.multi
    )
  }

  implicit object UpdateWriter extends pack.Writer[ResolvedUpdate] {
    def writes(cmd: ResolvedUpdate): pack.Document = Json.obj(
      "update" -> cmd.collection,
      "updates" -> Json.toJson(cmd.command.updates),
      "ordered" -> cmd.command.ordered,
      "writeConcern" -> cmd.command.writeConcern
    )
  }

  implicit object UpsertedReader extends pack.Reader[Upserted] {
    def reads(js: JsValue): JsResult[Upserted] = for {
      ix <- (js \ "index").validate[Int]
      id <- (js \ "_id").validate[JsValue].flatMap(BSONFormats.toBSON)
    } yield Upserted(index = ix, _id = id)
  }

  implicit object WriteErrorReader extends pack.Reader[WriteError] {
    def reads(js: JsValue): JsResult[WriteError] = for {
      id <- (js \ "index").validate[Int]
      co <- (js \ "code").validate[Int]
      em <- (js \ "errmsg").validate[String]
    } yield WriteError(index = id, code = co, errmsg = em)
  }

  implicit object WriteConcernErrorReader
    extends pack.Reader[WriteConcernError] {
    def reads(js: JsValue): JsResult[WriteConcernError] = for {
      co <- (js \ "code").validate[Int]
      em <- (js \ "errmsg").validate[String]
    } yield WriteConcernError(code = co, errmsg = em)
  }

  implicit object UpdateReader extends pack.Reader[UpdateCommand.UpdateResult] {
    def reads(js: JsValue): JsResult[UpdateCommand.UpdateResult] = for {
      ok <- readOpt[Int](js \ "ok")
      n <- readOpt[Int](js \ "n")
      mo <- readOpt[Int](js \ "nModified")
      up <- readOpt[Seq[Upserted]](js \ "upserted")
      we <- readOpt[Seq[WriteError]](js \ "writeErrors")
      ce <- readOpt[WriteConcernError](js \ "writeConcernError")
      co <- readOpt[Int](js \ "code") //FIXME There is no corresponding official docs.
      em <- readOpt[String](js \ "errmsg") //FIXME There is no corresponding official docs.
    } yield UpdateWriteResult(
      ok = ok.exists(_ != 0),
      n = n.getOrElse(0),
      nModified = mo.getOrElse(0),
      upserted = up.getOrElse(Seq.empty[Upserted]),
      writeErrors = we.getOrElse(Seq.empty[WriteError]),
      writeConcernError = ce,
      code = co,
      errmsg = em
    )
  }

  object JSONDeleteCommand extends DC[JSONSerializationPack.type] {
    val pack = commands.pack
  }
  val DeleteCommand = JSONDeleteCommand
  type ResolvedDelete = ResolvedCollectionCommand[DeleteCommand.Delete]

  implicit object DeleteElementWriter
    extends pack.Writer[DeleteCommand.DeleteElement] {
    def writes(e: DeleteCommand.DeleteElement): pack.Document = Json.obj(
      "q" -> e.q, "limit" -> e.limit
    )
  }

  implicit object DeleteWriter extends pack.Writer[ResolvedDelete] {
    def writes(cmd: ResolvedDelete): pack.Document = Json.obj(
      "delete" -> cmd.collection,
      "deletes" -> Json.toJson(cmd.command.deletes),
      "ordered" -> cmd.command.ordered,
      "writeConcern" -> cmd.command.writeConcern
    )
  }

  implicit object DefaultWriteResultReader
    extends pack.Reader[DefaultWriteResult] {
    def reads(js: JsValue): JsResult[DefaultWriteResult] = for {
      ok <- readOpt[Int](js \ "ok")
      n <- readOpt[Int](js \ "n")
      we <- readOpt[Seq[WriteError]](js \ "writeErrors")
      ce <- readOpt[WriteConcernError](js \ "writeConcernError")
      co <- readOpt[Int](js \ "code") //FIXME There is no corresponding official docs.
      em <- readOpt[String](js \ "errmsg") //FIXME There is no corresponding official docs.
    } yield DefaultWriteResult(
      ok = ok.exists(_ != 0),
      n = n.getOrElse(0),
      writeErrors = we.getOrElse(Seq.empty[WriteError]),
      writeConcernError = ce,
      code = co,
      errmsg = em
    )
  }

  implicit object LastErrorReader extends pack.Reader[LastError] {
    def reads(js: JsValue): JsResult[LastError] = for {
      ok <- readOpt[Int](js \ "ok")
      er <- readOpt[String](js \ "err")
      co <- readOpt[Int](js \ "code")
      lo <- readOpt[Long](js \ "lastOp")
      n <- readOpt[Int](js \ "n")
      ss <- readOpt[String](js \ "singleShard")
      ux <- readOpt[Boolean](js \ "updatedExisting")
      ue <- readOpt[JsValue](js \ "upserted").flatMap(
        _.fold[JsResult[Option[BSONValue]]](
          JsSuccess(None)
        )(BSONFormats.toBSON(_).map(Some(_)))
      )
      wn <- (js \ "wnote").get match {
        case JsString("majority") => JsSuccess(Some(GLE.Majority))
        case JsString(tagSet)     => JsSuccess(Some(GLE.TagSet(tagSet)))
        case JsNumber(acks) => JsSuccess(
          Some(GLE.WaitForAcknowledgments(acks.toInt))
        )
        case _ => JsSuccess(Option.empty[GLE.W])
      }
      wt <- readOpt[Boolean](js \ "wtimeout")
      we <- readOpt[Int](js \ "waited")
      wm <- readOpt[Int](js \ "wtime")
    } yield LastError(ok.exists(_ != 0), er, co, lo, n.getOrElse(0),
      ss, ux.getOrElse(false), ue, wn, wt.getOrElse(false), we, wm)
  }

  import reactivemongo.play.json.commands.{
    JSONFindAndModifyCommand,
    JSONFindAndModifyImplicits
  }
  val FindAndModifyCommand = JSONFindAndModifyCommand

  implicit val FindAndModifyWriter =
    JSONFindAndModifyImplicits.FindAndModifyWriter

  implicit val FindAndModifyReader =
    JSONFindAndModifyImplicits.FindAndModifyResultReader

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
  import play.api.libs.json.{ JsError, JsSuccess, OWrites }
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
