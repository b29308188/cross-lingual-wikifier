package edu.illinois.cs.cogcomp.xlwikifier.evaluation;

import java.io.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

import edu.illinois.cs.cogcomp.core.datastructures.textannotation.Constituent;
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


    public class Key{
        String start;
        String end;
        String gold_title;
        public Key(String start, String end, String gold_title){
            this.start = start;
            this.end = end;
            this.gold_title = gold_title;
        }
    }

    Map<String, Key> keyMap = new HashMap<String, Key>();
    Map<Integer, String> goldMap = new HashMap<Integer, String>();//offset to gold title
    String text = "";
    public void readKeys(String file){
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\t");
                for(int i = 2; i < parts.length; i++){
                    String[] tokens = parts[i].split(":");
                    if(tokens[0].equals("wiki")) {
                        keyMap.put(parts[0], new Key(parts[0], parts[1], tokens[1]));
                        break;
                    }
                }
            }
        }
        catch (Exception e){e.printStackTrace();}
    }
    public void readData(String xmlFile) {
        try {
            File fXmlFile = new File(xmlFile);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document d = dBuilder.parse(fXmlFile);
            d.getDocumentElement().normalize();
            NodeList nList = d.getElementsByTagName("sentence");
            int offset = 0;
            for(int i = 0; i < nList.getLength(); i++){
                Node nNode = nList.item(i);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    for(int j = 0; j < eElement.getElementsByTagName("wf").getLength(); j ++){
                        String key = eElement.getElementsByTagName("wf")
                                .item(j).getAttributes().getNamedItem("id").getTextContent();
                        String word = eElement.getElementsByTagName("wf").item(j).getTextContent();

                        if(keyMap.containsKey(key)){
                            goldMap.put(offset, keyMap.get(key).gold_title);
                        }
                        text += word + " ";
                        offset += word.length()+1;
                    }
                }
            }
        }
        catch (Exception e){e.printStackTrace();}
    }

    public void evaluate() throws Exception{
        String language = "en";
        String default_config = "config/xlwikifier-demo.config";
        Language lang = Language.getLanguage(language);
        //Tokenizer tokenizer = MultiLingualTokenizer.getTokenizer(language);
        //TextAnnotation ta = tokenizer.getTextAnnotation(text);

        /*Create textannotation*/
        AnnotatorService annotator = CuratorFactory.buildCuratorClient();
        TextAnnotation ta = annotator.createBasicTextAnnotation("corpus", "id", text);

        /*Add part-of-speech*/
        annotator.addView(ta, ViewNames.POS);

        /*ChunkerAnnotator*/
        ChunkerAnnotator ca = new ChunkerAnnotator(true);
        ca.initialize(new ChunkerConfigurator().getDefaultConfig());
        ca.addView(ta);

        Ngram ngram = new Ngram(lang, default_config);
        ngram.addView(ta);
        CrossLingualWikifier xlwikifier = CrossLingualWikifierManager.buildWikifierAnnotator(lang, default_config);
        xlwikifier.addView(ta);
        double total = 0;
        double hit = 0;
        List<Constituent> L = ta.getView("EN_WIKIFIER").getConstituents();
        Collections.sort(L, new Comparator<Constituent>(){
            public int compare(Constituent  c1, Constituent  c2){
                if(c1.getStartCharOffset() == c2.getStartCharOffset())
                    return 0;
                return c1.getStartCharOffset() < c2.getStartCharOffset() ? -1 : 1;
            }
        });

        for(Constituent c: L){
            int offset = c.getStartCharOffset();
            if(goldMap.containsKey(offset)){
                total += 1;
                System.out.printf("Offset:%d, Surface: %s -> (gold) %s/ (predicted) %s\n",
                        offset,c.getSurfaceForm(), goldMap.get(offset), c.getLabel());
                if(c.getLabel().equals(goldMap.get(offset)))
                    hit += 1;
            }
        }
        System.out.printf("Accuracy %f\n", hit/total);

    }
    public Semeval15(String dataFile, String keyFile){
        readKeys(keyFile);
        readData(dataFile);
    }


    public static void main(String[] args) throws Exception{
        String keyFile = "./data/SemEval-2015-task-13-v1.0/keys/gold_keys/EN/semeval-2015-task-13-en-WSD.key";
        String dataFile = "./data/SemEval-2015-task-13-v1.0/data/semeval-2015-task-13-en.xml";
        Semeval15 evaluator = new Semeval15(dataFile, keyFile);
        evaluator.evaluate();
    }
}
