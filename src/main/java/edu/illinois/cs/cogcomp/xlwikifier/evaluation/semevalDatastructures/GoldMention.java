package edu.illinois.cs.cogcomp.xlwikifier.evaluation.semevalDatastructures;
import java.util.*;
/**
 * Created by lchen112 on 3/27/17.
 */
public class GoldMention {
    public String startID; //token ID
    public String endID;
    public int startOffset; //character offset
    public int endOffset;
    public Set<String> gold_titles = new HashSet<>();
    public GoldMention(String startID, String endID){
        this.startID = startID;
        this.endID = endID;
    }
}
