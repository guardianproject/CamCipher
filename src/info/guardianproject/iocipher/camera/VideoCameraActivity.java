package info.guardianproject.iocipher.camera;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileOutputStream;
import info.guardianproject.iocipher.camera.encoders.AACHelper;
import info.guardianproject.iocipher.camera.encoders.ImageToMJPEGMOVMuxer;
import info.guardianproject.iocipher.camera.io.IOCipherFileChannelWrapper;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;

import org.jcodec.common.SeekableByteChannel;

import android.app.Activity;
import android.content.Intent;
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
import android.widget.Toast;

public class VideoCameraActivity extends CameraBaseActivity {
	
	private final static String LOG = "VideoJPEGRecorder";
	
	private String mFileBasePath = null;
	private boolean mIsRecording = false;
	
	private ArrayDeque<byte[]> mFrameQ = null;
	
	private int mLastWidth = -1;
	private int mLastHeight = -1;
	private int mPreviewFormat = -1;
	
	private AACHelper aac;
	private boolean useAAC = false;
	private byte[] audioData;
	private AudioRecord audioRecord;
	
	private int mFramesTotal = 0;
	private int mFPS = 0;
	
	private boolean mPreCompressFrames = true;
	private OutputStream outputStreamAudio;
	private info.guardianproject.iocipher.File fileAudio;

	private int frameCounter = 0;
	private long start = 0;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mFileBasePath = getIntent().getStringExtra("basepath");
		
		button.setVisibility(View.VISIBLE);
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
				
				
				mFrameQ = new ArrayDeque<byte[]>();
				
				mFramesTotal = 0;
	
				String fileName = "video" + new java.util.Date().getTime() + ".mov";
				info.guardianproject.iocipher.File fileOut = new info.guardianproject.iocipher.File(mFileBasePath,fileName);
				
				try {
					mIsRecording = true;
					
					if (useAAC)
						initAudio(fileOut.getAbsolutePath()+".aac");
					else
						initAudio(fileOut.getAbsolutePath()+".pcm");
					
					new Encoder(fileOut).start();
					//start capture
					startAudioRecording();
					
					progress.setText("[REC]");

				} catch (Exception e) {
					Log.d("Video","error starting video",e);
					Toast.makeText(this, "Error init'ing video: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
					finish();
				}
				
				
			}
			else
			{
				progress.setText("[SAVING]");
				h.sendEmptyMessageDelayed(1, 2000);
				progress.setText("");
			}
		}
		else
		{
			mPreviewing = false;
			camera.takePicture(null, null, this);
		}
	}
	
	private void toggleCamera ()
	{
		mIsSelfie = !mIsSelfie;
		releaseCamera();
		initCamera();
	}

	//support still pictures if you tap on the screen
	@Override
	public void onPictureTaken(final byte[] data, Camera camera) {		
		File fileSecurePicture;
		try {
			long mTime = System.currentTimeMillis();
			fileSecurePicture = new File(mFileBasePath,"secureselfie_" + mTime + ".jpg");

			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(fileSecurePicture));
			out.write(data);
			out.flush();
			out.close();

			setResult(Activity.RESULT_OK, new Intent().putExtra("path", fileSecurePicture.getAbsolutePath()));
			
			view.postDelayed(new Runnable()
			{
				@Override
				public void run() {
					resumePreview();
				}
			},200);

		} catch (Exception e) {
			e.printStackTrace();
			setResult(Activity.RESULT_CANCELED);

		}

	}
	
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		
		if (mIsRecording && mFrameQ != null)
		{
			
		    Camera.Parameters parameters = camera.getParameters();
		    mLastWidth = parameters.getPreviewSize().width;
		    mLastHeight = parameters.getPreviewSize().height;
		    
			if (mRotation > 0)
			{
				mLastWidth =parameters.getPreviewSize().height;
				mLastHeight =parameters.getPreviewSize().width;
			}
		    
		    mPreviewFormat = parameters.getPreviewFormat();
			
			
			synchronized (mFrameQ)
			{
				if (data != null)
				{
					mFrameQ.add(data);
					mFramesTotal++;
					
					frameCounter++;
                    if((System.currentTimeMillis() - start) >= 1000) {
                    	mFPS = frameCounter;
                        frameCounter = 0; 
                        start = System.currentTimeMillis();
                    }
				}
			}
			
			
		}
		
	}
	
	private byte[] rotateYUV420Degree90(byte[] data, int imageWidth, int imageHeight) 
	{
	    byte [] yuv = new byte[imageWidth*imageHeight*3/2];
	    // Rotate the Y luma
	    int i = 0;
	    for(int x = 0;x < imageWidth;x++)
	    {
	        for(int y = imageHeight-1;y >= 0;y--)                               
	        {
	            yuv[i] = data[y*imageWidth+x];
	            i++;
	        }
	    }
	    // Rotate the U and V color components 
	    i = imageWidth*imageHeight*3/2-1;
	    for(int x = imageWidth-1;x > 0;x=x-2)
	    {
	        for(int y = 0;y < imageHeight/2;y++)                                
	        {
	            yuv[i] = data[(imageWidth*imageHeight)+(y*imageWidth)+x];
	            i--;
	            yuv[i] = data[(imageWidth*imageHeight)+(y*imageWidth)+(x-1)];
	            i--;
	        }
	    }
	    return yuv;
	}

	
	ImageToMJPEGMOVMuxer muxer;
	
	private class Encoder extends Thread {
		private static final String TAG = "ENCODER";

		private File fileOut;
		
		public Encoder (File fileOut) throws IOException
		{
			this.fileOut = fileOut;

			FileOutputStream fos = new info.guardianproject.iocipher.FileOutputStream(fileOut);
			SeekableByteChannel sbc = new IOCipherFileChannelWrapper(fos.getChannel());
	        //int sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_SYSTEM);

			org.jcodec.common.AudioFormat af = null;//new org.jcodec.common.AudioFormat(org.jcodec.common.AudioFormat.MONO_S16_LE(mAudioSampleRate));
			
			muxer = new ImageToMJPEGMOVMuxer(sbc,af);
			
		}
		
		public void run ()
		{

			try {
				
				while (mIsRecording || (!mFrameQ.isEmpty()))
				{
					if (mFrameQ.peek() != null)
					{
						byte[] data = mFrameQ.pop();		
						
						byte[] dataResult = data;
						
						if (mPreCompressFrames)
						{
							if (mRotation > 0)
							{
								dataResult = rotateYUV420Degree90(data,mLastHeight,mLastWidth);
								
								 if (getCameraDirection() == CameraInfo.CAMERA_FACING_FRONT)
								 {						 
									 dataResult = rotateYUV420Degree90(dataResult,mLastWidth,mLastHeight);
									 dataResult = rotateYUV420Degree90(dataResult,mLastHeight,mLastWidth);						 
								 }
								
							}
							
							YuvImage yuv = new YuvImage(dataResult, mPreviewFormat, mLastWidth, mLastHeight, null);
						    ByteArrayOutputStream out = new ByteArrayOutputStream();
						    yuv.compressToJpeg(new Rect(0, 0, mLastWidth, mLastHeight), MediaConstants.sJpegQuality, out);				    
						    dataResult = out.toByteArray();
						}   
						
						muxer.addFrame(mLastWidth, mLastHeight, ByteBuffer.wrap(dataResult),mFPS);						
					}

				}

				muxer.finish();
				
			} catch (Exception e) {
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
			
			if (msg.what == 0)
			{
				int frames = msg.getData().getInt("frames");
				
					if (!mIsRecording)
						if (frames == 0)
							progress.setText("");
						else
							progress.setText("Processing: " + (mFramesTotal-frames) + '/' +  mFramesTotal);
					else
						progress.setText("Recording: " + mFramesTotal);
			}
			else if (msg.what == 1)
			{
				mIsRecording = false; //stop recording
				
				if (aac != null)
					aac.stopRecording();
			}
		}
		
	};
	
	 private void initAudio(final String audioPath) throws FileNotFoundException{

			fileAudio  = new File(audioPath); 
			
			   outputStreamAudio = new BufferedOutputStream(new info.guardianproject.iocipher.FileOutputStream(fileAudio),8192*8);
				
			   if (useAAC)
			   {
				   aac = new AACHelper();
				   aac.setEncoder(MediaConstants.sAudioSampleRate, MediaConstants.sAudioChannels, MediaConstants.sAudioBitRate);
			   }
			   else
			   {
			   
				   int minBufferSize = AudioRecord.getMinBufferSize(MediaConstants.sAudioSampleRate, 
					MediaConstants.sChannelConfigIn, 
				     AudioFormat.ENCODING_PCM_16BIT)*8;
				   
				   audioData = new byte[minBufferSize];
	
				   int audioSource = MediaRecorder.AudioSource.CAMCORDER;
				   
				   if (this.getCameraDirection() == CameraInfo.CAMERA_FACING_FRONT)
				   {
					   audioSource = MediaRecorder.AudioSource.MIC;
					   
				   }
				   
				   audioRecord = new AudioRecord(audioSource,
						   MediaConstants.sAudioSampleRate,
						   MediaConstants.sChannelConfigIn,
				     AudioFormat.ENCODING_PCM_16BIT,
				     minBufferSize);
			   }
	 }
	 
	 private void startAudioRecording ()
	 {
			  
		 
		 Thread thread = new Thread ()
		 {
			 
			 public void run ()
			 {
			 
				 if (useAAC)
				 {
					 try {
						aac.startRecording(outputStreamAudio);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				 }
				 else
				 {
				   audioRecord.startRecording();
				   
				   while(mIsRecording){
				    int audioDataBytes = audioRecord.read(audioData, 0, audioData.length);
				    if (AudioRecord.ERROR_INVALID_OPERATION != audioDataBytes
		                    && outputStreamAudio != null) {
		                try {
		                	outputStreamAudio.write(audioData,0,audioDataBytes);
		                	
		                //	muxer.addAudio(ByteBuffer.wrap(audioData));
		                } catch (IOException e) {
		                    e.printStackTrace();
		                }
		            }
				   }
				   
				   audioRecord.stop();
				   try {
					   outputStreamAudio.flush();
					outputStreamAudio.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				 }
				 
				 
			 }
		 };
		 
		 thread.start();

	 }
	 
	 
	
}
