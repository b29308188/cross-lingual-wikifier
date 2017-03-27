package edu.illinois.cs.cogcomp.xlwikifier.evaluation;
import edu.illinois.cs.cogcomp.annotation.AnnotatorService;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.xlwikifier.*;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.Language;
import edu.illinois.cs.cogcomp.curator.CuratorFactory;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.ELMention;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.WikiCand;
import edu.illinois.cs.cogcomp.xlwikifier.evaluation.semevalDatastructures.*;
import java.util.*;

/**
 * Created by lchen112 on 3/27/17.
 */
public class SemevalEval {
    public static void evaluate(List<GoldDocument> goldDocuments) throws Exception{
        String language = "en";
        String default_config = "config/test.config";
        Language lang = Language.getLanguage(language);
        AnnotatorService annotator = CuratorFactory.buildCuratorClient();
        Ngram ngram = new Ngram(lang, default_config);
        CrossLingualWikifier xlwikifier = CrossLingualWikifierManager.buildWikifierAnnotator(lang, default_config);
        double mentionHit = 0; //Mention detection
        double candHit = 0; //Candidate Generation
        double rankerHit = 0; // Ranker Accuracy
        double total = 0; // Number of mentions

        for(GoldDocument doc: goldDocuments) {
            total += doc.mentionMap.size();
            //Create textannotation
            TextAnnotation ta = annotator.createBasicTextAnnotation("corpus", "id", doc.text);
            //Add part-of-speech*
            annotator.addView(ta, ViewNames.POS);
            //ChunkerAnnotator
            annotator.addView(ta, ViewNames.SHALLOW_PARSE);
            //Add mention detection
            ngram.addView(ta);
            //Wikification
            xlwikifier.addView(ta);
            GoldMention goldm;
            for (ELMention m : xlwikifier.result.mentions) {
                if (doc.mentionMap.containsKey(m.getStartOffset()+"_"+m.getEndOffset())){
                    mentionHit += 1;
                    goldm = doc.mentionMap.get(m.getStartOffset()+"_"+m.getEndOffset());

                    System.out.printf("Mention:%s  / Predicted:%s / Gold: ",
                            m.getSurface(), m.getWikiTitle());
                    for(String title:goldm.gold_titles)
                        System.out.printf("%s;", title);
                    System.out.printf("\n");

                    boolean inCand = false;
                    for (WikiCand c : m.getCandidates()) {
                        if (goldm.gold_titles.contains(c.orig_title)) {
                            candHit += 1;
                            inCand = true;
                            break;
                        }
                    }
                    if(inCand){
                        if(goldm.gold_titles.contains(m.getWikiTitle()))
                            rankerHit += 1;
                        else
                            System.out.printf("--> Ranking mistake\n");
                    }
                    else
                        System.out.printf("--> Candidate mistake\n");
                }
                else {
                    System.out.printf("Mention:%s  / Predicted:%s / Gold: Not in Gold\n",
                            m.getSurface(), m.getWikiTitle());
                }
            }
        }
        System.out.println("");
        System.out.printf("Mention Recall = %f\n", mentionHit/total);
        System.out.printf("Candidate Generation Recall = %f\n", candHit/mentionHit);
        System.out.printf("Ranker Accuracy = %f\n", rankerHit/candHit);
        System.out.printf("End System Accuracy = %f\n", rankerHit/total);

    }
    public static void main(String[] args) throws Exception{

        Semeval13Reader reader = new Semeval13Reader();
        evaluate(reader.goldDocuments);
        Semeval15Reader reader = new Semeval15Reader();
        evaluate(reader.goldDocuments);
    }
}
