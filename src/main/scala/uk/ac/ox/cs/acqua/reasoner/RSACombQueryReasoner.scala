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

package uk.ac.ox.cs.acqua.reasoner

import java.util.Collection;
import scala.collection.JavaConverters._

import org.semanticweb.owlapi.model.OWLOntology
import uk.ac.ox.cs.rsacomb.approximation.{Approximation,Lowerbound}
import uk.ac.ox.cs.rsacomb.ontology.{Ontology,RSAOntology}
import uk.ac.ox.cs.pagoda.query.QueryRecord
import uk.ac.ox.cs.pagoda.reasoner.QueryReasoner
import uk.ac.ox.cs.acqua.approximation.Noop

class RSACombQueryReasoner(
  val origin: Ontology,
  val toRSA: Approximation[RSAOntology] = Noop
) extends QueryReasoner {

  /* Implicit compatibility between PAGOdA and RSAComb types */
  import uk.ac.ox.cs.acqua.implicits.PagodaConverters._

  val rsa: RSAOntology = origin approximate toRSA

  /** Doesn't perform any action.
    *
    * @note Implemented for compatibility with other reasoners.
   */
  def loadOntology(ontology: OWLOntology): Unit = {
    /* Nothing to do */
  }

  /** Check consistency and returns whether the ontology is RSA.
    *
    * Preprocessing is performed on instance creation, so no actual work
    * is being done here.
    *
    * @note Implemented for compatibility with other reasoners.
   */
  def preprocess(): Boolean = {
    origin.isRSA
  }

  /** Check consistency and returns whether the ontology is RSA.
    *
    * Preprocessing is performed on instance creation, along with
    * consistency checking, so no actual work is being done here.
    *
    * @note Implemented for compatibility with other reasoners.
   */
  def isConsistent(): Boolean = {
    origin.isRSA
  }

  /** Evaluates a collection of queries.
    *
    * Uses RSAComb internally to reuse part of the computation of
    * multiple calls to [[uk.ac.ox.cs.rsacomb.RSAOntology.ask]].
    *
    * TODO: perform logging of answers
    */
  override def evaluate(queries: Collection[QueryRecord]): Unit = {
    val answers = rsa ask queries
    /* Perform logging */
    // Logger write answers
    // Logger.generateSimulationScripts(datapath, queries)
  }

  /** Evaluates a single query.
    *
    * Uses RSAComb internally to reuse part of the computation of
    * multiple calls to [[uk.ac.ox.cs.rsacomb.RSAOntology.ask]].
    *
    * TODO: perform logging of answers
    */
  def evaluate(query: QueryRecord): Unit = {
    val answers = rsa ask query
    /* Perform logging */
    // Logger write answers
    // Logger.generateSimulationScripts(datapath, queries)
  }

  def evaluateUpper(record: QueryRecord): Unit= ???
}
