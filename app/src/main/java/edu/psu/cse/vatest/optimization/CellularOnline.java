package edu.psu.cse.vatest.optimization;

import android.annotation.TargetApi;
import android.app.ApplicationErrorReport;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Pair;

import org.opencv.core.Mat;

import java.util.Vector;
import java.math.*;

import edu.psu.cse.vatest.VANet;
import edu.psu.cse.vatest.VATest;


public class CellularOnline {	
	private double video_datasize;
	private double video_length;
	private double extract_rate;
	private double frame_datasize;
	private double trans_rate;
	private int total_frames;
    private int n_failures = 0;
	
	//extraction time;
    private double extract_time = 0.018;   // in in second
    //prediction time = 240 + 400 * x;
	private double predict_alpha = 0.240; // in second
	private double predict_beta = 0.400;  // in second
	private double time_local = 1000*(predict_alpha + predict_beta);

	private double local_power = 2191; // in mW
	private double energy_local = local_power*time_local;

    private double optimal_predict;
    private int max_offload;

    private double backoff;
	private double data_constraint;
	
	
	public CellularOnline(double video_length /*second*/,
                   double extract_rate /*fps*/,
                   double frame_datasize /*KB*/,
                   double data_constraint /*kB*/) {
		
		this.video_length = video_length;
		this.extract_rate = extract_rate;
		this.frame_datasize = frame_datasize;	
		this.total_frames = (int) (video_length * extract_rate);
		this.data_constraint = data_constraint;

        optimal_predict = optimalPredictionTime();
		if (data_constraint <= 0)
			max_offload = total_frames;
		else
			max_offload = (int) (data_constraint/frame_datasize);
        backoff = optimal_predict/max_offload; /*in second*/
	}


	
	public double optimalPredictionTime(){
		int n_frame = (int) (video_length * extract_rate);
		double min_time = -1;
		Vector<Integer> vecFrames = new Vector<Integer>();
		
		for(int n_batch = 1; n_batch <= n_frame; n_batch++){	
			vecFrames.clear();
			vecFrames.add(0, n_frame);
			while(vecFrames.size() != n_batch){
				int split_first = (int)Math.ceil((vecFrames.get(0)*extract_time - predict_alpha)
						/(extract_time + predict_beta));
				if(split_first != 0){
						vecFrames.set(0, vecFrames.get(0) - split_first);
						vecFrames.add(0, split_first);
				}else{
					break;
				}
			}
			
			double complete_predict = 0;
	        int done_frames = 0;
	        for(int i = 0; i < vecFrames.size(); i++){
	        	done_frames += vecFrames.get(i);
	        	if(complete_predict <= done_frames * extract_time)
	        		complete_predict = done_frames * extract_time + predict_alpha + predict_beta * vecFrames.get(i);
	        	else
	        		complete_predict += predict_alpha + predict_beta * vecFrames.get(i);
	        }
	        if(complete_predict < min_time || min_time < 0){
	        	min_time = complete_predict;
//	        	vecBatch.clear();
//	        	vecBatch.addAll(vecFrames);
	        }else{
	        	break;
	        }
		}
		return min_time;
	}

	public double getOptimalPredictionTime() {
		return optimal_predict;
	}
	
	//cur_frame is available frames in the queue, remain_frame is the remaining 
	//frames to processed including the frames that have not be extracted yet.
	public int frameToPredict(int cur_frame, int remain_frame){
        double timeout = backoff * Math.pow(2, n_failures++);
		
		int max_frame = (int)Math.floor((timeout - predict_alpha)/predict_beta);
		if(max_frame <= cur_frame)
			return max_frame;
		
		int more_frame = (int)Math.floor((timeout - predict_alpha - predict_beta 
							* cur_frame)/(extract_time + predict_beta));
		
		if(more_frame + cur_frame <= remain_frame)
			return more_frame + cur_frame;
		else
			return remain_frame;
	}

	// time ms, energy mJ
	public boolean isSuccess(Pair<Double,Double> pair) {
		return pair.first < time_local && pair.second < energy_local;
	}

    public double getBackoff() {
        return backoff;
    }

    public void resetBackoff() {
        n_failures = 0;
    }

    private int getNAndIncrement() {
        return n_failures++;
    }

	public boolean isBelowDataLimit(int count) {
		return (data_constraint <= 0 || count*frame_datasize <= data_constraint);
	}

    // Return pair (time, energy)
    public Pair<Double, Double> frameSendTest(Mat mat, VANet vanet) {
        Pair<Double, Double> pair = new Pair<Double, Double>(0.,0.);

        return pair;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private long getBatteryLevel() {
        Context context = VATest.getContext();
        BatteryManager batteryManager =
                (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);

        return batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER) * 3600;
    }
	
}
