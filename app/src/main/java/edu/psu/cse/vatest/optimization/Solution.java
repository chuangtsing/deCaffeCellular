package edu.psu.cse.vatest.optimization;

import java.util.Vector;

/**
 * Created by Zack on 12/1/15.
 */
public class Solution{
    public int mode;            //0, video offload; 1, local processing
    public double energy;       //energy consumption;
    public double time;         //completion time;
    public int frames_offload;  //frames to be offloaded;
    public Vector<Integer> batches;  //frame prediction batches if any.
}