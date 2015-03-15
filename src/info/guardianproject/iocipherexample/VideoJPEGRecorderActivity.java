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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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
	private int mFPS = 15;
	
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
			if (mLastWidth == -1)
			{
			 Camera.Parameters parameters = camera.getParameters();
			    mLastWidth = parameters.getPreviewSize().width;
			    mLastHeight = parameters.getPreviewSize().height;
			    mPreviewFormat = parameters.getPreviewFormat();
			    
			  //  int[] range = new int[2];
			  //  parameters.getPreviewFpsRange(range);
			  //  mFPS = range[Camera.Parameters.PREVIEW_FPS_MAX_INDEX];
			}
			
			YuvImage yuv = new YuvImage(data, mPreviewFormat, mLastWidth, mLastHeight, null);

		    ByteArrayOutputStream out = new ByteArrayOutputStream();
		    yuv.compressToJpeg(new Rect(0, 0, mLastWidth, mLastHeight), 30, out);

		    byte[] bytes = out.toByteArray();
			
			synchronized (mFrameQ)
			{
				if (bytes != null)
				{
					
				    
					mFrameQ.add(bytes);
					
				   
				}
			}
			
			
			    
		}
		
	}

	private void processFrames ()
	{
		String fileName = "video" + new java.util.Date().getTime() + ".mp4";
		info.guardianproject.iocipher.File fileOut = new info.guardianproject.iocipher.File(mFileBasePath,fileName);
		//java.io.File fileOut = new java.io.File("/sdcard",fileName);
		new Encoder(fileOut).start();
	}
	
	private class Encoder extends Thread {
		private static final String TAG = "ENCODER";

		private File fileOut;
		
		public Encoder (File fileOut)
		{
			this.fileOut = fileOut;
			this.setPriority(Thread.MAX_PRIORITY);
		}
		
		public void run ()
		{
			SequenceEncoder se = null;
			try {
				
				FileOutputStream fos = new info.guardianproject.iocipher.FileOutputStream(fileOut);
				se = new SequenceEncoder(new IOCipherFileChannelWrapper(fos.getChannel()),mLastWidth,mLastHeight,mFPS);
				//se = new SequenceEncoder(params[0]);
				int i = 0;
				
				while (mIsRecording || (!mFrameQ.isEmpty()))
				{
					if (mFrameQ.peek() != null)
					{
						byte[] bytes = mFrameQ.pop();
						
						Bitmap bmp = BitmapFactory.decodeByteArray(bytes,0,bytes.length);
						se.encodeImage(bmp);
						
						Message msg = new Message();
						msg.getData().putInt("frames", mFrameQ.size());
						h.sendMessage(msg);
					}

				}
				se.finish();
				
			} catch (IOException e) {
				Log.e(TAG, "IO", e);
			}

		}

		
	}
	
	Handler h = new Handler ()
	{

		@Override
		public void handleMessage(Message msg) {
			// TODO Auto-generated method stub
			super.handleMessage(msg);
			
			int frames = msg.getData().getInt("frames");
			
				if (!mIsRecording)
					if (frames == 0)
						progress.setText("");
					else
						progress.setText("Processing: " + frames + " frames left...");
			
		}
		
	};
	
}
