package edu.illinois.cs.cogcomp.xlwikifier.evaluation.semevalDatastructures;
import java.util.*
;/**
 * Created by lchen112 on 3/27/17.
 */
public class GoldDocument {
    public Map<String, GoldMention> mentionMap = new HashMap<String, GoldMention>();// (startOffset_EndOffset, GoldMention)
    public String text = "";
}
