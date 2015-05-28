package uk.ac.ox.cs.pagoda.endomorph.plan;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import uk.ac.ox.cs.pagoda.endomorph.*;
import uk.ac.ox.cs.pagoda.query.AnswerTuple;
import uk.ac.ox.cs.pagoda.query.QueryRecord;
import uk.ac.ox.cs.pagoda.query.QueryRecord.Step;
import uk.ac.ox.cs.pagoda.reasoner.full.Checker;
import uk.ac.ox.cs.pagoda.summary.NodeTuple;
import uk.ac.ox.cs.pagoda.util.Timer;
import uk.ac.ox.cs.pagoda.util.Utility;

public class OpenEndPlan implements CheckPlan {
	
	public static final int TIME_OUT_MIN = 1; 
	
	Checker checker; 
	DependencyGraph dGraph; 
	QueryRecord m_record; 
	int m_answerArity; 

	public OpenEndPlan(Checker checker, DependencyGraph dGraph, QueryRecord record) {
		this.checker = checker; 
		this.dGraph = dGraph;
		m_record = record;
		m_answerArity = record.getAnswerVariables().length;
	}
	
	Set<Clique> validated = new HashSet<Clique>(); 
	Set<Clique> falsified = new HashSet<Clique>(); 
	Set<AnswerTuple> passedAnswers = new HashSet<AnswerTuple>();
	
	@Override
	public int check() {
		LinkedList<Clique> topo = new LinkedList<Clique>(dGraph.getTopologicalOrder());
		Utility.logInfo("Entrances: " + dGraph.getEntrances().size() + " Exists: " + dGraph.getExits().size()); 

		boolean flag = true;
		Clique clique; 
		Timer t = new Timer(); 
		
		AnswerTuple answerTuple;
		while (!topo.isEmpty()) { 
			if (flag) {
				clique = topo.removeFirst();
				if (redundant(clique)) continue; 
				if (validated.contains(clique)) continue; 
				if (falsified.contains(clique)) { flag = false; continue; }
				Utility.logDebug("start checking front ... " + (answerTuple = clique.getRepresentative().getAnswerTuple())); 
				if (checker.check(answerTuple)) {
					Utility.logDebug(answerTuple.toString() + " is verified.");
					setMarkCascadelyValidated(clique); 
				}
				else {
					falsified.add(clique);
					flag = false; 
				}
			}
			else {
				clique = topo.removeLast(); 
				if (falsified.contains(clique)) continue; 
				if (validated.contains(clique)) { flag = true; continue; }
				Utility.logDebug("start checking back ... " + (answerTuple = clique.getRepresentative().getAnswerTuple())); 
				if (!checker.check(answerTuple)) 
					setMarkCascadelyFasified(clique); 
				else {
					Utility.logDebug(answerTuple.toString() + " is verified.");
					addProjections(clique); 
					flag = true; 
				}
			}
		}
		
//		Utility.logDebug("HermiT was called " + times + " times."); 
		
		int count = 0; 
		AnswerTuple ans; 
		Collection<AnswerTuple> validAnswers = new LinkedList<AnswerTuple>(); 
		for (Clique c: dGraph.getTopologicalOrder()) 
			if (validated.contains(c)) {
				count += c.getNodeTuples().size();
//				validAnswers.add(c.getRepresentative().getAnswerTuple()); 
				for (NodeTuple nodeTuple: c.getNodeTuples()) {
					ans = nodeTuple.getAnswerTuple(); 
					validAnswers.add(ans);
					Utility.logDebug(ans + " is verified."); 
				}
			}
		
		m_record.addLowerBoundAnswers(validAnswers);
		m_record.addProcessingTime(Step.FullReasoning, t.duration());
		return count; 		
	}

	private boolean redundant(Clique clique) {
		for (NodeTuple nodeTuple: clique.getNodeTuples())
			if (!passedAnswers.contains(AnswerTuple.create(nodeTuple.getAnswerTuple(), m_answerArity))) 
				return false; 
		return true;
	}

	private void addProjections(Clique clique) {
		for (NodeTuple nodeTuple: clique.getNodeTuples()) 
			passedAnswers.add(AnswerTuple.create(nodeTuple.getAnswerTuple(), m_answerArity)); 
	}

	private void setMarkCascadelyValidated(Clique clique) { 
		validated.add(clique);
		addProjections(clique);
		Map<Clique, Collection<Clique>> edges = dGraph.getOutGoingEdges(); 
		if (edges.containsKey(clique))
			for (Clique c: edges.get(clique))
				if (!validated.contains(c)) 
					setMarkCascadelyValidated(c);
	}

	private void setMarkCascadelyFasified(Clique clique) { 
			falsified.add(clique);
		Map<Clique, Collection<Clique>> edges = dGraph.getInComingEdges(); 
		if (edges.containsKey(clique))
			for (Clique c: edges.get(clique))
				if (!falsified.contains(c)) 
					setMarkCascadelyFasified(c);
	}

}
