package edu.illinois.cs.cogcomp.xlwikifier.evaluation;

import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.ELMention;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.QueryDocument;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ctsai12 on 10/19/16.
 */
public class EMNLP2013Reader {

    private DocumentBuilder db;
    public Map<String, String> redirects = new HashMap<>();
    public Set<String> valid_titles = new HashSet<>();

    public EMNLP2013Reader(){

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            db = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
        try {
//            valid_titles = LineIO.read("/shared/preprocessed/ctsai12/multilingual/wikidump-2014/en/docs/file.list.rand")
//                    .stream().map(x -> x.toLowerCase().replaceAll(" ", "_"))
//                    .collect(Collectors.toSet());
            valid_titles = LineIO.read("/shared/preprocessed/ctsai12/multilingual/wikidump-2014/en/valid-titles")
                    .stream().map(x -> x.toLowerCase())
                    .collect(Collectors.toSet());
            String rfile ="/shared/bronte/tac2014/data/WikiData/Redirects/2014-01-24.redirect";
            for(String line: LineIO.read(rfile)){
                String[] parts = line.toLowerCase().split("\t");
                if(parts.length == 2 && !parts[0].equals(parts[1]))
                    if(valid_titles.contains(parts[1]))
                        redirects.put(parts[0], parts[1]);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    public QueryDocument readDocument(File documentFile){
        Document document = null;
        try {
            document = db.parse(documentFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
        document.getDocumentElement().normalize();
        String text = ((Element) document.getElementsByTagName("text").item(0)).getTextContent();
        String id = ((Element) document.getElementsByTagName("id").item(0)).getTextContent();

        QueryDocument doc = new QueryDocument(id);
        doc.plain_text = text;

        doc.gold_mentions = new ArrayList<>();
        NodeList nList = document.getElementsByTagName("reference");
        for (int temp = 0; temp < nList.getLength(); temp++) {
            Node node = nList.item(temp);
            Element eElement = (Element) node;
            String surface = eElement.getElementsByTagName("surface").item(0).getTextContent();
            int charStart = Integer.parseInt(eElement.getElementsByTagName("charStart").item(0).getTextContent());
            int charLength = Integer.parseInt(eElement.getElementsByTagName("charLength").item(0).getTextContent());
            String annotation = eElement.getElementsByTagName("annotation").item(0).getTextContent().toLowerCase();
            if(annotation.equals("*null*"))
                annotation = "NIL";
            else {
                if(redirects.containsKey(annotation))
                    annotation = redirects.get(annotation);
                if(!valid_titles.contains(annotation)) {
                    annotation = "NIL";
//                    continue;
                }
            }


            if(charLength == surface.length()+1 && text.substring(charStart, charStart+1).trim().isEmpty()) {
                charStart++;
                charLength--;
            }

            ELMention m = new ELMention(id, charStart, charStart+charLength);
            m.setMention(surface);
            m.gold_wiki_title = annotation;
            doc.gold_mentions.add(m);
        }

        return doc;
    }
}
