package edu.psu.cse.vatest.optimization;

import java.util.Vector;
import edu.psu.cse.vatest.optimization.WiFiOffline;

public class WiFiEnergy implements AbstractOffline {

	private double p_predict = 2.191;	 // unit in W
	private double p_offload_base = 1.000;		 // unit in W
	private double p_offload_step = 0.0002;        // unit in W
	private double p_exrtract = 1.178;
	private double p_offload;

	private double e_video_offload;
	private double e_frame_offload;
	private double e_frame_prediction;
	private double e_frame_extraction;
	private double e_local_process;
	private double e_minimum;
	private double e_maximum;
	private double e_constraint;

	private double video_datasize;
	private double video_length;
	private double extract_rate;
	private double frame_datasize;
	private double trans_rate;

	private double t_video_offload;
	private double t_local_process;

	private int total_frames;

	//frame offloading time;
	private double offload_time;
	private double arrival_time;
	//extraction time;
	private double extract_time = 0.018;   // in seconds
	//prediction time = 240 + 400 * x;
	private double predict_alpha = 0.240; // in seconds
	private double predict_beta = 0.400;  // in seconds

	private WiFiOffline offline;
	private Vector<Integer> predict_batch;

	public WiFiEnergy(double video_datasize, /*KB*/
			   double video_length /*second*/,
			   double extract_rate /*fps*/,
			   double frame_datasize /*KB*/,
			   double trans_rate /*KBps*/){

		this.video_datasize = video_datasize;
		this.video_length = video_length;
		this.extract_rate = extract_rate;
		this.frame_datasize = frame_datasize;
		this.trans_rate = trans_rate;
		this.offload_time = frame_datasize/trans_rate;
		this.arrival_time = (offload_time * extract_time) /(offload_time - extract_time);

		this.total_frames = (int) (video_length * extract_rate);
		this.p_offload = p_offload_base + p_offload_step * trans_rate;

		offline = new WiFiOffline(video_datasize, video_length, extract_rate, frame_datasize, trans_rate);
		predict_batch = new Vector<Integer>();

		t_local_process = offline.localProcessing(predict_batch);
		t_video_offload = video_datasize/trans_rate;

		int predict_frames = 0;
		int offload_frames = 0;
		double e_local_prediction = 0;
		for(int i = 0; i<predict_batch.size(); i++){
			predict_frames += predict_batch.get(i);
			e_local_prediction += (predict_batch.get(i) * predict_beta + predict_alpha) * p_predict;
		}
		offload_frames = total_frames - predict_frames;

		e_frame_extraction = p_exrtract * extract_time * (int)(video_length * extract_rate);

		e_local_process = p_offload * (offload_frames * frame_datasize / trans_rate)
				+ e_local_prediction + e_frame_extraction;

		e_video_offload = (video_datasize/trans_rate)*p_offload;
		e_frame_offload = (((int)(video_length * extract_rate) * frame_datasize) / trans_rate)
				* p_offload;
		e_frame_prediction = p_predict * (predict_alpha + predict_beta * ((int)(video_length * extract_rate)));


		//calculate minimum energy
		if(e_video_offload <= e_frame_offload + e_frame_extraction
				&& e_video_offload <= e_frame_prediction + e_frame_extraction){
			e_minimum = e_video_offload;
			System.out.println("min energy is video_offload");
		}else{
			if(e_frame_offload + e_frame_extraction <= e_frame_prediction + e_frame_extraction){
				e_minimum = e_frame_offload + e_frame_extraction;
				System.out.println("min energy frame_offload");
			}
			else{
				e_minimum = e_frame_prediction + e_frame_extraction;
				System.out.println("min energy is frame_prediction");
			}
		}
		//calculate maximum energy
		if(e_video_offload >= e_local_process && t_video_offload >= t_local_process){
			e_maximum = e_local_process;
			System.out.println("max energy is local_process");
		}
		else{
			if(e_video_offload > e_local_process){
				e_maximum = e_video_offload;
				System.out.println("max energy is video_offload");
			}else{
				e_maximum = e_local_process;
				System.out.println("min energy is local_process");
			}
		}
	}


	public double getMinEnergy(){
		return e_minimum;
	}

	public double getMaxEnergy(){
		return e_maximum;
	}

	public void setEnergyConstraint(int e_constraint){
        if (e_constraint > 100)
            e_constraint = 100;
        else if (e_constraint < 0)
            e_constraint = 0;

        this.e_constraint = e_minimum + ((e_maximum - e_minimum) * e_constraint) / 100;
	}


	public Solution algorithm(){
		Solution optimum = new Solution();
		Vector<Integer> batches = new Vector<Integer>();

		if(e_constraint == e_minimum){
			if(e_minimum == e_video_offload){
				optimum.mode = 0;
				optimum.time = t_video_offload;
				optimum.batches = null;
				optimum.frames_offload = 0;
			}
			if(e_minimum == e_frame_offload + e_frame_extraction){
				optimum.mode = 1;
				optimum.time = offload_time > extract_time ? offload_time * total_frames:
						extract_time * total_frames;
				optimum.batches = null;
				optimum.frames_offload = total_frames;
			}
			if(e_minimum == e_frame_prediction + e_frame_extraction){
				optimum.mode = 1;
				optimum.time = predict_alpha + predict_beta * total_frames +
						extract_time * total_frames;
				batches.add(total_frames);
				optimum.batches = batches;
				optimum.frames_offload = 0;
			}
			optimum.energy = e_minimum;
			return optimum;
		}

		if(t_local_process >= t_video_offload && e_minimum == e_video_offload){
			optimum.mode = 0;
			optimum.energy = e_video_offload;
			optimum.time = t_video_offload;
			optimum.batches = null;
			optimum.frames_offload = 0;
		}
		if(t_local_process < t_video_offload && e_minimum == e_video_offload){
			if(e_frame_offload + e_frame_extraction < e_frame_prediction + e_frame_extraction){
				double local_time = offloadWithConstraint(e_constraint, batches);
				int n_frame_predict = 0;
				for(int i=0; i<batches.size(); i++){
					n_frame_predict += batches.get(i);
				}
				if( local_time >= t_video_offload){
					optimum.mode = 0;
					optimum.energy = e_minimum;
					optimum.time = t_video_offload;
					optimum.batches = null;
					optimum.frames_offload = 0;
				}else{
					optimum.mode = 1;
					optimum.energy = e_constraint;
					optimum.time = local_time;
					optimum.batches = batches;
					optimum.frames_offload = (int)(video_length * extract_rate) - n_frame_predict;

				}
			}else{
				double local_time = predictWithConstraint(e_constraint, batches);
				int n_frame_predict = 0;
				for(int i=0; i<batches.size(); i++){
					n_frame_predict += batches.get(i);
				}
				if( local_time >= t_video_offload && e_constraint >= e_video_offload){
					optimum.mode = 0;
					optimum.energy = e_video_offload;
					optimum.time = t_video_offload;
					optimum.batches = null;
					optimum.frames_offload = 0;
				}else{
					optimum.mode = 1;
					optimum.energy = e_constraint;
					optimum.time = local_time;
					optimum.batches = batches;
					optimum.frames_offload = (int)(video_length * extract_rate) - n_frame_predict;
				}
			}
		}
		if(e_minimum == e_frame_offload + e_frame_extraction){
			double local_time = offloadWithConstraint(e_constraint, batches);
			int n_frame_predict = 0;
			for(int i=0; i<batches.size(); i++){
				n_frame_predict += batches.get(i);
			}
			if( local_time >= t_video_offload && e_constraint >= e_video_offload){
				optimum.mode = 0;
				optimum.energy = e_video_offload;
				optimum.time = t_video_offload;
				optimum.batches = null;
				optimum.frames_offload = 0;
			}else if(e_constraint >= e_local_process && local_time <= t_video_offload){
				optimum.mode = 1;
				optimum.energy = e_local_process;
				optimum.time = local_time;
				optimum.batches = batches;
				optimum.frames_offload = (int)(video_length * extract_rate) - n_frame_predict;
			}else{
				optimum.mode = 1;
				optimum.energy = e_constraint;
				optimum.time = local_time;
				optimum.batches = batches;
				optimum.frames_offload = (int)(video_length * extract_rate) - n_frame_predict;
			}
		}
		if(e_minimum == e_frame_prediction + e_frame_extraction){
			double local_time = predictWithConstraint(e_constraint, batches);
			int n_frame_predict = 0;
			for(int i=0; i<batches.size(); i++){
				n_frame_predict += batches.get(i);
			}
			if( local_time >= t_video_offload && e_constraint >= e_video_offload){
				optimum.mode = 0;
				optimum.energy = e_video_offload;
				optimum.time = t_video_offload;
				optimum.batches = null;
				optimum.frames_offload = 0;
			}else if(e_constraint >= e_local_process && local_time <= t_video_offload){
				optimum.mode = 1;
				optimum.energy = e_local_process;
				optimum.time = local_time;
				optimum.batches = batches;
				optimum.frames_offload = (int)(video_length * extract_rate) - n_frame_predict;
			}else{
				optimum.mode = 1;
				optimum.energy = e_constraint;
				optimum.time = local_time;
				optimum.batches = batches;
				optimum.frames_offload = (int)(video_length * extract_rate) - n_frame_predict;
			}
		}

		return optimum;
	}


	private double offloadWithConstraint(double e_constraint, Vector<Integer> vecFrames){

		if(e_constraint >= e_local_process){
			vecFrames.addAll(predict_batch);
			return t_local_process;
		}

		arrival_time = (offload_time * extract_time) /(offload_time - extract_time);

		int n_batch = 1;
		int n_predict;
		Vector<Integer> batches = new Vector<Integer>();
		Vector<Integer> vecMark = new Vector<Integer>();
		double time_min;
		double mark = -1;
		vecFrames.clear();

		while(true){
			n_predict = (int)Math.floor((e_constraint - e_frame_extraction - e_frame_offload
					- n_batch * predict_alpha * p_predict)/(predict_beta
					* p_predict - frame_datasize * p_offload_base / trans_rate
					- frame_datasize * p_offload_step));

			if(n_predict <= 0){
				vecFrames.clear();
				return (total_frames)*frame_datasize/trans_rate;
			}

			time_min = calculateLocalTime(n_predict, n_batch, batches);

			if(time_min > (total_frames - n_predict)*frame_datasize/trans_rate){
				if(batches.size() == 1 && batches.get(0) * arrival_time <= predict_alpha){
					n_predict = (int)Math.floor((total_frames * frame_datasize - predict_alpha * trans_rate)
							/(frame_datasize + trans_rate * predict_beta + trans_rate * arrival_time));
					vecFrames.add(0, n_predict);
					return (total_frames - n_predict) * frame_datasize / trans_rate;
				}
				if(canReduceProcessTime(batches)){
					if(mark < 0 || mark > time_min){
						n_batch ++;
						mark = time_min;
						vecMark.clear();
						vecMark.addAll(batches);
					}else{
						vecFrames.addAll(vecMark);
						return mark;
					}
				}else{
					if(time_min < mark || mark < 0){
						vecFrames.addAll(batches);
						return time_min;
					}else{
						vecFrames.addAll(vecMark);
						return mark;
					}
				}
			}else{
				vecFrames.addAll(batches);
				return (total_frames - n_predict)*frame_datasize/trans_rate;
			}
		}
	}


	private double predictWithConstraint(double e_constraint, Vector<Integer> vecFrames){

		if(e_constraint >= e_local_process){
			vecFrames.addAll(predict_batch);
			return t_local_process;
		}

		double e_remain = e_constraint - e_frame_extraction - e_frame_prediction;

		int n_predict = total_frames;
		int n_offload = 0;
		int batch = 1;
		double t_min = -1;
		double estimate_time = 99999999;

		while(e_remain > 0 && n_predict > 0){
			arrival_time = (estimate_time * extract_time) /(estimate_time - extract_time);

			t_min = calculateLocalTime(n_predict, batch, vecFrames);
			Vector<Integer> vecMark = new Vector<Integer>();
			vecMark = vecFrames;

			double eff_predict = (t_min - calculateLocalTime(n_predict, batch+1, vecMark))
					/ (predict_alpha * p_predict);
			double eff_offload = predict_beta/(p_offload * frame_datasize / trans_rate - predict_beta * p_predict);

			if(eff_predict <= eff_offload){
				e_remain -= p_offload * frame_datasize / trans_rate - predict_beta * p_predict;
				n_predict --;
				n_offload ++;

				if(offload_time >= t_min / n_offload){
					break;
				}
				else {
					estimate_time = t_min / n_offload;
					e_remain += predict_alpha * p_predict * (batch -1);
					batch = 1;
				}

			}else{
				e_remain -= predict_alpha * p_predict;
				batch ++;
				vecFrames = vecMark;
			}
		}
		return t_min;
	}


	private boolean canReduceProcessTime(Vector<Integer> vecFrames){
		if(vecFrames.size() == 0)
			return false;

		if(vecFrames.get(0) * arrival_time <= predict_alpha)
			return false;

		int split_first = (int)Math.ceil((vecFrames.get(0)*arrival_time - predict_alpha)
				/(arrival_time + predict_beta));
		if(split_first != 0 && vecFrames.get(0) - split_first != 0)
			return true;

		return false;
	}

	private double calculateLocalTime(int n_frame, int n_batch, Vector<Integer> vecFrames){
		vecFrames.clear();
		vecFrames.add(0, n_frame);
		while(vecFrames.size() != n_batch){
			int split_first = (int)Math.ceil((vecFrames.get(0)*arrival_time - predict_alpha)
					/(arrival_time + predict_beta));
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
			if(complete_predict <= done_frames * arrival_time)
				complete_predict = done_frames * arrival_time + predict_alpha + predict_beta * vecFrames.get(i);
			else
				complete_predict += predict_alpha + predict_beta * vecFrames.get(i);
		}
		return complete_predict;
	}


}
