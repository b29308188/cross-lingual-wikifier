package edu.illinois.cs.cogcomp.wikitrans;

import java.util.Arrays;
import java.util.List;

/**
 * Created by ctsai12 on 10/14/16.
 */
public class EvalOtherTransliterator {

    public static void main(String[] args) {

        String lang = args[0];

        TitleTranslator tt = new TitleTranslator();


        List<String> types = Arrays.asList("loc", "org", "per");

        String modeldir = "/shared/experiments/ctsai12/workspace/illinois-transliteration/output/";
        for(String type: types){
//            if(!type.equals("loc")) continue;
            System.out.println(type);
            String testfile = "/shared/corpora/ner/gazetteers/" + lang + "/" + type + ".pair.test";
            String seqquery = "/shared/corpora/ner/gazetteers/"+lang+"/"+type+"-wordpairs/test.tokens";
            tt.evalModelPreds(testfile, seqquery+".fa");
//            tt.evalDirecTL(testfile, seqquery+".directl.out");
//            tt.printTestTokens(testfile, seqquery);
//            for(int i = 4; i > 0; i--){
            String modelfile = modeldir+lang+"."+type+".fa";
//                String f = modeldir+lang+"."+type+"."+i;
//                tt.evalModel(testfile, modelfile);
//            }
        }
        System.exit(-1);
    }
}
