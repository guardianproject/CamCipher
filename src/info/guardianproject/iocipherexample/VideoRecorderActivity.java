package info.guardianproject.iocipherexample;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileOutputStream;
import info.guardianproject.iocipherexample.IOCipherContentProvider.PipeFeederThread;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import android.app.Activity;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
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


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selfie);

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
						startRecording();
					else					
						stopRecording();
					
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
         String path;
        
         path= Environment.getExternalStorageDirectory().getAbsolutePath().toString();
         
         Date date=new Date();
         filename="/rec"+date.toString().replace(" ", "_").replace(":", "_")+".mp4";
         
        mrec = new MediaRecorder(); 

        mCamera.lock();
        mCamera.unlock();

        // Please maintain sequence of following code. 

        // If you change sequence it will not work.
        mrec.setCamera(mCamera);    
        mrec.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mrec.setAudioSource(MediaRecorder.AudioSource.MIC);     
        mrec.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mrec.setVideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP);
        mrec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mrec.setPreviewDisplay(surfaceHolder.getSurface());
        
        
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        mrec.setOutputFile(pipe[1].getFileDescriptor());
        
        AutoCloseInputStream acis = new AutoCloseInputStream(pipe[0]);
        File file = new File(filename);        
        new PipeFeederThread(acis,new FileOutputStream(file)).start();
        
        mrec.prepare();
        mrec.start();

        
    }

    static class PipeFeederThread extends Thread {
		InputStream in;
		BufferedOutputStream out;

		PipeFeederThread(InputStream in, OutputStream out) {
			this.in = in;
			this.out = new BufferedOutputStream(out, 64000);	
			setDaemon(true);
		}

		@Override
		public void run() {
			
			byte[] buf = new byte[4096];
			int len;

			try {
				
				while ((len = in.read(buf)) != -1)
					out.write(buf, 0, len);
				
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
    	
    		mrec.release();
    		mCamera.release();
    		
    		mrec = null;
    		mCamera = null;
        }
    }

    private void releaseMediaRecorder() {

        if (mrec != null) {
            mrec.reset(); // clear recorder configuration
            mrec.release(); // release the recorder object
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
