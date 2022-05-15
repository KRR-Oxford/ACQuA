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

package uk.ac.ox.cs.acqua.approximation

import uk.ac.ox.cs.rsacomb.ontology.{Ontology,RSAOntology}
import uk.ac.ox.cs.rsacomb.approximation.Approximation

/** Dummy approximation without any effect.
  *
  * @note this is only useful to convert an already RSA
  * [[uk.ac.ox.cs.rsacomb.ontology.Ontology]] into an 
  * [[uk.ac.ox.cs.rsacomb.ontology.RSAOntology]].
  */
object Noop extends Approximation[RSAOntology] {

  def approximate(ontology: Ontology): RSAOntology =
    RSAOntology(ontology.origin, ontology.axioms, ontology.datafiles)

}

