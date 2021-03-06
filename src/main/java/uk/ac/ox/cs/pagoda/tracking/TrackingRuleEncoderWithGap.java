package uk.ac.ox.cs.pagoda.tracking;

import org.semanticweb.HermiT.model.*;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import uk.ac.ox.cs.pagoda.hermit.DLClauseHelper;
import uk.ac.ox.cs.pagoda.query.GapTupleIterator;
import uk.ac.ox.cs.pagoda.reasoner.light.BasicQueryEngine;
import uk.ac.ox.cs.pagoda.rules.UpperDatalogProgram;
import uk.ac.ox.cs.pagoda.util.Namespace;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class TrackingRuleEncoderWithGap extends TrackingRuleEncoder {

	public TrackingRuleEncoderWithGap(UpperDatalogProgram program, BasicQueryEngine store) {
		super(program, store);
	}

	@Override
	protected String getEqualityRelatedRuleText() {
		if (equalityRelatedRuleText != null) return equalityRelatedRuleText.replace("_tn", getTrackingPredicate("")); 
		
		Collection<DLClause> equalityRelatedClauses = new LinkedList<DLClause>(); 
		Variable X = Variable.create("X"); 
		AtomicRole trackingSameAs = AtomicRole.create(Namespace.EQUALITY + "_tn");  
		OWLOntology onto = program.getOntology();
		Atom[] headAtom = new Atom[]{Atom.create(trackingSameAs, X, X)}, bodyAtom;
		for(OWLOntology o : onto.getImportsClosure())
			for(OWLClass cls : o.getClassesInSignature()) {
			String clsIRI = cls.getIRI().toString();
			unaryPredicates.add(clsIRI); 
			bodyAtom = new Atom[] {
					Atom.create(AtomicConcept.create(clsIRI + "_tn"), X),
					Atom.create(AtomicConcept.create(GapTupleIterator.getGapPredicate(clsIRI)), X)}; 
			equalityRelatedClauses.add(DLClause.create(headAtom, bodyAtom)); 
		}

		Variable Y = Variable.create("Y");
		Set<OWLObjectProperty> setOfProperties = new HashSet<OWLObjectProperty>();
		for(OWLOntology o : onto.getImportsClosure())
			for(OWLObjectProperty prop : o.getObjectPropertiesInSignature())
				setOfProperties.add(prop);
		setOfProperties.add(onto.getOWLOntologyManager()
								.getOWLDataFactory()
								.getOWLObjectProperty(IRI.create(Namespace.INEQUALITY)));
		for(OWLObjectProperty prop : setOfProperties) {
			String propIRI = prop.getIRI().toString();
			binaryPredicates.add(propIRI); 
			AtomicRole trackingRole = AtomicRole.create(propIRI + "_tn"); 
			AtomicRole gapRole = AtomicRole.create(GapTupleIterator.getGapPredicate(propIRI)); 
//			AtomicRole role = AtomicRole.create(propIRI); 
			bodyAtom = new Atom[] {
					Atom.create(trackingRole, X, Y), 
					Atom.create(gapRole, X, Y)}; 
			equalityRelatedClauses.add(DLClause.create(headAtom, bodyAtom));
			
			bodyAtom = new Atom[] {
					Atom.create(trackingRole, Y, X), 
					Atom.create(gapRole, Y, X)}; 
			equalityRelatedClauses.add(DLClause.create(headAtom, bodyAtom)); 
		}
		
		equalityRelatedClauses.add(
				DLClause.create(
						new Atom[] {Atom.create(trackingSameAs, Y, X)}, 
						new Atom[] {Atom.create(trackingSameAs, X, Y)}));
		
		equalityRelatedRuleText = DLClauseHelper.toString(equalityRelatedClauses).toString();
		return equalityRelatedRuleText.replace("_tn", getTrackingPredicate("")); 
	}

	@Override
	protected void encodingRule(DLClause clause) {
		LinkedList<Atom> newHeadAtoms = new LinkedList<Atom>();
		newHeadAtoms.add(Atom.create(selected, getIndividual4GeneralRule(clause)));
		
		Atom headAtom;
		for (Atom atom: clause.getBodyAtoms()) {
			headAtom = Atom.create(
					getTrackingDLPredicate(atom.getDLPredicate()), 
					DLClauseHelper.getArguments(atom));
			newHeadAtoms.add(headAtom);
		}

		DLClause newClause;
		headAtom = clause.getHeadAtom(0);
		
		boolean equalityHead = headAtom.getDLPredicate() instanceof Equality || headAtom.getDLPredicate() instanceof AnnotatedEquality; 
		int offset = (equalityHead || (clause.getBodyLength() == 1 && clause.getBodyAtom(0).getDLPredicate().toString().contains("owl:Nothing"))) ? 1 : 2;
		
		Atom[] newBodyAtoms = new Atom[clause.getBodyLength() + offset];
		newBodyAtoms[0] = Atom.create(
				getTrackingDLPredicate(headAtom.getDLPredicate()), 
				DLClauseHelper.getArguments(headAtom));
		
		if (offset == 2)
		newBodyAtoms[1] = Atom.create(
				getGapDLPredicate(headAtom.getDLPredicate()), 
				DLClauseHelper.getArguments(headAtom));

		for (int i = 0; i < clause.getBodyLength(); ++i)
			newBodyAtoms[i + offset] = clause.getBodyAtom(i); 
		
		for (Atom atom: newHeadAtoms) {
			newClause = DLClause.create(new Atom[] {atom}, newBodyAtoms); 
			trackingClauses.add(newClause);
		}
		
	}
}
