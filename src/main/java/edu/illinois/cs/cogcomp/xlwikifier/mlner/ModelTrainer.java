package edu.illinois.cs.cogcomp.xlwikifier.mlner;

import edu.illinois.cs.cogcomp.LbjNer.ExpressiveFeatures.ExpressiveFeaturesAnnotator;
import edu.illinois.cs.cogcomp.LbjNer.LbjTagger.Data;
import edu.illinois.cs.cogcomp.LbjNer.LbjTagger.NERDocument;
import edu.illinois.cs.cogcomp.LbjNer.LbjTagger.NEWord;
import edu.illinois.cs.cogcomp.LbjNer.LbjTagger.ParametersForLbjCode;
import edu.illinois.cs.cogcomp.annotation.BasicTextAnnotationBuilder;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.utilities.configuration.ResourceManager;
import edu.illinois.cs.cogcomp.lbjava.parse.LinkedVector;
import edu.illinois.cs.cogcomp.xlwikifier.ConfigParameters;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.ELMention;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.QueryDocument;
import edu.illinois.cs.cogcomp.xlwikifier.freebase.FreeBaseQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import static edu.illinois.cs.cogcomp.LbjNer.LbjTagger.LearningCurveMultiDataset.getLearningCurve;
import static edu.illinois.cs.cogcomp.LbjNer.LbjTagger.Parameters.readAndLoadConfig;

/**
 * Created by ctsai12 on 11/2/16.
 */
public class ModelTrainer {

    private static final Logger logger = LoggerFactory.getLogger(ModelTrainer.class);


    public static List<QueryDocument> data2QueryDocs(Data data){

        List<QueryDocument> docs = new ArrayList<>();
        for(NERDocument nerdoc: data.documents){
            List<String[]> tokenized_sentences = new ArrayList<>();
            int nt = 0;
            for(LinkedVector sen: nerdoc.sentences){
                String[] tokens = new String[sen.size()];
                for(int i = 0; i < sen.size(); i++) {
                    tokens[i] = ((NEWord) sen.get(i)).form;
                    nt++;
                }
                tokenized_sentences.add(tokens);
            }

            TextAnnotation ta = BasicTextAnnotationBuilder.createTextAnnotationFromTokens(tokenized_sentences);
            if(ta.getTokens().length!=nt){
                System.out.println("# tokens doesn't match!!! ");
                System.out.println(ta.getTokens().length+" "+nt);
                System.exit(-1);
            }
            QueryDocument doc = new QueryDocument(nerdoc.docname);
            doc.setText(ta.getText());
            doc.setTextAnnotation(ta);
            docs.add(doc);
        }
        return docs;
    }

    public static void copyWikifierFeatures(Data data, List<QueryDocument> docs){
        for(int i = 0; i < data.documents.size(); i++){
            NERDocument nerdoc = data.documents.get(i);
            QueryDocument qdoc = docs.get(i);
            int nt = 0;
            for(LinkedVector sen: nerdoc.sentences){
                String[] tokens = new String[sen.size()];
                for(int j = 0; j < sen.size(); j++) {
                    ELMention m = qdoc.mentions.get(nt);
                    String[] wikif = new String[m.ner_features.size()];
                    int k = 0;
                    for (String key : m.ner_features.keySet()) {
                        wikif[k++] = key + ":" + m.ner_features.get(key);
                    }
                    ((NEWord) sen.get(j)).wikifierfeats = wikif;
                    nt++;
                }
            }

            if(nt!= qdoc.mentions.size()){
                System.out.println("# tokens doesn't match!!! ");
                System.out.println(qdoc.mentions.size()+" "+nt);
                System.exit(-1);
            }

        }
    }

    public static void main(String[] args) {

        String train_dir = args[0];
        String test_dir = args[1];
        String lang = args[2];
        String config = args[3];
        ConfigParameters.setPropValues(config);

        if (!FreeBaseQuery.isloaded())
            FreeBaseQuery.loadDB(true);

        try {
            ResourceManager ner_rm = new ResourceManager(ConfigParameters.ner_models.get(lang));
            ParametersForLbjCode.currentParameters = readAndLoadConfig(ner_rm, true);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        NERUtils nerutils = new NERUtils(lang);
        int iter = 30;

        Data train_data = null, test_data = null;
        try {
            train_data = new Data(train_dir, train_dir, "-c", new String[]{}, new String[]{});
            test_data = new Data(test_dir, test_dir, "-c", new String[]{}, new String[]{});
            List<QueryDocument> train_docs = data2QueryDocs(train_data);
            List<QueryDocument> test_docs = data2QueryDocs(test_data);
            if (ParametersForLbjCode.currentParameters.featuresToUse.containsKey("Wikifier")) {
                logger.info("Wikifying training documents");
                for (QueryDocument doc : train_docs) {
                    nerutils.wikifyNgrams(doc);
                }
                copyWikifierFeatures(train_data, train_docs);
                logger.info("Wikifying test documents");
                for (QueryDocument doc : test_docs) {
                    nerutils.wikifyNgrams(doc);
                }
                copyWikifierFeatures(train_data, train_docs);
            }
            ExpressiveFeaturesAnnotator.train = true;
            ExpressiveFeaturesAnnotator.annotate(train_data);
            ExpressiveFeaturesAnnotator.train = false;
            ExpressiveFeaturesAnnotator.annotate(test_data);
            Vector<Data> train=new Vector<>();
            train.addElement(train_data);
            Vector<Data> test=new Vector<>();
            test.addElement(test_data);
            getLearningCurve(train, test, iter);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}