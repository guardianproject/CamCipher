package info.guardianproject.iocipherexample;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.iocipher.FileOutputStream;
import info.guardianproject.iocipherexample.encoders.ImageToH264MP4Encoder;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;

import org.jcodec.common.SeekableByteChannel;
import org.jcodec.scale.BitmapUtil;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;

public class VideoJPEGRecorderActivity extends SurfaceGrabberActivity {
	
	private final static String LOG = "VideoJPEGRecorder";
	
	private String mFileBasePath = null;
	private boolean mIsRecording = false;
	
	private ArrayDeque<byte[]> mFrameQ = null;
	
	private int mLastWidth = -1;
	private int mLastHeight = -1;
	private int mPreviewFormat = -1;
	private int mTimeScale = 15;
	
	private int mJpegQuality = 100;
	
	private int mFramesTotal = 0;
	
	private boolean mPreCompressFrames = true;
	OutputStream outputStreamAudio;
	 info.guardianproject.iocipher.File fileAudio;
	  
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mFileBasePath = getIntent().getStringExtra("basepath");

		mJpegQuality = 80;
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
			mFramesTotal = 0;

			
			File fileOut = processFrames();
			startAudioRecord(fileOut.getAbsolutePath()+".pcm");
			
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
			    
			}
			
			if (mPreCompressFrames)
			{
				YuvImage yuv = new YuvImage(data, mPreviewFormat, mLastWidth, mLastHeight, null);
			    ByteArrayOutputStream out = new ByteArrayOutputStream();
			    yuv.compressToJpeg(new Rect(0, 0, mLastWidth, mLastHeight), mJpegQuality, out);			   
			    data = out.toByteArray();
			}   
			
			synchronized (mFrameQ)
			{
				if (data != null)
				{
					mFrameQ.add(data);
					mFramesTotal++;
				}
			}
			
			
		}
		
	}

	private File processFrames ()
	{
		String fileName = "video" + new java.util.Date().getTime() + ".mp4";
		info.guardianproject.iocipher.File fileOut = new info.guardianproject.iocipher.File(mFileBasePath,fileName);
		new Encoder(fileOut).start();
		return fileOut;
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

			try {
				
				FileOutputStream fos = new info.guardianproject.iocipher.FileOutputStream(fileOut);
				SeekableByteChannel sbc = new IOCipherFileChannelWrapper(fos.getChannel());
				
				//ImageToMP4Muxer mx = new ImageToMP4Muxer(sbc);
				//YUVtoWebmMuxer mx = new YUVtoWebmMuxer(sbc,mLastWidth,mLastHeight);
				//ImageToVP8Encoder mx = new ImageToVP8Encoder(sbc);
				org.jcodec.common.AudioFormat af = new org.jcodec.common.AudioFormat(org.jcodec.common.AudioFormat.MONO_S16_LE(22050));
				
				ImageToH264MP4Encoder mx = new ImageToH264MP4Encoder(sbc,af);
				
				while (mIsRecording || (!mFrameQ.isEmpty()))
				{
					if (mFrameQ.peek() != null)
					{
						byte[] bytes = mFrameQ.pop();
					
						 Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0,bytes.length);
						 
					//	se.encodeImage(mLastWidth, mLastHeight,ByteBuffer.wrap(bytes));
					//	mx.addJPEGFrame(mLastWidth, mLastHeight, ByteBuffer.wrap(bytes));
					//	mx.encodeNativeFrame(ByteBuffer.wrap(bytes), mLastWidth, mLastHeight, frameIdx++);
					//	mx.addBitmap(mLastWidth, mLastHeight, bmp, frameIdx);
						
						 
						mx.addFrame(BitmapUtil.fromBitmap(bmp));
						
						Message msg = new Message();
						msg.getData().putInt("frames", mFrameQ.size());
						h.sendMessage(msg);
					}

				}

				
				//FileInputStream fis = new FileInputStream(fileAudio);
				
				//mx.addAudio(ByteBuffer.wrap(outputStreamAudio.toByteArray()));
				
				
				mx.finish();
				
			} catch (IOException e) {
				Log.e(TAG, "IO", e);
			}

		}
		
		/*
		public void run ()
		{
			SequenceEncoder se = null;
			try {
				
				FileOutputStream fos = new info.guardianproject.iocipher.FileOutputStream(fileOut);
				se = new SequenceEncoder(new IOCipherFileChannelWrapper(fos.getChannel()),mLastWidth,mLastHeight,mTimeScale);
				//se = new SequenceEncoder(params[0]);
				
				while (mIsRecording || (!mFrameQ.isEmpty()))
				{
					if (mFrameQ.peek() != null)
					{f
						byte[] bytes = mFrameQ.pop();
						
						Bitmap bmp = BitmapFactory.decodeByteArray(bytes,0,bytes.length);
						
						for (int i = 0; i < mFrameFreq; i++)
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
		*/

		
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
						progress.setText("Processing: " + (mFramesTotal-frames) + '/' +  mFramesTotal);
				else
					progress.setText("Recording: " + mFramesTotal);
		}
		
	};
	

	 private void startAudioRecord(final String audioPath){

		 outputStreamAudio = new ByteArrayOutputStream();
		 
		 Thread thread = new Thread ()
		 {
			 
			 public void run ()
			 {
			 
				fileAudio  = new File(audioPath); 
				    
				  try {
				   
				   outputStreamAudio = new info.guardianproject.iocipher.FileOutputStream(fileAudio);
					  
					  BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStreamAudio);
					   
					  DataOutputStream dataOutputStream = new DataOutputStream(bufferedOutputStream);
				   
				   int minBufferSize = AudioRecord.getMinBufferSize(22050, 
				     AudioFormat.CHANNEL_CONFIGURATION_MONO, 
				     AudioFormat.ENCODING_PCM_16BIT);
				   
				   short[] audioData = new short[minBufferSize];
				   
				   AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
						   22050,
				     AudioFormat.CHANNEL_CONFIGURATION_MONO,
				     AudioFormat.ENCODING_PCM_16BIT,
				     minBufferSize);
				   
				   audioRecord.startRecording();
				   
				   while(mIsRecording){
				    int numberOfShort = audioRecord.read(audioData, 0, minBufferSize);
				    for(int i = 0; i < numberOfShort; i++){
				     dataOutputStream.writeShort(audioData[i]);
				    }
				   }
				   
				   audioRecord.stop();
				   dataOutputStream.close();
				   
				  } catch (IOException e) {
				   e.printStackTrace();
				  }
			 }
		 };
		 
		 thread.start();

	 }
	
}
