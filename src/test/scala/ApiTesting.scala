package reactivemongo.api

import reactivemongo.api.collections.{ GenericCollection, GenericQueryBuilder }

package object tests {
  @inline def queryBuilder[C <: GenericCollection[_ <: SerializationPack with Singleton]](coll: C): GenericQueryBuilder[coll.pack.type] = coll.genericQueryBuilder

  @inline def merge[Q <: GenericQueryBuilder[_ <: SerializationPack with Singleton]](queryBuilder: Q, readPreference: ReadPreference): queryBuilder.pack.Document = queryBuilder.merge(readPreference, Int.MaxValue)
}
