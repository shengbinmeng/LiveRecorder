package me.shengbin.liverecorder;

import java.util.List;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.app.AlertDialog;
import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout.LayoutParams;

/**
 * This class is a SurfaceView which handles the camera instance and its preview image.
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

	private final static String TAG = "CameraPreview";
	private SurfaceHolder mHolder;
    private Camera mCamera;
    private Context mContext;

    public CameraPreview(Context context) {
        super(context);
    }
    
    public CameraPreview(Context context, Camera camera) {
        super(context);
        mContext = context;
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        
        setCamera(camera);
    }
    
    public void setCamera(Camera c) {
    	mCamera = c;
    	
    	Camera.Parameters p = mCamera.getParameters();
    	p.setPreviewFormat(ImageFormat.YV12);
    	mCamera.setParameters(p);
    }
    
    /**
     * Prefer preview size, which is the resolution of the image provided by the camera.
     */
    public Camera.Size preferPreviewSize (int width, int height) {
    	Camera.Parameters p = mCamera.getParameters();
    	List<Camera.Size> l = p.getSupportedPreviewSizes();
    	Camera.Size size = l.get(0);
    	long minDiff = Long.MAX_VALUE;
    	for (int i = 0; i < l.size(); i++) {
        	Camera.Size s = l.get(i);
        	Log.d("CameraPreview", "candidate size: " + s.width + "x" +s.height);
        	long diff = Math.abs(s.width - width) + Math.abs(s.height - height);
        	if (diff < minDiff) {
            	minDiff = diff;
            	size = s;
        	}
    	}
    	p.setPreviewSize(size.width, size.height);
    	mCamera.setParameters(p);
    	Log.i("CameraPreview", "preview size: " + size.width + "x" + size.height);
    	return size;
    }
    
    public int[] preferPreviewFps (int fps) {
    	Camera.Parameters p = mCamera.getParameters();
    	List<int[]> l = p.getSupportedPreviewFpsRange();
    	int[] r = l.get(0);
    	for (int i = 0; i < l.size(); i++) {
    		r = l.get(i);
    		//TODO: Choose a range according to preferred FPS.
    	}
    	int min = r[0];
    	int max = r[1];
    	p.setPreviewFpsRange(max, max);
    	Log.i("CameraPreview", "preview FPS range: " + min + "," + max);
    	return r;
    }
   
    
    /**
     * Set display size, which specified how large the preview image will be displayed on the screen.
     * The camera preview image will be scaled from preview size to display size.
     */
    public void setDisplaySize(int displayWidth, int displayHeight) {
    	LayoutParams params = (LayoutParams) this.getLayoutParams(); 
    	params.gravity = Gravity.CENTER;
        params.width = displayWidth;
        params.height = displayHeight;
        this.setLayoutParams(params);
    }

    public void surfaceCreated(SurfaceHolder holder) {
    	if (mCamera == null) {
    		AlertDialog dialog = new AlertDialog.Builder(this.mContext).setMessage("Camera is null!").setTitle("Sorry")
   			.setCancelable(false)
   			.setPositiveButton(android.R.string.ok, null)
   			.create();
       		dialog.show();
    	}
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // Empty. Take care of releasing the Camera preview in your activity.
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null) {
        	// Preview surface does not exist.
        	return;
        }

        if (mCamera == null) {
       		return;
    	}
        // Stop preview before making changes.
        try {
            mCamera.stopPreview();
        } catch (Exception e){
        	// Tried to stop a non-existent preview.
        	e.printStackTrace();
        }

        // Set preview size and make any resize, rotate or reformatting changes here.
        

        // Start preview with new settings.
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();
        } catch (Exception e) {
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

}
