package info.guardianproject.iocipherexample;

// inspired by https://github.com/commonsguy/cw-omnibus/tree/master/ContentProvider/Pipe

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;
import android.util.Log;
import android.webkit.MimeTypeMap;

public class IOCipherContentProvider extends ContentProvider {
	public static final String TAG = "IOCipherContentProvider";
	public static final Uri FILES_URI = Uri
			.parse("content://info.guardianproject.iocipherexample/");
	private MimeTypeMap mimeTypeMap;

	@Override
	public boolean onCreate() {
		mimeTypeMap = MimeTypeMap.getSingleton();
		return true;
	}

	@Override
	public String getType(Uri uri) {
		String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
		return mimeTypeMap.getMimeTypeFromExtension(fileExtension);
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode)
			throws FileNotFoundException {
		ParcelFileDescriptor[] pipe = null;
		BufferedInputStream in = null;

		try {
			pipe = ParcelFileDescriptor.createPipe();
			in = new BufferedInputStream(new FileInputStream(new File("/test.pdf")));
			new PipeFeederThread(in,
					new AutoCloseOutputStream(pipe[1])).start();
		} catch (IOException e) {
			Log.e(TAG, "Error opening pipe", e);
			throw new FileNotFoundException("Could not open pipe for: "
					+ uri.toString());
		}

		return (pipe[0]);
	}

	@Override
	public Cursor query(Uri url, String[] projection, String selection,
			String[] selectionArgs, String sort) {
		throw new RuntimeException("Operation not supported");
	}

	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {
		throw new RuntimeException("Operation not supported");
	}

	@Override
	public int update(Uri uri, ContentValues values, String where,
			String[] whereArgs) {
		throw new RuntimeException("Operation not supported");
	}

	@Override
	public int delete(Uri uri, String where, String[] whereArgs) {
		throw new RuntimeException("Operation not supported");
	}

	static class PipeFeederThread extends Thread {
		InputStream in;
		OutputStream out;

		PipeFeederThread(InputStream in, OutputStream out) {
			this.in = in;
			this.out = out;
		}

		@Override
		public void run() {
			byte[] buf = new byte[8192];
			int len;

			try {
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
				}

				in.close();
				out.flush();
				out.close();
			} catch (IOException e) {
				Log.e(TAG, "File transfer failed:", e);
			}
		}
	}
}
