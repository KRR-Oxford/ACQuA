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

package uk.ac.ox.cs.acqua

import uk.ac.ox.cs.rsacomb.converter.Normalizer
import uk.ac.ox.cs.rsacomb.ontology.Ontology
import uk.ac.ox.cs.rsacomb.util.{RDFoxUtil,RSA}

import uk.ac.ox.cs.pagoda.owl.OWLHelper
import uk.ac.ox.cs.pagoda.reasoner.{ELHOQueryReasoner,MyQueryReasoner,QueryReasoner,RLQueryReasoner}
import uk.ac.ox.cs.pagoda.util.PagodaProperties;
import uk.ac.ox.cs.pagoda.util.Utility;

import uk.ac.ox.cs.acqua.reasoner.{
  AcquaQueryReasoner,
  RSACombQueryReasoner
}
import uk.ac.ox.cs.acqua.util.AcquaConfig

object Acqua extends App {
  val config = AcquaConfig.parse(args.toList)
  AcquaConfig describe config

  val ontopath = os.Path("tests/lubm/univ-bench.owl", base = os.pwd)
  val ontology = Ontology(ontopath, List.empty).normalize(new Normalizer)

  val properties = new PagodaProperties()

  val performMultiStages = true
  val considerEqualities = true

  val reasoner: QueryReasoner = if (OWLHelper.isInOWL2RL(ontology.origin)) {
    new RLQueryReasoner();
  } else if (OWLHelper.isInELHO(ontology.origin)) {
    new ELHOQueryReasoner();
  } else if (ontology.isRSA) {
    new RSACombQueryReasoner(ontology)
  } else {
    new AcquaQueryReasoner(ontology)
  }

  /* Preprocessing */
  reasoner.setProperties(properties)
  reasoner.loadOntology(ontology.origin)
  reasoner.importData(properties.getDataPath())
  if (reasoner.preprocess()) {
      Utility logInfo "The ontology is consistent!"
  }
  else {
      Utility logInfo "The ontology is inconsistent!"
      reasoner.dispose();
      sys.exit(0)
  }

  /* Query Answering */
  if (config contains 'queries) {
    val queryManager = reasoner.getQueryManager()
    config('queries).get[List[os.Path]].map(path => {
      val queries = queryManager collectQueryRecords path.toString
      reasoner evaluate queries
    })
  }
}

