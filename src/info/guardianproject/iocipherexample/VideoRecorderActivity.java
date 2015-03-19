package info.guardianproject.iocipherexample;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import android.app.Activity;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class VideoRecorderActivity extends Activity implements Callback {

    @Override
    protected void onDestroy() {
        stopRecording();
        super.onDestroy();
    }

    private SurfaceHolder surfaceHolder;
    private SurfaceView surfaceView;
    public MediaRecorder mrec;
    private Camera mCamera;

    private Handler h = new Handler ()
    {

		@Override
		public void handleMessage(Message msg) {
		
			super.handleMessage(msg);
			
			try
			{
				if (msg.what == 1)
					startRecording();
				else if (msg.what == 2)
					stopRecording();
			}
			catch (IOException ioe)
			{
				Log.e("Video","error recording",ioe);
			}
			
		}
    	
    };
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera);

        surfaceView = (SurfaceView) findViewById(R.id.surface_grabber_holder);
        mCamera = Camera.open();
        
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
     
        Button btn = (Button)findViewById(R.id.surface_grabber_button);
        btn.setOnClickListener(new OnClickListener ()
        {

			@Override
			public void onClick(View v) {
				
				try
				{
					if (mrec == null)
						h.sendEmptyMessage(1);
					else					
						h.sendEmptyMessage(2);
					
				}
				catch (Exception e)
				{
					Log.d("VideoCamera","error doing camera",e);
				}
			}
        	
        });
    }

    protected void startRecording() throws IOException
    {
        if(mCamera==null)
            mCamera = Camera.open();
        
         String filename;
        
         Date date=new Date();
         filename="/rec"+date.toString().replace(" ", "_").replace(":", "_")+".mp4";
         
        mrec = new MediaRecorder(); 

        mCamera.lock();
        mCamera.unlock();

        mrec.setCamera(mCamera);    
        mrec.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        //mrec.setAudioSource(MediaRecorder.AudioSource.MIC);
        
        //this sets the streaming format "TS"
       mrec.setOutputFormat(/*MediaRecorder.OutputFormat.OUTPUT_FORMAT_MPEG2TS*/8);
        
      //  mrec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        
        CamcorderProfile cpHigh = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
 
        int width=720, height=480;
        int frameRate = 30;
        
        mrec.setVideoEncoder(MediaRecorder.VideoEncoder.H264);        
        mrec.setVideoSize(width, height);
        mrec.setVideoFrameRate(frameRate);
        mrec.setVideoEncodingBitRate(cpHigh.videoBitRate);
      
        /*
        mrec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);                
        mrec.setAudioEncodingBitRate(cpHigh.audioBitRate);
        mrec.setAudioChannels(1);
        mrec.setAudioSamplingRate(cpHigh.audioSampleRate);
        */        
        
        mrec.setPreviewDisplay(surfaceHolder.getSurface());
 
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        mrec.setOutputFile(pipe[1].getFileDescriptor());
        
        AutoCloseInputStream acis = new AutoCloseInputStream(pipe[0]);
        info.guardianproject.iocipher.File file = new info.guardianproject.iocipher.File(filename);        
        new PipeFeederThread(acis,new info.guardianproject.iocipher.FileOutputStream(file)).start();
        
        mrec.prepare();
        mrec.start();

        
    }

    static class PipeFeederThread extends Thread {
		InputStream in;
		OutputStream out;

		PipeFeederThread(InputStream in, OutputStream out) {
			this.in = in;
			this.out = out;
			setDaemon(true);
		}

		@Override
		public void run() {
			
			byte[] buf = new byte[16000];
			int len;

			try {
				int idx = 0;
				while ((len = in.read(buf)) != -1)
				{
					out.write(buf, 0, len);
					idx += buf.length;
					Log.d("video","writing to IOCipher at " + idx);
				}
				
				in.close();
				out.flush();
				out.close();
				
			} catch (IOException e) {
				Log.e("Video", "File transfer failed:", e);
			}
		}
	}
    
    protected void stopRecording() {

        if(mrec!=null)
        {    
    		mrec.stop();
    		
    		releaseMediaRecorder();
    		
    		releaseCamera();
    		
        }
    }

    private void releaseMediaRecorder() {

        if (mrec != null) {
            mrec.reset(); // clear recorder configuration
            mrec.release(); // release the recorder object
            mrec = null;
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release(); // release the camera for other applications
            mCamera = null;
        }

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,int height) {      

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {       

        if (mCamera != null) {
            Parameters params = mCamera.getParameters();
            mCamera.setParameters(params);
            Log.i("Surface", "Created");
        }
        else {
            Toast.makeText(getApplicationContext(), "Camera not available!",
                    Toast.LENGTH_LONG).show();

            finish();
        }

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    	

    }
}
