package net.nikolabanovic.colordetector;

import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.Toast;
import android.speech.tts.TextToSpeech;

public class MainActivity extends Activity implements CvCameraViewListener2, OnTouchListener, PictureCallback,
		TextToSpeech.OnInitListener {
	private static final String TAG = "ColorDetector::MainActivity";

	private static final double RESIZE_SCALE = 0.4;
	
	private static final int COLOR_SAMPLE_MAT_SIZE = 10;

	private Map<String, Mat> COLORS = new HashMap<String, Mat>();

	private TextToSpeech tts;

	private CameraView openCvCameraView;

	private BaseLoaderCallback loaderCallback = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			switch (status) {
			case LoaderCallbackInterface.SUCCESS: {
				Log.i(TAG, "OpenCV loaded successfully");
				openCvCameraView.enableView();
				openCvCameraView.setOnTouchListener(MainActivity.this);

				COLORS.put("WHITE", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(255, 255, 255)));
				COLORS.put("GRAY", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(127, 127, 127)));
				COLORS.put("BLACK", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(0, 0, 0)));
				COLORS.put("RED", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(255, 0, 0)));
				COLORS.put("MAROON", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(127, 0, 0)));
				COLORS.put("YELLOW", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(255, 255, 0)));
				COLORS.put("GREEN", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(0, 127, 0)));
				COLORS.put("BLUE", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(0, 0, 255)));
				COLORS.put("NAVY", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(0, 0, 127)));
				COLORS.put("PURPLE", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(127, 0, 127)));
				COLORS.put("ORANGE", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(255, 166, 0)));
				COLORS.put("BROWN", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(139, 69, 19)));

				for (Entry<String, Mat> color : COLORS.entrySet()) {
					Imgproc.cvtColor(color.getValue(), color.getValue(), Imgproc.COLOR_RGB2HSV_FULL);
				}
			}
				break;
			default: {
				super.onManagerConnected(status);
			}
				break;
			}
		}
	};

	public MainActivity() {
		Log.i(TAG, "Instantiated new " + this.getClass());
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "called onCreate");
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		setContentView(R.layout.activity_main);

		openCvCameraView = (CameraView) findViewById(R.id.camera_view);

		openCvCameraView.setVisibility(SurfaceView.VISIBLE);

		openCvCameraView.setCvCameraViewListener(this);
		
		tts = new TextToSpeech(this, this);
	}

	@Override
	public void onPause() {
		super.onPause();
		if (openCvCameraView != null)
			openCvCameraView.disableView();
	}

	@Override
	public void onResume() {
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_6, this, loaderCallback);
	}

	public void onDestroy() {
		super.onDestroy();
		if (openCvCameraView != null)
			openCvCameraView.disableView();
	}

	public void onCameraViewStarted(int width, int height) {
	}

	public void onCameraViewStopped() {
	}

	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		Mat mat = inputFrame.rgba();

		Mat gray = new Mat();
		Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY);

		Mat mask = new Mat(new Size(gray.size().width + 2, gray.size().height + 2), CvType.CV_8U, new Scalar(0));

		Imgproc.GaussianBlur(gray, gray, new Size(9, 9), -1);

		Rect roi = new Rect();
		Imgproc.floodFill(gray, mask, new Point(gray.cols() / 2, gray.rows() / 2), new Scalar(255), roi, new Scalar(0),
				new Scalar(50), 4 + Imgproc.FLOODFILL_FIXED_RANGE);

		// Imgproc.threshold(gray, gray, 254, 255, Imgproc.THRESH_BINARY);

		// Imgproc.erode(mask, mask, new Mat());

		// Imgproc.threshold(mat, mat, 0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);

		Core.rectangle(mat, roi.tl(), roi.br(), new Scalar(255, 0, 0), 3);

		gray.release();
		mask.release();

		return mat;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return false;
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		return true;
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		Log.i(TAG, "onTouch event");

		openCvCameraView.takePicture(this);

		return false;
	}

	@SuppressLint("SimpleDateFormat")
	@Override
	public void onPictureTaken(byte[] data, Camera camera) {
		Log.i(TAG, "Saving a bitmap to file");
		// The camera preview was automatically stopped. Start it again.
		camera.startPreview();
		camera.setPreviewCallback((CameraView) findViewById(R.id.camera_view));

		// Get bitmap from the camera data.
		Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length);

		// Convert the image to openCV matrix for processing.
		// OpenCV mat containing the image.
		Mat imageMat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8U, new Scalar(4));
		Bitmap myBitmap32 = image.copy(Bitmap.Config.ARGB_8888, true);
		Utils.bitmapToMat(myBitmap32, imageMat);

		// Resize the mat to something manageable. Change to 3 channels for grabCut to work.
		Mat imgC3 = new Mat();
		Imgproc.cvtColor(imageMat, imgC3, Imgproc.COLOR_RGBA2RGB);
		Imgproc.resize(imgC3, imgC3, new Size(), RESIZE_SCALE, RESIZE_SCALE, Imgproc.INTER_AREA);

		// Perform segmentation of foreground using grabcut.

		// Find region that contains the primary color.
		Mat gray = new Mat();
		Imgproc.cvtColor(imgC3, gray, Imgproc.COLOR_RGB2GRAY);
		Imgproc.GaussianBlur(gray, gray, new Size(5, 5), -1);

		Mat mask = new Mat(new Size(gray.size().width + 2, gray.size().height + 2), CvType.CV_8U, new Scalar(0));

		Rect roi = new Rect();
		Imgproc.floodFill(gray, mask, new Point(gray.cols() / 2, gray.rows() / 2), new Scalar(255), roi, new Scalar(0),
				new Scalar(50), 4 + Imgproc.FLOODFILL_FIXED_RANGE);

		Mat firstMask = new Mat();
		Mat bgModel = new Mat();
		Mat fgModel = new Mat();
		Mat source = new Mat(1, 1, CvType.CV_8U, new Scalar(Imgproc.GC_FGD));
		Mat copy = new Mat(imageMat.size(), imageMat.type(), new Scalar(0, 0, 0, 0));

		roi = new Rect(new Point(roi.tl().x + 2, roi.tl().y + 2), new Point(roi.br().x - 2, roi.br().y - 2));

		Imgproc.grabCut(imgC3, firstMask, roi, bgModel, fgModel, 1, Imgproc.GC_INIT_WITH_RECT);

		Core.convertScaleAbs(firstMask, firstMask, 100, 0);
		Imgproc.threshold(firstMask, firstMask, 254, 255, Imgproc.THRESH_BINARY);
		
		Imgproc.erode(firstMask, firstMask, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2,2)));        
		//Imgproc.dilate(firstMask, firstMask, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2)));

		Imgproc.resize(firstMask, firstMask, imageMat.size());

		imageMat.copyTo(copy, firstMask);
		
		Mat colorMat = new Mat();
		Imgproc.cvtColor(copy, colorMat, Imgproc.COLOR_RGB2HSV_FULL);
		Rect sample = new Rect(new Point(colorMat.cols() / 2 - COLOR_SAMPLE_MAT_SIZE/2, colorMat.rows() / 2 - COLOR_SAMPLE_MAT_SIZE/2), new Point(colorMat.cols() / 2 + COLOR_SAMPLE_MAT_SIZE/2,
				colorMat.rows() / 2 + COLOR_SAMPLE_MAT_SIZE/2));

		double minDist = Double.MAX_VALUE;
		String colorName = "";
		for (Entry<String, Mat> preDefColor : COLORS.entrySet()) {
			double distance = distance(preDefColor.getValue(), new Mat(colorMat, sample));
			if (distance < minDist) {
				minDist = distance;
				colorName = preDefColor.getKey();
			}
		}
		
		tts.speak(colorName.toLowerCase(), TextToSpeech.QUEUE_FLUSH, null);

		Utils.matToBitmap(copy, myBitmap32);

		// release MAT part
		imageMat.release();
		// mask.release();
		firstMask.release();
		source.release();
		bgModel.release();
		fgModel.release();

		// Write the image in a file (in jpeg format)
		try {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
			String currentDateandTime = sdf.format(new Date());
			String fileName = Environment.getExternalStorageDirectory().getPath() + "/Pictures/ColorDetector/picture_"
					+ currentDateandTime + ".jpg";

			FileOutputStream out = new FileOutputStream(fileName);

			myBitmap32.compress(Bitmap.CompressFormat.JPEG, 90, out);

			out.close();

			Toast.makeText(this, fileName + " saved", Toast.LENGTH_SHORT).show();

		} catch (java.io.IOException e) {
			Log.e("PictureDemo", "Exception in photoCallback", e);
		}

	}
	
	private double distance(Mat mat1, Mat mat2) {
		double distance = 0;

		for (int row = 0; row < mat1.rows(); row++) {
			for (int col = 0; col < mat1.cols(); col++) {
				distance += distance(mat1.get(row, col), mat2.get(row, col));
			}
		}

		return distance / (mat1.cols() * mat1.rows());
	}
	
	private double distance(double[] color1, double[] color2) {
		double distance = 0;
		
		for (int i = 0; i < color1.length; i++) {
			if(color2[i] > 0) {
				distance += Math.pow(color1[i] - color2[i], 2);
			}
		}

		return Math.sqrt(distance);
	}

	@Override
	public void onInit(int status) {
		if (status == TextToSpeech.SUCCESS) {

			int result = tts.setLanguage(Locale.US);

			if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
				Log.e("TTS", "This Language is not supported");
			}

		} else {
			Log.e("TTS", "Initilization Failed!");
		}

	}
}
