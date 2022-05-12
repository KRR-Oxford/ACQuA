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

import org.semanticweb.karma2.profile.ELHOProfile;
import org.semanticweb.owlapi.model.OWLOntology;
// import org.semanticweb.owlapi.model.parameters.Imports;
// import uk.ac.ox.cs.JRDFox.JRDFStoreException;
import uk.ac.ox.cs.pagoda.multistage.MultiStageQueryEngine
// import uk.ac.ox.cs.pagoda.owl.EqualitiesEliminator;
// import uk.ac.ox.cs.pagoda.owl.OWLHelper;
// import uk.ac.ox.cs.pagoda.query.AnswerTuples;
// import uk.ac.ox.cs.pagoda.query.GapByStore4ID;
// import uk.ac.ox.cs.pagoda.query.GapByStore4ID2;
import uk.ac.ox.cs.pagoda.query.QueryRecord
// import uk.ac.ox.cs.pagoda.query.QueryRecord.Step;
import uk.ac.ox.cs.pagoda.reasoner.{ConsistencyManager,MyQueryReasoner,QueryReasoner}
import uk.ac.ox.cs.pagoda.reasoner.light.{KarmaQueryEngine,BasicQueryEngine}
import uk.ac.ox.cs.pagoda.rules.DatalogProgram
// import uk.ac.ox.cs.pagoda.summary.HermitSummaryFilter;
// import uk.ac.ox.cs.pagoda.tracking.QueryTracker;
// import uk.ac.ox.cs.pagoda.tracking.TrackingRuleEncoder;
// import uk.ac.ox.cs.pagoda.tracking.TrackingRuleEncoderDisjVar1;
// import uk.ac.ox.cs.pagoda.tracking.TrackingRuleEncoderWithGap;
// import uk.ac.ox.cs.pagoda.util.ExponentialInterpolation;
// import uk.ac.ox.cs.pagoda.util.PagodaProperties;
import uk.ac.ox.cs.pagoda.util.Timer;
import uk.ac.ox.cs.pagoda.util.Utility
// import uk.ac.ox.cs.pagoda.util.disposable.DisposedException;
// import uk.ac.ox.cs.pagoda.util.tuples.Tuple;
import uk.ac.ox.cs.rsacomb.ontology.Ontology

// import java.util.Collection;
// import java.util.LinkedList;

class AcquaQueryReasoner(var ontology: Ontology)
  extends QueryReasoner {

//    OWLOntology ontology;
//    OWLOntology elho_ontology;
//    DatalogProgram program;

  private var lazyUpperStore: Option[MultiStageQueryEngine] = None;
//    TrackingRuleEncoder encoder;


//    private Collection<String> predicatesWithGap = null;
////    private int relevantOntologiesCounter = 0;
  private val timer: Timer = new Timer();

  private var _isConsistent: ConsistencyStatus = StatusUnchecked
  // TODO: explicit casting to MyQueryReasoner makes no sense. Find
  // another solution. Probably requires changing PAGOdA source code.
  private val consistencyManager: ConsistencyManager  = new ConsistencyManager(this.asInstanceOf[MyQueryReasoner])

  private val rlLowerStore: BasicQueryEngine = new BasicQueryEngine("rl-lower-bound")
  private val elLowerStore: KarmaQueryEngine = new KarmaQueryEngine("elho-lower-bound")

  private val trackingStore = new MultiStageQueryEngine("tracking", false);

  /* Load ontology into PAGOdA */
  private val datalog = new DatalogProgram(ontology.origin);
  //datalog.getGeneral().save();
  if (!datalog.getGeneral().isHorn())
    lazyUpperStore = Some(new MultiStageQueryEngine("lazy-upper-bound", true))
  importData(datalog.getAdditionalDataFile());
  private val elhoOntology: OWLOntology = new ELHOProfile().getFragment(ontology.origin);
  elLowerStore.processOntology(elhoOntology);


  /** Performs nothing.
    *
    * Loading of the ontology is performed at instance creation to avoid
    * unnecessary complexity (see main class constructor).
    * 
    * @note Implemented for compatibility with other reasoners.
    */
  def loadOntology(ontology: OWLOntology): Unit = { }

  def preprocess(): Boolean = {
    ???
  }

  /** Returns a the consistency status of the ontology.
    *
    * Performs a consistency check if the current status is undefined.
    * Some logging is performed as well.
    *
    * @returns true if the ontology is consistent, false otherwise.
    */
  def isConsistent(): Boolean = {
    if (_isConsistent == StatusUnchecked) {
      _isConsistent = consistencyManager.check()
      Utility logDebug s"time for satisfiability checking: ${timer.duration()}"
    }
    Utility logInfo s"The ontology is ${_isConsistent}!"
    return _isConsistent.asBoolean
  }

  def evaluate(query: QueryRecord): Unit = {
    ???
  }

  def evaluateUpper(record: QueryRecord): Unit= ???

//    public Collection<String> getPredicatesWithGap() {
//        if(isDisposed()) throw new DisposedException();
//        return predicatesWithGap;
//    }

//    @Override
//    public boolean preprocess() {
//        if(isDisposed()) throw new DisposedException();

//        t.reset();
//        Utility.logInfo("Preprocessing (and checking satisfiability)...");

//        String name = "data", datafile = getImportedData();
//        rlLowerStore.importRDFData(name, datafile);
//        rlLowerStore.materialise("lower program", program.getLower().toString());
////		program.getLower().save();
//        if(!consistency.checkRLLowerBound()) {
//            Utility.logDebug("time for satisfiability checking: " + t.duration());
//            isConsistent = ConsistencyStatus.INCONSISTENT;
//            return false;
//        }
//        Utility.logDebug("The number of sameAs assertions in RL lower store: " + rlLowerStore.getSameAsNumber());

//        String originalMarkProgram = OWLHelper.getOriginalMarkProgram(ontology);

//        elLowerStore.importRDFData(name, datafile);
//        elLowerStore.materialise("saturate named individuals", originalMarkProgram);
//        elLowerStore.materialise("lower program", program.getLower().toString());
//        elLowerStore.initialiseKarma();
//        if(!consistency.checkELLowerBound()) {
//            Utility.logDebug("time for satisfiability checking: " + t.duration());
//            isConsistent = ConsistencyStatus.INCONSISTENT;
//            return false;
//        }

//        if(lazyUpperStore != null) {
//            lazyUpperStore.importRDFData(name, datafile);
//            lazyUpperStore.materialise("saturate named individuals", originalMarkProgram);
//            int tag = lazyUpperStore.materialiseRestrictedly(program, null);
//            if(tag == -1) {
//                Utility.logDebug("time for satisfiability checking: " + t.duration());
//                isConsistent = ConsistencyStatus.INCONSISTENT;
//                return false;
//            }
//            else if(tag != 1) {
//                lazyUpperStore.dispose();
//                lazyUpperStore = null;
//            }
//        }
//        if(consistency.checkUpper(lazyUpperStore)) {
//            isConsistent = ConsistencyStatus.CONSISTENT;
//            Utility.logDebug("time for satisfiability checking: " + t.duration());
//        }

//        trackingStore.importRDFData(name, datafile);
//        trackingStore.materialise("saturate named individuals", originalMarkProgram);

////		materialiseFullUpper();
////		GapByStore4ID gap = new GapByStore4ID(trackingStore);
//        GapByStore4ID gap = new GapByStore4ID2(trackingStore, rlLowerStore);
//        trackingStore.materialiseFoldedly(program, gap);
//        predicatesWithGap = gap.getPredicatesWithGap();
//        gap.clear();

//        if(program.getGeneral().isHorn())
//            encoder = new TrackingRuleEncoderWithGap(program.getUpper(), trackingStore);
//        else
//            encoder = new TrackingRuleEncoderDisjVar1(program.getUpper(), trackingStore);
////			encoder = new TrackingRuleEncoderDisj1(program.getUpper(), trackingStore);
////			encoder = new TrackingRuleEncoderDisjVar2(program.getUpper(), trackingStore);
////			encoder = new TrackingRuleEncoderDisj2(program.getUpper(), trackingStore);

//        // TODO? add consistency check by Skolem-upper-bound

//        if(!isConsistent())
//            return false;

//        consistency.extractBottomFragment();

//        return true;
//    }

//    @Override
//    public void evaluate(QueryRecord queryRecord) {
//        if(isDisposed()) throw new DisposedException();

//        if(queryLowerAndUpperBounds(queryRecord))
//            return;

//        OWLOntology relevantOntologySubset = extractRelevantOntologySubset(queryRecord);

////        queryRecord.saveRelevantOntology("/home/alessandro/Desktop/test-relevant-ontology-"+relevantOntologiesCounter+".owl");
////        relevantOntologiesCounter++;

//        if(properties.getSkolemUpperBound() == PagodaProperties.SkolemUpperBoundOptions.BEFORE_SUMMARISATION
//                && querySkolemisedRelevantSubset(relevantOntologySubset, queryRecord)) {
//            return;
//        }

//        Utility.logInfo(">> Summarisation <<");
//        HermitSummaryFilter summarisedChecker = new HermitSummaryFilter(queryRecord, properties.getToCallHermiT());
//        if(summarisedChecker.check(queryRecord.getGapAnswers()) == 0) {
//            summarisedChecker.dispose();
//            return;
//        }

//        if(properties.getSkolemUpperBound() == PagodaProperties.SkolemUpperBoundOptions.AFTER_SUMMARISATION
//                && querySkolemisedRelevantSubset(relevantOntologySubset, queryRecord)) {
//            summarisedChecker.dispose();
//            return;
//        }

//        Utility.logInfo(">> Full reasoning <<");
//        Timer t = new Timer();
//        summarisedChecker.checkByFullReasoner(queryRecord.getGapAnswers());
//        Utility.logDebug("Total time for full reasoner: " + t.duration());

//        if(properties.getToCallHermiT())
//            queryRecord.markAsProcessed();
//        summarisedChecker.dispose();
//    }

//    @Override
//    public void evaluateUpper(QueryRecord queryRecord) {
//        if(isDisposed()) throw new DisposedException();
//        // TODO? add new upper store
//        AnswerTuples rlAnswer = null;
//        boolean useFull = queryRecord.isBottom() || lazyUpperStore == null;
//        try {
//            rlAnswer =
//                    (useFull ? trackingStore : lazyUpperStore).evaluate(queryRecord.getQueryText(), queryRecord.getAnswerVariables());
//            queryRecord.updateUpperBoundAnswers(rlAnswer, true);
//        } finally {
//            if(rlAnswer != null) rlAnswer.dispose();
//        }
//    }

//    @Override
//    public void dispose() {
//        super.dispose();

//        if(encoder != null) encoder.dispose();
//        if(rlLowerStore != null) rlLowerStore.dispose();
//        if(lazyUpperStore != null) lazyUpperStore.dispose();
//        if(elLowerStore != null) elLowerStore.dispose();
//        if(trackingStore != null) trackingStore.dispose();
//        if(consistency != null) consistency.dispose();
//        if(program != null) program.dispose();
//    }

//    protected void internal_importDataFile(String name, String datafile) {
////		addDataFile(datafile);
//        rlLowerStore.importRDFData(name, datafile);
//        if(lazyUpperStore != null)
//            lazyUpperStore.importRDFData(name, datafile);
//        elLowerStore.importRDFData(name, datafile);
//        trackingStore.importRDFData(name, datafile);
//    }

//    /**
//     * It deals with blanks nodes differently from variables
//     * according to SPARQL semantics for OWL2 Entailment Regime.
//     * <p>
//     * In particular variables are matched only against named individuals,
//     * and blank nodes against named and anonymous individuals.
//     */
//    private boolean queryUpperStore(BasicQueryEngine upperStore, QueryRecord queryRecord,
//                                    Tuple<String> extendedQuery, Step step) {
//        t.reset();

//        Utility.logDebug("First query type");
//        queryUpperBound(upperStore, queryRecord, queryRecord.getQueryText(), queryRecord.getAnswerVariables());
//        if(!queryRecord.isProcessed() && !queryRecord.getQueryText().equals(extendedQuery.get(0))) {
//            Utility.logDebug("Second query type");
//            queryUpperBound(upperStore, queryRecord, extendedQuery.get(0), queryRecord.getAnswerVariables());
//        }
//        if(!queryRecord.isProcessed() && queryRecord.hasNonAnsDistinguishedVariables()) {
//            Utility.logDebug("Third query type");
//            queryUpperBound(upperStore, queryRecord, extendedQuery.get(1), queryRecord.getDistinguishedVariables());
//        }

//        queryRecord.addProcessingTime(step, t.duration());
//        if(queryRecord.isProcessed()) {
//            queryRecord.setDifficulty(step);
//            return true;
//        }
//        return false;
//    }

//    /**
//     * Returns the part of the ontology relevant for Hermit, while computing the bound answers.
//     */
//    private boolean queryLowerAndUpperBounds(QueryRecord queryRecord) {

//        Utility.logInfo(">> Base bounds <<");

//        AnswerTuples rlAnswer = null, elAnswer = null;

//        t.reset();
//        try {
//            rlAnswer = rlLowerStore.evaluate(queryRecord.getQueryText(), queryRecord.getAnswerVariables());
//            Utility.logDebug(t.duration());
//            queryRecord.updateLowerBoundAnswers(rlAnswer);
//        } finally {
//            if(rlAnswer != null) rlAnswer.dispose();
//        }
//        queryRecord.addProcessingTime(Step.LOWER_BOUND, t.duration());

//        Tuple<String> extendedQueryTexts = queryRecord.getExtendedQueryText();

//        if(properties.getUseAlwaysSimpleUpperBound() || lazyUpperStore == null) {
//            Utility.logDebug("Tracking store");
//            if(queryUpperStore(trackingStore, queryRecord, extendedQueryTexts, Step.SIMPLE_UPPER_BOUND))
//                return true;
//        }

//        if(!queryRecord.isBottom()) {
//            Utility.logDebug("Lazy store");
//            if(lazyUpperStore != null && queryUpperStore(lazyUpperStore, queryRecord, extendedQueryTexts, Step.LAZY_UPPER_BOUND))
//                return true;
//        }

//        t.reset();
//        try {
//            elAnswer = elLowerStore.evaluate(extendedQueryTexts.get(0),
//                                             queryRecord.getAnswerVariables(),
//                                             queryRecord.getLowerBoundAnswers());
//            Utility.logDebug(t.duration());
//            queryRecord.updateLowerBoundAnswers(elAnswer);
//        } finally {
//            if(elAnswer != null) elAnswer.dispose();
//        }
//        queryRecord.addProcessingTime(Step.EL_LOWER_BOUND, t.duration());

//        if(queryRecord.isProcessed()) {
//            queryRecord.setDifficulty(Step.EL_LOWER_BOUND);
//            return true;
//        }

//        return false;
//    }

//    private OWLOntology extractRelevantOntologySubset(QueryRecord queryRecord) {
//        Utility.logInfo(">> Relevant ontology-subset extraction <<");

//        t.reset();

//        QueryTracker tracker = new QueryTracker(encoder, rlLowerStore, queryRecord);
//        OWLOntology relevantOntologySubset = tracker.extract(trackingStore, consistency.getQueryRecords(), true);

//        queryRecord.addProcessingTime(Step.FRAGMENT, t.duration());

//        int numOfABoxAxioms = relevantOntologySubset.getABoxAxioms(Imports.INCLUDED).size();
//        int numOfTBoxAxioms = relevantOntologySubset.getAxiomCount() - numOfABoxAxioms;
//        Utility.logInfo("Relevant ontology-subset has been extracted: |ABox|="
//                                + numOfABoxAxioms + ", |TBox|=" + numOfTBoxAxioms);

//        return relevantOntologySubset;
//    }

//    private void queryUpperBound(BasicQueryEngine upperStore, QueryRecord queryRecord, String queryText, String[] answerVariables) {
//        AnswerTuples rlAnswer = null;
//        try {
//            Utility.logDebug(queryText);
//            rlAnswer = upperStore.evaluate(queryText, answerVariables);
//            Utility.logDebug(t.duration());
//            queryRecord.updateUpperBoundAnswers(rlAnswer);
//        } finally {
//            if(rlAnswer != null) rlAnswer.dispose();
//        }
//    }

//    private boolean querySkolemisedRelevantSubset(OWLOntology relevantSubset, QueryRecord queryRecord) {
//        Utility.logInfo(">> Semi-Skolemisation <<");
//        t.reset();

//        DatalogProgram relevantProgram = new DatalogProgram(relevantSubset);

//        MultiStageQueryEngine relevantStore =
//                new MultiStageQueryEngine("Relevant-store", true); // checkValidity is true

//        relevantStore.importDataFromABoxOf(relevantSubset);
//        String relevantOriginalMarkProgram = OWLHelper.getOriginalMarkProgram(relevantSubset);

//        relevantStore.materialise("Mark original individuals", relevantOriginalMarkProgram);

//        boolean isFullyProcessed = false;
//        LinkedList<Tuple<Long>> lastTwoTriplesCounts = new LinkedList<>();
//        for (int currentMaxTermDepth = 1; !isFullyProcessed; currentMaxTermDepth++) {

//            if(currentMaxTermDepth > properties.getSkolemDepth()) {
//                Utility.logInfo("Maximum term depth reached");
//                break;
//            }

//            if(lastTwoTriplesCounts.size() == 2) {
//                if(lastTwoTriplesCounts.get(0).get(1).equals(lastTwoTriplesCounts.get(1).get(1)))
//                    break;

//                ExponentialInterpolation interpolation = new ExponentialInterpolation(lastTwoTriplesCounts.get(0).get(0),
//                        lastTwoTriplesCounts.get(0).get(1),
//                        lastTwoTriplesCounts.get(1).get(0),
//                        lastTwoTriplesCounts.get(1).get(1));
//                double triplesEstimate = interpolation.computeValue(currentMaxTermDepth);

//                Utility.logDebug("Estimate of the number of triples:" + triplesEstimate);

//                // exit condition if the query is not fully answered
//                if(triplesEstimate > properties.getMaxTriplesInSkolemStore()) {
//                    Utility.logInfo("Interrupting Semi-Skolemisation because of triples count limit");
//                    break;
//                }
//            }

//            Utility.logInfo("Trying with maximum depth " + currentMaxTermDepth);

//            int materialisationTag = relevantStore.materialiseSkolemly(relevantProgram, null,
//                    currentMaxTermDepth);
//            queryRecord.addProcessingTime(Step.SKOLEM_UPPER_BOUND, t.duration());
//            if(materialisationTag == -1) {
//                relevantStore.dispose();
//                throw new Error("A consistent ontology has turned out to be " +
//                                        "inconsistent in the Skolemises-relevant-upper-store");
//            }
//            else if(materialisationTag != 1) {
//                Utility.logInfo("Semi-Skolemised relevant upper store cannot be employed");
//                break;
//            }

//            Utility.logInfo("Querying semi-Skolemised upper store...");
//            isFullyProcessed = queryUpperStore(relevantStore, queryRecord,
//                    queryRecord.getExtendedQueryText(),
//                    Step.SKOLEM_UPPER_BOUND);

//            try {
//                lastTwoTriplesCounts.add
//                        (new Tuple<>((long) currentMaxTermDepth, relevantStore.getStoreSize()));
//            } catch (JRDFStoreException e) {
//                e.printStackTrace();
//                break;
//            }
//            if(lastTwoTriplesCounts.size() > 2)
//                lastTwoTriplesCounts.remove();

//            Utility.logDebug("Last two triples counts:" + lastTwoTriplesCounts);
//        }

//        relevantStore.dispose();
//        Utility.logInfo("Semi-Skolemised relevant upper store has been evaluated");
//        return isFullyProcessed;
//    }

  private sealed trait ConsistencyStatus {
    val asBoolean = false
  }
  private case object StatusConsistent extends ConsistencyStatus {
    override val asBoolean = true
    override def toString(): String = "consistent"
  }
  private case object StatusInconsistent extends ConsistencyStatus {
    override def toString(): String = "inconsistent"
  }
  private case object StatusUnchecked extends ConsistencyStatus {
    override def toString(): String = "N/A"
  }
  private implicit def boolean2consistencyStatus(b: Boolean): ConsistencyStatus = {
    if (b) StatusConsistent else StatusInconsistent
  }

}
