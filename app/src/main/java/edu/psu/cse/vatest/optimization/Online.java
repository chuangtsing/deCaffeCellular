package edu.psu.cse.vatest.optimization;


//How to implement cellular Online solution

public class Online {
	
	
	public static void main(String[] args) {
		
		
		double video_length = 60;       /*second*/
		double extract_rate = 2;        /*fps*/
		double frame_datasize = 200;
		double data_constraint = 2000;  /*KB*/

		int total_frames = (int) (video_length * extract_rate);
		
		CellularOnline cellular = new CellularOnline(video_length, extract_rate, frame_datasize, data_constraint);
		double optimal_predict = cellular.optimalPredictionTime();
		int max_offload = (int)(data_constraint/frame_datasize);

		double backoff = (long)(optimal_predict/max_offload); /*in second*/
		
		
		/*Procedure*/
		//1. A thread extracts frames from a video into a queue;
		//2. Initially or after timeout of the timer, offload a frame to cloud and record the energy consumption;
		//3. If the consumed energy is less than predicting a frame locally, offload another frame; 
		//   otherwise, set a timer to "backoff*2^n", where n is the consecutive failed offloading attempts 
		//   that incurs more energy than local frame prediction, and then perform a batch of frame prediction locally 
		//   and its completion time is within timer's timeout (use function frameToPredict to determine the number of frames to process);
		//4. The processing continues until all the frames are processed.
	}
}
