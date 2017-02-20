package edu.illinois.cs.cogcomp.xlwikifier.evaluation;

import java.io.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.ELMention;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.WikiCand;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import edu.illinois.cs.cogcomp.annotation.AnnotatorService;
import edu.illinois.cs.cogcomp.chunker.main.ChunkerAnnotator;
import edu.illinois.cs.cogcomp.chunker.main.ChunkerConfigurator;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.xlwikifier.*;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.Language;
import edu.illinois.cs.cogcomp.curator.CuratorFactory;
/**

/**
 * Created by lchen112 on 1/29/17.
 */
public class Semeval15 {

    public class SEMention{
        int startID; //token ID
        int endID;
        int startOffset; //character offset
        int endOffset;
        boolean discovered = false;
        String gold_title;
        public SEMention(int startID, int endID, String gold_title){
            this.startID = startID;
            this.endID = endID;
            this.gold_title = gold_title;
        }
    }
    public class Word{
        int startOffset;
        int endOffset;
        String text;
        public Word(int startOffset,  String text){
            this.startOffset = startOffset;
            this.endOffset = startOffset+text.length();
            this.text = text;
        }
    }

    public int IDtoNum(String ID){
        String d = ID.substring(1, 4), s = ID.substring(6, 9), t = ID.substring(11, 14);
        return Integer.parseInt(t) + Integer.parseInt(s)*1000 + Integer.parseInt(d)*1000000;
    }

    List<SEMention> goldMentions = new ArrayList<SEMention>();//[(startID, endID, gold_title)]
    Map<Integer, Word> words = new HashMap<Integer, Word>();// (ID, word)
    Map<Integer, SEMention> goldMap = new HashMap<Integer, SEMention>();// (startOffset, SEMention)
    String text = "";

    public void readIDs(String file){ // read ID, title
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\t");
                for(int i = 2; i < parts.length; i++){
                    String[] tokens = parts[i].split(":");
                    if(tokens[0].equals("wiki")) {
                        goldMentions.add(new SEMention(IDtoNum(parts[0]), IDtoNum(parts[1]), tokens[1]));
                        break;
                    }
                }
            }
        }
        catch (Exception e){e.printStackTrace();}
    }

    public void readData(String xmlFile) {// Read text data and map it to the gold_title
        try {
            File fXmlFile = new File(xmlFile);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document d = dBuilder.parse(fXmlFile);
            d.getDocumentElement().normalize();
            NodeList nList = d.getElementsByTagName("sentence");
            int offset = 0;
            for (int i = 0; i < nList.getLength(); i++) {
                Node nNode = nList.item(i);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    for (int j = 0; j < eElement.getElementsByTagName("wf").getLength(); j++) {
                        String ID = eElement.getElementsByTagName("wf")
                                .item(j).getAttributes().getNamedItem("id").getTextContent();
                        String word = eElement.getElementsByTagName("wf").item(j).getTextContent();
                        text += word + " ";
                        words.put(IDtoNum(ID), new Word(offset, word));
                        offset += word.length()+1;
                    }
                }
            }
            for(SEMention m : goldMentions){
                m.startOffset = words.get(m.startID).startOffset;
                m.endOffset = words.get(m.endID).endOffset;
                goldMap.put(m.startOffset, m);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("end");
    }

    public void evaluate() throws Exception{
        String language = "en";
        String default_config = "config/test.config";
        Language lang = Language.getLanguage(language);
        //Tokenizer tokenizer = MultiLingualTokenizer.getTokenizer(language);
        //TextAnnotation ta = tokenizer.getTextAnnotation(text);

        /*Create textannotation*/
        System.out.println("Create text annotation...");
        AnnotatorService annotator = CuratorFactory.buildCuratorClient();
        TextAnnotation ta = annotator.createBasicTextAnnotation("corpus", "id", text);

        /*Add part-of-speech*/
        System.out.println("Add POS...");
        annotator.addView(ta, ViewNames.POS);

        /*ChunkerAnnotator*/
        System.out.println("Add Chunking...");
        ChunkerAnnotator ca = new ChunkerAnnotator(true);
        ca.initialize(new ChunkerConfigurator().getDefaultConfig());
        ca.addView(ta);

        System.out.println("Wikification...");
        Ngram ngram = new Ngram(lang, default_config);
        ngram.addView(ta);
        CrossLingualWikifier xlwikifier = CrossLingualWikifierManager.buildWikifierAnnotator(lang, default_config);
        xlwikifier.addView(ta);


        System.out.println("Evaluation...");
        System.out.println(text+"\ns");
        double mentionStartHit = 0; //Mention Start detection
        double mentionHit = 0; //Mention detection
        double candHit = 0; //Candidate Generation
        double rankerHit = 0; // Ranker Accuracy
        SEMention goldm;
        Set<String> bag_gold_titles = new HashSet<String>();
        Set<String> bag_titles = new HashSet<String>();
        for(int offset : goldMap.keySet()) bag_gold_titles.add(goldMap.get(offset).gold_title);
        for(ELMention m : xlwikifier.result.mentions){
            bag_titles.add(m.getWikiTitle());
            if(goldMap.containsKey(m.getStartOffset())){
                boolean inCand = false;
                mentionStartHit += 1;
                goldm = goldMap.get(m.getStartOffset());
                goldm.discovered = true;
                System.out.printf("Mention:%s  / Predicted:%s / Gold:%s\n",
                        m.getSurface(), m.getWikiTitle(), goldm.gold_title);
                if(goldm.endOffset == m.getEndOffset()) {
                    mentionHit += 1;
                    for(WikiCand c : m.getCandidates()){
                        if(c.orig_title.equals(goldm.gold_title)){
                            candHit += 1;
                            inCand = true;
                            break;
                        }
                    }
                    if (m.getWikiTitle().equals(goldm.gold_title))
                        rankerHit += 1;
                    else{
                        if(!inCand)
                            System.out.printf("--> Candidate mistake\n");
                        else
                            System.out.printf("--> Ranking mistake\n");
                    }
                }
                else
                    System.out.printf("--> endOffset mistake: Gold mention should be:%s\n", text.substring(goldm.startOffset, goldm.endOffset));
            }
            else
            System.out.printf("Mention:%s  / Predicted:%s / Gold: Not in Gold\n",
                    m.getSurface(), m.getWikiTitle());

        }
        System.out.println("Not Discovered Mentions: ");
        for(SEMention m: goldMentions)
            if(!m.discovered)
                System.out.printf("%s ;", text.substring(m.startOffset, m.endOffset));
        System.out.println("");
        System.out.printf("Mention Starting Recall = %f\n", mentionStartHit/goldMap.size());
        System.out.printf("Mention Recall = %f\n", mentionHit/goldMap.size());
        System.out.printf("Candidate Generation Recall = %f\n", candHit/mentionHit);
        System.out.printf("Ranker Accuracy = %f\n", rankerHit/candHit);
        System.out.printf("End System Accuracy = %f\n", rankerHit/goldMap.size());
        bag_titles.retainAll(bag_gold_titles);
        System.out.printf("End System Coverage (Bag of Title) = %f\n",
                (float)bag_titles.size()/bag_gold_titles.size());
    }
    public Semeval15(String dataFile, String keyFile){
        readIDs(keyFile);
        readData(dataFile);
    }


    public static void main(String[] args) throws Exception{
        String keyFile = "./data/SemEval-2015-task-13-v1.0/keys/gold_keys/EN/semeval-2015-task-13-en-WSD.key";
        String dataFile = "./data/SemEval-2015-task-13-v1.0/data/semeval-2015-task-13-en.xml";
        Semeval15 evaluator = new Semeval15(dataFile, keyFile);
        evaluator.evaluate();
    }
}
