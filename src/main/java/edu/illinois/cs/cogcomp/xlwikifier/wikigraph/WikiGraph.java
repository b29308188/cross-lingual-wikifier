package edu.illinois.cs.cogcomp.xlwikifier.wikigraph;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

import edu.illinois.cs.cogcomp.xlwikifier.datastructures.ELMention;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.QueryDocument;
import edu.illinois.cs.cogcomp.xlwikifier.datastructures.WikiCand;
import edu.illinois.cs.cogcomp.xlwikifier.core.Ranker;
import org.mapdb.*;
import org.mapdb.Serializer;

/**
 * Created by lchen112 on 3/2/17.
 */
public class WikiGraph {
    private  DB db;
    public  HTreeMap<String, String> edges;
    public ArrayList<Integer>[] inNbrs;
    public int[] outDeg;
    public WikiGraph(){
        loadDB(true);
        //closeDB();
    }
    public void loadDB(boolean read_only){
        String db_file = "/shared/experiments/lchen112/PPR/wikipedia_en_2013/wiki_graph2.dp";
        if (read_only) {
            db = DBMaker.fileDB(db_file)
                    .fileChannelEnable()
                    .allocateStartSize(1024*1024*1024)
                    .allocateIncrement(1024*1024*1024)
                    .closeOnJvmShutdown()
                    .readOnly()
                    .make();
            edges = db.hashMap("edges")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.STRING)
                    .open();
        } else {
            db = DBMaker.fileDB(db_file)
                    .closeOnJvmShutdown()
                    .make();
            edges = db.hashMap("edges")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.STRING)
                    .createOrOpen();
        }
    }

    public void dumpdb() throws Exception{
        loadDB(false);
        String file = "/shared/experiments/lchen112/PPR/wikipedia_en_2013/wiki_hr.txt";
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line = br.readLine();
        int cnt = 0;
        while(line != null){
            if(cnt %100000 == 0) System.out.printf("Process line %d\n", cnt);
            cnt ++;
            String[] parts = line.split(" ");
            String u = parts[0].substring(2).toLowerCase();
            String v = parts[1].substring(2).toLowerCase();
            edges.put( u + "@@@" + v, "");
            line = br.readLine();
        }
        closeDB();
    }
    public void closeDB() {
        if (db != null && !db.isClosed())
            db.close();
    }
    public boolean queryEdge(String u, String v){
        return edges.containsKey(u+"@@@"+v) || edges.containsKey(v+"@@@"+u);
    }
    public void inference(QueryDocument doc, Ranker ranker){
        //initialization
        int M = doc.mentions.size();
        int topK = 5;
        int window_size = 10;
        int N = M*topK;
        ArrayList<Integer>[] inNbrs = new ArrayList[N];
        for( int i = 0; i < N; i++)
            inNbrs[i] = new ArrayList<Integer>();
        int[] outDeg = new int[N];
        double[] personal= new double[N];
        Arrays.fill(outDeg, 0);
        //Arrays.fill(personal, 1.0/N);
        //for(ELMention m : doc.mentions)
            //m.setCandidates(m.getCandidates().subList(0,Math.min(topK, m.getCandidates().size())));

        //build graph
        for(int i = 0; i < M; i++){
            ELMention m1 = doc.mentions.get(i);
            for(int p = 0; p < m1.getCandidates().size() && p < topK; p++){
                personal[i*topK+p] = m1.getCandidates().get(p).getScore();
                for(int s = -window_size; s <= window_size; s ++){
                    int j = i + s;
                    if(j < 0 || j >= M || i == j)
                        continue;
                    ELMention m2 = doc.mentions.get(j);
                    if(m1.getSurface().toLowerCase().equals(m2.getSurface().toLowerCase()))
                        continue;
                    for(int q = 0; q < m2.getCandidates().size() && q < topK; q++){
                        if((m1.getCandidates().get(p).title.equals(m2.getCandidates().get(q).title)))
                            continue;

                        double sim = ranker.fm.we.cosine(
                                ranker.fm.we.getTitleVector(m1.getCandidates().get(p).title, "en"),
                                ranker.fm.we.getTitleVector(m2.getCandidates().get(q).title, "en"));
                        if( sim> 0.7 )

                        //if(queryEdge(m1.getCandidates().get(p).title,
                                //m2.getCandidates().get(q).title))
                        {
                            inNbrs[i*topK+p].add(j*topK+q);
                            outDeg[j*topK+q] += 1;
                            System.out.printf("m1 = %s, m2 = %s  u = %s, v = %s\n",
                                    m1.getSurface(),
                                    m2.getSurface(),
                                    m1.getCandidates().get(p).title,
                                    m2.getCandidates().get(q).title);
                        }
                    }
                }
            }
        }
        //normalization

        double total = 0;
        for(int i = 0; i < N; i++)
            total += Math.exp(personal[i]);
        for(int i = 0; i < N; i++)
            personal[i] = Math.exp(personal[i])/total;


        List<WikiCand> can;
        //re-ranking
        double[] PR = PersonalizedPageRank.calScores(inNbrs, outDeg, personal);
        for(int i = 0; i < M; i++) {
            ELMention m = doc.mentions.get(i);
            //System.out.println(m.getSurface());
            for (int p = 0; p < m.getCandidates().size(); p++){
                if(p < topK)
                    m.getCandidates().get(p).setScore(PR[i*topK+p]);
                else
                    m.getCandidates().get(p).setScore(0);
                //System.out.printf("%s %f\n", m.getCandidates().get(p).title, PR[i*topK+p]);
            }
            //System.out.printf("%s %f\n", m.getCandidates().get(0).title, m.getCandidates().get(0).getScore());
            //System.out.printf("%s %f\n", m.getCandidates().get(1).title, m.getCandidates().get(1).getScore());
            Collections.sort(m.getCandidates(), (x1, x2) -> Double.compare(x2.getScore(), x1.getScore()));
            //m.getCandidates().stream().sorted(
                    //(x1, x2) -> Double.compare(x2.getScore(), x1.getScore()));
            //System.out.println(m.getCandidates().get(0).getTitle());
            //System.out.printf("%s %f\n", m.getCandidates().get(0).title, m.getCandidates().get(0).getScore());
            //System.out.printf("%s %f\n", m.getCandidates().get(1).title, m.getCandidates().get(1).getScore());
            if(m.getCandidates().size() == 0)
                m.setWikiTitle("NIL");
            else{
                //System.out.println("SET->" + m.getCandidates().get(0).getTitle());
                m.setWikiTitle(m.getCandidates().get(0).getTitle());
            }
        }
        //System.out.print("end");
    }
    public static void main(String[] args) throws Exception {
        //System.out.println("hello");
        WikiGraph WG = new WikiGraph();
        WG.loadDB(true);
        System.out.println("world");
        //System.out.println(edges.size());
        System.out.println(WG.edges.containsKey("vein@@@blood"));
        //System.out.println("end");
    }
}
