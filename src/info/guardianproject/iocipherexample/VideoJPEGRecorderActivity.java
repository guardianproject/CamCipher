package info.guardianproject.iocipherexample;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;

import org.jcodec.api.android.SequenceEncoder;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class VideoJPEGRecorderActivity extends SurfaceGrabberActivity {
	
	private final static String LOG = "SecureSelfie";
	
	private String mFileBasePath = null;
	private boolean mIsRecording = false;
	
	private ArrayDeque<byte[]> mFrameQ = null;
	
	private int mLastWidth = -1;
	private int mLastHeight = -1;
	private int mPreviewFormat = -1;
	private int mFPS = 10;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mFileBasePath = getIntent().getStringExtra("basepath");

		
	}

	@Override
	protected int getLayout()
	{
		return R.layout.camera;
	}
	
	@Override
	protected int getCameraDirection() {
		return CameraInfo.CAMERA_FACING_BACK;
	}
	
	@Override
	public void onClick(View view) {
		if (!mIsRecording)
		{
			//start capture
			mFrameQ = new ArrayDeque<byte[]>();
			progress.setText("Recording...");
			processFrames();
		}
		else
		{

			progress.setText("");
		}
		 
		 mIsRecording = !mIsRecording;
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		super.onPreviewFrame(data, camera);
		
		if (mIsRecording && mFrameQ != null)
		{
			synchronized (mFrameQ)
			{
				mFrameQ.add(data);
				
			    Camera.Parameters parameters = camera.getParameters();
			    mLastWidth = parameters.getPreviewSize().width;
			    mLastHeight = parameters.getPreviewSize().height;
			    mPreviewFormat = parameters.getPreviewFormat();
			    
			}
		}
		
	}

	private void processFrames ()
	{
		String fileName = "video" + new java.util.Date().getTime() + ".mp4";
		info.guardianproject.iocipher.File fileOut = new info.guardianproject.iocipher.File(mFileBasePath,fileName);
		//java.io.File fileOut = new java.io.File("/sdcard",fileName);
		new Encoder().execute(fileOut);
	}
	
	private class Encoder extends AsyncTask<File, Integer, Integer> {
		private static final String TAG = "ENCODER";

		protected Integer doInBackground(File... params) {
			SequenceEncoder se = null;
			try {
				
				FileOutputStream fos = new info.guardianproject.iocipher.FileOutputStream(params[0]);
				se = new SequenceEncoder(new IOCipherFileChannelWrapper(fos.getChannel()),mLastWidth,mLastHeight,mFPS);
				//se = new SequenceEncoder(params[0]);
				int i = 0;
				
				while (mIsRecording || (!mFrameQ.isEmpty()))
				{
					if (mFrameQ.peek() != null)
					{
						byte[] frame = mFrameQ.pop();
						
					    YuvImage yuv = new YuvImage(frame, mPreviewFormat, mLastWidth, mLastHeight, null);

					    ByteArrayOutputStream out = new ByteArrayOutputStream();
					    yuv.compressToJpeg(new Rect(0, 0, mLastWidth, mLastHeight), 50, out);

					    byte[] bytes = out.toByteArray();
						    
						Bitmap bmp = BitmapFactory.decodeByteArray(bytes,0,bytes.length);
						se.encodeImage(bmp);

						publishProgress(i++);
					}

				}
				publishProgress(0);
				se.finish();
				
			} catch (IOException e) {
				Log.e(TAG, "IO", e);
			}

			return 0;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			
			if (!mIsRecording)
				if (values[0] == 0)
					progress.setText("");
				else
					progress.setText("Processing...");
		}
	}
	
}
