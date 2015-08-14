package me.shengbin.corerecorder;

import java.util.Timer;
import java.util.TimerTask;

public class QualityController {
	private CoreRecorder mRecorder;
	
	public QualityController(CoreRecorder recorder) {
		mRecorder = recorder;
	}
	
	public void start() {
		Timer timer = new Timer();
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				int bandwidth = predictBandwidth();
				bandwidthChanged(bandwidth);
			}
		};
		// Execute the task every 10 seconds.
		timer.scheduleAtFixedRate(task, 0, 10000);
	}
	
	public void bandwidthChanged(int bandwidth) {
		try {
			mRecorder.updateBitrate(bandwidth);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private int predictBandwidth() {
		//TODO: Predict the current bandwidth
		return (int) (200 * (System.currentTimeMillis() % 5));
	}
}
