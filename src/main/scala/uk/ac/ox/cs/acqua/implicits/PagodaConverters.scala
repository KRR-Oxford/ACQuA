/*
 * Copyright 2021,2022 KRR Oxford
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.ox.cs.acqua.implicits

import java.util.Collection
import scala.collection.JavaConverters._

import uk.ac.ox.cs.rsacomb.sparql.ConjunctiveQuery
import uk.ac.ox.cs.rsacomb.util.RSA
import uk.ac.ox.cs.pagoda.query.QueryRecord

object PagodaConverters {

  implicit def queryRecord2conjuctiveQuery(q: QueryRecord): ConjunctiveQuery = {
    ConjunctiveQuery.parse(
      q.getQueryID.toIntOption.getOrElse(-1),
      q.getQueryText(),
      RSA.Prefixes
    ).get
  }

  implicit def queryRecords2conjuctiveQueries(qs: Collection[QueryRecord]): List[ConjunctiveQuery] =
    qs.asScala.map(queryRecord2conjuctiveQuery).toList

}



