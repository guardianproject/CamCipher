package info.guardianproject.iocipher.camera;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.iocipher.FileOutputStream;
import info.guardianproject.iocipher.VirtualFileSystem;
import info.guardianproject.iocipher.camera.io.IOCipherContentProvider;
import info.guardianproject.iocipher.camera.viewer.ImageViewerActivity;
import info.guardianproject.iocipher.camera.viewer.MjpegViewerActivity;
import info.guardianproject.iocipher.camera.viewer.StreamOverHttp;
import info.guardianproject.iocipher.camera.viewer.VideoViewerActivity;
import info.guardianproject.iocipher.camera.R;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class GalleryActivity extends ListActivity {
	private final static String TAG = "FileBrowser";

	private List<String> item = null;
	private List<String> path = null;
	private TextView fileInfo;
	private String[] items;
	private java.io.File dbFile;
	private String root = "/";
	private String mPassword = "this is just a test";
	/** Called when the activity is first created. */

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		String action = intent.getAction();
		String type = intent.getType();
		if (Intent.ACTION_SEND.equals(action) && type != null) {
			if (intent.hasExtra(Intent.EXTRA_STREAM)) {
				Log.i(TAG, "save extra stream URI");
				handleSendUri((Uri) intent.getExtras().get(Intent.EXTRA_STREAM));
			} else {
				Log.i(TAG, "save data");
				handleSendUri(intent.getData());
			}
		}

		setContentView(R.layout.main);
		fileInfo = (TextView) findViewById(R.id.info);
		dbFile = new java.io.File(getDir("vfs", MODE_PRIVATE),"gallery.db");
		dbFile.getParentFile().mkdirs();
		
		if (!dbFile.exists())
			VirtualFileSystem.get().createNewContainer(dbFile.getAbsolutePath(), mPassword);
	}

	protected void onResume() {
		super.onResume();
		
		if (!VirtualFileSystem.get().isMounted())
		{
			// TODO don't use a hard-coded password! prompt for the password
			VirtualFileSystem.get().mount(dbFile.getAbsolutePath(),mPassword);
		}
		
		getFileList(root);
		
		if (sHttp != null)
		{
			sHttp.close();
		}
	}

	protected void onDestroy() {
		super.onDestroy();
		
		/**
		if (VirtualFileSystem.get().is)
		{
			try
			{
				VirtualFileSystem.get().unmount();
			}catch(IllegalArgumentException iae){}
		}*/
		
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        
        return true;
	}
	

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	
    	Intent intent = null;
    	
        switch (item.getItemId()) {

        case R.id.menu_camera:
        	
        	intent = new Intent(this,StillCameraActivity.class);
        	intent.putExtra("basepath", "/");
        	intent.putExtra("selfie", false);
        	startActivityForResult(intent, 1);
        	
        	return true;
        	
        case R.id.menu_video:
        	
        	intent = new Intent(this,VideoCameraActivity.class);
        	intent.putExtra("basepath", "/");
        	intent.putExtra("selfie", false);
        	startActivityForResult(intent, 1);
        	
        	return true;
        }	
        
        return false;
    }

	private void handleSendUri(Uri dataUri) {
		try {
			ContentResolver cr = getContentResolver();
			InputStream in = cr.openInputStream(dataUri);
			Log.i(TAG, "incoming URI: " + dataUri.toString());
			String fileName = dataUri.getLastPathSegment();
			File f = new File("/" + fileName);
			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(f));
			readBytesAndClose(in, out);
			Log.v(TAG, f.getAbsolutePath() + " size: " + String.valueOf(f.length()));
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private void readBytesAndClose(InputStream in, OutputStream out)
			throws IOException {
		try {
			int block = 8 * 1024; // IOCipher works best with 8k blocks
			byte[] buff = new byte[block];
			while (true) {
				int len = in.read(buff, 0, block);
				if (len < 0) {
					break;
				}
				out.write(buff, 0, len);
			}
		} finally {
			in.close();
			out.flush();
			out.close();
		}
	}

	// To make listview for the list of file
	public void getFileList(String dirPath) {

		item = new ArrayList<String>();
		path = new ArrayList<String>();

		File file = new File(dirPath);
		File[] files = file.listFiles();

		if (!dirPath.equals(root)) {
			item.add(root);
			path.add(root);// to get back to main list

			item.add("..");
			path.add(file.getParent()); // back one level
		}

		for (int i = 0; i < files.length; i++) {

			File fileItem = files[i];
			path.add(fileItem.getPath());
			if (fileItem.isDirectory()) {
				// input name directory to array list
				item.add("[" + fileItem.getName() + "]");
			} else {
				// input name file to array list
				item.add(fileItem.getName());
			}
		}
		fileInfo.setText("Info: " + dirPath + " [ " + files.length + " item ]");
		// declare array with specific number of items
		items = new String[item.size()];
		// send data arraylist(item) to array(items)
		item.toArray(items);
		setListAdapter(new IconicList());
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		final File file = new File(path.get(position));
		if (file.isDirectory()) {
			if (file.canRead()) {
				getFileList(path.get(position));
			} else {
				new AlertDialog.Builder(this)
						.setIcon(R.drawable.icon)
						.setTitle(
								"[" + file.getName() + "] folder can't be read")
						.setPositiveButton("OK",
								new DialogInterface.OnClickListener() {

									// @Override
									public void onClick(DialogInterface dialog,
											int which) {
										// TODO Auto-generated method stub

									}
								}).show();

			}
		} else {
			Log.i(TAG,"open URL: " + Uri.parse(IOCipherContentProvider.FILES_URI + file.getName()));
			final Uri uri = Uri.parse(IOCipherContentProvider.FILES_URI + file.getName());
			
			new AlertDialog.Builder(this)
					.setIcon(R.drawable.icon)
					.setTitle("[" + file.getName() + "]")
					.setNeutralButton("View",
							new DialogInterface.OnClickListener() {
						// @Override
						public void onClick(DialogInterface dialog,
								int which) {
							try {
								String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
								String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension);
								if (fileExtension.equals("ts"))
									mimeType = "application/mpeg*";
								
								if (mimeType == null)
									mimeType = "application/octet-stream";
	
								if (mimeType.startsWith("image"))
								{
									 Intent intent = new Intent(GalleryActivity.this,ImageViewerActivity.class);
									  intent.setType(mimeType);
									  intent.putExtra("vfs", file.getAbsolutePath());
									  startActivity(intent);	
								}
								else if (fileExtension.equals("mp4") || mimeType.startsWith("video"))
								{
									Intent intent = new Intent(GalleryActivity.this,MjpegViewerActivity.class);
									  intent.setType(mimeType);
									  intent.putExtra("video", file.getAbsolutePath());
									  intent.putExtra("audio", file.getAbsolutePath() + ".pcm");
									  
									  startActivity(intent);	
									
									//shareVideoUsingStream(file, mimeType);
								}
								else {
						          Intent intent = new Intent(Intent.ACTION_VIEW);													
								  intent.setDataAndType(uri, mimeType);
								  startActivity(intent);
								}
								 
								
							} catch (ActivityNotFoundException e) {
								Log.e(TAG, "No relevant Activity found", e);
							}
						}
					})
					.setNegativeButton("Delete",
							new DialogInterface.OnClickListener() {
						
							public void onClick(DialogInterface dialog,
								int which) {
								
								file.delete();
								getFileList(root);
							}
							
					})
					.setPositiveButton("Share...",
							new DialogInterface.OnClickListener() {

								// @Override
								public void onClick(DialogInterface dialog,
										int which) {
									
									java.io.File exportFile = exportToDisk(file);
									Uri uriExport = Uri.fromFile(exportFile);
									
									Intent intent = new Intent(Intent.ACTION_SEND);
									
									String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
									String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension);
									if (fileExtension.equals("mp4")||fileExtension.equals("mkv")||fileExtension.equals("mov"))
										mimeType = "video/*";
									if (mimeType == null)
										mimeType = "application/octet-stream";
									
									intent.setDataAndType(uri, mimeType);
									intent.putExtra(Intent.EXTRA_STREAM, uriExport);
									intent.putExtra(Intent.EXTRA_SUBJECT, exportFile.getName());
									intent.putExtra(Intent.EXTRA_TITLE, exportFile.getName());
									
									try {
										startActivity(Intent.createChooser(intent, "Share this!"));
									} catch (ActivityNotFoundException e) {
										Log.e(TAG, "No relevant Activity found", e);
									}
								}
							}).show();
		}
		
	}
	
	StreamOverHttp sHttp = null;
	
	private void shareVideoUsingStream(final File f, final String mimeType)
	{
		
		final int port = 8080;
		
		final String shareMimeType = "video/mp4";
		
		try {
			sHttp = new StreamOverHttp(f,shareMimeType,f.length(),new info.guardianproject.iocipher.FileInputStream(f),port);
			
			Uri uri = Uri.parse("http://localhost:" + port + f.getAbsolutePath());
			
			Intent intent = new Intent(GalleryActivity.this,VideoViewerActivity.class);
													
			intent.setDataAndType(uri, mimeType);
			startActivity(intent);
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		  
		
	}
	
	
	public java.io.File exportToDisk (File fileIn)
	{
		java.io.File fileOut = null;
		
		try {
			
			fileOut = new java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),fileIn.getName());
			FileInputStream fis = new FileInputStream(fileIn);		
			java.io.FileOutputStream fos = new java.io.FileOutputStream(fileOut);
			
			byte[] b = new byte[4096];
			int len;
			while ((len = fis.read(b))!=-1)
			{
				fos.write(b, 0, len);
			}
			
			fis.close();
			fos.flush();
			fos.close();
			
		} catch (IOException e) {
			Log.d(TAG,"error exporting",e);
		}
		
		return fileOut;
		
	}

	class IconicList extends ArrayAdapter {

		public IconicList() {
			super(GalleryActivity.this, R.layout.row, items);

			// TODO Auto-generated constructor stub
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			LayoutInflater inflater = getLayoutInflater();
			View row = inflater.inflate(R.layout.row, null);
			TextView label = (TextView) row.findViewById(R.id.label);
			ImageView icon = (ImageView) row.findViewById(R.id.icon);
			
			File f = new File(path.get(position)); // get the file according the
													// position
			
			String mimeType = null;

			String[] tokens = f.getName().split("\\.(?=[^\\.]+$)");
			
			if (tokens.length > 1)
				mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.getName().split("\\.")[1]);
			
			if (mimeType == null)
				mimeType = "application/octet-stream";
			
			StringBuffer labelText = new StringBuffer();
			labelText.append(items[position]).append('\n');
			
			//TODO this lastModified is returning a strange value
			//Date dateMod = new Date(f.lastModified());
			//labelText.append("Modified: " ).append(dateMod.toGMTString()).append('\n');
			
			labelText.append("Size: ").append(f.length());
					
			label.setText(labelText.toString());
			
			if (f.isDirectory()) {
				icon.setImageResource(R.drawable.folder);
			} else if (mimeType.startsWith("image")){
				
				try
				{
					icon.setImageBitmap(getPreview(f));
				}
				catch (Exception e)
				{
					Log.d(TAG,"error showing thumbnail",e);
					icon.setImageResource(R.drawable.text);	
				}
			}
			else
			{
				icon.setImageResource(R.drawable.text);
			}
			
			return (row);
		}

	}

	private final static int THUMB_DIV = 8;
	
	private Bitmap getPreview(File fileImage) throws FileNotFoundException {

		
	    BitmapFactory.Options bounds = new BitmapFactory.Options();
	    
	    bounds.inJustDecodeBounds = true;
	    BitmapFactory.decodeStream(new BufferedInputStream(new FileInputStream(fileImage)), null, bounds);
	    
	    if ((bounds.outWidth == -1) || (bounds.outHeight == -1))
	        return null;

//	    opts.inSampleSize = 4;//originalSize / THUMBNAIL_SIZE;	 
	    
	    Bitmap b = BitmapFactory.decodeStream(new BufferedInputStream(new FileInputStream(fileImage)), null, null);
	    
	    return Bitmap.createScaledBitmap(b, bounds.outWidth/THUMB_DIV, bounds.outWidth/THUMB_DIV, false);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	
		if (requestCode == 1)
			getFileList(root);
	}
	
	
}
