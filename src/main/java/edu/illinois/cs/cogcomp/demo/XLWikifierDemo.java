package edu.illinois.cs.cogcomp.demo;

import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.xlwikifier.CrossLingualWikifier;
import edu.illinois.cs.cogcomp.xlwikifier.CrossLingualWikifierManager;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.ELMention;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.Language;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.QueryDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import edu.illinois.cs.cogcomp.annotation.AnnotatorService;
import edu.illinois.cs.cogcomp.chunker.main.ChunkerAnnotator;
import edu.illinois.cs.cogcomp.chunker.main.ChunkerConfigurator;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.xlwikifier.*;
import edu.illinois.cs.cogcomp.curator.CuratorFactory;

/**
 * Created by ctsai12 on 4/25/16.
 */
public class XLWikifierDemo {

    private String output;
    private String runtime;

    private String default_config = "config/test.config";
    private static Logger logger = LoggerFactory.getLogger(XLWikifierDemo.class);

    public XLWikifierDemo(String text, String language) {
        language = "en";
        Language lang = Language.getLanguage(language);
        //Tokenizer tokenizer = MultiLingualTokenizer.getTokenizer(language);
        //TextAnnotation ta = tokenizer.getTextAnnotation(text);

        try{
        /*Create textannotation*/
            AnnotatorService annotator = CuratorFactory.buildCuratorClient();
            TextAnnotation ta = annotator.createBasicTextAnnotation("corpus", "id", text);

            /*Add part-of-speech*/
            annotator.addView(ta, ViewNames.POS);


            /*ChunkerAnnotator*/
            annotator.addView(ta, ViewNames.SHALLOW_PARSE);
            //ChunkerAnnotator ca = new ChunkerAnnotator(true);
            //ca.initialize(new ChunkerConfigurator().getDefaultConfig());
            //ca.addView(ta);
            Ngram ngram = new Ngram(lang, default_config);
            ngram.addView(ta);
            CrossLingualWikifier xlwikifier = CrossLingualWikifierManager.buildWikifierAnnotator(lang, default_config);
            xlwikifier.addView(ta);
            output = formatOutput(xlwikifier.result, language);
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    /**
     * The output to be shown on the web demo
     *
     * @param doc
     * @param lang
     * @return
     */
    private String formatOutput(QueryDocument doc, String lang) {
        logger.info("Formatting demo outputs...");
        String out = "";

        int pend = 0;
        for (ELMention m : doc.mentions) {
            int start = m.getStartOffset();
            out += doc.text.substring(pend, start);
            String ref = "#";
            String en_title = formatTitle(m.getEnWikiTitle());
            if (!m.getEnWikiTitle().startsWith("NIL"))
                ref = "http://en.wikipedia.org/wiki/" + en_title;
            //else if(!m.getWikiTitle().startsWith("NIL"))
            //    ref = "http://"+lang+".wikipedia.org/wiki/"+m.getWikiTitle();
            //String tip = "English Wiki: "+m.en_wiki_title+" <br> "+lang+": "+m.getWikiTitle()+" <br> "+m.getType();
            String tip = "English Wiki: " + en_title + " <br> Entity Type: " + m.getType();
            out += "<a class=\"top\" target=\"_blank\" title=\"\" data-html=true data-placement=\"top\" data-toggle=\"tooltip\" href=\"" + ref + "\" data-original-title=\"" + tip + "\">" + m.getSurface() + "</a>";
            pend = m.getEndOffset();
        }
        out += doc.text.substring(pend, doc.text.length());
        return out;
    }

    public String formatTitle(String title) {
        String tmp = "";
        for (String token : title.split("_")) {
            if (token.length() == 0) continue;
            if (token.startsWith("(")) {
                tmp += token.substring(0, 2).toUpperCase();
                if (token.length() > 2)
                    tmp += token.substring(2, token.length());
            } else {
                tmp += token.substring(0, 1).toUpperCase();
                if (token.length() > 1)
                    tmp += token.substring(1, token.length());
            }
            tmp += "_";
        }
        //return tmp.substring(0, tmp.length() - 1);
        return tmp.substring(0, tmp.length() - 1).toLowerCase();
    }

    public String getOutput() {
        return this.output;
    }

    public String getRuntime() {
        return this.runtime;
    }

    public static void main(String[] args) {
        String text = "The Chicago Cubs are an American professional baseball team based in Chicago, Illinois. The Cubs compete in Major League Baseball (MLB) as a member club of the National League (NL) Central division, where they are the defending World Series Champions.";
        String lang = "en";
//        text = "Paul Kantor teaches information science at Rutgers University";
        XLWikifierDemo result = new XLWikifierDemo(text, lang);
        System.out.println(result.getOutput());
    }

}
