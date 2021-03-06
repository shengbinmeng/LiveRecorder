package me.shengbin.corerecorder;

import java.util.Timer;
import java.util.TimerTask;

import android.util.Log;

public class QualityController {
	private static final String TAG = "QualityController";
	private CoreRecorder mRecorder;
	private TimerTask mTimerTask = null;
	
	public QualityController(CoreRecorder recorder) {
		mRecorder = recorder;
	}
	
	public void start() {
		Timer timer = new Timer();
		mTimerTask = new TimerTask() {
			@Override
			public void run() {
				int bandwidth = predictBandwidth();
				Log.i(TAG, "Bandwidth changed to " + bandwidth);
				//bandwidthChanged(bandwidth);
			}
		};
		// Execute the task every 10 seconds.
		timer.scheduleAtFixedRate(mTimerTask, 0, 10000);
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
		return (int)((Math.random()*10)+1) * 100000;
	}
	
	public void stop() {
		if (mTimerTask != null) {
			mTimerTask.cancel();
		}
	}
}
