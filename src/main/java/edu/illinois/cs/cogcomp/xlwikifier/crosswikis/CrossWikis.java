package edu.illinois.cs.cogcomp.xlwikifier.crosswikis;


import org.mapdb.*;
import org.mapdb.Serializer;
import org.mapdb.serializer.SerializerArray;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

/**
 * Created by lchen112 on 2/8/17.
 */
public class CrossWikis {
    private static DB db;
    public static HTreeMap<String, String[]> surface2title;
    public static HTreeMap<String, Double[]> surface2prob;

    public static void loadDB(boolean read_only){
        String db_file = "/shared/experiments/lchen112/crosswikis/crosswikis.db";
        if (read_only) {
            db = DBMaker.fileDB(db_file)
                    .fileChannelEnable()
                    .allocateStartSize(1024*1024*1024)
                    .allocateIncrement(1024*1024*1024)
                    .closeOnJvmShutdown()
                    .readOnly()
                    .make();
            surface2title = db.hashMap("surface2title")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(new SerializerArray(Serializer.STRING))
                    .open();
            surface2prob = db.hashMap("surface2prob")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(new SerializerArray(Serializer.DOUBLE))
                    .open();
        } else {
            db = DBMaker.fileDB(db_file)
                    .closeOnJvmShutdown()
                    .make();
            surface2title = db.hashMap("surface2title")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(new SerializerArray(Serializer.STRING))
                    .createOrOpen();
            surface2prob = db.hashMap("surface2prob")
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(new SerializerArray(Serializer.DOUBLE))
                    .createOrOpen();
        }
    }
    public static void importDump() throws Exception{
        String file = "/shared/experiments/lchen112/crosswikis/dictionary";
        loadDB(false);
        List<String> s2t = new ArrayList<String>();
        List<Double> s2p = new ArrayList<Double>();
        String[] titles;
        Double[] probs;
        String surface = "###########################";
        String title;
        Double prob;
        BufferedReader br = new BufferedReader(new FileReader(file));
        String line = br.readLine();
        int cnt = 0;
        while(line != null){
            cnt ++;
            if(cnt %100000 == 0) System.out.printf("Process line %d\n", cnt);
            String[] parts = line.split("\t");
            String[] tokens = parts[1].trim().split(" ");
            prob = Double.parseDouble(tokens[0]);
            title = tokens[1];
            if(surface.equals(parts[0])){
                s2t.add(title);
                s2p.add(prob);
            }
            else{
                titles = new String[s2t.size()];
                for (int i = 0; i < titles.length; i++) titles[i] = s2t.get(i);
                surface2title.put(surface, titles);
                probs = new Double[s2p.size()];
                for (int i = 0; i < probs.length; i++) probs[i] = s2p.get(i);
                surface2prob.put(surface, probs);
                s2t = new ArrayList<String>();
                s2p = new ArrayList<Double>();
            }
            surface = parts[0];
            line = br.readLine();
        }
        titles = new String[s2t.size()];
        for (int i = 0; i < titles.length; i++) titles[i] = s2t.get(i);
        surface2title.put(surface, titles);
        probs = new Double[s2p.size()];
        for (int i = 0; i < probs.length; i++) probs[i] = s2p.get(i);
        surface2prob.put(surface, probs);
        s2t = new ArrayList<String>();
        s2p = new ArrayList<Double>();
        System.out.println("end");
    }

    public static void main(String[] args) throws Exception {
        //importDump();
        System.out.println("load db");
        loadDB(true);
        System.out.println("query db");
        String query = "power";
        Object[] tmp = surface2title.get(query);
        Object[] tmp2 = surface2prob.get(query);
        for(int i = 0 ; i < tmp.length; i++) {
            System.out.println(tmp[i] + " ->" + tmp2[i]);
        }
        db.close();
    }
}