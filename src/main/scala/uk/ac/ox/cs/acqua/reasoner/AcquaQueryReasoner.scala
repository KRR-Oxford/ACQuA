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

import scala.collection.JavaConverters._
import org.semanticweb.karma2.profile.ELHOProfile
import org.semanticweb.owlapi.model.OWLOntology
// import org.semanticweb.owlapi.model.parameters.Imports;
// import uk.ac.ox.cs.JRDFox.JRDFStoreException;
import uk.ac.ox.cs.pagoda.multistage.MultiStageQueryEngine
// import uk.ac.ox.cs.pagoda.owl.EqualitiesEliminator;
import uk.ac.ox.cs.pagoda.owl.OWLHelper
import uk.ac.ox.cs.pagoda.query.{
  AnswerTuples,
  GapByStore4ID,
  GapByStore4ID2,
  QueryRecord,
}
import uk.ac.ox.cs.pagoda.query.QueryRecord.Step;
import uk.ac.ox.cs.pagoda.reasoner.{
  ConsistencyManager,
  MyQueryReasoner,
  QueryReasoner
}
import uk.ac.ox.cs.pagoda.reasoner.light.{KarmaQueryEngine,BasicQueryEngine}
import uk.ac.ox.cs.pagoda.rules.DatalogProgram
// import uk.ac.ox.cs.pagoda.summary.HermitSummaryFilter;
// import uk.ac.ox.cs.pagoda.tracking.QueryTracker;
import uk.ac.ox.cs.pagoda.tracking.{
  TrackingRuleEncoder,
  TrackingRuleEncoderDisjVar1,
  TrackingRuleEncoderWithGap,
}
// import uk.ac.ox.cs.pagoda.util.ExponentialInterpolation;
// import uk.ac.ox.cs.pagoda.util.PagodaProperties;
import uk.ac.ox.cs.pagoda.util.Timer
import uk.ac.ox.cs.pagoda.util.Utility
// import uk.ac.ox.cs.pagoda.util.disposable.DisposedException;
import uk.ac.ox.cs.pagoda.util.tuples.Tuple;
import uk.ac.ox.cs.rsacomb.ontology.Ontology
import uk.ac.ox.cs.rsacomb.approximation.{Lowerbound,Upperbound}

// import java.util.Collection;
// import java.util.LinkedList;

class AcquaQueryReasoner(val ontology: Ontology)
  extends QueryReasoner {

  private var encoder: Option[TrackingRuleEncoder] = None
  private var lazyUpperStore: Option[MultiStageQueryEngine] = None;

  private val timer: Timer = new Timer();

  private var _isConsistent: ConsistencyStatus = StatusUnchecked
  // TODO: explicit casting to MyQueryReasoner makes no sense. Find
  // another solution. Probably requires changing PAGOdA source code.
  private val consistencyManager: ConsistencyManager  = new ConsistencyManager(this.asInstanceOf[MyQueryReasoner])

  private val rlLowerStore: BasicQueryEngine = new BasicQueryEngine("rl-lower-bound")
  private val elLowerStore: KarmaQueryEngine = new KarmaQueryEngine("elho-lower-bound")
  private lazy val lowerRSAOntology = ontology approximate (new Lowerbound)
  private lazy val upperRSAOntology = ontology approximate (new Upperbound)

  private val trackingStore = new MultiStageQueryEngine("tracking", false);

  var predicatesWithGap: Seq[String] = Seq.empty

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

  /** Preprocessing of input ontology.
    *
    * This is mostly PAGOdA related. Note that, while most of the
    * computation in RSAComb is performed "on-demand", we are forcing
    * the approximation from above/below of the input ontology to RSA,
    * and the compuation of their respective canonical models to make timing
    * measured more consistent.
    *
    * @returns whether the input ontology is found consistent after the
    *          preprocessing phase.
    */
  def preprocess(): Boolean = {
    timer.reset();
    Utility logInfo "Preprocessing (and checking satisfiability)..."

    val name = "data"
    val datafile = getImportedData()

    /* RL lower-bound check */
    rlLowerStore.importRDFData(name, datafile);
    rlLowerStore.materialise("lower program", datalog.getLower.toString);
    if (!consistencyManager.checkRLLowerBound()) {
      Utility logDebug s"time for satisfiability checking: ${timer.duration()}"
      _isConsistent = StatusInconsistent
      return false
    }
    Utility logDebug s"The number of 'sameAs' assertions in RL lower store: ${rlLowerStore.getSameAsNumber}"

    /* EHLO lower bound check */ 
    val originalMarkProgram = OWLHelper.getOriginalMarkProgram(ontology.origin)
    elLowerStore.importRDFData(name, datafile);
    elLowerStore.materialise("saturate named individuals", originalMarkProgram);
    elLowerStore.materialise("lower program", datalog.getLower.toString);
    elLowerStore.initialiseKarma();
    if (!consistencyManager.checkELLowerBound()) {
      Utility logDebug s"time for satisfiability checking: ${timer.duration()}"
      _isConsistent = StatusInconsistent
      return false
    }

    /* Lazy upper store */
    val tag = lazyUpperStore.map(store => {
      store.importRDFData(name, datafile)
      store.materialise("saturate named individuals", originalMarkProgram)
      store.materialiseRestrictedly(datalog, null)
    }).getOrElse(1)
    if (tag == -1) {
      Utility logDebug s"time for satisfiability checking: ${timer.duration()}"
      _isConsistent = StatusInconsistent
      return false
    }
    lazyUpperStore.flatMap(store => { store.dispose(); None })

    trackingStore.importRDFData(name, datafile)
    trackingStore.materialise("saturate named individuals", originalMarkProgram)
    val gap: GapByStore4ID = new GapByStore4ID2(trackingStore, rlLowerStore);
    trackingStore.materialiseFoldedly(datalog, gap);
    this.predicatesWithGap = gap.getPredicatesWithGap.asScala.toSeq;
    gap.clear();

    if (datalog.getGeneral.isHorn)
      encoder = Some(new TrackingRuleEncoderWithGap(datalog.getUpper, trackingStore))
    else
      encoder = Some(new TrackingRuleEncoderDisjVar1(datalog.getUpper, trackingStore))

    /* Perform consistency checking if not already inconsistent */
    if (!isConsistent()) return false
    consistencyManager.extractBottomFragment();

    /* Force computation of lower/upper RSA approximations */
    lowerRSAOntology//.computeCanonicalModel()
    upperRSAOntology//.computeCanonicalModel()

    true
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
    if(queryLowerAndUpperBounds(query))
      return;

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
    ???
  }

  def evaluateUpper(record: QueryRecord): Unit= ???

//    @Override
//    public void evaluate(QueryRecord queryRecord) {
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

  /**
   * Returns the part of the ontology relevant for Hermit, while computing the bound answers.
   */
  private def queryLowerAndUpperBounds(query: QueryRecord): Boolean = {
    Utility logInfo ">> Base bounds <<"
    val extendedQueryTexts: Tuple[String] = query.getExtendedQueryText()
    var rlAnswer: AnswerTuples = null
    var elAnswer: AnswerTuples = null

    /* Computing RL lower bound answers */
    timer.reset();
    try {
      rlAnswer = rlLowerStore.evaluate(query.getQueryText, query.getAnswerVariables)
      Utility logDebug timer.duration()
      query updateLowerBoundAnswers rlAnswer
    } finally {
        if (rlAnswer != null) rlAnswer.dispose()
    }
    query.addProcessingTime(Step.LOWER_BOUND, timer.duration());

    if(properties.getUseAlwaysSimpleUpperBound() || lazyUpperStore.isEmpty) {
        Utility logDebug "Tracking store"
        // if (queryUpperStore(trackingStore, query, extendedQueryTexts, Step.SIMPLE_UPPER_BOUND))
        //     return true;
    }

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

    return false;
  }

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
