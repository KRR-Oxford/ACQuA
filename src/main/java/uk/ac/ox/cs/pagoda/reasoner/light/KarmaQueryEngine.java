package uk.ac.ox.cs.pagoda.reasoner.light;

import org.semanticweb.karma2.MyKarma;
import org.semanticweb.karma2.clausifier.OntologyProcesser;
import org.semanticweb.karma2.exception.IllegalInputOntologyException;
import org.semanticweb.karma2.model.ConjunctiveQuery;
import org.semanticweb.owlapi.model.OWLOntology;
import uk.ac.ox.cs.JRDFox.JRDFStoreException;
import uk.ac.ox.cs.JRDFox.store.DataStore;
import uk.ac.ox.cs.pagoda.query.AnswerTuple;
import uk.ac.ox.cs.pagoda.query.AnswerTuples;
import uk.ac.ox.cs.pagoda.query.AnswerTuplesImp;
import uk.ac.ox.cs.pagoda.util.ConjunctiveQueryHelper;
import uk.ac.ox.cs.pagoda.util.Utility;
import uk.ac.ox.cs.pagoda.util.disposable.DisposedException;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.Set;

public class KarmaQueryEngine extends RDFoxQueryEngine {

    String karmaDataFile = null, karmaRuleFile = null;
    private MyKarma reasoner = null;

    public KarmaQueryEngine(String name) {
        super(name);

//		int Base = 1 << 6; 
//		int index = (new Random().nextInt() % Base + Base) % Base;
//		karmaDataFile = "karma_data" + index + ".ttl"; 
//		karmaRuleFile = "karma_rule" + index + ".dlog";
        karmaDataFile = Paths.get(Utility.getGlobalTempDirAbsolutePath(), "karma_data.ttl").toString();
        karmaRuleFile = Paths.get(Utility.getGlobalTempDirAbsolutePath(), "karma_rule.dlog").toString();

        reasoner = new MyKarma();
    }

    public MyKarma getReasoner() {
        if(isDisposed()) throw new DisposedException();
        return reasoner;
    }

    public void processOntology(OWLOntology elhoOntology) {
        if(isDisposed()) throw new DisposedException();
        try {
            OntologyProcesser.transformOntology(elhoOntology, new File(karmaDataFile), new File(karmaRuleFile));
        } catch(IllegalInputOntologyException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        reasoner.dispose();
    }

    @Override
    public AnswerTuples evaluate(String queryText) {
        if(isDisposed()) throw new DisposedException();
        return evaluate(queryText, ConjunctiveQueryHelper.getAnswerVariables(queryText)[0], null);
    }

    @Override
    public AnswerTuples evaluate(String queryText, String[] answerVars) {
        if(isDisposed()) throw new DisposedException();
        return evaluate(queryText, answerVars, null);
    }

    public AnswerTuples evaluate(String queryText, AnswerTuples soundAnswerTuples) {
        if(isDisposed()) throw new DisposedException();
        return evaluate(queryText, ConjunctiveQueryHelper.getAnswerVariables(queryText)[0], soundAnswerTuples);
    }

    public AnswerTuples evaluate(String queryText, String[] answerVars, AnswerTuples soundAnswerTuples) {
        if(isDisposed()) throw new DisposedException();
        KarmaQuery karmaQuery = new KarmaQuery(queryText.replace("_:", "?"));
        reasoner.setConcurrence(false);
        ConjunctiveQuery cq = karmaQuery.getConjunctiveQuery();
        if(cq == null) return null;
        Set<AnswerTuple> answers = reasoner.answerCQ(cq, soundAnswerTuples, !queryText.contains("_:"));
        return new AnswerTuplesImp(answerVars, answers);
    }

    @Override
    public DataStore getDataStore() {
        if(isDisposed()) throw new DisposedException();
        return reasoner.getStore();
    }

    public void initialiseKarma() {
        if(isDisposed()) throw new DisposedException();
        try {
            reasoner.initializeData(new File(karmaDataFile));
            reasoner.materialise(new File(karmaRuleFile));

            File tmp;
            if(karmaDataFile != null && ((tmp = new File(karmaDataFile)).exists())) tmp.delete();
            if(karmaRuleFile != null && ((tmp = new File(karmaRuleFile)).exists())) tmp.delete();
        } catch(FileNotFoundException e) {
            e.printStackTrace();
        } catch(JRDFStoreException e) {
            e.printStackTrace();
        }
    }

}
