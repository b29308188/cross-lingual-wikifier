package edu.illinois.cs.cogcomp.xlwikifier.wikipedia;

import edu.illinois.cs.cogcomp.core.io.LineIO;
import edu.illinois.cs.cogcomp.tokenizers.MultiLingualTokenizer;
import edu.illinois.cs.cogcomp.tokenizers.Tokenizer;
import edu.illinois.cs.cogcomp.xlwikifier.ConfigParameters;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.ELMention;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.QueryDocument;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.WikiCand;
import edu.illinois.cs.cogcomp.core.algorithms.LevensteinDistance;
import org.apache.commons.io.FileUtils;
import org.mapdb.BTreeKeySerializer;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Fun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

/**
 * Created by ctsai12 on 12/1/15.
 */
public class WikiCandidateGenerator {

    private DB db;
    private Map<String, DB> db_pool = new HashMap<>();
    public ConcurrentNavigableMap<String, String[]> surface2titles;
    public ConcurrentNavigableMap<String, String[]> word2titles;
    public ConcurrentNavigableMap<String, String[]> fourgramidx;
    public ConcurrentNavigableMap<String, String[]> trigramidx;
    public ConcurrentNavigableMap<String, Integer> title2cnt;
    public NavigableSet<Fun.Tuple3<String, String, Double>> ptgivens;
    public NavigableSet<Fun.Tuple3<String, String, Double>> psgivent;
    public NavigableSet<Fun.Tuple3<String, String, Double>> ptgivenss;
    public NavigableSet<Fun.Tuple3<String, String, Double>> pssgivent;
    public NavigableSet<String> titles;
    private String lang;
    public Map<String, String> title2id, id2redirect;
    private Map<String, List<WikiCand>> cand_cache = new HashMap<>();
    private boolean use_cache = false;
    private int top = 10;
    public boolean tac = false;
    private Tokenizer tokenizer;
    private Set<String> valid_titles;
    private static Logger logger = LoggerFactory.getLogger(WikiCandidateGenerator.class);

    public WikiCandidateGenerator(){

    }

    public WikiCandidateGenerator(boolean tac){
        this.tac = tac;
    }

    public void setId2Redirect(Map<String, String> map){
        this.id2redirect = map;
    }

    public void setTitle2Id(Map<String, String> map){
        this.title2id = map;
    }

    public void loadDB(String lang, boolean read_only){
        this.lang = lang;
//        if(db_pool.containsKey(lang)) db = db_pool.get(lang);
//        else {
            String dbfile = ConfigParameters.db_path+"/candidates/"+lang+"_candidates";
//            String dbfile = "/shared/bronte/ctsai12/multilingual/olden/"+lang+"_candidates-new1";
            if(read_only) {
                db = DBMaker.newFileDB(new File(dbfile))
                        .cacheSize(1000)
                        .transactionDisable()
                        .closeOnJvmShutdown()
                        .readOnly()
                        .make();
            }
            else {
                db = DBMaker.newFileDB(new File(dbfile))
                        .cacheSize(1000)
                        .transactionDisable()
                        .closeOnJvmShutdown()
                        .make();
            }
//            db_pool.put(lang, db);
//        }
        surface2titles = db.createTreeMap("s2t")
                .keySerializer(BTreeKeySerializer.STRING)
                .makeOrGet();
        title2cnt = db.createTreeMap("title2cnt")
                .keySerializer(BTreeKeySerializer.STRING)
                .makeOrGet();
        fourgramidx = db.createTreeMap("4gram")
                .keySerializer(BTreeKeySerializer.STRING)
                .makeOrGet();
        ptgivens = db.createTreeSet("pts")
                .serializer(BTreeKeySerializer.TUPLE3)
                .makeOrGet();
        psgivent = db.createTreeSet("pst")
                .serializer(BTreeKeySerializer.TUPLE3)
                .makeOrGet();
        ptgivenss = db.createTreeSet("ptss")
                .serializer(BTreeKeySerializer.TUPLE3)
                .makeOrGet();
        pssgivent = db.createTreeSet("psst")
                .serializer(BTreeKeySerializer.TUPLE3)
                .makeOrGet();
//        titles = db.createTreeSet("titles")
//                .serializer(BTreeKeySerializer.STRING)
//                .makeOrGet();
        if(ConfigParameters.db_path.contains("olden")) {
            word2titles = db.createTreeMap("ss2t")
                    .keySerializer(BTreeKeySerializer.STRING)
                    .makeOrGet();
        }
    }

    public void closeDB(){
        if(db!=null && !db.isClosed()) {
            db.commit();
            db.close();
        }
        this.lang = null;
    }

    public Set<String> getAllTitles(){
        logger.info("Getting all titles...");
        Set<String> ret = new HashSet<>();
        for(Fun.Tuple3<String, String, Double> t: ptgivens){
            ret.add(t.a);
        }
        logger.info("{}", ret.size());
        return ret;
    }

    public String getFinalTitle(String title){
        title = title.toLowerCase().replaceAll(" ", "_");

        if(title2id.containsKey(title)){
            String id = title2id.get(title);
            if(id2redirect.containsKey(id)) {
                return id2redirect.get(id);
            }
        }
        return title;
    }

    public String getFinalTitleOldEN(String title){
        title = title.toLowerCase().replaceAll(" ", "_");

        if(id2redirect.containsKey(title)){
            return id2redirect.get(title);
        }
        return title;
    }

    public void setCandidates(QueryDocument doc, String lang){


        for(ELMention m: doc.mentions){
            if(m.is_ne) continue;

                List<WikiCand> cands = getCandidate(m.getMention(), lang);


//                cands = cands.subList(0, Math.min(top, cands.size()));
//                m.getCandidates().addAll(cands);
            double sum = 0.0;
            for(WikiCand c: cands)
                sum += c.score;
            for(WikiCand c: cands) {
                c.score /= sum;
                c.psgivent *= c.score;
            }

            cands = cands.stream().sorted((x1, x2) -> Double.compare(x2.psgivent, x1.psgivent))
                    .collect(Collectors.toList());

            cands = cands.subList(0, Math.min(top, cands.size()));

            m.setCandidates(cands);


        }
    }

    /**
     * The old one for the NAACL submission
     * @param surface
     * @param lang
     * @return
     */
    public List<WikiCand> getCandidate(String surface, String lang){
        if(this.lang == null || !this.lang.equals(lang)) {
            loadDB(lang, true);
            this.lang = lang;
        }
        surface = surface.toLowerCase().trim();

        List<WikiCand> cands = getCandsBySurface(surface, lang, false);
//        if (cands.size() == 0 && !tac)
//            cands = getCandidateByWord(surface, lang, 6);
//        if(tac && cands.size() == 0) {  // NAACL
//            cands = getCandsBySurface(surface, "en", false);
//        }

        return cands;
    }


    public List<WikiCand> getCandsBySurface(String surface, String lang, boolean extend){
        if(this.lang == null || !this.lang.equals(lang)) {
            tokenizer = MultiLingualTokenizer.getTokenizer(lang);
            loadDB(lang, true);
            this.lang = lang;
        }
        List<WikiCand> cands = new ArrayList<>();
        List<String> possible_surface = new ArrayList<>();
        possible_surface.add(surface);
        if(extend) {
            List<String> tokens = Arrays.asList(surface.split("\\s+"));
            for (int n = tokens.size() - 1; n >= 2; n--) {
                for (int i = 0; i < tokens.size() - n + 1; i++) {
                    String ngram = tokens.subList(i, i + n).stream().collect(joining(" "));
                    possible_surface.add(ngram);
                }
            }
        }
        int cnt = 0;
        for(String ps: possible_surface) {
            if (surface2titles.containsKey(ps)) {
                for (String title : surface2titles.get(ps)) {
//                    if (cnt++ == top) break;
                    double pts = 0, pst = 0;
                    for (Double score : Fun.filter(ptgivens, title, ps)) {
                        pts = score;
                        break;
                    }
                    for (Double score : Fun.filter(psgivent, ps, title)) {
                        pst = score;
                        break;
                    }
//                    int dist = getEditDistance(title, surface);
//                    double s = 1;
//                    if(dist != 0) s = 1.0/dist;

                    double s = 0;
                    if(title2cnt.containsKey(title))
                        s = title2cnt.get(title);
                    WikiCand cand = new WikiCand(title, s);
                    cand.psgivent = pst;
                    cand.ptgivens = pts;
                    cand.lang = lang;
                    cand.src = "surface";
                    cand.query_surface = surface;
                    cands.add(cand);
                }
            }
        }
        return cands;
    }

    private int getEditDistance(String title, String surface){
        title = title.replaceAll("_", " ");
        int idx = title.indexOf("(");
        if(idx>0)
            title = title.substring(0, idx).trim();
        int st = surface.split("\\s+").length;
        String[] tt = surface.split("\\s+");
        if(tt.length == 3 && st == 2 && tt[1].endsWith("."))
            title = tt[0]+" "+tt[2];
        int dist = LevensteinDistance.getLevensteinDistance(surface, title);
        return dist;
    }


    public List<WikiCand> getCandidateByWord(String surface, String lang, int max_cand){
        if(this.lang == null || !this.lang.equals(lang)) {
            loadDB(lang, true);
            tokenizer = MultiLingualTokenizer.getTokenizer(lang);
            this.lang = lang;
        }
        List<WikiCand> word_cands = new ArrayList<>();
        String[] tokens = null;

        if(lang.equals("zh"))
            tokens = surface.split("·");
        else
            tokens = tokenizer.getTextAnnotation(surface).getTokens();
//            tokens = surface.split("\\s+");
        int each_word_top = max_cand/tokens.length;
        for(String t: tokens) {
            if (word2titles.containsKey(t)) {
                int cnt = 0;
                List<String> titles = new ArrayList<>();
                for(String title: word2titles.get(t)){
                    if(cnt++==each_word_top) break;
                    double pts=0, pst=0;
                    for(Double score: Fun.filter(ptgivenss, title, t)){
                        pts = score;
                        break;
                    }
                    for(Double score: Fun.filter(pssgivent, t, title)){
                        pst = score;
                        break;
                    }
                    titles.add(title);

                    int dist = getEditDistance(title, surface);
                    double s = 1;
                    if(dist != 0) s = 1.0/dist;
                    WikiCand cand = new WikiCand(title, s);
                    cand.psgivent = pst;
                    cand.ptgivens = pts;
                    cand.lang = lang;
                    cand.src = "word";
                    cand.query_surface = surface;
                    word_cands.add(cand);
                }
            }
        }
        // TODO: the way of sorting is changed from the NAACL submission
//        word_cands = word_cands.stream().sorted((x1, x2) -> Double.compare(x2.getScore(), x1.getScore())).collect(toList());
        word_cands = word_cands.stream().sorted((x1, x2) -> Double.compare(x2.ptgivens, x1.ptgivens)).collect(toList());
        return word_cands.subList(0,Math.min(word_cands.size(), max_cand));
    }

    private List<WikiCand> getCandidatesByNgram(String surface){

        int top = 20000000;

        List<String> cand_titles = new ArrayList<>();
        for(int i = 0; i < surface.length()-4; i++){
            String ngram = surface.substring(i, i+4);
            if(fourgramidx.containsKey(ngram)){
                String[] titles = fourgramidx.get(ngram);
                cand_titles.addAll(Arrays.asList(titles));
            }
        }

        Map<String, Long> t2cnt = cand_titles.stream().collect(groupingBy(x -> x, counting()));
        List<Map.Entry<String, Long>> sortedt = t2cnt.entrySet().stream().sorted((x1, x2) -> Long.compare(x2.getValue(), x1.getValue()))
                .collect(toList());

        List<WikiCand> ret = new ArrayList<>();
        for(int i = 0; i < sortedt.size() && i < top; i++){
            String title = sortedt.get(i).getKey();
            Long cnt = sortedt.get(i).getValue();

            int dist = getEditDistance(title, surface);
            double s = 1;
            if(dist != 0) s = 1.0/dist;
            WikiCand c = new WikiCand(title, s);
            c.lang = lang;
            c.src = "ngram";
            c.query_surface = surface;
            ret.add(c);
        }
        return ret;
    }

    private void populateWord2Title(String file, String lang){
        logger.info("Populating "+lang+" candidate database from "+file);
        if(db==null)
            loadDB(lang, false);

//        word2titles.clear();
//        pssgivent.clear();
//        ptgivenss.clear();

        Map<String, Map<String, Integer>> s2t = new HashMap<>();
        Map<String, Map<String, Integer>> t2s = new HashMap<>();

        try {
            for(String line: LineIO.read(file)){
//            for(File f: new File(wikidir).listFiles()) {
//                Map<String, String> s2t_ = getSurface2Title(f.getName(), lang);
//                for(String surf: s2t_.keySet()){

                String[] sp = line.split("\t");
                if(sp.length<2) continue;
                    String s = sp[0].toLowerCase().trim();
                String t;
                if(lang.equals("en"))
                    t = getFinalTitleOldEN(sp[1]);
                else
                    t = getFinalTitle(sp[1]);

                    String[] tokens;
                    if (lang.equals("zh"))
                        tokens = s.split("·");
                    else
                        tokens = tokenizer.getTextAnnotation(s).getTokens();

                    for (String ss : tokens) {
                        if (!s2t.containsKey(ss))
                            s2t.put(ss, new HashMap<>());
                        Map<String, Integer> tmp = s2t.get(ss);
                        if(tmp.containsKey(t))
                            tmp.put(t, tmp.get(t)+1);
                        else
                            tmp.put(t, 1);
                        if (!t2s.containsKey(t))
                            t2s.put(t, new HashMap<>());
                        tmp = t2s.get(t);
                        if(tmp.containsKey(ss))
                            tmp.put(ss, tmp.get(ss)+1);
                        else
                            tmp.put(ss, 1);
                    }
//                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.info("s2t size "+s2t.size());
        logger.info("t2s size "+t2s.size());

//        for(String title: t2s.keySet()){
//            String[] title_tokens = null;
//            if(lang.equals("zh"))
//                title_tokens = title.toLowerCase().split("·");
//            else
//                title_tokens = title.toLowerCase().split("_");
//
//            for(String token: title_tokens){
//                if (!s2t.containsKey(token))
//                    s2t.put(token, new HashMap<>());
//                Map<String, Integer> tmp = s2t.get(token);
//                if(tmp.containsKey(title))
//                    tmp.put(title, tmp.get(title)+1);
//                else
//                    tmp.put(title, 1);
//
//                if(!t2s.containsKey(title))
//                    t2s.put(title, new HashMap<>());
//                tmp = t2s.get(title);
//                if(tmp.containsKey(token))
//                    tmp.put(token, tmp.get(token)+1);
//                else
//                    tmp.put(token, 1);
//            }
//        }

//        if(lang.equals("olden")){
//            for(String r: id2redirect.keySet()){
//                String[] tokens = r.trim().split("_");
//                String title = id2redirect.get(r);
//                for(String token: tokens) {
//                    if (!s2t.containsKey(token))
//                        s2t.put(token, new ArrayList<>());
//                    s2t.get(token).add(title);
//                    t2s.get(title).add(token);
//                }
//            }
//        }

        logger.info("Calculating p(t|s)...");
        int cnt = 0;
        for(String surface: s2t.keySet()){
            cnt ++;
            if(cnt % 10000 == 0)
                System.out.print(cnt*100.0/s2t.size()+"\r");
            Map<String, Integer> t2cnt = s2t.get(surface);
            double sum = 0;
            for(String title: t2cnt.keySet()){
                sum+=t2cnt.get(title);
            }
            for(String title: t2cnt.keySet()){
                ptgivenss.add(new Fun.Tuple3<>(title, surface, t2cnt.get(title)/sum));
            }

            List<String> sorted_titles = t2cnt.entrySet().stream().sorted((x1, x2) -> Integer.compare(x2.getValue(), x1.getValue()))
                    .map(x -> x.getKey()).collect(toList());

            sorted_titles = sorted_titles.subList(0, Math.min(sorted_titles.size(), 50));

            String[] titles = new String[sorted_titles.size()];
            titles = sorted_titles.toArray(titles);
//            System.out.println(word2titles.size());
            try {
                word2titles.put(surface, titles);
            } catch (Exception e){
                System.out.println(surface);
                System.out.println(Arrays.asList(titles));
                continue;
            }
        }

        logger.info("Calculating p(s|t)...");
        for(String title: t2s.keySet()){
            Map<String, Integer> s2cnt = t2s.get(title);
            double sum = 0;
            for(String surface: s2cnt.keySet()){
                sum+=s2cnt.get(surface);
            }
            for(String surface: s2cnt.keySet()){
                pssgivent.add(new Fun.Tuple3<>(surface, title, s2cnt.get(surface)/sum));
            }
        }
    }

    /**
     * Import the candidate generation DB
     * @param lang
     * @param redirect_file
     * @param page_file
     * @param cand_file
     */
    public void populateDB(String lang, String redirect_file, String page_file, String cand_file){
        tokenizer = MultiLingualTokenizer.getTokenizer(lang);
        DumpReader dr = new DumpReader();
        dr.readRedirects(redirect_file);
        dr.readTitle2ID(page_file);
        this.setId2Redirect(dr.id2redirect);
        this.setTitle2Id(dr.title2id);
        populateMentionDB(cand_file, lang);
        populateWord2Title(cand_file, lang);
        db.commit();
        db.close();
//        loadDB(lang, true);
    }

    public void populateDBforOldEN(String lang, String cand_file){
        tokenizer = MultiLingualTokenizer.getTokenizer(lang);

//        this.id2redirect = new HashMap<>();
//        try {
//            logger.info("Loading valid titles...");
//            valid_titles = LineIO.read("/shared/preprocessed/ctsai12/multilingual/wikidump-2014/en/docs/file.list.rand")
//                    .stream().map(x -> x.toLowerCase().replaceAll(" ", "_"))
//                    .collect(Collectors.toSet());
//
//            logger.info("Loading redirects...");
//            String rfile ="/shared/bronte/tac2014/data/WikiData/Redirects/2014-01-24.redirect";
//            for(String line: LineIO.read(rfile)){
//                String[] parts = line.toLowerCase().split("\t");
//                if(parts.length == 2)
//                    if(valid_titles.contains(parts[1]))
//                        id2redirect.put(parts[0], parts[1]);
//            }
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }

        populateMentionDB(cand_file, lang);
//        populateWord2Title(cand_file, lang);
        db.commit();
        db.close();
    }

    public void populate4GramIdx(String lang, String redirect_file, String page_file){
        loadDB(lang, false);
        DumpReader dr = new DumpReader();
        dr.readRedirects(redirect_file);
        dr.readTitle2ID(page_file);
        this.setId2Redirect(dr.id2redirect);
        this.setTitle2Id(dr.title2id);

        Map<String, Map<String, Integer>> gram2titlecnt = new HashMap<>();
        System.out.println("Counting 4 grams...");
        int cnt = 0;
        for(String title: title2id.keySet()){
            if(cnt++%100000 == 0) System.out.print(cnt+"\r");
            String ftitle = getFinalTitle(title);
            String[] tokens = title.split("_");
            for(String token: tokens){
                token = token.toLowerCase();
                if(token.trim().isEmpty()) continue;
                for(int i = 0; i < token.length()-3; i++){
                    String gram = token.substring(i, i + 3);
                    if(!gram2titlecnt.containsKey(gram))
                        gram2titlecnt.put(gram, new HashMap<>());
                    Map<String, Integer> titlecnt = gram2titlecnt.get(gram);
                    if(!titlecnt.containsKey(ftitle))
                        titlecnt.put(ftitle, 1);
                    else
                        titlecnt.put(ftitle, titlecnt.get(ftitle)+1);
                }
            }
        }
        System.out.println("#4grams "+gram2titlecnt.size());

        System.out.println("Sorting and saving...");
        for(String gram: gram2titlecnt.keySet()){
            List<String> sorted = gram2titlecnt.get(gram).entrySet().stream()
                    .sorted((x1, x2) -> Integer.compare(x2.getValue(), x1.getValue()))
                    .map(x -> x.getKey()).collect(toList());

            String[] titles = new String[sorted.size()];
            titles = sorted.toArray(titles);
            trigramidx.put(gram, titles);
        }
    }

    private Map<String, String> getSurface2Title(String title, String lang){

        Map<String, String> ret = new HashMap<>();

        try {

            String plain = FileUtils.readFileToString(new File(ConfigParameters.dump_path+lang+"/"+lang+"_wiki_view/plain/"+title), "UTF-8");
            for(String line: LineIO.read(ConfigParameters.dump_path+lang+"/"+lang+"_wiki_view/annotation/"+title)){

                if(line.startsWith("#")){

                    String[] sp = line.substring(1).split("\t");
                    if(sp.length < 3) continue;
                    int start = Integer.parseInt(sp[1]);
                    int end = Integer.parseInt(sp[2]);

                    ret.put(plain.substring(start, end), sp[0]);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return ret;

    }

    private void populateMentionDB(String file, String lang){
        logger.info("Populating "+lang+" candidate database from "+file);
        if(db == null)
            loadDB(lang, false);

        surface2titles.clear();
        psgivent.clear();
        ptgivens.clear();

        Map<String, Map<String, Integer>> s2t = new HashMap<>();
        Map<String, Map<String, Integer>> t2s = new HashMap<>();
        Map<String, Integer> title2cnt = new HashMap<>();

        try {
            for(String line: LineIO.read(file)){

                String[] sp = line.split("\t");

                if(sp.length<2) continue;

                String s = sp[0].toLowerCase().trim();
                String t = sp[1];

                t = getFinalTitle(t);

                if(title2cnt.containsKey(t))
                    title2cnt.put(t, title2cnt.get(t)+1);
                else
                    title2cnt.put(t, 1);


                if (!s2t.containsKey(s))
                    s2t.put(s, new HashMap<>());

                Map<String, Integer> tmp = s2t.get(s);

                if(!tmp.containsKey(t))
                    tmp.put(t, 1);
                else
                    tmp.put(t, tmp.get(t)+1);

                if (!t2s.containsKey(t))
                    t2s.put(t, new HashMap<>());

                tmp = t2s.get(t);
                if(!tmp.containsKey(s))
                    tmp.put(s, 1);
                else
                    tmp.put(s, tmp.get(s)+1);
//                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        logger.info("Calculating p(t|s)...");
        int cnt = 0;
        for(String surface: s2t.keySet()){
            cnt++;
            if(cnt % 10000 == 0)
                System.out.print(cnt*100.0/s2t.size()+"\r");
            Map<String, Integer> t2cnt = s2t.get(surface);
            double sum = 0;
            for(String title: t2cnt.keySet()){
                sum+=t2cnt.get(title);
            }
            for(String title: t2cnt.keySet()){
                ptgivens.add(new Fun.Tuple3<>(title, surface, t2cnt.get(title)/sum));
            }

            List<String> sorted_titles = t2cnt.entrySet().stream().sorted((x1, x2) -> Integer.compare(x2.getValue(), x1.getValue()))
                    .map(x -> x.getKey()).collect(toList());

            sorted_titles = sorted_titles.subList(0, Math.min(sorted_titles.size(), 50));

            String[] titles = new String[sorted_titles.size()];
            titles = sorted_titles.toArray(titles);
            surface2titles.put(surface, titles);
        }

        logger.info("Calculating p(s|t)...");
        for(String title: t2s.keySet()){
            Map<String, Integer> s2cnt = t2s.get(title);
            double sum = 0;
            for(String surface: s2cnt.keySet()){
                sum+=s2cnt.get(surface);
            }
            for(String surface: s2cnt.keySet()){
                psgivent.add(new Fun.Tuple3<>(surface, title, s2cnt.get(surface)/sum));
            }
        }

        logger.info("Writing title to count");
        for(String title: title2cnt.keySet()){
            this.title2cnt.put(title, title2cnt.get(title));
        }
    }

    public void genCandidates(List<QueryDocument> docs, String lang){
//        logger.info("Generating candidates...");
        tokenizer = MultiLingualTokenizer.getTokenizer(lang);
        if(db == null || db.isClosed() || this.lang != lang)
            loadDB(lang, true);
        for(QueryDocument doc: docs) {
            setCandidates(doc, lang);
        }
    }

    public void selectMentions(List<QueryDocument> docs, double p){
        System.out.println("#mentions before selection: "+docs.stream().flatMap(x -> x.mentions.stream()).count());
        List<ELMention> easy_all = new ArrayList<>();
        List<ELMention> hard_all = new ArrayList<>();
        for(QueryDocument doc: docs) {
            List<ELMention> hard = doc.mentions.stream().filter(x -> x.getCandidates().size()==0
                    || !x.getCandidates().get(0).getTitle().toLowerCase().equals(x.gold_wiki_title.toLowerCase()))
                    .collect(toList());
            hard.forEach(x -> x.eazy = false);
            hard_all.addAll(hard);
            List<ELMention> easy = doc.mentions.stream().filter(x -> x.getCandidates().size()>0
                    && x.getCandidates().get(0).getTitle().toLowerCase().equals(x.gold_wiki_title.toLowerCase()))
                    .collect(toList());
            easy.forEach(x -> x.eazy = true);
            easy_all.addAll(easy);

        }
        System.out.println("#hard "+hard_all.size()+" #easy "+easy_all.size());
        Collections.shuffle(easy_all, new Random(0));
        hard_all.addAll(easy_all.subList(0, (int) Math.min(easy_all.size(), hard_all.size()*p)));
        for(QueryDocument doc: docs){
            doc.mentions = hard_all.stream().filter(x -> x.getDocID().equals(doc.getDocID()))
                    .sorted((x1, x2) -> Integer.compare(x1.getStartOffset(), x2.getStartOffset()))
                    .collect(toList());
        }
        logger.info("#mentions after selection: "+docs.stream().flatMap(x -> x.mentions.stream()).count());
    }

    public static void main(String[] args) {
        ConfigParameters params = new ConfigParameters();
        params.getPropValues();
        WikiCandidateGenerator g = new WikiCandidateGenerator();
        g.tac = true;
        g.loadDB(args[0], false);
        for(WikiCand c:g.getCandsBySurface(args[1], "en", false))
            System.out.println(c);
//        System.out.println(g.getCandsBySurface("Michael Pettis", "en", false));
//        System.out.println(g.getCandsBySurface("czech", "en", false));
        System.exit(-1);

        String lang = "en";
        String redirect = "/shared/bronte/ctsai12/multilingual/wikidump/en/enwiki-20151002-redirect.sql.gz";
        String page = "/shared/bronte/ctsai12/multilingual/wikidump/en/enwiki-20151002-page.sql.gz";
//        String redirect = "/shared/bronte/ctsai12/multilingual/wikidump/en-old/enwiki-20080103-redirect.sql";
//        String page = "/shared/bronte/ctsai12/multilingual/wikidump/en-old/enwiki-20080103-page.sql";
//        String redirect = "/shared/bronte/ctsai12/multilingual/wikidump/"+lang+"/"+lang+"wikipedia-20151123-redirect.sql";
//        String page = "/shared/bronte/ctsai12/multilingual/wikidump/"+lang+"/"+lang+"wikipedia-20151123-page.sql";
        String cand_view = "/shared/bronte/ctsai12/multilingual/wikidump/cand_view/"+lang+".wikiview";
//        g.populateDB(lang, redirect, page, cand_view);
//        g.populate4GramIdx(lang, redirect, page);

//        String redirect = "/shared/bronte/ctsai12/multilingual/wikidump/es/eswiki-20150901-redirect.sql";
//        String page = "/shared/bronte/ctsai12/multilingual/wikidump/es/eswiki-20150901-page.sql";
//        DumpReader dr = new DumpReader();
//        dr.readTitle2ID(page);
//        dr.readRedirects(redirect);
//        g.setTitle2Id(dr.title2id);
//        g.setId2Redirect(dr.id2redirect);

        g.closeDB();
    }


}
