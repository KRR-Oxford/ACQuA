package uk.ac.ox.cs.acqua.reasoner

import java.util.LinkedList
import scala.collection.JavaConverters._

import org.semanticweb.HermiT.model.{
  Atom,
  AtomicConcept,
  DLClause,
  Variable
}
import org.semanticweb.owlapi.model.{
  OWLOntology,
  OWLOntologyCreationException,
  OWLOntologyManager
}
import uk.ac.ox.cs.JRDFox.JRDFStoreException
// import uk.ac.ox.cs.JRDFox.store.DataStore;
import uk.ac.ox.cs.JRDFox.store.DataStore.UpdateType
import uk.ac.ox.cs.pagoda.hermit.DLClauseHelper
import uk.ac.ox.cs.pagoda.query.{
  AnswerTuples,
  QueryRecord
}
// import uk.ac.ox.cs.pagoda.query.QueryManager;
import uk.ac.ox.cs.pagoda.reasoner.full.Checker
import uk.ac.ox.cs.pagoda.reasoner.light.BasicQueryEngine
// import uk.ac.ox.cs.pagoda.rules.UpperDatalogProgram;
import uk.ac.ox.cs.pagoda.summary.HermitSummaryFilter
import uk.ac.ox.cs.pagoda.tracking.QueryTracker
// import uk.ac.ox.cs.pagoda.tracking.TrackingRuleEncoder;
import uk.ac.ox.cs.pagoda.util.{
  Timer,
  Utility
}
import uk.ac.ox.cs.pagoda.util.disposable.{
  Disposable,
  DisposedException
}
// import uk.ac.ox.cs.pagoda.util.disposable.DisposedException;

/** Consistency checker inspired by [[uk.ac.ox.cs.pagoda.reaseoner.ConsistencyManager]].
  *
  * @param reasoner an [[AcquaQueryReasoner]] instance
  *
  * TODO: document public methods and rework the code to be more
  * Scala-esque.
  */
class ConsistencyManager(
  protected val reasoner: AcquaQueryReasoner
) extends Disposable {

  protected val queryManager = reasoner.getQueryManager
  private val timer = new Timer()
  private var fragmentExtracted = false

  private var fullQueryRecord: Option[QueryRecord] = None
  private var botQueryRecords = Array.empty[QueryRecord]
  private var toAddClauses = new LinkedList[DLClause]()


  override def dispose(): Unit = {
    super.dispose()
    fullQueryRecord.map(_.dispose())
  }

  /**
    *
    */
  def extractBottomFragment(): Unit = {
    if (isDisposed) throw new DisposedException
    if (!fragmentExtracted) {
      fragmentExtracted = true

      val upperProgram = reasoner.datalog.getUpper
      val number = upperProgram.getBottomNumber

      if (number <= 1) {
        botQueryRecords = Array[QueryRecord](fullQueryRecord.get)
      } else {
        var record: QueryRecord = null
        var tempQueryRecords = new Array[QueryRecord](number-1)
        for(i <- 0 until (number-1)) {
          record = queryManager.create(QueryRecord.botQueryText.replace("Nothing", s"Nothing${i+1}"), 0, i + 1)
          tempQueryRecords(i) = record
          var iter: AnswerTuples = null
          try {
            iter = reasoner.trackingStore.evaluate(record.getQueryText, record.getAnswerVariables)
            record updateUpperBoundAnswers iter
          } finally {
            if (iter != null) iter.dispose()
          }
        }

        var bottomNumber = 0;
        val group = (0 until (number-1)).toArray
        for(i <- 0 until (number-1))
          if (tempQueryRecords(i).isProcessed)
            tempQueryRecords(i).dispose()
          else if(group(i) == i) {
            bottomNumber += 1
            record = tempQueryRecords(i)
            for (j <- i until (number-1))
              if (record hasSameGapAnswers tempQueryRecords(j))
                group(j) = i
          }

        Utility logInfo s"There are $bottomNumber different bottom fragments."
        toAddClauses = new LinkedList[DLClause]()
        var bottomCounter = 0
        botQueryRecords = new Array[QueryRecord](bottomNumber)
        val X = Variable.create("X")
        for(i <- 0 until (number-1)) {
          if (!tempQueryRecords(i).isDisposed() && !tempQueryRecords(i).isProcessed()) {
            if (group(i) == i) {
              record = tempQueryRecords(i)
              botQueryRecords(bottomCounter) = record
              bottomCounter += 1
              group(i) = bottomCounter
              record.resetInfo(
                QueryRecord.botQueryText.replace(
                  "Nothing",
                  s"Nothing_final$bottomCounter"
                ), 0, bottomCounter)
              toAddClauses.add(
                DLClause.create(
                  Array[Atom](Atom.create(AtomicConcept.create(s"${AtomicConcept.NOTHING.getIRI}_final$bottomCounter"), X)),
                  Array[Atom](Atom.create(AtomicConcept.create(s"${AtomicConcept.NOTHING.getIRI}${i+1}"), X))
                )
              )
            } else {
              toAddClauses.add(
                DLClause.create(
                  Array[Atom](Atom.create(AtomicConcept.create(s"${AtomicConcept.NOTHING.getIRI}_final${group(group(i))}"), X)),
                  Array[Atom](Atom.create(AtomicConcept.create(s"${AtomicConcept.NOTHING.getIRI}${i+1}"), X))
                )
              )
              tempQueryRecords(i).dispose()
            }
          }
        }
        upperProgram updateDependencyGraph toAddClauses
      }

      val programs: Array[String] = collectTrackingProgramAndImport()
      if (programs.length > 0) {
        val datastore  = reasoner.trackingStore.getDataStore
        var oldTripleCount: Long = 0
        var tripleCount: Long = 0
        try {
          val t1 = new Timer();
          oldTripleCount = datastore.getTriplesCount
          for(program <- programs)
            datastore.importRules(program, UpdateType.ScheduleForAddition)
          datastore applyReasoning true
          tripleCount = datastore.getTriplesCount

          Utility logDebug s"tracking store after materialising tracking program: $tripleCount (${tripleCount - oldTripleCount} new)"
          Utility logDebug s"tracking store finished the materialisation of tracking program in ${t1.duration()} seconds."

          extractAxioms()
          datastore.clearRulesAndMakeFactsExplicit()
        } catch {
          case e: JRDFStoreException => e.printStackTrace()
          case e: OWLOntologyCreationException => e.printStackTrace()
        }
      }
    }
  }

  /**
    * 
    * @note provided for compatibility reasons
    */
  val getQueryRecords: Array[QueryRecord] = botQueryRecords

  /** RL lower bound check for satisfiability. */
  lazy val checkRLLowerBound: Boolean = {
    if (isDisposed) throw new DisposedException
    val record: QueryRecord = queryManager.create(QueryRecord.botQueryText, 0)
    fullQueryRecord = Some(record)

    var iter: AnswerTuples = null
    try {
      iter = reasoner.rlLowerStore.evaluate(record.getQueryText, record.getAnswerVariables)
      record updateLowerBoundAnswers iter
    } finally {
      iter.dispose()
    }

    if (record.getNoOfSoundAnswers > 0) {
      Utility logInfo s"Answers to bottom in the lower bound: ${record.outputSoundAnswerTuple}"
    }
    record.getNoOfSoundAnswers <= 0
  }

  /** ELHO lower bound check for satisfiability */
  lazy val checkELLowerBound: Boolean = {
    if (isDisposed) throw new DisposedException
    val record: QueryRecord = fullQueryRecord.get

    val answers: AnswerTuples =
      reasoner.elLowerStore.evaluate(
        record.getQueryText,
        record.getAnswerVariables
      )
    record updateLowerBoundAnswers answers

    if (record.getNoOfSoundAnswers > 0) {
      Utility logInfo s"Answers to bottom in the lower bound: ${record.outputSoundAnswerTuple}"
    }
    record.getNoOfSoundAnswers <= 0
  }

  /**
    *
    */
  def checkUpper(upperStore: BasicQueryEngine): Boolean = {
    if (isDisposed) throw new DisposedException
    val record = fullQueryRecord.get

    if (upperStore != null) {
      var tuples: AnswerTuples = null
      try {
        tuples = upperStore.evaluate(record.getQueryText, record.getAnswerVariables)
        if (!tuples.isValid) {
          Utility logInfo s"There are no contradictions derived in ${upperStore.getName} materialisation."
          Utility logDebug "The ontology and dataset is satisfiable."
          return true
        }
      } finally {
        if (tuples != null) tuples.dispose()
      }
    }
    false
  }

  /** True if the KB associate with the [[reasoner]] is consistent.
    *
    * This is the main entry point of the consistency checker.
    */
  lazy val check: Boolean = {
    if (isDisposed) throw new DisposedException
    val record = fullQueryRecord.get
    var tuples: AnswerTuples = null

    try {
      tuples = reasoner.trackingStore.evaluate(
        record.getQueryText,
        record.getAnswerVariables
      )
      record updateUpperBoundAnswers tuples
    } finally {
      if (tuples != null) tuples.dispose()
    }

    var satisfiability = true
    if (record.getNoOfCompleteAnswers != 0) {

      extractBottomFragment()

      try {
        extractAxioms4Full()
      } catch {
        case e: OWLOntologyCreationException => e.printStackTrace()
      }

      var checker: Checker = null
      for (r <- getQueryRecords) {
        checker = new HermitSummaryFilter(r, true)
        satisfiability = checker.isConsistent()
        checker.dispose()
      }
    }
    satisfiability
  }

  /**
    *
    */
  private def extractAxioms4Full(): Unit = {
    val manager: OWLOntologyManager =
      reasoner.encoder.get
        .getProgram
        .getOntology
        .getOWLOntologyManager
    val fullOntology: OWLOntology = manager.createOntology()
    for (record <- botQueryRecords) {
      for (clause <- record.getRelevantClauses.asScala) {
        fullQueryRecord.get addRelevantClauses clause
      }
      manager.addAxioms(
        fullOntology,
        record.getRelevantOntology.getAxioms()
      )
    }
    fullQueryRecord.get.setRelevantOntology(fullOntology)
  }

  /**
    *
    */
  private def extractAxioms(): Unit = {
    val manager: OWLOntologyManager =
      reasoner.encoder.get
        .getProgram
        .getOntology
        .getOWLOntologyManager
    for (record <- botQueryRecords) {
      record setRelevantOntology manager.createOntology()
      val tracker: QueryTracker  = new QueryTracker(reasoner.encoder.get, reasoner.rlLowerStore, record)
      reasoner.encoder.get setCurrentQuery record
      tracker extractAxioms reasoner.trackingStore
      Utility logInfo s"finish extracting axioms for bottom ${record.getQueryID}"
    }
  }

  /**
    *
    */
  private def collectTrackingProgramAndImport(): Array[String] = {
    val programs = new Array[String](botQueryRecords.length)
    val encoder = reasoner.encoder.get
    val currentClauses: LinkedList[DLClause] = new LinkedList[DLClause]()

    botQueryRecords.zipWithIndex map { case (record,i) => {
      encoder setCurrentQuery record
      val builder = new StringBuilder(encoder.getTrackingProgram)
      val currentClauses = toAddClauses.asScala.filter{clause =>
        clause.getHeadAtom(0).getDLPredicate.toString().contains(s"_final${i + 1}")
      }.asJava
      builder append (DLClauseHelper toString currentClauses)
      builder.toString
    }}
  }
}
