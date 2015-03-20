package info.guardianproject.iocipher.camera;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileOutputStream;
import info.guardianproject.iocipher.camera.encoders.ImageToMJPEGMOVMuxer;
import info.guardianproject.iocipher.camera.io.IOCipherFileChannelWrapper;
import info.guardianproject.iocipher.camera.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;

import org.jcodec.common.SeekableByteChannel;

import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ToggleButton;

public class VideoCameraActivity extends CameraBaseActivity {
	
	private final static String LOG = "VideoJPEGRecorder";
	
	private String mFileBasePath = null;
	private boolean mIsRecording = false;
	
	private ArrayDeque<byte[]> mFrameQ = null;
	
	private int mLastWidth = -1;
	private int mLastHeight = -1;
	private int mPreviewFormat = -1;
	
	private int mJpegQuality = 70;
	
	private int mFramesTotal = 0;
	
	private int mAudioSampleRate = 11025;
	
	private boolean mPreCompressFrames = true;
	OutputStream outputStreamAudio;
	 info.guardianproject.iocipher.File fileAudio;

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
	public void onClick(View view) {
		
		if (view == button)
		{
			if (!mIsRecording)
			{
				
				//start capture
				mIsRecording = true;
				
				mFrameQ = new ArrayDeque<byte[]>();
				progress.setText("[REC]");
				mFramesTotal = 0;
	
				File fileOut = processFrames();
				startAudioRecord(fileOut.getAbsolutePath()+".pcm");
				
			}
			else
			{
				mIsRecording = false;
				progress.setText("");
			}
		}
		else if (view == buttonSelfieSwitch)
		{
			mIsSelfie = !mIsSelfie;
			releaseCamera();
			initCamera();
			
		}
		
		
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		
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
		String fileName = "video" + new java.util.Date().getTime() + ".mov";
		info.guardianproject.iocipher.File fileOut = new info.guardianproject.iocipher.File(mFileBasePath,fileName);
		new Encoder(fileOut).start();
		return fileOut;
	}
	
	ImageToMJPEGMOVMuxer muxer;
	
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
		        //int sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_SYSTEM);

				org.jcodec.common.AudioFormat af = new org.jcodec.common.AudioFormat(org.jcodec.common.AudioFormat.MONO_S16_LE(mAudioSampleRate));
				
				muxer = new ImageToMJPEGMOVMuxer(sbc,af);
				
				while (mIsRecording || (!mFrameQ.isEmpty()))
				{
					if (mFrameQ.peek() != null)
					{
						byte[] bytes = mFrameQ.pop();
						 
						muxer.addFrame(mLastWidth, mLastHeight, ByteBuffer.wrap(bytes));
						
					}

				}

				muxer.finish();
				
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
		     //   int sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_SYSTEM);

				  try {
				   
				   outputStreamAudio = new info.guardianproject.iocipher.FileOutputStream(fileAudio);
					  
					
				   int minBufferSize = AudioRecord.getMinBufferSize(mAudioSampleRate, 
				     AudioFormat.CHANNEL_IN_MONO, 
				     AudioFormat.ENCODING_PCM_16BIT);
				   
				   byte[] audioData = new byte[minBufferSize];
				   
				   AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
						   mAudioSampleRate,
				     AudioFormat.CHANNEL_IN_MONO,
				     AudioFormat.ENCODING_PCM_16BIT,
				     minBufferSize);
				   
				   audioRecord.startRecording();
				   
				   while(mIsRecording){
				    int audioDataBytes = audioRecord.read(audioData, 0, audioData.length);
				    if (AudioRecord.ERROR_INVALID_OPERATION != audioDataBytes
		                    && outputStreamAudio != null) {
		                try {
		                	outputStreamAudio.write(audioData,0,audioDataBytes);
		                	
		                	muxer.addAudio(ByteBuffer.wrap(audioData));
		                } catch (IOException e) {
		                    e.printStackTrace();
		                }
		            }
				   }
				   
				   audioRecord.stop();
				   outputStreamAudio.close();
				   
				  } catch (IOException e) {
				   e.printStackTrace();
				  }
			 }
		 };
		 
		 thread.start();

	 }
	 
	 
	
}
