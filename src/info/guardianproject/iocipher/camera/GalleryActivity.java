package info.guardianproject.iocipher.camera;

import info.guardianproject.cacheword.CacheWordHandler;
import info.guardianproject.cacheword.ICacheWordSubscriber;
import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.iocipher.FileOutputStream;
import info.guardianproject.iocipher.camera.io.IOCipherContentProvider;
import info.guardianproject.iocipher.camera.viewer.ImageViewerActivity;
import info.guardianproject.iocipher.camera.viewer.MjpegViewerActivity;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

public class GalleryActivity extends Activity  implements ICacheWordSubscriber {
	private final static String TAG = "FileBrowser";

	private List<String> item = null;
	private List<String> path = null;
	private String[] items;
	private java.io.File dbFile;
	private String root = "/";
	
	private GridView gridview;
	private HashMap<String,Bitmap> mBitCache = new HashMap<String,Bitmap>();
	
	private CacheWordHandler mCacheWord;
	
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
		
		gridview = (GridView) findViewById(R.id.gridview);

        mCacheWord = new CacheWordHandler(this, this);
        mCacheWord.connectToService(); 
        
	}

	protected void onResume() {
		super.onResume();
		
		if (!StorageManager.isStorageMounted())
		{
			goToLockScreen ();
		}
		else
		{
			mCacheWord.reattach();
			
		}
		
	}

	@Override
	public void onCacheWordLocked() {
	
		if (StorageManager.isStorageMounted())
		{
			//if storage is mounted, then we should lock it
			boolean unmounted = StorageManager.unmountStorage();
		}
		
		goToLockScreen ();
		
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

        getFileList(root);
	}

	@Override
	public void onCacheWordOpened() {
		
		//great!
        getFileList(root);
	}

	@Override
	public void onCacheWordUninitialized() {
		
		goToLockScreen ();
		
	}
	
	private void goToLockScreen ()
	{
		mCacheWord.disconnectFromService();
		Intent intent = new Intent(this,LockScreenActivity.class);
		startActivity(intent);
		finish();
	}

	@Override
	protected void onPause() {
		super.onPause();
		

		mCacheWord.reattach();
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
        	
        case R.id.menu_lock:
        	
        	if (StorageManager.isStorageMounted())
    		{
    			//if storage is mounted, then we should lock it
    			boolean unmounted = StorageManager.unmountStorage();
    		
    			if (unmounted)
    			{
    				mCacheWord.lock();
    			}
    			else
    			{
    				Toast.makeText(this, "Storage is busy... cannot lock yet.",Toast.LENGTH_LONG).show();
    			}
    		}
        	
        	
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

		for (int i = files.length-1; i >= 0; i--) {

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
		
		// declare array with specific number of items
		items = new String[item.size()];
		// send data arraylist(item) to array(items)
		item.toArray(items);
	    gridview.setAdapter(new IconicList());

	    gridview.setOnItemClickListener(new OnItemClickListener() {
	        public void onItemClick(AdapterView<?> parent, View v,
	                int position, long id) {
	    
					File file = new File(path.get(position));
					
					if (file.isDirectory()) {
							if (file.canRead()) {
								getFileList(path.get(position));
							} else {
								//show error
				
							}
					} else {
						showItem (file);
					}
	        }
					
	     });
	    
	    gridview.setOnItemLongClickListener(new OnItemLongClickListener () {

			@Override
			public boolean onItemLongClick(AdapterView<?> arg0, View arg1,
					int position, long arg3) {
				
				File file = new File(path.get(position));
				if (file.isDirectory()) {
					if (file.canRead()) {
						getFileList(path.get(position));
					} else {
						//show error
		
					}
				} else {
					showItemDialog (file);
				}
				
				return false;
			}
	    	
	    });
	    
	}
	
	private void showItemDialog (final File file)
	{
		
		new AlertDialog.Builder(GalleryActivity.this)
				.setIcon(R.drawable.ic_launcher)
				.setTitle("[" + file.getName() + "]")
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
								
								//Log.i(TAG,"open URL: " + Uri.parse(IOCipherContentProvider.FILES_URI + file.getName()));
								Uri uri = Uri.parse(IOCipherContentProvider.FILES_URI + file.getName());
								
								//java.io.File exportFile = exportToDisk(file);
								//Uri uriExport = Uri.fromFile(exportFile);
								
								Intent intent = new Intent(Intent.ACTION_SEND);
								
								String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
								String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension);
								if (fileExtension.equals("mp4")||fileExtension.equals("mkv")||fileExtension.equals("mov"))
									mimeType = "video/*";
								if (mimeType == null)
									mimeType = "application/octet-stream";
								
								intent.setDataAndType(uri, mimeType);
								intent.putExtra(Intent.EXTRA_STREAM, uri);
								intent.putExtra(Intent.EXTRA_SUBJECT, file.getName());
								intent.putExtra(Intent.EXTRA_TITLE, file.getName());
								
								try {
									startActivity(Intent.createChooser(intent, "Share this!"));
								} catch (ActivityNotFoundException e) {
									Log.e(TAG, "No relevant Activity found", e);
								}
							}
						}).show();
	}
	
	private void showItem (File file)
	{
		try {
			String fileExtension = MimeTypeMap.getFileExtensionFromUrl(file.getAbsolutePath());
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
				  
				  startActivity(intent);	
				
			}
			else {
			  Uri uri = Uri.parse(IOCipherContentProvider.FILES_URI + file.getName());
				
	          Intent intent = new Intent(Intent.ACTION_VIEW);													
			  intent.setDataAndType(uri, mimeType);
			  startActivity(intent);
			}
			 
			
		} catch (ActivityNotFoundException e) {
			Log.e(TAG, "No relevant Activity found", e);
		}
	}

	static class ViewHolder {
		  
		  ImageView icon;		  
		  
		}
	
	class IconicList extends ArrayAdapter<Object> {

		public IconicList() {
			super(GalleryActivity.this, R.layout.row, items);
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			LayoutInflater inflater = getLayoutInflater();
			
			ViewHolder holder = null;
			
			if (convertView == null)
				convertView = inflater.inflate(R.layout.gridsq, null);							
			else 
				holder = (ViewHolder)convertView.getTag();
			
			if (holder == null)
			{
				holder = new ViewHolder();
			
				holder.icon = (ImageView) convertView.findViewById(R.id.icon);

				holder.icon.setImageResource(R.drawable.text);
			}
			
			
			File f = new File(path.get(position)); // get the file according the
												// position
		
			String mimeType = null;

			String[] tokens = f.getName().split("\\.(?=[^\\.]+$)");
			
			if (tokens.length > 1)
				mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.getName().split("\\.")[1]);
			
			if (mimeType == null)
				mimeType = "application/octet-stream";
			
			if (f.isDirectory()) {
				holder.icon.setImageResource(R.drawable.folder);
			} else if (mimeType.startsWith("image")){
				
				try
				{
					Bitmap b = getPreview(f);
					if (b != null)
						holder.icon.setImageBitmap(b);
					else
						holder.icon.setImageResource(R.drawable.text);//placeholder while its loading
				}
				catch (Exception e)
				{
					Log.d(TAG,"error showing thumbnail",e);
					holder.icon.setImageResource(R.drawable.text);	
				}
			}			
			else if (mimeType.startsWith("audio")||f.getName().endsWith(".pcm")||f.getName().endsWith(".aac"))
			{
				holder.icon.setImageResource(R.drawable.audioclip);
			}
			else if (mimeType.startsWith("video")||f.getName().endsWith(".mp4")||f.getName().endsWith(".mov"))
			{
				holder.icon.setImageResource(R.drawable.videoclip);
			}
			else
			{
				holder.icon.setImageResource(R.drawable.text);
			}
				
			
			
			return (convertView);
		}

	}

	private Bitmap getPreview(File fileImage) throws FileNotFoundException {

		Bitmap b = mBitCache.get(fileImage.getAbsolutePath());
		
		if (b == null)	
			new BitmapWorkerTask().execute(fileImage);
		
		return b;
	}
	
	class BitmapWorkerTask extends AsyncTask<File, Void, Bitmap> {

	    // Decode image in background.
	    @Override
	    protected Bitmap doInBackground(File... fileImage) {
	        
	        BitmapFactory.Options bounds = new BitmapFactory.Options();	    
			bounds.inSampleSize = 8;	 	    
			Bitmap b;
			try {
				FileInputStream fis = new FileInputStream(fileImage[0]);
				b = BitmapFactory.decodeStream(fis, null, bounds);
				fis.close();
				mBitCache.put(fileImage[0].getAbsolutePath(), b);
				return b;
			} catch (Exception e) {
				Log.e(TAG,"error decoding bitmap preview",e);
			}
			
	        return null;
	        
	    }

	    // Once complete, see if ImageView is still around and set bitmap.
	    @Override
	    protected void onPostExecute(Bitmap bitmap) {	    	
	    	((IconicList)gridview.getAdapter()).notifyDataSetChanged();
			
	    }
	}


	
}
