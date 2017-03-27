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
public class Semeval13Reader {


    List<GoldMention> goldMentions = new ArrayList<GoldMention>();//[(ID, gold_title)]
    List<GoldDocument> goldDocuments = new ArrayList<GoldDocument>();


    public void readIDs(String file){ // read ID, title
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(" ");
                boolean added = false;
                for(int i = 2; i < parts.length; i++){
                    if(!added){
                        added = true;
                        goldMentions.add(new GoldMention(parts[1], parts[1]));
                    }
                    goldMentions.get(goldMentions.size()-1).gold_titles.add(parts[i]);
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
                    Element ele = (Element)nNode;
                    String sentenceID = ele.getAttribute("id");
                    if (!prevDocID.equals(sentenceID.substring(0, 4))) {
                        goldDocuments.add(new GoldDocument());
                        prevDocID = sentenceID.substring(0, 4);
                        textOffset = 0;
                    }
                    NodeList nodeList = ((Element) nNode).getElementsByTagName("*");
                    for(int j = 0; j < nodeList.getLength(); j ++) {
                        Node nod = nodeList.item(j);
                        if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                            Element e = (Element) nod;
                            String word = e.getTextContent();
                            word.replaceAll("_", " ");
                            goldDocuments.get(goldDocuments.size() - 1).text += word + " ";
                            if(e.getTagName().equals("instance")) {
                                String ID = e.getAttribute("id");
                                words.put(ID, new Word(textOffset, word));
                            }
                            textOffset += word.length() + 1;
                        }
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

    public Semeval13Reader(){
        String keyFile = "./data/semeval-2013-task12-test-data/keys/gold/wikipedia/wikipedia.en.key";
        String dataFile = "./data/semeval-2013-task12-test-data/data/multilingual-all-words.en.xml";
        readIDs(keyFile);
        readData(dataFile);
    }
}
