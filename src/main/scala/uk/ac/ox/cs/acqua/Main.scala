package uk.ac.ox.cs.acqua

import uk.ac.ox.cs.rsacomb.ontology.Ontology

import uk.ac.ox.cs.pagoda.owl.OWLHelper
import uk.ac.ox.cs.pagoda.reasoner.{ELHOQueryReasoner,QueryReasoner,RLQueryReasoner}
// import uk.ac.ox.cs.pagoda.Pagoda
// import uk.ac.ox.cs.pagoda.util.PagodaProperties;

object Acqua extends App {

  val ontopath = os.Path("tests/lubm/univ-bench.owl", base = os.pwd)
  val ontology = Ontology(ontopath, List.empty)

  val performMultiStages = true
  val considerEqualities = true

  val reasoner: QueryReasoner = if (OWLHelper.isInOWL2RL(ontology.origin)) {
    new RLQueryReasoner();
  } else if (OWLHelper.isInELHO(ontology.origin)) {
    new ELHOQueryReasoner();
  } else if (ontology.isRSA) {
    // Use combined approach for RSA
    ???
  } else {
    new MyQueryReasoner(performMultiStages, considerEqualities);
  }
        // else
        //     switch(type) {
        //         case RLU:
        //             reasoner = new RLUQueryReasoner(performMultiStages, considerEqualities);
        //             break;
        //         case ELHOU:
        //             reasoner = new ELHOUQueryReasoner(performMultiStages, considerEqualities);
        //             break;
        //         default:
        //             reasoner = new MyQueryReasoner(performMultiStages, considerEqualities);
        //     }
        // return reasoner;

}
