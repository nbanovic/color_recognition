package net.nikolabanovic.colordetector;

import org.opencv.android.JavaCameraView;

import android.content.Context;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.util.AttributeSet;
import android.util.Log;

/**
 * TODO: document your custom view class.
 */
public class CameraView extends JavaCameraView {

    private static final String TAG = "ColorDetector::CameraView";
    
    public CameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void takePicture(PictureCallback callback) {
        Log.i(TAG, "Taking picture");

        // Postview and jpeg are sent in the same buffers if the queue is not empty when performing a capture.
        // Clear up buffers to avoid mCamera.takePicture to be stuck because of a memory issue
        mCamera.setPreviewCallback(null);

        // PictureCallback is implemented by the current class
        mCamera.takePicture(null, null, callback);
    }
    
    public void turnFlashlightOn() {
    	Parameters p = mCamera.getParameters();
    	p.setFlashMode(Parameters.FLASH_MODE_TORCH);
    	mCamera.setParameters(p);
    }
    
    public void turnFlashlightOff() {
    	Parameters p = mCamera.getParameters();
    	p.setFlashMode(Parameters.FLASH_MODE_OFF);
    	mCamera.setParameters(p);
    }
}