package uk.ac.ox.cs.pagoda.rules.approximators;

import org.semanticweb.HermiT.model.*;
import uk.ac.ox.cs.pagoda.util.Namespace;
import uk.ac.ox.cs.pagoda.util.tuples.Tuple;

import java.util.HashMap;
import java.util.Map;

/**
 * If you need a Skolem term (i.e. fresh individual), ask this class.
 */
public class SkolemTermsManager {

    public static final String SKOLEMISED_INDIVIDUAL_PREFIX = Namespace.PAGODA_ANONY + "individual";
    public static final String RULE_UNIQUE_SKOLEMISED_INDIVIDUAL_PREFIX = SKOLEMISED_INDIVIDUAL_PREFIX + "_unique";

    private static SkolemTermsManager skolemTermsManager;

    private int termsCounter = 0;
    private Map<DLClause, Integer> clauseToId_map = new HashMap<>();
    private Map<Individual, Integer> individualToDepth_map = new HashMap<>();
    private int dependenciesCounter = 0;

    private Map<Tuple<Individual>, Integer> dependencyToId_map = new HashMap<>();

    private SkolemTermsManager() {
    }

    public static int indexOfSkolemisedIndividual(Atom atom) {
        Term t;
        for(int index = 0; index < atom.getArity(); ++index) {
            t = atom.getArgument(index);
            if(t instanceof Individual && ((Individual) t).getIRI().contains(SKOLEMISED_INDIVIDUAL_PREFIX))
                return index;
        }
        return -1;
    }

    /**
     * Returns the existing unique <tt>SkolemTermsManager</tt> or a new one.
     * <p>
     * Indeed the <tt>SkolemTermsManager</tt> is a singleton.
     */
    public static SkolemTermsManager getInstance() {
        if(skolemTermsManager == null) skolemTermsManager = new SkolemTermsManager();
        return skolemTermsManager;
    }

    /**
     * Get a fresh Individual, unique for the clause, the offset and the dependency.
     */
    public Individual getFreshIndividual(DLClause originalClause, int offset, Tuple<Individual> dependency) {
        String termId = Integer.toString(mapClauseToId(originalClause) + offset)
                + "_" + mapDependencyToId(dependency);
        Individual newIndividual = Individual.create(SKOLEMISED_INDIVIDUAL_PREFIX + termId);

        if(!individualToDepth_map.containsKey(newIndividual)) {
            int depth = 0;
            for (Individual individual : dependency)
                depth = Integer.max(depth, getDepthOf(individual));
            individualToDepth_map.put(newIndividual, depth + 1);
        }

        return newIndividual;
    }

    /***
     * Create a term of a given depth, unique for the clause and the depth.
     *
     * @param originalClause
     * @param offset
     * @param depth
     * @return
     */
    public Individual getFreshIndividual(DLClause originalClause, int offset, int depth) {
        String termId = Integer.toString(mapClauseToId(originalClause) + offset) + "_depth_" + depth;
        Individual newIndividual = Individual.create(RULE_UNIQUE_SKOLEMISED_INDIVIDUAL_PREFIX + termId);

        individualToDepth_map.putIfAbsent(newIndividual, depth);

        return newIndividual;
    }

    /**
     * Get a fresh Individual, unique for the clause and the offset.
     */
    public Individual getFreshIndividual(DLClause originalClause, int offset) {
        String termId = Integer.toString(mapClauseToId(originalClause) + offset);
        return Individual.create(SKOLEMISED_INDIVIDUAL_PREFIX + termId);
    }

    /**
     * Get the depth of a term.
     * <p>
     * The term must have been generated by this manager.
     */
    public int getDepthOf(Individual individual) {
        if(individualToDepth_map.containsKey(individual)) return individualToDepth_map.get(individual);
        else return 0;
    }

    /**
     * Get the number of individuals generated by this manager.
     */
    public int getSkolemIndividualsCount() {
        return individualToDepth_map.keySet().size();
    }

    /**
     * Just for reading the clause id from <tt>LimitedSkolemisationApproximator</tt>.
     */
    int getClauseId(DLClause clause) {
        return clauseToId_map.get(clause);
    }

    private int mapClauseToId(DLClause clause) {
        if(!clauseToId_map.containsKey(clause)) {
            clauseToId_map.put(clause, termsCounter);
            termsCounter += noOfExistential(clause);
        }
        return clauseToId_map.get(clause);
    }

    private int mapDependencyToId(Tuple<Individual> dependency) {
        if(!dependencyToId_map.containsKey(dependency))
            dependencyToId_map.put(dependency, dependenciesCounter++);
        return dependencyToId_map.get(dependency);
    }

    private int noOfExistential(DLClause originalClause) {
        int no = 0;
        for(Atom atom : originalClause.getHeadAtoms())
            if(atom.getDLPredicate() instanceof AtLeast)
                no += ((AtLeast) atom.getDLPredicate()).getNumber();
        return no;
    }

}
