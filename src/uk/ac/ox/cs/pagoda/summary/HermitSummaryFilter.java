package uk.ac.ox.cs.pagoda.summary;

import java.util.HashSet;
import java.util.Set;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import uk.ac.ox.cs.JRDFox.model.Individual;
import uk.ac.ox.cs.pagoda.endomorph.Endomorph;
import uk.ac.ox.cs.pagoda.owl.OWLHelper;
import uk.ac.ox.cs.pagoda.query.AnswerTuple;
import uk.ac.ox.cs.pagoda.query.AnswerTuples;
import uk.ac.ox.cs.pagoda.query.AnswerTuplesImp;
import uk.ac.ox.cs.pagoda.query.QueryRecord;
import uk.ac.ox.cs.pagoda.query.QueryRecord.Step;
import uk.ac.ox.cs.pagoda.reasoner.full.Checker;
import uk.ac.ox.cs.pagoda.reasoner.full.HermitChecker;
import uk.ac.ox.cs.pagoda.tracking.TrackingRuleEncoder;
import uk.ac.ox.cs.pagoda.util.Timer;
import uk.ac.ox.cs.pagoda.util.Utility;

public class HermitSummaryFilter implements Checker {

	QueryRecord m_record; 
	Summary summary = null;
	HermitChecker summarisedHermiT = null; 
	boolean summarisedConsistency;
	
	Endomorph endomorphismChecker = null;
	
	public HermitSummaryFilter(QueryRecord record, boolean toCallHermiT) {
		m_record = record; 
		HermitChecker hermitChecker = new HermitChecker(record.getRelevantOntology(), record, toCallHermiT); 
		endomorphismChecker = new Endomorph(record, hermitChecker);
		hermitChecker.setDependencyGraph(endomorphismChecker.getDependencyGraph()); 
	}
	
	@Override
	public boolean isConsistent() {
		if (summary == null)
			summary = new Summary(endomorphismChecker.getOntology(), endomorphismChecker.getGraph()); 
		
		if (summarisedHermiT == null) 
			initialiseSummarisedReasoner();

		if (summarisedConsistency) return true; 
		return endomorphismChecker.isConsistent(); 
	}
	
	private void initialiseSummarisedReasoner() {
		Timer t = new Timer(); 
		summarisedHermiT = new HermitChecker(summary.getSummary(), summary.getSummary(m_record));
//		summary.save("summarised_query" + m_record.getQueryID() + ".owl");
		if (summarisedConsistency = summarisedHermiT.isConsistent()) 
			Utility.logDebug("The summary of ABox is consistent with the TBox.");
		else 
			Utility.logDebug("The summary of ABox is NOT consistent with the TBox.");
		m_record.addProcessingTime(Step.Summarisation, t.duration());
	}
	
	@Override
	public int check(AnswerTuples answers) {
		Timer t = new Timer(); 
		OWLOntology newOntology = addOntologyWithQueryPreciate(endomorphismChecker.getOntology(), m_record, answers);
		summary = new Summary(newOntology); 
		initialiseSummarisedReasoner(); 
		
		if (summarisedConsistency) {
			Set<AnswerTuple> passed = new HashSet<AnswerTuple>(), succ = new HashSet<AnswerTuple>();
			Set<AnswerTuple> falsified = new HashSet<AnswerTuple>(), fail = new HashSet<AnswerTuple>(); 
			
			int counter = 0; 
			AnswerTuple representative;
			for (AnswerTuple answer; answers.isValid(); answers.moveNext()) {
				++counter; 
				answer = answers.getTuple();
				representative = summary.getSummary(answer); 
				if (fail.contains(representative)) 
					falsified.add(answer);
				else if (succ.contains(representative)) 
					passed.add(answer);
				else 
					if (summarisedHermiT.check(representative)) {  
						succ.add(representative);
						passed.add(answer);
					}
					else {
						fail.add(representative);
						falsified.add(answer); 
					}
			}
			answers.dispose();
			
			Utility.logDebug("@TIME to filter out non-answers by summarisation: " + t.duration());
	
			m_record.removeUpperBoundAnswers(falsified);
			
			if (m_record.processed()) {
				m_record.setDifficulty(Step.Summarisation);
				m_record.addProcessingTime(Step.Summarisation, t.duration());
				return 0; 
			}
			
			Utility.logDebug("The number of answers to be checked with HermiT: " + passed.size() + "/" + counter);
			
			m_record.setDifficulty(Step.FullReasoning);
			m_record.addProcessingTime(Step.Summarisation, t.duration());
			
			return endomorphismChecker.check(new AnswerTuplesImp(m_record.getAnswerVariables(), passed));
		}
		else {
			m_record.addProcessingTime(Step.Summarisation, t.duration());
//			m_record.saveRelevantOntology("fragment.owl");
			m_record.setDifficulty(Step.FullReasoning);
			return endomorphismChecker.check(answers);
		}
	}

	public static final String QueryAnswerTermPrefix = TrackingRuleEncoder.QueryPredicate + "_term"; 
	
	public static OWLOntology addOntologyWithQueryPreciate(OWLOntology ontology, QueryRecord record, AnswerTuples answers) {
		OWLOntology newOntology = null; 
		OWLOntologyManager manager = ontology.getOWLOntologyManager();
		OWLDataFactory factory = manager.getOWLDataFactory(); 
		try {
			 newOntology = manager.createOntology();
			 manager.addAxioms(newOntology, ontology.getAxioms()); 
			 
			 OWLClass[] queryClass = new OWLClass[answers.getArity()];
			 int arity = answers.getArity(); 
			 for (int i = 0; i < arity; ++i)
				 queryClass[i] = factory.getOWLClass(IRI.create(QueryAnswerTermPrefix + i));
			 AnswerTuple answer; 
			 for (; answers.isValid(); answers.moveNext()) {
				answer = answers.getTuple();
			 	for (int i = 0; i < arity; ++i) 
			 		if (answer.getGroundTerm(i) instanceof Individual) {
				 		String iri = ((Individual) answer.getGroundTerm(i)).getIRI(); 
				 		if (!record.isPredicate(answer, i)) { 
					 		manager.addAxiom(newOntology, 
					 				factory.getOWLClassAssertionAxiom(
					 						queryClass[i], 
					 						factory.getOWLNamedIndividual(IRI.create(iri)))); 
					 	}
			 		}
			 }
			 answers.reset();
		} catch (OWLOntologyCreationException e) {
			e.printStackTrace();
		} 
		
		return newOntology;
	}

	public static void printRelatedABoxAxioms(OWLOntology onto, String str) {
		if (!str.startsWith("<")) str = OWLHelper.addAngles(str); 
		
		System.out.println("Axioms in " + onto.getOntologyID().getOntologyIRI() + " related to " + str); 
		
		for (OWLAxiom axiom: onto.getABoxAxioms(true))
			if (axiom.toString().contains(str))
				System.out.println(axiom); 
		
		System.out.println("-----------------------------");
	}

	public static void printRelatedTBoxAxioms(OWLOntology onto, String str) {
		
		System.out.println("Axioms in " + onto.getOntologyID().getOntologyIRI() + " related to " + str); 
		
		for (OWLAxiom axiom: onto.getTBoxAxioms(true))
			if (axiom.toString().contains(str))
				System.out.println(axiom); 
		
		for (OWLAxiom axiom: onto.getRBoxAxioms(true))
			if (axiom.toString().contains(str))
				System.out.println(axiom); 
		
		System.out.println("-----------------------------");
	}

	@Override
	public boolean check(AnswerTuple answer) {
		AnswerTuple representative = summary.getSummary(answer); 
		if (summarisedHermiT.isConsistent() && !summarisedHermiT.check(representative))
			return false;
		return endomorphismChecker.check(answer); 
	}

	@Override
	public void dispose() {
		if (summarisedHermiT != null) summarisedHermiT.dispose(); 
		endomorphismChecker.dispose(); 
	}

}
