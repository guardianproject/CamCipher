package info.guardianproject.iocipher.camera;

import info.guardianproject.iocipher.VirtualFileSystem;

import java.io.File;

import android.content.Context;

public class StorageManager {

	private final static String DEFAULT_PATH = "gallery.db";
	
	public static boolean isStorageMounted ()
	{
		return VirtualFileSystem.get().isMounted();
	}
	
	public static void unmountStorage ()
	{
		VirtualFileSystem.get().unmount();
	}
	
	public static boolean mountStorage (Context context, String storagePath, byte[] passphrase)
	{
		File dbFile = null;
		
		if (storagePath == null)
		{
			dbFile = new java.io.File(context.getDir("vfs", Context.MODE_PRIVATE),DEFAULT_PATH);
		}
		else
		{
			dbFile = new java.io.File(storagePath);
		}
		dbFile.getParentFile().mkdirs();
		
		if (!dbFile.exists())
			VirtualFileSystem.get().createNewContainer(dbFile.getAbsolutePath(), passphrase);
		

		if (!VirtualFileSystem.get().isMounted())
		{
			// TODO don't use a hard-coded password! prompt for the password
			VirtualFileSystem.get().mount(dbFile.getAbsolutePath(),passphrase);
		}
		
		
		return true;
	}
}
