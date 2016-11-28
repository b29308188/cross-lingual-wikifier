package edu.illinois.cs.cogcomp.xlwikifier;

import edu.illinois.cs.cogcomp.annotation.Annotator;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.SpanLabelView;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.core.utilities.configuration.ResourceManager;
import edu.illinois.cs.cogcomp.lbjava.nlp.Word;
import edu.illinois.cs.cogcomp.lbjava.parse.LinkedVector;
import edu.illinois.cs.cogcomp.ner.ExpressiveFeatures.BrownClusters;
import edu.illinois.cs.cogcomp.ner.ExpressiveFeatures.ExpressiveFeaturesAnnotator;
import edu.illinois.cs.cogcomp.ner.ExpressiveFeatures.Gazetteers;
import edu.illinois.cs.cogcomp.ner.ExpressiveFeatures.GazetteersFactory;
import edu.illinois.cs.cogcomp.ner.InferenceMethods.Decoder;
import edu.illinois.cs.cogcomp.ner.LbjFeatures.NETaggerLevel1;
import edu.illinois.cs.cogcomp.ner.LbjFeatures.NETaggerLevel2;
import edu.illinois.cs.cogcomp.ner.LbjTagger.Data;
import edu.illinois.cs.cogcomp.ner.LbjTagger.NERDocument;
import edu.illinois.cs.cogcomp.ner.LbjTagger.NEWord;
import edu.illinois.cs.cogcomp.ner.LbjTagger.ParametersForLbjCode;
import edu.illinois.cs.cogcomp.ner.ParsingProcessingData.TaggedDataReader;
import edu.illinois.cs.cogcomp.ner.config.NerBaseConfigurator;
import edu.illinois.cs.cogcomp.xlwikifier.core.StopWord;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.Language;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.ELMention;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.QueryDocument;
import edu.illinois.cs.cogcomp.xlwikifier.freebase.FreeBaseQuery;
import edu.illinois.cs.cogcomp.xlwikifier.mlner.NERUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

import static edu.illinois.cs.cogcomp.ner.LbjTagger.Parameters.readAndLoadConfig;

/**
 * Generate NER annotations using the Annotator API.
 * The input TextAnnotation has to be tokenized.
 *
 * Created by ctsai12 on 10/24/16.
 */
public class Ngram extends Annotator {


    private final Logger logger = LoggerFactory.getLogger(MultiLingualNER.class);

    private Language language;
    private NERUtils nerutils;
    private ParametersForLbjCode parameters;


    /**
     * Language has to be set before initialization.
     *
     * @param lang       the target language
     * @param configFile a global configuration for entire cross-lingual wikification. It contains paths to the
     *                   NER models of each language
     * @throws IOException
     */
    public Ngram(Language lang, String configFile) throws IOException {
        super(lang.getNgramViewName(), new String[]{}, true, new ResourceManager(configFile));
        System.out.println(lang.getNgramViewName());
        this.language = lang;

        // set all config properties of cross-lingual wikifier
        ConfigParameters.setPropValues(configFile);

        doInitialize();
    }

    @Override
    public void initialize(ResourceManager rm) {

        logger.info("Initializing MultiLingualNER...");

        String lang = this.language.getShortName();

        if (!FreeBaseQuery.isloaded())
            FreeBaseQuery.loadDB(true);

        nerutils = new NERUtils(lang);
    }



    @Override
    public void addView(TextAnnotation textAnnotation) {

        QueryDocument doc = new QueryDocument("");
        doc.text = textAnnotation.getText();
        doc.setTextAnnotation(textAnnotation);

        annotate(doc);

        SpanLabelView ngramView = new SpanLabelView(getViewName(), getViewName() + "-annotator", textAnnotation , 1.0, true);
        Set<String> seen = new HashSet<>();
        for (ELMention m : doc.mentions) {
            int start = textAnnotation.getTokenIdFromCharacterOffset(m.getStartOffset());
            int end = textAnnotation.getTokenIdFromCharacterOffset(m.getEndOffset() - 1) + 1;
            if(!seen.contains(start+"_"+end)) {
                ngramView.addSpanLabel(start, end, m.getType(), 1d);
                seen.add(start+"_"+end);
            }
        }

        textAnnotation.addView(getViewName(), ngramView);
    }

    /**
     * The real work from illinois-ner happens here
     *
     * @param doc
     * @return
     */
    public void annotate(QueryDocument doc) {

        for(Constituent c : doc.getTextAnnotation().getView("SHALLOW_PARSE")){
            List<String> words = Arrays.asList(c.getSurfaceForm().split("\\s+"));
            Set<String> stops = StopWord.getStopWords("en");
            List<String> tokens = new ArrayList<String>();
            for(String s : words)
                if(!stops.contains(s.toLowerCase()))
                    tokens.add(s);
            int total_size = tokens.size();
            for(int n = total_size; n > 0 ; n --){
                int tokens_size = tokens.size();
                for(int base = 0; base < tokens_size - n + 1 ; base++){
                    String text = String.join(" ", tokens.subList(base, base+n));
                    ELMention m = new ELMention(""
                            , c.getStartCharOffset() + c.getSurfaceForm().indexOf(tokens.get(base)),
                            c.getStartCharOffset() + c.getSurfaceForm().indexOf(tokens.get(base+n-1)) + tokens.get(base+n-1).length());// -1?
                    m.setSurface(text);
                    nerutils.wikifyMention(m, n);
                    if (m.getCandidates().size() > 0){
                        doc.mentions.add(m);
                        for(int i = base+n -1; i >= base; i --)
                            tokens.remove(i);
                        n++;
                        break;
                    }
                }
            }
            /*

            ELMention m = new ELMention("", c.getStartCharOffset(), c.getEndCharOffset());
            m.setSurface(c.getSurfaceForm());
            for(int n = 4; n > 0; n--) {
                nerutils.wikifyMention(m, n);
                if (m.getCandidates().size() > 0){
                    doc.mentions.add(m);
                    break;
                }
            }
            */
        }

        /*
        ParametersForLbjCode.currentParameters = this.parameters;
        for (int n = 4; n > 0; n--){
            List<ELMention> mentions = nerutils.getNgramMentions(doc, n);
            for (int j = 0; j < mentions.size(); j++){
                ELMention m = mentions.get(j);
                nerutils.wikifyMention(m, n);
                if(m.getCandidates().size() > 0)
                    doc.mentions.add(m);
            }
        }
        */
        //nerutils.wikifyNgrams(doc);
        //doc.mentions = nerutils.getNgramMentions(doc, n);

    }

}
