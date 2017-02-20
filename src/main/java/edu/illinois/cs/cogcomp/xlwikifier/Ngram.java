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
        //System.out.println(lang.getNgramViewName());
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
        Set<String> stops = StopWord.getStopWords("en");

        for (Constituent chunk : doc.getTextAnnotation().getView("SHALLOW_PARSE")) {

            //Get POS for each token in the chunk
            List<Constituent> words = doc.getTextAnnotation().getView("POS").getConstituentsCovering(chunk);

            //Remove stop words
            List<Constituent> tokens = new ArrayList<Constituent>();
            for (Constituent c : words)
                if (!stops.contains(c.getSurfaceForm().toLowerCase()))
                    tokens.add(c);

            //Detect mentions
            List<Boolean> used = Arrays.asList(new Boolean[tokens.size()]);//record whether the token is used
            for (int n = tokens.size(); n > 0; n--) {//from long to short
                for (int base = 0; base < tokens.size() - n + 1; base++) {//slide over the tokens
                    if(used.subList(base, base+n).contains(true))// overlapping
                        continue;
                    List<Boolean> tmp = used.subList(base, base+n);
                    List<String> ts = new ArrayList<String>();
                    tokens.subList(base, base + n).forEach(x -> ts.add(x.getSurfaceForm()));
                    String text = String.join(" ", ts);//the surface text
                    ELMention m = new ELMention("", tokens.get(base).getStartCharOffset(),
                            tokens.get(base + n-1).getEndCharOffset());
                    m.setSurface(text);
                    nerutils.wikifyMention(m, n);
                    if (m.getCandidates().size() > 0){//have candidates
                        boolean isNoun = false;
                        for(int i = base+n -1; i >= base; i --)
                            if(tokens.get(i).getLabel().startsWith("N"))
                                isNoun = true;
                        if(isNoun){
                            doc.mentions.add(m);
                            for(int i = base+n -1; i >= base; i --)
                                used.set(i, true);
                        }
                    }
                }
            }
        }
        //doc.mentions.forEach(x -> System.out.println(x.getSurface()));
    }
}
