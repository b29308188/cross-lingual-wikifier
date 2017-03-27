package edu.illinois.cs.cogcomp.xlwikifier.evaluation.semevalDatastructures;

/**
 * Created by lchen112 on 3/27/17.
 */
public class Word {
    public int startOffset;
    public int endOffset;
    public String text;
    public Word(int startOffset,  String text){
        this.startOffset = startOffset;
        this.endOffset = startOffset+text.length();
        this.text = text;
    }
}
