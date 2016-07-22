package edu.psu.cse.vatest.optimization;

import java.util.Vector;


/**
 * Created by zongqing on 11/17/2015.
 */


public class WiFiOffline implements AbstractOffline {

    private double p_predict = 2.191;	 		 // unit in W
    private double p_offload_base = 1.000;		 // unit in W
    private double p_offload_step = 0.0002;       // unit in W
    private double p_exrtract = 1.178;			 // unit in W
    private double p_offload;

    private double e_video_offload;
    private double e_frame_offload;
    private double e_frame_prediction;
    private double e_frame_extraction;
    private double e_local_processing;
    private double e_minimum;
    private double e_maximum;


    private double t_video_offload;
    private double t_local_process;


    private double video_datasize; // video data size in KB
    private double extract_rate;   // how many frames per second
    private double video_length;  // video length in seconds.
    private double data_frame;     // data size of extract frame in KByte;
    private double trans_rate;     // transmission rate of wifi in KByte/S;

    private int total_frames;      // total extracted frames;
    private int predict_frames;    // frames to be predicted;
    private int offload_frames;    // frames to be offloaded;

    //extraction time;
    private double extract_time = 0.018;   // in second

    //prediction time = 240 + 400 * x;
    private double alpha = 0.240;    // in second
    private double beta = 0.400;     // in second

    //offloading time
    private double offload_time;   // in second

    public WiFiOffline(double video_datasize, /*KB*/ double video_length /*second*/, double extract_rate /*fps*/, double data_frame /*KB*/, double trans_rate /*KBps*/){
        this.video_datasize = video_datasize;
        this.video_length = video_length;
        this.extract_rate = extract_rate;
        this.data_frame = data_frame;
        this.trans_rate = trans_rate;
        this.total_frames = (int) (video_length * extract_rate);
        this.offload_time = data_frame/trans_rate;

        this.p_offload = p_offload_base + p_offload_step * trans_rate;
        this.t_video_offload = video_datasize / trans_rate;
    }

    public Solution algorithm(){
        Solution optimum = new Solution();
        Vector<Integer> batches = new Vector<Integer>();

        double e_prediction = 0;
        int n_frame_predict = 0;
        double local_time = localProcessing(batches);
//        if(t_video_offload <local_time){
//            optimum.mode = 0;
//            optimum.time = t_video_offload;
//            optimum.energy = p_offload * t_video_offload;
//            optimum.frames_offload = 0;
//            optimum.batches = null;
//        }else{
            optimum.mode = 1;
            optimum.time = local_time;

            for(int i=0; i<batches.size(); i++){
                n_frame_predict += batches.get(i);
                e_prediction += (batches.get(i) * beta + alpha) * p_predict;
            }
            optimum.energy = p_offload * local_time + e_prediction +
                    p_exrtract * extract_time * (int)(video_length * extract_rate);
            optimum.batches = batches;
            optimum.frames_offload = (int)(video_length * extract_rate) - n_frame_predict;
//        }


//		BufferedWriter bw = null;
//	    try{
//	    	double video_energy = p_offload * t_video_offload;
//	    	double extract_energy = p_exrtract * extract_time * (int)(video_length * extract_rate);
//	    	double frame_energy = p_offload * total_frames * offload_time + extract_energy;
//	    	double predict_energy = 0;
//	    	for(int i=0; i<batches.size(); i++){
//				n_frame_predict += batches.get(i);
//				predict_energy += (batches.get(i) * beta + alpha) * p_predict;
//			}
//	    	double local_energy = extract_energy + p_offload * local_time + predict_energy;
//	    	predict_energy = (alpha + (int)(video_length * extract_rate) * beta) * p_predict + extract_energy;
//	    	bw = new BufferedWriter(new FileWriter("WiFiOfflineEnergy.csv", true));
//	        bw.write(video_energy + "," + frame_energy + "," + local_energy + "," + predict_energy);
//	        bw.newLine();
//	        bw.flush();
//	    }catch (IOException e) {
//	    	e.printStackTrace();
//	    }
//	    finally {
//	    	if (bw != null) try {
//	    		bw.close();
//	    	} catch (IOException e) {}
//	    }


        System.out.println("video offload: " + t_video_offload + "S, " + p_offload * t_video_offload + "J");
        System.out.println("Local Process: " + local_time + "S, " + (p_offload * local_time + e_prediction +
                p_exrtract * extract_time * (int)(video_length * extract_rate)) + "J");

        return optimum;
    }

    public double localProcessing(Vector<Integer> vecBatch){
        vecBatch.clear();

        predict_frames = 0;
        offload_frames = 0;

        if(offload_time <= extract_time) {
//    		BufferedWriter bw = null;
//    	    try{
//    	    	bw = new BufferedWriter(new FileWriter("WiFiOfflineTime.csv", true));
//    	        bw.write(t_video_offload + "," + total_frames * extract_time + "," + extract_time * total_frames + "," + "0");
//    	        bw.newLine();
//    	        bw.flush();
//    	    }catch (IOException e) {
//    	    	e.printStackTrace();
//    	    }
//    	    finally {
//    	    	if (bw != null) try {
//    	    		bw.close();
//    	    	} catch (IOException e) {}
//    	    }

            return extract_time * total_frames;
        }

        int max_predict_frames = (int)Math.floor((offload_time * total_frames - alpha) / (beta + offload_time));
        double arrival_time = (offload_time * extract_time) /(offload_time - extract_time);

        if(max_predict_frames * arrival_time <= alpha) {
            predict_frames = (int) Math.floor((offload_time * total_frames - alpha) / (arrival_time + beta + offload_time));
            vecBatch.add(0, predict_frames);
            offload_frames = total_frames - predict_frames;

//    		BufferedWriter bw = null;
//    	    try{
//    	    	bw = new BufferedWriter(new FileWriter("WiFiOfflineTime.csv", true));
//    	        bw.write(t_video_offload + "," + total_frames * offload_time + "," + offload_frames * offload_time + ","
//    	        			+ predict_frames * arrival_time + alpha + beta * predict_frames);
//    	        bw.newLine();
//    	        bw.flush();
//    	    }catch (IOException e) {
//    	    	e.printStackTrace();
//    	    }
//    	    finally {
//    	    	if (bw != null) try {
//    	    		bw.close();
//    	    	} catch (IOException e) {}
//    	    }

            return offload_frames * offload_time >= predict_frames * arrival_time
                    + alpha + beta * predict_frames ? offload_frames * offload_time
                    : predict_frames * arrival_time + alpha + beta * predict_frames;

        }else{
            predict_frames = max_predict_frames;
            if(arrival_time >= alpha){
                while(predict_frames != 0){
                    vecBatch.add(1);
                    --predict_frames;
                }
            }else{
                while(predict_frames * arrival_time > alpha){
                    int temp_frames = (int) Math.ceil((predict_frames * arrival_time - alpha) / (arrival_time + beta));
                    if(predict_frames - temp_frames > 0 && temp_frames != 0){
                        vecBatch.add(0, predict_frames - temp_frames);
                        predict_frames = temp_frames;
                    }else
                        break;
                }
                vecBatch.add(0, predict_frames);
            }
        }

        predict_frames = (int)Math.floor((offload_time * total_frames - alpha * vecBatch.size() - arrival_time
                * vecBatch.get(0)) / (beta + offload_time));


        int temp_frames = 0;
        for(int i=0; i<vecBatch.size(); i++){
            temp_frames += vecBatch.get(i);
        }

        while(temp_frames - predict_frames >= vecBatch.lastElement()) {
            temp_frames -= vecBatch.lastElement();
            vecBatch.remove(vecBatch.size() - 1);
            if(vecBatch.size() == 0)
                break;
            else
                predict_frames = (int)Math.floor((offload_time * total_frames - alpha * vecBatch.size() - arrival_time
                        * vecBatch.get(0)) / (beta + offload_time));
        }
        if(vecBatch.size() != 0)
            vecBatch.set(vecBatch.size() - 1, vecBatch.lastElement() + predict_frames - temp_frames);


        predict_frames = 0;
        for(int i=0; i<vecBatch.size(); i++){
            predict_frames += vecBatch.get(i);
        }

        offload_frames = total_frames - predict_frames;

        double complete_offload = offload_frames * offload_time;
        double complete_predict = 0;
        int done_frames = 0;
        for(int i = 0; i < vecBatch.size(); i++){
            done_frames += vecBatch.get(i);
            if(complete_predict <= done_frames * arrival_time)
                complete_predict = done_frames * arrival_time + alpha + beta * vecBatch.get(i);
            else
                complete_predict += alpha + beta * vecBatch.get(i);
        }

        System.out.println("Offload completion time: " + complete_offload + "s");
        System.out.println("Prediction completion time: " + complete_predict + "s");


//		BufferedWriter bw = null;
//	    try{
//	    	bw = new BufferedWriter(new FileWriter("WiFiOfflineTime.csv", true));
//	        bw.write(t_video_offload + "," +  total_frames * offload_time + "," + complete_offload + "," + complete_predict);
//	        bw.newLine();
//	        bw.flush();
//	    }catch (IOException e) {
//	    	e.printStackTrace();
//	    }
//	    finally {
//	    	if (bw != null) try {
//	    		bw.close();
//	    	} catch (IOException e) {}
//	    }


        return complete_offload >= complete_predict ? complete_offload : complete_predict;
    }
}


