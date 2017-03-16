package edu.illinois.cs.cogcomp.demo;

import edu.illinois.cs.cogcomp.annotation.AnnotatorService;
import edu.illinois.cs.cogcomp.chunker.main.ChunkerAnnotator;
import edu.illinois.cs.cogcomp.chunker.main.ChunkerConfigurator;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.tokenizers.MultiLingualTokenizer;
import edu.illinois.cs.cogcomp.tokenizers.Tokenizer;
import edu.illinois.cs.cogcomp.xlwikifier.*;
import edu.illinois.cs.cogcomp.core.utilities.configuration.ResourceManager;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.Language;
import edu.illinois.cs.cogcomp.annotation.AnnotatorService;
import edu.illinois.cs.cogcomp.core.datastructures.ViewNames;
import edu.illinois.cs.cogcomp.core.datastructures.textannotation.TextAnnotation;
import edu.illinois.cs.cogcomp.curator.CuratorFactory;
/**
 * Created by lchen112 on 11/14/16.
 */
public class Tester {
    public static void main(String[] args) throws Exception{
        //String text = "The Chicago Cubs are an American professional baseball team based in Chicago, Illinois. The Cubs compete in Major League Baseball (MLB) as a member club of the National League (NL) Central division, where they are the defending World Series Champions.";
        //String text = "United States";
        //String text = "They are World Series Champions.";
        //String text = "Helicopters will patrol the temporary no-fly zone around New Jersey's MetLife Stadium Sunday, with F-16s based in Atlantic City ready to be scrambled if an unauthorized aircraft does enter the restricted airspace. Down below, bomb-sniffing dogs will patrol the trains and buses that are expected to take approximately 30,000 of the 80,000-plus spectators to Sunday's Super Bowl between the Denver Broncos and Seattle Seahawks. The Transportation Security Administration said it has added about two dozen dogs to monitor passengers coming in and out of the airport around the Super Bowl. On Saturday, TSA agents demonstrated how the dogs can sniff out many different types of explosives. Once they do, they're trained to sit rather than attack, so as not to raise suspicion or create a panic. TSA spokeswoman Lisa Farbstein said the dogs undergo 12 weeks of training, which costs about $200,000, factoring in food, vehicles and salaries for trainers. Dogs have been used in cargo areas for some time, but have just been introduced recently in passenger areas at Newark and JFK airports. JFK has one dog and Newark has a handful, Farbstein said.";
        //String text = "time";

        String text = "Dozens of passengers heading to Chicago had to undergo additional screenings.\n" +
                "This weekend was quieter, with things getting back to normal. No longer are families waiting for hours, but immigration attorneys are standing by just in case.\n" +
                "It took Hesam Aamyab two tries to make it back to the United States from Iran. He is an Iranian citizen with a US visa who is doing post-doctoral research at UIC. He was working in Malyasia when the ban was implemented; when it did, he immediately tried to fly back to Chicago. He was stopped, but after a federal judge halted the ban, he made it here safely.\n" +
                "\"Right now, I am in the USA and I'm very happy,\" Aamyab said.\n" +
                "But now, he can't go back to Iran or anywhere else without risk.  Other travelers shared the same worry. Asem Aleisawi was at O'Hare on Sunday to meet his wife who was coming in from Jordan.";

        String language = "en";
        String default_config = "config/test.config";
        Language lang = Language.getLanguage(language);
        //Tokenizer tokenizer = MultiLingualTokenizer.getTokenizer(language);
        //TextAnnotation ta = tokenizer.getTextAnnotation(text);

        /*Create textannotation*/
        AnnotatorService annotator = CuratorFactory.buildCuratorClient();
        TextAnnotation ta = annotator.createBasicTextAnnotation("corpus", "id", text);

        /*Add part-of-speech*/
        annotator.addView(ta, ViewNames.POS);

        annotator.addView(ta, ViewNames.SHALLOW_PARSE);
        /*ChunkerAnnotator*/
        //ChunkerAnnotator ca = new ChunkerAnnotator(true);
        //ca.initialize(new ChunkerConfigurator().getDefaultConfig());
        //ResourceManager rm = new ChunkerConfigurator().getDefaultConfig();
        //ca.addView(ta);

        Ngram ngram = new Ngram(lang, default_config);
        ngram.addView(ta);
        CrossLingualWikifier xlwikifier = CrossLingualWikifierManager.buildWikifierAnnotator(lang, default_config);
        xlwikifier.addView(ta);
        xlwikifier.result.mentions.forEach(m -> System.out.println(m.getSurface()+": "+m.getWikiTitle()));
        System.out.println("end");
    }
}
