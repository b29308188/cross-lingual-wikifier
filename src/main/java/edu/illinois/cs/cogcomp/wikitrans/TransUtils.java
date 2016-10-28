package edu.illinois.cs.cogcomp.wikitrans;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ctsai12 on 9/28/16.
 */
public class TransUtils {
    public static List<List<Integer>> getAllAssignments(int l, int m){
        List<List<Integer>> ret = new ArrayList<>();

        if(l == 0){
            ret.add(new ArrayList<>());
            return ret;
        }


        List<List<Integer>> ass = getAllAssignments(l - 1, m);
        for(int j = 0; j < m; j++){
            for(List<Integer> as: ass){
                List<Integer> tmp = new ArrayList<>(as);
                tmp.add(j);
                ret.add(tmp);
            }
        }

        return ret;
    }

    public static void updateExistMap(String key1, String key2, Double value, Map<String, Map<String, Double>> map){
        if(!map.containsKey(key1)) return;
        if(!map.get(key1).containsKey(key2)) return;

        map.get(key1).put(key1, value);

    }

    public static void addToMap(String key1, String key2, Double value, Map<String, Map<String, Double>> map){
        if(!map.containsKey(key1)) map.put(key1, new HashMap<>());

        Map<String, Double> submap = map.get(key1);

        if(!submap.containsKey(key2)) submap.put(key2, 0.0);

        submap.put(key2, submap.get(key2)+value);

    }

    public static void addToMap(String key, Double value, Map<String, Double> map){

        if(!map.containsKey(key)) map.put(key, 0.0);

        map.put(key, map.get(key)+value);

    }

    public static void normalizeProb(Map<String, Map<String, Double>> map){
//        System.out.println("Normalizing probs...");
        for(String s:  map.keySet()){
            Map<String, Double> t2prob = map.get(s);
            double sum = 0;
            for(String t: t2prob.keySet()){
                sum+=t2prob.get(t);
            }
            for(String t: t2prob.keySet()){
                t2prob.put(t, t2prob.get(t)/sum);
            }
        }
    }

    public static void normalizeProb1(Map<String, Double> map){
//        System.out.println("Normalizing probs...");
        double sum = 0;
        for(String k: map.keySet()){
            sum+=map.get(k);
        }
        for(String k: map.keySet()){
            map.put(k, map.get(k)/sum);
        }
    }
}
