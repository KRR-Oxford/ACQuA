package uk.ac.ox.cs.data;

import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.parameters.Imports;
import uk.ac.ox.cs.pagoda.owl.OWLHelper;

public class OntologyStatistics {

	public static void main(String[] args) {
		args = ("/home/yzhou/ontologies/uobm/univ-bench-dl-minus.owl").split("\\ "); 

		OWLOntology onto = OWLHelper.loadOntology(args[0]);
		System.out.println(onto.getTBoxAxioms(Imports.INCLUDED).size() + onto.getRBoxAxioms(Imports.INCLUDED).size()); 
	}
	
}
