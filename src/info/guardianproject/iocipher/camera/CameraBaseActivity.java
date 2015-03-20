package info.guardianproject.iocipher.camera;

import java.io.IOException;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

public abstract class CameraBaseActivity extends Activity implements OnClickListener, SurfaceHolder.Callback, PictureCallback, PreviewCallback {
	
	Button button;
	TextView progress;
	ToggleButton buttonSelfieSwitch;
	
	SurfaceView view;
	SurfaceHolder holder;
	Camera camera;
	CameraInfo cameraInfo;
	
	private boolean mPreviewing;

	private final static String LOG = "Camera";

	int mPreviewWidth = 720;
	int mPreviewHeight = 480;
	int mRotation = 0;
	
	boolean mIsSelfie = false;
	
	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(getLayout());
		

		mIsSelfie = getIntent().getBooleanExtra("selfie", false);

		
		button = (Button) findViewById(R.id.surface_grabber_button);
		button.setOnClickListener(this);
		
		buttonSelfieSwitch = (ToggleButton)findViewById(R.id.tbSelfie);
		buttonSelfieSwitch.setOnClickListener(this);
		
		progress = (TextView) findViewById(R.id.surface_grabber_progress);
		
		view = (SurfaceView) findViewById(R.id.surface_grabber_holder);
		holder = view.getHolder();
		holder.addCallback(this);
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
				
	}

	protected int getLayout()
	{
		return R.layout.camera;
	}
	

	protected int getCameraDirection() {
		if (mIsSelfie)
			return CameraInfo.CAMERA_FACING_FRONT;
		else
			return CameraInfo.CAMERA_FACING_BACK;
	}

	
	/**
     * Whether or not we can default to "other" direction if our preferred facing camera can't be opened
     * @return true to try camera facing other way, false otherwise
     */
    protected boolean canUseOtherDirection()
    {
            return false;
    }

	
	@Override
	public void onResume() {
		super.onResume();
		
		initCamera();
	}
	
	protected void initCamera()
	{

		if (!tryCreateCamera(getCameraDirection()))
        {
                if (!canUseOtherDirection() || !tryCreateCamera(getOtherDirection(getCameraDirection())))
                {
                        finish();
                        return;
                }
        }

		if(camera == null)
			finish();
		
	}

	private int getOtherDirection(int facing)
	{
		return (facing == CameraInfo.CAMERA_FACING_BACK) ? CameraInfo.CAMERA_FACING_FRONT : CameraInfo.CAMERA_FACING_BACK;
	}
	
	private boolean tryCreateCamera(int facing)
	{
	     Camera.CameraInfo info = new Camera.CameraInfo();
	     for (int nCam = 0; nCam < Camera.getNumberOfCameras(); nCam++)
	     {
		     Camera.getCameraInfo(nCam, info);
		     if (info.facing == facing)
		     {
		    	 camera = Camera.open(nCam);
		    	 cameraInfo = info;

		    	 Camera.Parameters params = camera.getParameters();
				 params.setPictureFormat(ImageFormat.JPEG);
				 
				 mRotation = setCameraDisplayOrientation(this,nCam,camera);
				 
				 
				 List<Camera.Size> supportedPreviewSizes =  camera.getParameters().getSupportedPreviewSizes();
				 List<Camera.Size> supportedPictureSize = camera.getParameters().getSupportedPictureSizes();
				 
				 int previewQuality = 4;
				 
				 params.setPreviewSize(supportedPreviewSizes.get(previewQuality).width, supportedPreviewSizes.get(previewQuality).height);
				 params.setPictureSize(supportedPictureSize.get(1).width, supportedPictureSize.get(1).height);
				 
				 mPreviewWidth = supportedPreviewSizes.get(previewQuality).width;
				 mPreviewHeight = supportedPreviewSizes.get(previewQuality).height;
				 
				 if (this.getCameraDirection() == CameraInfo.CAMERA_FACING_BACK)
				 {
					 params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
					 
					 if (mRotation > 0)
						 params.setRotation(mRotation);

				 }
				 else
				 {
					 if (mRotation > 0)
						 params.setRotation(360-mRotation);
				 }
									
					camera.setParameters(params);
					
					camera.setPreviewCallback(this);
					
					//Log.d(TAG, "Initialize Callback Buffers: " + BUFFER_SIZE);

					 //int BUFFER_SIZE = (int) (mPreviewWidth * mPreviewHeight * 1.5);
					 //int NUM_CAMERA_PREVIEW_BUFFERS = 3;
					 
			        //camera.setPreviewCallbackWithBuffer(null);
			        //camera.setPreviewCallbackWithBuffer(this);
			        //for (int i = 0; i < NUM_CAMERA_PREVIEW_BUFFERS; i++) {
			         //   camera.addCallbackBuffer(new byte[BUFFER_SIZE]);
			        //}
			        
					if (holder != null)
						try {
							camera.setPreviewDisplay(holder);
							camera.startPreview();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					
		    	 return true;
		     }
	     }
	     return false;
	}
	
	
	@Override
	public void onPause() {
		releaseCamera();
				
		super.onPause();
	}
	
	protected void releaseCamera ()
	{
		try
	    {    
	        // release the camera immediately on pause event   
			camera.stopPreview(); 
			camera.setPreviewCallback(null);
			camera.release();
			camera = null;

	    }
	    catch(Exception e)
	    {
	        e.printStackTrace();
	    }

	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		camera.startPreview();
		mPreviewing = true;
	}

	protected Size choosePictureSize(List<Size> localSizes)
	{
		Size size = null;
		
		for(Size sz : localSizes) {
			if(sz.width > 640 && sz.width <= 1024)
				size = sz;
			
			if(size != null)
				break;
		}
		
		if(size == null)
			size = localSizes.get(localSizes.size() - 1);
		return size;
	}
	
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		try {
			
			this.holder = holder;
			camera.setPreviewDisplay(holder);
			
		} catch(IOException e) {
			Log.e(LOG, e.toString());
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {}

	@Override
	public void onClick(View view) {
		if(view == button && mPreviewing) {
			mPreviewing = false;
			camera.takePicture(null, null, this);
		}
		else if (view == buttonSelfieSwitch)
		{
			mIsSelfie = !mIsSelfie;
			releaseCamera();
			initCamera();
			
		}
	}

	@Override
	public void onPictureTaken(byte[] data, Camera camera) {
		
		try
		{
			String pathToData = "";
			//data, new File(pathToData));
				
			view.post(new Runnable()
			{
				@Override
				public void run() {
					resumePreview();
				}
			});
		}
		catch (Exception ioe)
		{
			Log.e(LOG,"error saving picture to iocipher",ioe);
		}
	}

	protected void resumePreview()
	{
		if (!mPreviewing)
		{
			camera.startPreview();
			mPreviewing = true;
		}
	}
	
	
	public static int setCameraDisplayOrientation(Activity activity,
	         int cameraId, android.hardware.Camera camera) {
	     android.hardware.Camera.CameraInfo info =
	             new android.hardware.Camera.CameraInfo();
	     android.hardware.Camera.getCameraInfo(cameraId, info);
	     int rotation = activity.getWindowManager().getDefaultDisplay()
	             .getRotation();
	     int degrees = 0;
	     switch (rotation) {
	         case Surface.ROTATION_0: degrees = 0; break;
	         case Surface.ROTATION_90: degrees = 90; break;
	         case Surface.ROTATION_180: degrees = 180; break;
	         case Surface.ROTATION_270: degrees = 270; break;
	     }

	     int result;
	     if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
	         result = (info.orientation + degrees) % 360;
	         result = (360 - result) % 360;  // compensate the mirror
	     } else {  // back-facing
	         result = (info.orientation - degrees + 360) % 360;
	     }
	     camera.setDisplayOrientation(result);
	     
	     return result;
	 }
}