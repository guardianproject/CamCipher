package info.guardianproject.iocipher.camera;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileOutputStream;
import info.guardianproject.iocipher.camera.R;

import java.io.BufferedOutputStream;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.media.ExifInterface;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;

public class StillCameraActivity extends CameraBaseActivity {
	
	private final static String LOG = "SecureSelfie";
	
	private String mFileBasePath = null;
	
	boolean isRequest = false;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mFileBasePath = getIntent().getStringExtra("basepath");
		
		button.setVisibility(View.GONE);//we don't need a shutter button - the user can just tap on the screen!
		
		isRequest = getIntent().getAction() != null && getIntent().getAction().equals(MediaStore.ACTION_IMAGE_CAPTURE);
	}

	@Override
	protected int getLayout()
	{
		return R.layout.camera;
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

			Intent intentResult = new Intent().putExtra(MediaStore.EXTRA_OUTPUT, fileSecurePicture.getAbsolutePath());			
			setResult(Activity.RESULT_OK, intentResult);
			
			if (isRequest)
			{
				finish();
			}
			else
			{
				view.postDelayed(new Runnable()
				{
					@Override
					public void run() {
						resumePreview();
					}
				},200);
			}

		} catch (Exception e) {
			e.printStackTrace();
			setResult(Activity.RESULT_CANCELED);

		}

	}

	
}
