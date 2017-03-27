package edu.illinois.cs.cogcomp.xlwikifier.evaluation;

import java.io.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;


import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import edu.illinois.cs.cogcomp.xlwikifier.evaluation.semevalDatastructures.*;
/**

/**
 * Created by lchen112 on 1/29/17.
 */
public class Semeval15Reader {


    List<GoldMention> goldMentions = new ArrayList<GoldMention>();//[(startID, endID, gold_title)]
    List<GoldDocument> goldDocuments = new ArrayList<GoldDocument>();


    public void readIDs(String file){ // read ID, title
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\t");
                boolean added = false;
                for(int i = 2; i < parts.length; i++){
                    String[] tokens = parts[i].split(":");
                    if(tokens[0].equals("wiki")) {
                        if(!added){
                            added = true;
                            goldMentions.add(new GoldMention(parts[0], parts[1]));
                        }
                        goldMentions.get(goldMentions.size()-1).gold_titles.add(tokens[1]);
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
            int textOffset = 0;
            String prevDocID = "";
            Map<String, Word> words = new HashMap<String, Word>();// (ID, word)
            for (int i = 0; i < nList.getLength(); i++) {
                Node nNode = nList.item(i);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    for (int j = 0; j < eElement.getElementsByTagName("wf").getLength(); j++) {
                        String ID = eElement.getElementsByTagName("wf")
                                .item(j).getAttributes().getNamedItem("id").getTextContent();
                        String word = eElement.getElementsByTagName("wf").item(j).getTextContent();
                        if(!prevDocID.equals(ID.substring(0, 4))){
                            goldDocuments.add(new GoldDocument());
                            prevDocID = ID.substring(0, 4);
                            textOffset = 0;
                        }
                        goldDocuments.get(goldDocuments.size()-1).text += word + " ";
                        words.put(ID, new Word(textOffset, word));
                        textOffset += word.length()+1;
                    }
                }
            }
            String prevDID = "";
            int docIndex = -1;
            for(GoldMention m : goldMentions){
                if(!prevDID.equals(m.startID.substring(0,4))){
                    prevDID = m.startID.substring(0,4);
                    docIndex += 1;
                }
                m.startOffset = words.get(m.startID).startOffset;
                m.endOffset = words.get(m.endID).endOffset;
                goldDocuments.get(docIndex).mentionMap.put(m.startOffset+"_"+m.endOffset, m);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Semeval15Reader(){
        String keyFile = "./data/SemEval-2015-task-13-v1.0/keys/gold_keys/EN/semeval-2015-task-13-en-WSD.key";
        String dataFile = "./data/SemEval-2015-task-13-v1.0/data/semeval-2015-task-13-en.xml";
        readIDs(keyFile);
        readData(dataFile);
    }
}
