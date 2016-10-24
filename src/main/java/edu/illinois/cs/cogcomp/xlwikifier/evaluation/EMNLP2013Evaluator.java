package edu.illinois.cs.cogcomp.xlwikifier.evaluation;

import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.core.utilities.StringUtils;
import edu.illinois.cs.cogcomp.mlner.CrossLingualNER;
import edu.illinois.cs.cogcomp.xlwikifier.ConfigParameters;
import edu.illinois.cs.cogcomp.xlwikifier.CrossLingualWikifier;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.ELMention;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.QueryDocument;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.WikiCand;
import edu.illinois.cs.cogcomp.xlwikifier.wikipedia.WikiCandidateGenerator;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by lchen112 on 10/10/16.
 */

public class EMNLP2013Evaluator {
    public static final String[] datasets = { "ACE", "MSNBC", "AQUAINT", "Wikipedia" };
//    public static final String[] datasets = {"MSNBC"};
//    public static final String[] datasets = {"Wikipedia" };
    private static WikiCandidateGenerator wcg = new WikiCandidateGenerator();
    private static Set<String> titles;

    public static void evaluateBOT(List<QueryDocument> docs){
        double hit = 0.0;
        double goldTotal = 0.0;
        double predTotal = 0.0;
        for(QueryDocument doc: docs){

            Set<String> gold = doc.gold_mentions.stream()
                    .map(x -> x.gold_wiki_title)
                    .filter(x -> !x.equals("NIL"))
                    .collect(Collectors.toSet());

            Set<String> pred = doc.mentions.stream()
                    .filter(x -> x.getNounType().equals("gold"))
                    .map(x -> x.getWikiTitle())
                    .filter(x -> !x.equals("NIL"))
                    .collect(Collectors.toSet());

            Set<String> inter = new HashSet<>(gold);
            inter.retainAll(pred);

            hit += inter.size();
            goldTotal += gold.size();
            predTotal += pred.size();
        }
        double precision = hit/predTotal;
        double recall = hit/goldTotal;
        double f1 = 2.0*precision*recall/ (precision+ recall);
        System.out.println();
        System.out.printf("(BOT) F1 = %f, Precision = %f, Recall = %f\n", f1, precision, recall);
    }

    public static void evaluateAccuracy(List<QueryDocument> docs){
        double hit = 0.0;
        double incand = 0.0;
        double nonnil = 0.0;
        double total = 0.0;
        for(QueryDocument doc: docs){
            for(ELMention m: doc.mentions.stream().filter(x -> x.getNounType().equals("gold")).collect(Collectors.toList())){
                if(m.gold_wiki_title.equals(m.getWikiTitle()))
                    hit++;
                else{
//                    System.out.println("---------------------");
//                    System.out.println("Gold: "+m.gold_wiki_title+" Mention: "+m.getMention()+" Pred: "+m.getWikiTitle());
//                    for(WikiCand cand: m.getCandidates()){
//                        System.out.print(cand.getTitle()+" "+cand.ptgivens+" "+cand.psgivent+" "+cand.score);
//                        if(cand.getTitle().equals(m.gold_wiki_title))
//                            System.out.println(" ***");
//                        else
//                            System.out.println();
//                    }
                }
                total++;
            }
        }
        System.out.printf("Accuracy = %f (%d/%d)\n", hit/total, (int)hit, (int)total);
        System.out.printf("Coverage = %f\n", incand/nonnil);
    }

    public static void checkTitle(List<QueryDocument> docs){

        int cnt = 0, total = 0;
        for(QueryDocument doc: docs){
            for(ELMention m: doc.mentions){
                if(m.gold_wiki_title.equals("NIL"))
                    continue;
                if(titles.contains(m.gold_wiki_title)) {
                    cnt++;
//                    System.out.println(m.gold_wiki_title);
                }
                total ++;
            }
        }

        System.out.println(cnt+" "+total);
    }

    private static void addPredictedMentions(QueryDocument doc){
        List<ELMention> pred_mentions = CrossLingualNER.annotate(doc.plain_text).mentions;

        List<ELMention> ms = new ArrayList<>();
        for(ELMention pm: pred_mentions){
            boolean over = false;
            for(ELMention gm: doc.gold_mentions){
                if((gm.getStartOffset() >= pm.getStartOffset() && gm.getStartOffset() < pm.getEndOffset())
                        || (pm.getStartOffset() >= gm.getStartOffset() && pm.getStartOffset() < gm.getEndOffset())) {
                    over = true;
                    gm.setType(pm.getType());
                    break;
                }
            }
            pm.setNounType("pred");
            if(!over) ms.add(pm);
        }

        doc.mentions = ms;

    }

    private static double jaccard(String s1, String s2){
        Set<String> tokens1 = new HashSet<>(Arrays.asList(s1.split("\\s+")));
        Set<String> tokens2 = new HashSet<>(Arrays.asList(s2.split("\\s+")));

        Set<String> inter = new HashSet<>(tokens1);
        inter.retainAll(tokens2);

        tokens2.addAll(tokens1);

        return (double)inter.size()/tokens2.size();
    }

    private static boolean checkMentionPair(String s1, String s2){

        Set<String> tokens1 = new HashSet<>(Arrays.asList(s1.split("\\s+")));
        Set<String> tokens2 = new HashSet<>(Arrays.asList(s2.split("\\s+")));

        if(tokens2.size() > 2 || tokens1.size() > 2)
            return false;

        for(String t: tokens2) {
            if (!t.substring(0, 1).toUpperCase().equals(t.substring(0, 1)))
                return false;

            if(StringUtils.isNumeric(t.substring(0,1)))
                return false;
        }
        return true;
    }

    private static void coref(QueryDocument doc){
        List<ELMention> mentions = doc.mentions.stream()
                .sorted((x1, x2) -> Integer.compare(x2.getMention().length(), x1.getMention().length()))
                .collect(Collectors.toList());

        for(int i = 1; i < mentions.size(); i++){
            ELMention mi = mentions.get(i);
            for(int j = 0; j < i; j++){

                ELMention mj = mentions.get(j);

                if(mi.getMention().equals(mj.getMention())) continue;

                if(!checkMentionPair(mi.getMention(), mj.getMention())) continue;

                double score = jaccard(mi.getMention(), mj.getMention());

                if(score >= 0.5) {
                    if(!mi.getWikiTitle().equals(mj.getWikiTitle())) {
//                        if(!mj.getWikiTitle().equals(mi.gold_wiki_title)){
//                            System.out.println("Mention: " + mi.getMention() + " " + mi.getWikiTitle() + " -> " + mj.getMention() + " " + mj.getWikiTitle());
//                            System.out.println("Gold: " + mi.gold_wiki_title + " " + mj.gold_wiki_title);
//                        }
                        mi.setWikiTitle(mj.getWikiTitle());
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ConfigParameters cp = new ConfigParameters();
        cp.getPropValues();
        String dataPrefix = "/shared/bronte/mssammon/WikifierResources/eval/ACL2010DataNewFormat/";
        CrossLingualNER.init("en", false);
        CrossLingualWikifier.init("en");

        wcg.loadDB("en", true);

        EMNLP2013Reader reader = new EMNLP2013Reader();
        for (String dataset : datasets) {
            System.out.printf("===========Evaluate Dataset %s ==========\n", dataset);
            String dataFolder = dataPrefix + dataset + "/";
            List<QueryDocument> docs = new ArrayList<>();
            for (File file : new File(dataFolder).listFiles()) {
                QueryDocument doc = reader.readDocument(file);

                doc.gold_mentions.forEach(x -> x.setNounType("gold"));

                addPredictedMentions(doc);

//                doc.mentions = new ArrayList<>();

                doc.mentions.addAll(doc.gold_mentions);

                doc.mentions = doc.mentions.stream().sorted((x1, x2) -> Integer.compare(x1.getStartOffset(), x2.getStartOffset()))
                        .collect(Collectors.toList());
                docs.add(doc);
            }

//            checkTitle(docs);

            for (QueryDocument doc : docs) {
                CrossLingualWikifier.wikify(doc);


                if(dataset.equals("Wikipedia")) {
                    for (ELMention m : doc.mentions) {
                        String mt = m.getMention().replaceAll(" ", "_");
                        if (reader.valid_titles.contains(mt))
                            m.setWikiTitle(mt);
                    }
                }
                else
                    coref(doc);
            }

            evaluateBOT(docs);
            evaluateAccuracy(docs);
        }

    }
}
