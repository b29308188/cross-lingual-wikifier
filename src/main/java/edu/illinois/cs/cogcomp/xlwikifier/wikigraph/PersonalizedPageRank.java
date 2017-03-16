package edu.illinois.cs.cogcomp.xlwikifier.wikigraph;
import java.util.*;

/**
 * Created by lchen112 on 3/3/17.
 */
public class PersonalizedPageRank {
    public static double minus_max(double[] a, double[] b){
        double c, max = 0;
        for(int i = 0; i < a.length; i++){
            c = Math.abs(a[i]-b[i]);
            if(c > max)
                max = c;
        }
        return max;
    }
    public static double[] calScores(ArrayList<Integer>[] inNbrs, int[] outDeg, double[] personal ){
        int N = outDeg.length;
        double alpha = 0.1;
        double tol = 0.0001;
        double[] PR = new double[N];
        double[] oldPR = new double[N];
        Arrays.fill(oldPR, 0);
        Arrays.fill(PR, 1.0/N);
        int u, v;
        while(minus_max(PR, oldPR) > tol){
            oldPR = PR.clone();
            for(u = 0; u < N; u++){
                double s = 0;
                for(int i = 0; i < inNbrs[u].size(); i ++){
                    v = inNbrs[u].get(i);
                    s += oldPR[v]/ outDeg[v];
                }
                PR[u] = alpha*s + (1-alpha)*personal[u];
                //PR[u] = alpha*s + (1-alpha);
            }
        }
        return PR;
    }

    public static void main(String[] args) throws Exception {
        int N = 3;
        double alpha = 0.7;
        double tol = 0.001;
        ArrayList<Integer>[] inNbrs = new ArrayList[N];
        for( int i = 0; i < N; i++)
            inNbrs[i] = new ArrayList<Integer>();
        double[] PR = new double[N];
        double[] oldPR = new double[N];
        int[] outDeg = new int[N];
        Arrays.fill(oldPR, 0);
        Arrays.fill(PR, 1.0/N);
        inNbrs[0].add(1);
        inNbrs[1].add(0);
        inNbrs[1].add(2);
        inNbrs[2].add(1);
        outDeg[0] = 1;
        outDeg[1] = 2;
        outDeg[2] = 1;


        int u, v;
        while(minus_max(PR, oldPR) > tol){
            oldPR = PR.clone();
            for(u = 0; u < N; u++){
                double s = 0;
                for(int i = 0; i < inNbrs[u].size(); i ++){
                    v = inNbrs[u].get(i);
                    s += oldPR[v]/ outDeg[v];
                }
                PR[u] = alpha*s + (1-alpha)/N;
            }
        }
        System.out.println("ee");
    }
}
