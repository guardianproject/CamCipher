package info.guardianproject.iocipherexample;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.iocipher.FileOutputStream;
import info.guardianproject.iocipher.VirtualFileSystem;

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
import android.media.MediaScannerConnection;
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

public class FileBrowser extends ListActivity {
	private final static String TAG = "FileBrowser";

	private List<String> item = null;
	private List<String> path = null;
	private TextView fileInfo;
	private String[] items;
	private String dbFile;
	private String root = "/";
	private VirtualFileSystem vfs;

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
		dbFile = getDir("vfs", MODE_PRIVATE).getAbsolutePath() + "/myfiles.db";
	}

	protected void onResume() {
		super.onResume();
		vfs = new VirtualFileSystem(dbFile);
		// TODO don't use a hard-coded password! prompt for the password
		vfs.mount("my fake password");
		getFileList(root);
	}

	protected void onDestroy() {
		super.onDestroy();
		try
		{
			vfs.mount("XXXXXXXXXXXXXXX"); //this ensures the old password is cleared
		}catch(IllegalArgumentException iae){}
		//vfs.unmount();
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        
        return true;
	}
	

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

        case R.id.menu_camera:
        	
        	Intent intent = new Intent(this,SecureSelfieActivity.class);
        	intent.putExtra("basepath", "/");
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
								if (mimeType == null)
									mimeType = "application/octet-stream";
								
								scanMediaFile(uri, mimeType);
								
								
							} catch (ActivityNotFoundException e) {
								Log.e(TAG, "No relevant Activity found", e);
							}
						}
					})
					.setPositiveButton("Share...",
							new DialogInterface.OnClickListener() {

								// @Override
								public void onClick(DialogInterface dialog,
										int which) {
									Intent intent = new Intent(Intent.ACTION_SEND);
									
									String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
									String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension);
									if (mimeType == null)
										mimeType = "application/octet-stream";
									
									intent.setDataAndType(uri, mimeType);
									intent.putExtra(Intent.EXTRA_STREAM, uri);
									intent.putExtra(Intent.EXTRA_TITLE, file.getName());
									intent.putExtra(Intent.EXTRA_SUBJECT, "shared from IOCipher");
									
									try {
										startActivity(Intent.createChooser(intent, "Share this!"));
									} catch (ActivityNotFoundException e) {
										Log.e(TAG, "No relevant Activity found", e);
									}
								}
							}).show();
		}
		
	}
	
	private void scanMediaFile (final Uri uri, final String mimeType)
	{
		MediaScannerConnection.scanFile(this, new String[] { uri.toString() }, new String[] { mimeType },
		          new MediaScannerConnection.OnScanCompletedListener() {
			
			
			
		      public void onScanCompleted(String path, Uri uriPost) {
		          Log.i(TAG, "Scanned " + path + ":");
		          Log.i(TAG, "-> uri=" + uri);
		          
		          Intent intent = new Intent(Intent.ACTION_VIEW);													
					
					intent.setType(mimeType);
					intent.setData(uri);
					
					startActivity(intent);
		      }
		 });
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
			super(FileBrowser.this, R.layout.row, items);

			// TODO Auto-generated constructor stub
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			LayoutInflater inflater = getLayoutInflater();
			View row = inflater.inflate(R.layout.row, null);
			TextView label = (TextView) row.findViewById(R.id.label);
			ImageView icon = (ImageView) row.findViewById(R.id.icon);
			label.setText(items[position]);
			File f = new File(path.get(position)); // get the file according the
													// position
			if (f.isDirectory()) {
				icon.setImageResource(R.drawable.folder);
			} else {
				
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
