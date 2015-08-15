package me.shengbin.corerecorder;

import java.util.Timer;
import java.util.TimerTask;

public class QualityController {
	private CoreRecorder mRecorder;
	private int count = 0;
	
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
		//plus 1 for in case zero.
	//	return (int) (20000 * ((System.currentTimeMillis() % 100)+1));
		return ((count++)%2)*400000 + 100000;
	}
}
