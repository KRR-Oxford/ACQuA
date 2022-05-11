package uk.ac.ox.cs.pagoda.owl;

import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLAsymmetricObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLAxiomVisitorEx;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLClassExpressionVisitorEx;
import org.semanticweb.owlapi.model.OWLDataAllValuesFrom;
import org.semanticweb.owlapi.model.OWLDataExactCardinality;
import org.semanticweb.owlapi.model.OWLDataHasValue;
import org.semanticweb.owlapi.model.OWLDataMaxCardinality;
import org.semanticweb.owlapi.model.OWLDataMinCardinality;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLDataPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLDataSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLDatatypeDefinitionAxiom;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLDifferentIndividualsAxiom;
import org.semanticweb.owlapi.model.OWLDisjointClassesAxiom;
import org.semanticweb.owlapi.model.OWLDisjointDataPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLDisjointObjectPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLDisjointUnionAxiom;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLEquivalentDataPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLEquivalentObjectPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLFunctionalDataPropertyAxiom;
import org.semanticweb.owlapi.model.OWLFunctionalObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLHasKeyAxiom;
import org.semanticweb.owlapi.model.OWLInverseFunctionalObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLInverseObjectPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLIrreflexiveObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLNegativeDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLNegativeObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectAllValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectComplementOf;
import org.semanticweb.owlapi.model.OWLObjectExactCardinality;
import org.semanticweb.owlapi.model.OWLObjectHasSelf;
import org.semanticweb.owlapi.model.OWLObjectHasValue;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectMaxCardinality;
import org.semanticweb.owlapi.model.OWLObjectMinCardinality;
import org.semanticweb.owlapi.model.OWLObjectOneOf;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectUnionOf;
import org.semanticweb.owlapi.model.OWLReflexiveObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLSameIndividualAxiom;
import org.semanticweb.owlapi.model.OWLSubAnnotationPropertyOfAxiom;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.OWLSubDataPropertyOfAxiom;
import org.semanticweb.owlapi.model.OWLSubObjectPropertyOfAxiom;
import org.semanticweb.owlapi.model.OWLSubPropertyChainOfAxiom;
import org.semanticweb.owlapi.model.OWLSymmetricObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLTransitiveObjectPropertyAxiom;
import org.semanticweb.owlapi.model.SWRLRule;

public class MyHornAxiomVisitorEx implements OWLAxiomVisitorEx<Boolean> {
	final PositiveAppearanceVisitorEx positive = new PositiveAppearanceVisitorEx();
	final NegativeAppearanceVisitorEx negative = new NegativeAppearanceVisitorEx();

    @Override
    public Boolean visit(OWLSubAnnotationPropertyOfAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLAnnotationPropertyDomainAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLAnnotationPropertyRangeAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLSubClassOfAxiom axiom) {
        return Boolean.valueOf(axiom.getSubClass().accept(negative).booleanValue()
                && axiom.getSuperClass().accept(positive).booleanValue());
	}

    @Override
    public Boolean visit(OWLNegativeObjectPropertyAssertionAxiom axiom) {
		return Boolean.FALSE;
	}

    @Override
    public Boolean visit(OWLAsymmetricObjectPropertyAxiom axiom) {
		return Boolean.FALSE;
	}

    @Override
    public Boolean visit(OWLReflexiveObjectPropertyAxiom axiom) {
		return Boolean.FALSE;
	}

    @Override
    public Boolean visit(OWLDisjointClassesAxiom axiom) {
		for (OWLClassExpression c : axiom.getClassExpressions()) {
            if (!c.accept(negative).booleanValue()) {
				return Boolean.FALSE;
			}
		}
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLDataPropertyDomainAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLObjectPropertyDomainAxiom axiom) {
		return axiom.getDomain().accept(positive);
	}

    @Override
    public Boolean visit(OWLEquivalentObjectPropertiesAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLNegativeDataPropertyAssertionAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLDifferentIndividualsAxiom axiom) {
		return Boolean.FALSE;
	}

    @Override
    public Boolean visit(OWLDisjointDataPropertiesAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLDisjointObjectPropertiesAxiom axiom) {
		return Boolean.FALSE;
	}

    @Override
    public Boolean visit(OWLObjectPropertyRangeAxiom axiom) {
		return axiom.getRange().accept(positive);
	}

    @Override
    public Boolean visit(OWLObjectPropertyAssertionAxiom axiom) {
		return Boolean.FALSE;
	}

    @Override
    public Boolean visit(OWLFunctionalObjectPropertyAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLSubObjectPropertyOfAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLDisjointUnionAxiom axiom) {
		OWLClassExpression c1 = axiom.getOWLClass();
        if (!c1.accept(positive).booleanValue() || !c1.accept(negative).booleanValue()) {
			return Boolean.FALSE;
		}
		for (OWLClassExpression c : axiom.getClassExpressions()) {
            if (!c.accept(positive).booleanValue() || !c.accept(negative).booleanValue()) {
				return Boolean.FALSE;
			}
		}
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLDeclarationAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLAnnotationAssertionAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLSymmetricObjectPropertyAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLDataPropertyRangeAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLFunctionalDataPropertyAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLEquivalentDataPropertiesAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLClassAssertionAxiom axiom) {
		return Boolean.FALSE;
	}

    @Override
    public Boolean visit(OWLEquivalentClassesAxiom axiom) {
		for (OWLClassExpression c : axiom.getClassExpressions()) {
            if (!c.accept(positive).booleanValue() || !c.accept(negative).booleanValue()) {
				return Boolean.FALSE;
			}
		}
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLDataPropertyAssertionAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLTransitiveObjectPropertyAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLIrreflexiveObjectPropertyAxiom axiom) {
		return Boolean.FALSE;
	}

    @Override
    public Boolean visit(OWLSubDataPropertyOfAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLInverseFunctionalObjectPropertyAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLSameIndividualAxiom axiom) {
		return Boolean.FALSE;
	}

    @Override
    public Boolean visit(OWLSubPropertyChainOfAxiom axiom) {
		return Boolean.FALSE;
	}

    @Override
    public Boolean visit(OWLInverseObjectPropertiesAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(OWLHasKeyAxiom axiom) {
		return Boolean.FALSE;
	}

    @Override
    public Boolean visit(OWLDatatypeDefinitionAxiom axiom) {
		return Boolean.TRUE;
	}

    @Override
    public Boolean visit(SWRLRule rule) {
		return Boolean.FALSE;
	}

	private class PositiveAppearanceVisitorEx implements
			OWLClassExpressionVisitorEx<Boolean> {
		public PositiveAppearanceVisitorEx() {
		}

        @Override
        public Boolean visit(OWLClass ce) {
            return Boolean.TRUE;
		}

        @Override
        public Boolean visit(OWLObjectIntersectionOf ce) {
			for (OWLClassExpression c : ce.getOperands()) {
				if (c.accept(this).equals(Boolean.FALSE)) {
                    return Boolean.FALSE;
				}
			}
            return Boolean.TRUE;
		}

        @Override
        public Boolean visit(OWLObjectUnionOf ce) {
			return Boolean.FALSE;
		}

        @Override
        public Boolean visit(OWLObjectComplementOf ce) {
			return ce.getOperand().accept(negative);
		}

        @Override
        public Boolean visit(OWLObjectSomeValuesFrom ce) {
			return ce.getFiller().accept(this);
		}

        @Override
        public Boolean visit(OWLObjectAllValuesFrom ce) {
			return ce.getFiller().accept(this);
		}

        @Override
        public Boolean visit(OWLObjectHasValue ce) {
			return Boolean.FALSE;
		}

        @Override
        public Boolean visit(OWLObjectMinCardinality ce) {
			return ce.getFiller().accept(this);
		}

        @Override
        public Boolean visit(OWLObjectExactCardinality ce) {
            return Boolean.valueOf(ce.getCardinality() <= 1
                    && ce.getFiller().accept(this).booleanValue()
                    && ce.getFiller().accept(negative).booleanValue());
		}

        @Override
        public Boolean visit(OWLObjectMaxCardinality ce) {
            return Boolean.valueOf(ce.getCardinality() <= 1
                    && ce.getFiller().accept(negative).booleanValue());
		}

        @Override
        public Boolean visit(OWLObjectHasSelf ce) {
			return Boolean.FALSE;
		}

        @Override
        public Boolean visit(OWLObjectOneOf ce) {
			return Boolean.FALSE;
		}

        @Override
        public Boolean visit(OWLDataSomeValuesFrom ce) {
			return Boolean.TRUE;
		}

        @Override
        public Boolean visit(OWLDataAllValuesFrom ce) {
			return Boolean.TRUE;
		}

        @Override
        public Boolean visit(OWLDataHasValue ce) {
			return Boolean.TRUE;
		}

        @Override
        public Boolean visit(OWLDataMinCardinality ce) {
			return Boolean.TRUE; 
		}

        @Override
        public Boolean visit(OWLDataExactCardinality ce) {
			return ce.getCardinality() <= 1; 
		}

        @Override
        public Boolean visit(OWLDataMaxCardinality ce) {
			return ce.getCardinality() <= 1;
		}
	}

	private class NegativeAppearanceVisitorEx implements
			OWLClassExpressionVisitorEx<Boolean> {
		public NegativeAppearanceVisitorEx() {
		}

        @Override
        public Boolean visit(OWLClass ce) {
			return Boolean.TRUE;
		}

        @Override
        public Boolean visit(OWLObjectIntersectionOf ce) {
			for (OWLClassExpression c : ce.getOperands()) {
				if (c.accept(this).equals(Boolean.FALSE)) {
                    return Boolean.FALSE;
				}
			}
            return Boolean.TRUE;
		}

        @Override
        public Boolean visit(OWLObjectUnionOf ce) {
			for (OWLClassExpression c : ce.getOperands()) {
				if (c.accept(this).equals(Boolean.FALSE)) {
                    return Boolean.FALSE;
				}
			}
            return Boolean.TRUE;
		}

        @Override
        public Boolean visit(OWLObjectComplementOf ce) {
			return Boolean.FALSE;
		}

        @Override
        public Boolean visit(OWLObjectSomeValuesFrom ce) {
			return ce.getFiller().accept(this);
		}

        @Override
        public Boolean visit(OWLObjectAllValuesFrom ce) {
			return Boolean.FALSE;
		}

        @Override
        public Boolean visit(OWLObjectHasValue ce) {
            return Boolean.FALSE;
		}

        @Override
        public Boolean visit(OWLObjectMinCardinality ce) {
            return Boolean.valueOf(ce.getCardinality() <= 1
                    && ce.getFiller().accept(this).booleanValue());
		}

        @Override
        public Boolean visit(OWLObjectExactCardinality ce) {
			return Boolean.FALSE;
		}

        @Override
        public Boolean visit(OWLObjectMaxCardinality ce) {
			return Boolean.FALSE;
		}

        @Override
        public Boolean visit(OWLObjectHasSelf ce) {
			return Boolean.FALSE;
		}

        @Override
        public Boolean visit(OWLObjectOneOf ce) {
			return Boolean.FALSE;
		}

        @Override
        public Boolean visit(OWLDataSomeValuesFrom ce) {
			return Boolean.TRUE;
		}

        @Override
        public Boolean visit(OWLDataAllValuesFrom ce) {
			return Boolean.FALSE;
		}

        @Override
        public Boolean visit(OWLDataHasValue ce) {
			return Boolean.TRUE;
		}

        @Override
        public Boolean visit(OWLDataMinCardinality ce) {
			return ce.getCardinality() <= 1;
		}

        @Override
        public Boolean visit(OWLDataExactCardinality ce) {
			return Boolean.FALSE;
		}

        @Override
        public Boolean visit(OWLDataMaxCardinality ce) {
			return Boolean.FALSE;
		}
	}
}
