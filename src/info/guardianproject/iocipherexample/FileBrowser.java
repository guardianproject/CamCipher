package info.guardianproject.iocipherexample;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;



public class FileBrowser extends ListActivity {

	private List<String> item = null;
	private List<String> path = null;
	private TextView fileInfo;
	private String[] items;
	private String root = "/";

	/** Called when the activity is first created. */
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        fileInfo = (TextView)findViewById(R.id.info);
        getFileList(root);
    }
    
    //To make listview for the list of file
    public void getFileList(String dirPath){
    	
    	item = new ArrayList<String>(); //Declare as Array list
    	path = new ArrayList<String>();
    	
 
    	
    	File file = new File(dirPath); // get the file
    	File[] files = file.listFiles(); //get the list array of file
    	
    	if(!dirPath.equals(root)){
    		item.add(root); 
    		path.add(root);// to get back to main list
    		
    		item.add("..");
    		path.add(file.getParent()); // back one level 
    	}
    	
    	for (int i = 0; i < files.length; i++){
    		
    		File fileItem = files[i];
    		path.add(fileItem.getPath());
    		if(fileItem.isDirectory()){
    			item.add("["+fileItem.getName()+"]"); // input name directory to array list
    		}
    		else {
    			item.add(fileItem.getName()); // input name file to array list
    		}
    	}
    	fileInfo.setText("Info: "+dirPath+" [ " +files.length +" item ]");
    	items = new String[item.size()]; //declare array with specific number off item
    	item.toArray(items); //send data arraylist(item) to array(items
    	setListAdapter(new IconicList()); //set the list with icon
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id){
    	File file = new File(path.get(position));
    	if(file.isDirectory()){ 
    		if(file.canRead()){
    			getFileList(path.get(position));
    		}
    		else {
    			new AlertDialog.Builder(this)
    			.setIcon(R.drawable.icon).setTitle("["+file.getName()+"] folder can't be read")
    			.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					
					//@Override
					public void onClick(DialogInterface dialog, int which) {
						// TODO Auto-generated method stub
						
					}
				}).show();
    			
    		}
    	}
    	else {
    		new AlertDialog.Builder(this)
    		.setIcon(R.drawable.icon)
    		.setTitle("["+file.getName()+"]")
    		.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				
				//@Override
				public void onClick(DialogInterface dialog, int which) {
					// TODO Auto-generated method stub
					
				}
			}).show();
    	}
    }
    
    class IconicList extends ArrayAdapter {

		
		public IconicList() {
			super(FileBrowser.this, R.layout.row, items);

			// TODO Auto-generated constructor stub
		}
		
		public View getView(int position, View convertView, ViewGroup parent){
			LayoutInflater inflater = getLayoutInflater(); //to instantiate layout XML file into its corresponding View objects
			View row = inflater.inflate(R.layout.row, null); //to Quick access to the LayoutInflater  instance that this Window retrieved from its Context.
			TextView label = (TextView)row.findViewById(R.id.label); //access the textview for the name file
			ImageView icon = (ImageView)row.findViewById(R.id.icon);//access the imageview for the icon list
			label.setText(items[position]);
			File f = new File(path.get(position)); //get the file according the position
			if(f.isDirectory()){ //decide are the file folder or file
				icon.setImageResource(R.drawable.folder);
			}
			else {
				icon.setImageResource(R.drawable.text);
			}
			return(row);
		}
		
		
    	
    }
}