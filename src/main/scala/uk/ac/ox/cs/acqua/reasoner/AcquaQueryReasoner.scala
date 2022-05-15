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

import java.util.LinkedList;

import scala.collection.JavaConverters._
import org.semanticweb.karma2.profile.ELHOProfile
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.model.parameters.Imports
import uk.ac.ox.cs.JRDFox.JRDFStoreException;
import uk.ac.ox.cs.pagoda.multistage.MultiStageQueryEngine
import uk.ac.ox.cs.pagoda.owl.OWLHelper
import uk.ac.ox.cs.pagoda.query.{
  AnswerTuples,
  GapByStore4ID,
  GapByStore4ID2,
  QueryRecord,
}
import uk.ac.ox.cs.pagoda.query.QueryRecord.Step
import uk.ac.ox.cs.pagoda.reasoner.{
  ConsistencyManager,
  MyQueryReasoner,
  QueryReasoner
}
import uk.ac.ox.cs.pagoda.reasoner.light.{KarmaQueryEngine,BasicQueryEngine}
import uk.ac.ox.cs.pagoda.rules.DatalogProgram
import uk.ac.ox.cs.pagoda.summary.HermitSummaryFilter;
import uk.ac.ox.cs.pagoda.tracking.{
  QueryTracker,
  TrackingRuleEncoder,
  TrackingRuleEncoderDisjVar1,
  TrackingRuleEncoderWithGap,
}
import uk.ac.ox.cs.pagoda.util.{
  ExponentialInterpolation,
  PagodaProperties,
  Timer,
  Utility
}
import uk.ac.ox.cs.pagoda.util.tuples.Tuple;
import uk.ac.ox.cs.rsacomb.ontology.Ontology
import uk.ac.ox.cs.rsacomb.approximation.{Lowerbound,Upperbound}

class AcquaQueryReasoner(val ontology: Ontology)
  extends QueryReasoner {

  /** Compatibility convertions between PAGOdA and RSAComb */
  import uk.ac.ox.cs.acqua.implicits.PagodaConverters._

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

    /* Force computation of lower RSA approximations and its canonical
     * model. We wait to process the upperbound since it might not be
     * necessary after all. */
    lowerRSAOntology.computeCanonicalModel()
    //upperRSAOntology.computeCanonicalModel()

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

  /** Evaluate a query against this reasoner.
    *
    * This is the main entry to compute the answers to a query.
    * By the end of the computation, the query record passed as input
    * will contain the answers found during the answering process.
    * This behaves conservately and will try very hard not to perform
    * unnecessary computation.
    *
    * @param query the query record to evaluate.
    */
  def evaluate(query: QueryRecord): Unit = {
    val processed =
      queryLowerAndUpperBounds(query) ||
      queryRSALowerBound(query) ||
      queryRSAUpperBound(query)
    if (!processed) {
      val relevantOntologySubset: OWLOntology =
        extractRelevantOntologySubset(query)

      if (properties.getSkolemUpperBound == PagodaProperties.SkolemUpperBoundOptions.BEFORE_SUMMARISATION &&
          querySkolemisedRelevantSubset(relevantOntologySubset, query)
      ) return;

      Utility logInfo ">> Summarisation <<"
      val summarisedChecker: HermitSummaryFilter =
        new HermitSummaryFilter(query, properties.getToCallHermiT)
      if(summarisedChecker.check(query.getGapAnswers) == 0) {
        summarisedChecker.dispose()
        return;
      }

      if (properties.getSkolemUpperBound == PagodaProperties.SkolemUpperBoundOptions.AFTER_SUMMARISATION &&
          querySkolemisedRelevantSubset(relevantOntologySubset, query)
      ) {
        summarisedChecker.dispose()
        return;
      }

      Utility logInfo ">> Full reasoning <<"
      timer.reset()
      summarisedChecker checkByFullReasoner query.getGapAnswers
      Utility logDebug s"Total time for full reasoner: ${timer.duration()}"

      if (properties.getToCallHermiT) query.markAsProcessed()

      summarisedChecker.dispose()
    }
  }

  /** Only compute the upperbound for a query.
    *
    * @note this is not supported at the moment. Look at
    * [[uk.ac.ox.cs.pagoda.reasoner.MyQueryReasoner]] for an example
    * implementation.
    */
  def evaluateUpper(record: QueryRecord): Unit = ???

  /** Clean up the query reasoner */
  override def dispose(): Unit = {
    super.dispose()
    if(encoder.isDefined) encoder.get.dispose()
    if(rlLowerStore != null) rlLowerStore.dispose();
    if(lazyUpperStore.isDefined) lazyUpperStore.get.dispose();
    if(elLowerStore != null) elLowerStore.dispose();
    if(trackingStore != null) trackingStore.dispose();
    if(consistencyManager != null) consistencyManager.dispose();
    if(datalog != null) datalog.dispose();
  }

  /** Perform CQ anwering for a specific upper bound engine.
    *
    * @param store upper bound engine to be used in the computation.
    * @param query query record.
    * @param queryText actual text of the query to be executed.
    * @param answerVariables answer variables for the query.
    */
  private def queryUpperBound(
    store: BasicQueryEngine,
    query: QueryRecord,
    queryText: String,
    answerVariables: Array[String]
  ): Unit = {
    var rlAnswer: AnswerTuples = null
    try {
      Utility logDebug queryText
      rlAnswer = store.evaluate(queryText, answerVariables)
      Utility logDebug timer.duration()
      query updateUpperBoundAnswers rlAnswer
    } finally {
        if (rlAnswer != null) rlAnswer.dispose()
    }
  }

  /** Perform CQ anwering for a specific upper bound engine.
    *
    * @param store upper bound engine to be used in the computation.
    * @param query query record.
    * @param extendedQuery extended version of the query.
    * @param step difficulty of the current step.
    * @returns whether the query has been fully answered, i.e., the
    *          bounds computed so far coincide.
    *
    * @note It deals with blanks nodes differently from variables
    * according to SPARQL semantics for OWL2 Entailment Regime. In
    * particular variables are matched only against named individuals,
    * and blank nodes against named and anonymous individuals.
    */
  private def queryUpperStore(
    upperStore: BasicQueryEngine,
    query: QueryRecord,
    extendedQuery: Tuple[String],
    step: Step
  ): Boolean = {
    timer.reset();

    Utility logDebug "First query type"
    queryUpperBound(upperStore, query, query.getQueryText, query.getAnswerVariables)
    if (!query.isProcessed() && !query.getQueryText().equals(extendedQuery.get(0))) {
      Utility logDebug "Second query type"
      queryUpperBound(upperStore, query, extendedQuery.get(0), query.getAnswerVariables)
    }
    if (!query.isProcessed() && query.hasNonAnsDistinguishedVariables()) {
      Utility logDebug "Third query type"
      queryUpperBound(upperStore, query, extendedQuery.get(1), query.getDistinguishedVariables)
    }

    query.addProcessingTime(step, timer.duration())
    if (query.isProcessed()) query.setDifficulty(step)
    query.isProcessed()
  }

  /** Computes the bounds to the answers for a query.
    * 
    * Both the lower (RL + ELHO) and upper bounds are computed here.
    *
    * @param query the query to be executed
    * @returns whether the query has been fully answered, i.e., the
    *          bounds computed so far coincide.
    */
  private def queryLowerAndUpperBounds(query: QueryRecord): Boolean = {
    Utility logInfo ">> Base bounds <<"
    val extendedQueryTexts: Tuple[String] = query.getExtendedQueryText()
    var rlAnswer: AnswerTuples = null
    var elAnswer: AnswerTuples = null

    /* Compute RL lower bound answers */
    timer.reset();
    try {
      rlAnswer = rlLowerStore.evaluate(query.getQueryText, query.getAnswerVariables)
      Utility logDebug timer.duration()
      query updateLowerBoundAnswers rlAnswer
    } finally {
        if (rlAnswer != null) rlAnswer.dispose()
    }
    query.addProcessingTime(Step.LOWER_BOUND, timer.duration());

    /* Compute upper bound answers */
    if(properties.getUseAlwaysSimpleUpperBound() || lazyUpperStore.isEmpty) {
      Utility logDebug "Tracking store"
      if (queryUpperStore(trackingStore, query, extendedQueryTexts, Step.SIMPLE_UPPER_BOUND))
        return true;
    }
    if (!query.isBottom) {
      Utility logDebug "Lazy store"
      if (lazyUpperStore.isDefined && queryUpperStore(lazyUpperStore.get, query, extendedQueryTexts, Step.LAZY_UPPER_BOUND))
        return true
    }

    timer.reset()
    /* Compute ELHO lower bound answers */
    try {
      elAnswer = elLowerStore.evaluate(
        extendedQueryTexts.get(0),
        query.getAnswerVariables,
        query.getLowerBoundAnswers
      )
      Utility logDebug timer.duration()
      query updateLowerBoundAnswers elAnswer
    } finally {
      if (elAnswer != null) elAnswer.dispose()
    }
    query.addProcessingTime(Step.EL_LOWER_BOUND, timer.duration())

    if (query.isProcessed()) query.setDifficulty(Step.EL_LOWER_BOUND)
    query.isProcessed()
  }

  /** Compute lower bound using RSAComb.
    *
    * @param query query record to update.
    * @returns true if the query is fully answered.
    */
  private def queryRSALowerBound(query: QueryRecord): Boolean = {
    import uk.ac.ox.cs.acqua.implicits.RSACombAnswerTuples._
    val answers = lowerRSAOntology ask query
    query updateLowerBoundAnswers answers
    query.isProcessed
  }

  /** Compute upper bound using RSAComb.
    *
    * @param query query record to update.
    * @returns true if the query is fully answered.
    */
  private def queryRSAUpperBound(query: QueryRecord): Boolean = {
    import uk.ac.ox.cs.acqua.implicits.RSACombAnswerTuples._
    val answers = upperRSAOntology ask query
    query updateUpperBoundAnswers answers
    query.isProcessed
  }

  /** Extract a subset of the ontology relevant to the query.
    *
    * @param query query record for which the subset ontology is computed.
    * @returns an [[OWLOntology]] subset of the input ontology.
    */
  private def extractRelevantOntologySubset(query: QueryRecord): OWLOntology = {
      Utility logInfo ">> Relevant ontology-subset extraction <<"
  
      timer.reset()
  
      val tracker: QueryTracker =
        new QueryTracker(encoder.get, rlLowerStore, query)
      val relevantOntologySubset: OWLOntology =
        tracker.extract( trackingStore, consistencyManager.getQueryRecords, true)

      query.addProcessingTime(Step.FRAGMENT, timer.duration())
  
      val numOfABoxAxioms: Int = relevantOntologySubset.getABoxAxioms(Imports.INCLUDED).size
      val numOfTBoxAxioms: Int = relevantOntologySubset.getAxiomCount() - numOfABoxAxioms
      Utility logInfo s"Relevant ontology-subset has been extracted: |ABox|=$numOfABoxAxioms, |TBox|=$numOfTBoxAxioms"
  
      return relevantOntologySubset
  }

  /** Query the skolemized ontology subset relevant to a query record.
    *
    * @param relevantSubset the relevant ontology subset.
    * @param query the query to be answered.
    * @returns true if the query has been fully answered.
    *
    * TODO: the code has been adapted from [[uk.ac.ox.cs.pagoda.reasoner.MyQueryReasoner]]
    * and ported to Scala. There are better, more Scala-esque ways of
    * deal with the big `while` in this function, but this should work
    * for now.
    */
  private def querySkolemisedRelevantSubset(
    relevantSubset: OWLOntology,
    query: QueryRecord
  ): Boolean = {
    Utility logInfo ">> Semi-Skolemisation <<"
    timer.reset()

    val relevantProgram: DatalogProgram = new DatalogProgram(relevantSubset)
    val relevantStore: MultiStageQueryEngine =
      new MultiStageQueryEngine("Relevant-store", true)
    relevantStore importDataFromABoxOf relevantSubset
    val relevantOriginalMarkProgram: String =
      OWLHelper getOriginalMarkProgram relevantSubset
    relevantStore.materialise("Mark original individuals", relevantOriginalMarkProgram)

    var isFullyProcessed = false
    val lastTwoTriplesCounts: LinkedList[Tuple[Long]] = new LinkedList()
    var currentMaxTermDepth = 1
    var keepGoing = true
    while (!isFullyProcessed && keepGoing) {
      if (currentMaxTermDepth > properties.getSkolemDepth) {
        Utility logInfo "Maximum term depth reached"
        keepGoing = false
      } else if (
        lastTwoTriplesCounts.size() == 2 && (
          lastTwoTriplesCounts.get(0).get(1).equals(lastTwoTriplesCounts.get(1).get(1)) ||
          {
            val interpolation: ExponentialInterpolation  =
              new ExponentialInterpolation(
                lastTwoTriplesCounts.get(0).get(0),
                lastTwoTriplesCounts.get(0).get(1),
                lastTwoTriplesCounts.get(1).get(0),
                lastTwoTriplesCounts.get(1).get(1)
              )
            val triplesEstimate: Double =
              interpolation computeValue currentMaxTermDepth
            Utility logDebug s"Estimate of the number of triples: $triplesEstimate"
            if (triplesEstimate > properties.getMaxTriplesInSkolemStore)
              Utility logInfo "Interrupting Semi-Skolemisation because of triples count limit"
            triplesEstimate > properties.getMaxTriplesInSkolemStore
          }
        )
      ) {
        keepGoing = false
      } else {
        Utility logInfo s"Trying with maximum depth $currentMaxTermDepth"

        val materialisationTag: Int =
          relevantStore.materialiseSkolemly(relevantProgram, null, currentMaxTermDepth)
        query.addProcessingTime(Step.SKOLEM_UPPER_BOUND, timer.duration())
        if (materialisationTag == -1) {
          relevantStore.dispose()
          throw new Error("A consistent ontology has turned out to be inconsistent in the Skolemises-relevant-upper-store")
        }

        if (materialisationTag != 1) {
          Utility logInfo "Semi-Skolemised relevant upper store cannot be employed"
          keepGoing = false
        } else {
          Utility logInfo "Querying semi-Skolemised upper store..."
          isFullyProcessed = queryUpperStore(
            relevantStore, query, query.getExtendedQueryText(), Step.SKOLEM_UPPER_BOUND
          )

          try {
            lastTwoTriplesCounts.add(new Tuple(currentMaxTermDepth, relevantStore.getStoreSize))
            if (lastTwoTriplesCounts.size() > 2)
              lastTwoTriplesCounts.remove()
            Utility logDebug s"Last two triples counts: $lastTwoTriplesCounts"
            currentMaxTermDepth += 1
          } catch {
            case e: JRDFStoreException => {
              e.printStackTrace()
              keepGoing = false
            }
          }
        }
      }
    }

    relevantStore.dispose()
    Utility logInfo "Semi-Skolemised relevant upper store has been evaluated"
    isFullyProcessed
  }

  /** Consistency status of the ontology */
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
