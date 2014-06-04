package info.guardianproject.iocipherexample;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileOutputStream;

import java.io.BufferedOutputStream;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.media.ExifInterface;
import android.os.Bundle;

public class SecureSelfieActivity extends SurfaceGrabberActivity {
	
	private final static String LOG = "SecureSelfie";
	
	private String mFileBasePath = null;
	
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
		return CameraInfo.CAMERA_FACING_FRONT;
		//return CameraInfo.CAMERA_FACING_BACK;
	}

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

			finish();
		} catch (Exception e) {
			e.printStackTrace();
			setResult(Activity.RESULT_CANCELED);
			finish();
		}
		finish();
	}

	
}
