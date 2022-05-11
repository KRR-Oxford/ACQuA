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

import org.semanticweb.owlapi.model.OWLOntology
import uk.ac.ox.cs.rsacomb.RSAOntology
import uk.ac.ox.cs.rsacomb.approximation.{Approximation,Lowerbound}
import uk.ac.ox.cs.rsacomb.ontology.Ontology
import uk.ac.ox.cs.pagoda.query.QueryRecord
import uk.ac.ox.cs.pagoda.reasoner.QueryReasoner

class RSAQueryReasoner(val origin: Ontology) extends QueryReasoner {

  /* Implicit compatibility between PAGOdA and RSAComb types */
  import uk.ac.ox.cs.acqua.implicits.PagodaConverters._

  /** This class is instantiated when the input ontology is RSA.
    * Approximation (via any algorithm with RSAOntology as target)
    * doesn't perform anything, but is useful to turn a generic
    * [[uk.ac.ox.cs.rsacomb.ontology.Ontology]] into an
    * [[uk.ac.ox.cs.rsacomb.RSAOntology]].
    */
  private val toRSA: Approximation[RSAOntology] = new Lowerbound
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

  // TODO: probably need to override `evaluate` on multiple queries
  def evaluate(query: QueryRecord): Unit = {
    rsa ask query
  }

  def evaluateUpper(record: QueryRecord): Unit= {
    ???
  }
}
