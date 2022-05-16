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

  /** Force computation of canonical model for combined approach in RSA.
    *
    * @returns whether the original ontolgy is RSA.
    *
    * @note that it is not necessary to call this method since the
    * preprocessing is performed "on demand" when evaluating a query.
   */
  def preprocess(): Boolean = {
    rsa.computeCanonicalModel()
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
  //override def evaluate(queries: Collection[QueryRecord]): Unit = {
  //  val answers = rsa ask queries
  //  /* Perform logging */
  //  // Logger write answers
  //  // Logger.generateSimulationScripts(datapath, queries)
  //}

  /** Evaluates a single query.
    *
    * Uses RSAComb internally to reuse part of the computation of
    * multiple calls to [[uk.ac.ox.cs.rsacomb.RSAOntology.ask]].
    *
    * TODO: perform logging of answers
    */
  def evaluate(query: QueryRecord): Unit = {
    import uk.ac.ox.cs.acqua.implicits.RSACombAnswerTuples._
    val answers = rsa ask query
    query updateLowerBoundAnswers answers
    if (toRSA == Noop) {
      /* Perform logging
       * In this case the engine is used as a standalone engine, meaning
       * that it is time to print out query answers and other related
       * logging routines.
       */
      //Logger write answers
      //Logger.generateSimulationScripts(datapath, queries)
    }
  }

  /** Evaluates a single query.
    *
    * Uses RSAComb internally to reuse part of the computation of
    * multiple calls to [[uk.ac.ox.cs.rsacomb.RSAOntology.ask]].
    *
    * @note the result of the computation is saved in the "upper bound"
    * of the input query record.
    *
    * TODO: perform logging of answers
    */
  def evaluateUpper(query: QueryRecord): Unit = {
    import uk.ac.ox.cs.acqua.implicits.RSACombAnswerTuples._
    val answers = rsa ask query
    query updateUpperBoundAnswers answers
    if (toRSA == Noop) {
      /* Perform logging
       * In this case the engine is used as a standalone engine, meaning
       * that it is time to print out query answers and other related
       * logging routines.
       */
      //Logger write answers
      //Logger.generateSimulationScripts(datapath, queries)
    }
  }
}
