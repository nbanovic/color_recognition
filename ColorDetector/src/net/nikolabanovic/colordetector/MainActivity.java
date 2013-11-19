package net.nikolabanovic.colordetector;

import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
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
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.Toast;

public class MainActivity extends Activity implements CvCameraViewListener2, OnTouchListener, PictureCallback,
		TextToSpeech.OnInitListener {
	private static final String TAG = "ColorDetector::MainActivity";

	private static final double RESIZE_SCALE = 0.4;

	private static final int COLOR_SAMPLE_MAT_SIZE = 20;

	private Map<String, Mat> COLORS = new HashMap<String, Mat>();

	private TextToSpeech tts;

	private CameraView openCvCameraView;

	private Timer snapshotTimer = null;

	private boolean isPressed = false;

	private SoundPool soundPool = null;
	private int beepId = 0;
	
	private Mat T = null;

	private BaseLoaderCallback loaderCallback = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			switch (status) {
			case LoaderCallbackInterface.SUCCESS: {
				Log.i(TAG, "OpenCV loaded successfully");
				openCvCameraView.enableView();
				openCvCameraView.setOnTouchListener(MainActivity.this);

				COLORS.put("WHITE", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(
						255, 255, 255)));
				COLORS.put("GRAY", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(
						127, 127, 127)));
				COLORS.put("BLACK", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(0,
						0, 0)));
				COLORS.put("RED", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(255,
						0, 0)));
				COLORS.put("MAROON", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(
						127, 0, 0)));
				COLORS.put("YELLOW", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(
						255, 255, 0)));
				COLORS.put("GREEN", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(0,
						127, 0)));
				COLORS.put("BLUE", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(0,
						0, 255)));
				COLORS.put("NAVY", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(0,
						0, 127)));
				COLORS.put("PURPLE", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(
						127, 0, 127)));
				COLORS.put("ORANGE", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(
						255, 166, 0)));
				COLORS.put("BROWN", new Mat(COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE, CvType.CV_8UC4, new Scalar(
						139, 69, 19)));

				for (Entry<String, Mat> color : COLORS.entrySet()) {
					Imgproc.cvtColor(color.getValue(), color.getValue(), Imgproc.COLOR_RGB2HSV_FULL);
				}
				
				// Set ground truth for self-calibration.
				T = new Mat(3, 6, CvType.CV_64F, new Scalar(0));
				T.put(0, 0, 0); T.put(1, 0, 0); T.put(2, 0, 0); // Black
				T.put(0, 1, 255); T.put(1, 1, 255); T.put(2, 1, 255); // White
				T.put(0, 2, 127); T.put(1, 2, 127); T.put(2, 2, 127); // Gray
				T.put(0, 3, 237); T.put(1, 3, 31); T.put(2, 3, 36); // Red
				T.put(0, 4, 105); T.put(1, 4, 189); T.put(2, 4, 69); // Green
				T.put(0, 5, 56); T.put(1, 5, 82); T.put(2, 5, 164); // Blue
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

		soundPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
		beepId = soundPool.load(this, R.raw.beep, 1);
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

		if (tts != null) {
			tts.stop();
			tts.shutdown();
		}

		if (soundPool != null) {
			soundPool.release();
		}
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

		android.graphics.RectF tempRoi = new android.graphics.RectF((float)roi.tl().x, (float)roi.tl().y, (float)roi.br().x, (float)roi.br().y);
		android.graphics.RectF tempInclusion = new android.graphics.RectF(0f, 0f, 900f, 720f);
		tempRoi.intersect(tempInclusion);
		
		roi = new Rect(new Point(tempRoi.left, tempRoi.top), new Point(tempRoi.right, tempRoi.bottom));
		
		Core.rectangle(mat, roi.tl(), roi.br(), new Scalar(255, 0, 0), 3);
		
		Core.rectangle(mat, new Point(0,0), new Point(900,720), new Scalar(255,255,255), 3);
		
		Rect blueRect = new Rect(new Point(1055,30), new Point(1075,50));
		Rect greenRect = new Rect(new Point(1055,140), new Point(1075,160));
		Rect redRect = new Rect(new Point(1055,275), new Point(1075,295));
		Rect grayRect = new Rect(new Point(1055,375), new Point(1075,395));
		Rect whiteRect = new Rect(new Point(1055,485), new Point(1075,505));
		Rect blackRect = new Rect(new Point(1055,620), new Point(1075,640));
		
		Mat rgbMat = new Mat();
		Imgproc.cvtColor(mat, rgbMat, Imgproc.COLOR_RGBA2RGB);
		
		Mat blueRectMat = new Mat(rgbMat, blueRect);
		Mat greenRectMat = new Mat(rgbMat, greenRect);
		Mat redRectMat = new Mat(rgbMat, redRect);
		Mat grayRectMat = new Mat(rgbMat, grayRect);
		Mat whiteRectMat = new Mat(rgbMat, whiteRect);
		Mat blackRectMat = new Mat(rgbMat, blackRect);
		
		Scalar blueValue = Core.mean(blueRectMat);
		Scalar greenValue = Core.mean(greenRectMat);
		Scalar redValue = Core.mean(redRectMat);
		Scalar grayValue = Core.mean(grayRectMat);
		Scalar whiteValue = Core.mean(whiteRectMat);
		Scalar blackValue = Core.mean(blackRectMat);
		
		Core.rectangle(mat, new Point(1055,30), new Point(1075,50), new Scalar(255,255,255), 2);
		Core.rectangle(mat, new Point(1055,140), new Point(1075,160), new Scalar(255,255,255), 2);
		Core.rectangle(mat, new Point(1055,275), new Point(1075,295), new Scalar(255,255,255), 2);
		Core.rectangle(mat, new Point(1055,375), new Point(1075,395), new Scalar(255,255,255), 2);
		Core.rectangle(mat, new Point(1055,485), new Point(1075,505), new Scalar(255,255,255), 2);
		Core.rectangle(mat, new Point(1055,620), new Point(1075,640), new Scalar(255,255,255), 2);

		Core.putText(mat, "Black: [" + (int) blackValue.val[0] + "," + (int) blackValue.val[1] + "," + (int) blackValue.val[2] + "]", new Point(25, 350), Core.FONT_HERSHEY_COMPLEX_SMALL, 1.0, new Scalar(255, 255, 255));
		Core.putText(mat, "White: [" + (int) whiteValue.val[0] + "," + (int) whiteValue.val[1] + "," + (int) whiteValue.val[2] + "]", new Point(25, 400), Core.FONT_HERSHEY_COMPLEX_SMALL, 1.0, new Scalar(255, 255, 255));
		Core.putText(mat, "Gray: [" + (int) grayValue.val[0] + "," + (int) grayValue.val[1] + "," + (int) grayValue.val[2] + "]", new Point(25, 450), Core.FONT_HERSHEY_COMPLEX_SMALL, 1.0, new Scalar(255, 255, 255));
		Core.putText(mat, "Red: [" + (int) redValue.val[0] + "," + (int) redValue.val[1] + "," + (int) redValue.val[2] + "]", new Point(25, 500), Core.FONT_HERSHEY_COMPLEX_SMALL, 1.0, new Scalar(255, 255, 255));
		Core.putText(mat, "Green: [" + (int) greenValue.val[0] + "," + (int) greenValue.val[1] + "," + (int) greenValue.val[2] + "]", new Point(25, 550), Core.FONT_HERSHEY_COMPLEX_SMALL, 1.0, new Scalar(255, 255, 255));
		Core.putText(mat, "Blue: [" + (int) blueValue.val[0] + "," + (int) blueValue.val[1] + "," + (int) blueValue.val[2] + "]", new Point(25, 600), Core.FONT_HERSHEY_COMPLEX_SMALL, 1.0, new Scalar(255, 255, 255));
		
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
		Log.d(TAG, "onTouch event");

		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			// Schedule taking a picture.
			Log.d(TAG, "Touch down.");

			// If the timer is already initialized, stop it.
			if (snapshotTimer != null) {
				snapshotTimer.cancel();
			}

			//openCvCameraView.turnFlashlightOn();

			isPressed = true;

			snapshotTimer = new Timer();
			snapshotTimer.schedule(new TakePictureTask(this), 100);
			break;

		case MotionEvent.ACTION_MOVE:
			break;

		case MotionEvent.ACTION_UP:
			Log.d(TAG, "Touch up");

			isPressed = false;

			// Stop future camera pictures.
			if (snapshotTimer != null) {
				snapshotTimer.cancel();
				snapshotTimer.purge();
				snapshotTimer = null;
			}

			//openCvCameraView.turnFlashlightOff();

			break;
		}

		return true;
	}

	public class TakePictureTask extends TimerTask {

		private MainActivity parent = null;

		public TakePictureTask(MainActivity parent) {
			super();
			this.parent = parent;
		}

		@Override
		public void run() {
			soundPool.play(beepId, 1f, 1f, 1, 0, 1f);
			openCvCameraView.takePicture(parent);
		}

	}

	@SuppressLint({ "SimpleDateFormat", "DefaultLocale" })
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
		
		// Perform self-calibration
		// Readings from camera.
		Rect blueRect = new Rect(new Point(525,80), new Point(535,90));
		Rect greenRect = new Rect(new Point(525,140), new Point(535,150));
		Rect redRect = new Rect(new Point(525,200), new Point(535,210));
		Rect grayRect = new Rect(new Point(525,255), new Point(535,265));
		Rect whiteRect = new Rect(new Point(525,310), new Point(535,320));
		Rect blackRect = new Rect(new Point(525,400), new Point(535,410));
		
		
		Mat rgbMat = new Mat();
		Imgproc.cvtColor(imageMat, rgbMat, Imgproc.COLOR_RGBA2RGB);
		
		Mat blueRectMat = new Mat(rgbMat, blueRect);
		Mat greenRectMat = new Mat(rgbMat, greenRect);
		Mat redRectMat = new Mat(rgbMat, redRect);
		Mat grayRectMat = new Mat(rgbMat, grayRect);
		Mat whiteRectMat = new Mat(rgbMat, whiteRect);
		Mat blackRectMat = new Mat(rgbMat, blackRect);
		
		Scalar blueValue = Core.mean(blueRectMat);
		Scalar greenValue = Core.mean(greenRectMat);
		Scalar redValue = Core.mean(redRectMat);
		Scalar grayValue = Core.mean(grayRectMat);
		Scalar whiteValue = Core.mean(whiteRectMat);
		Scalar blackValue = Core.mean(blackRectMat);
		
		Core.rectangle(imageMat, blueRect.tl(), blueRect.br(), new Scalar(255,255,255), 1);
		Core.rectangle(imageMat, greenRect.tl(), greenRect.br(), new Scalar(255,255,255), 1);
		Core.rectangle(imageMat, redRect.tl(), redRect.br(), new Scalar(255,255,255), 1);
		Core.rectangle(imageMat, grayRect.tl(), grayRect.br(), new Scalar(255,255,255), 1);
		Core.rectangle(imageMat, whiteRect.tl(), whiteRect.br(), new Scalar(255,255,255), 1);
		Core.rectangle(imageMat, blackRect.tl(), blackRect.br(), new Scalar(255,255,255), 1);
		
		Mat M = new Mat(4, 6, CvType.CV_64F, new Scalar(0));
		M.put(0, 0, blackValue.val[0]); M.put(1, 0, blackValue.val[1]); M.put(2, 0, blackValue.val[2]); M.put(3, 0, 1.0); // Black
		M.put(0, 1, whiteValue.val[0]); M.put(1, 1, whiteValue.val[1]); M.put(2, 1, whiteValue.val[2]); M.put(3, 1, 1.0); // White
		M.put(0, 2, grayValue.val[0]); M.put(1, 2, grayValue.val[1]); M.put(2, 2, grayValue.val[2]); M.put(3, 2, 1.0); // Gray
		M.put(0, 3, redValue.val[0]); M.put(1, 3, redValue.val[1]); M.put(2, 3, redValue.val[2]); M.put(3, 3, 1.0); // Red
		M.put(0, 4, greenValue.val[0]); M.put(1, 4, greenValue.val[1]); M.put(2, 4, greenValue.val[2]); M.put(3, 4, 1.0); // Green
		M.put(0, 5, blueValue.val[0]); M.put(1, 5, blueValue.val[1]); M.put(2, 5, blueValue.val[2]); M.put(3, 5, 1.0); // Blue
		
		// Transformation matrix.
		Mat A = new Mat();
		
		// Compute Moor-Penrose pseudoinverse matrix.
		Mat Mt = M.t();
		Mat Minv = new Mat();
		Core.gemm(M, Mt, 1, new Mat(), 0, Minv);
		Core.invert(Minv, Minv);
		Core.gemm(Mt, Minv, 1, new Mat(), 0, Minv); 
		
		// Calculate A.
		Core.gemm(T, Minv, 1, new Mat(), 0, A);

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
		
		android.graphics.RectF tempRoi = new android.graphics.RectF((float)roi.tl().x, (float)roi.tl().y, (float)roi.br().x, (float)roi.br().y);
		android.graphics.RectF tempInclusion = new android.graphics.RectF(0f, 0f, 450f * 0.4f, 480f * 0.4f);
		tempRoi.intersect(tempInclusion);
		
		roi = new Rect(new Point(tempRoi.left, tempRoi.top), new Point(tempRoi.right, tempRoi.bottom));

		Mat firstMask = new Mat();
		Mat bgModel = new Mat();
		Mat fgModel = new Mat();
		Mat source = new Mat(1, 1, CvType.CV_8U, new Scalar(Imgproc.GC_FGD));
		Mat copy = new Mat(imageMat.size(), imageMat.type(), new Scalar(0, 0, 0, 0));

		if (roi.width < COLOR_SAMPLE_MAT_SIZE && roi.height < COLOR_SAMPLE_MAT_SIZE) {
			// There is nothing we can tell from this.
			tts.speak("Not sure.", TextToSpeech.QUEUE_FLUSH, null);
		} else {
			roi = new Rect(new Point(roi.tl().x + 2, roi.tl().y + 2), new Point(roi.br().x - 2, roi.br().y - 2));

			Imgproc.grabCut(imgC3, firstMask, roi, bgModel, fgModel, 1, Imgproc.GC_INIT_WITH_RECT);

			Core.convertScaleAbs(firstMask, firstMask, 100, 0);
			Imgproc.threshold(firstMask, firstMask, 254, 255, Imgproc.THRESH_BINARY);

			Imgproc.erode(firstMask, firstMask, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2)));
			// Imgproc.dilate(firstMask, firstMask, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2)));

			Imgproc.resize(firstMask, firstMask, imageMat.size());

			List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
			Imgproc.findContours(firstMask, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

			Mat finalMask = new Mat();
			firstMask.copyTo(finalMask);
			Rect boundingRect = new Rect();
			if (contours.size() > 0) {
				//imageMat.copyTo(copy, firstMask);
				imageMat.copyTo(copy);
				
				// Go through all the contours and find the largest rect.
				for (MatOfPoint contour : contours) {
					Rect rect = Imgproc.boundingRect(contour);

					if (rect.area() > boundingRect.area()) {
						boundingRect = new Rect(rect.tl(), rect.br());
					}
				}
			} else {
				imageMat.copyTo(copy);
				boundingRect = new Rect(new Point(0, 0), copy.size());
				finalMask.setTo(new Scalar(255));
			}

			int cX = (int) (boundingRect.tl().x + boundingRect.width / 2);
			int cY = (int) (boundingRect.tl().y + boundingRect.height / 2);

			Rect sample = new Rect(cX - COLOR_SAMPLE_MAT_SIZE / 2, cY - COLOR_SAMPLE_MAT_SIZE / 2,
					COLOR_SAMPLE_MAT_SIZE, COLOR_SAMPLE_MAT_SIZE);

			double minDist = Double.MAX_VALUE;
			String colorName = "";
			for (Entry<String, Mat> preDefColor : COLORS.entrySet()) {
				double distance = distance(preDefColor.getValue(), new Mat(copy, sample), new Mat(finalMask, sample), A);
				if (distance < minDist) {
					minDist = distance;
					colorName = preDefColor.getKey();
				}
			}

			tts.speak(colorName.toLowerCase(), TextToSpeech.QUEUE_FLUSH, null);

			Core.rectangle(copy, boundingRect.tl(), boundingRect.br(), new Scalar(0, 255, 255));
			Core.rectangle(copy, sample.tl(), sample.br(), new Scalar(255, 0, 0));
			
			Core.rectangle(copy, new Point(0,0), new Point(450,480), new Scalar(0,255,0), 3);

			Utils.matToBitmap(copy, myBitmap32);

			// release MAT part
			imageMat.release();
			// mask.release();
			firstMask.release();
			source.release();
			bgModel.release();
			fgModel.release();
		}

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
			Log.e(TAG, "Exception in photoCallback", e);
		}

		if (isPressed) {
			snapshotTimer.schedule(new TakePictureTask(this), 2000);
		}

	}

	private double distance(Mat ground, Mat sample, Mat sampleMask, Mat A) {
		double distance = 0;
		
		Scalar groundAvg = Core.mean(ground);
		Scalar sampleAvg = Core.mean(sample, sampleMask);
		
		Mat sampleAvgMat = new Mat(4, 1, CvType.CV_64F, new Scalar(0));
		sampleAvgMat.put(0, 0, sampleAvg.val[0]); sampleAvgMat.put(1, 0, sampleAvg.val[1]); sampleAvgMat.put(2, 0, sampleAvg.val[2]);  sampleAvgMat.put(3, 0, 1.0);
		
		// Transform based on the self-calibration matrix.
		// TODO: Transform each pixel in the mat?
		Mat transformedSample = new Mat();
		
		Core.gemm(A, sampleAvgMat, 1.0, new Mat(), 0.0, transformedSample);
		
		double r = transformedSample.get(0, 0)[0];
		double g = transformedSample.get(1, 0)[0];
		double b = transformedSample.get(2, 0)[0];
		
		if(r < 0) {
			r = 1;
		} else if(r > 254) {
			r = 254;
		}
		
		if(g < 0) {
			g = 1;
		} else if(g > 254) {
			g = 254;
		}
		
		if(b < 0) {
			b = 1;
		} else if(b > 254) {
			b = 254;
		}
		
		Mat transformedSampleMat = new Mat(1, 1, sample.type());
		transformedSampleMat.setTo(new Scalar(r, g, b, 255));
		
		// Convert to HSV, then compare.
		Imgproc.cvtColor(transformedSampleMat, transformedSampleMat, Imgproc.COLOR_RGB2HSV_FULL);
		
		Scalar transformedSampleAvg = Core.mean(transformedSampleMat);
		
		distance = distance(transformedSampleAvg.val, groundAvg.val);
		
		return distance;
	}

	private double distance(double[] color1, double[] color2) {
		double distance = 0;

		for (int i = 0; i < color1.length; i++) {
			distance += Math.pow(color1[i] - color2[i], 2);
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

	public boolean isPressed() {
		return isPressed;
	}
}
