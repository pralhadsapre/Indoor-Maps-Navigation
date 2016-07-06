package comp.inmaps.gui;


import java.io.IOException;

import org.xmlpull.v1.XmlPullParserException;

import comp.inmaps.graph.Graph;
import comp.inmaps.graph.GraphNode;
import comp.inmaps.graph.LatLonPos;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources.NotFoundException;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;
import comp.inmaps.R;
//PRALHAD CHANGEimport de.uvwxy.inMAPS.Rev;

/**
 * 
 * @author 	Pralhad Sapre 
 * 			Sanket Shetye
 *
 */

public class Loader extends Activity {

	public static final String LOADER_SETTINGS = "inMAPSSettings";
	
	// GRAPH
	private static Graph g;					// Holds the data structure
	private String nodeFrom;				// Node name to start from, i.e. "5052"
	private int closestNodeID;				// Node ID if closest node was found via GPS
	private String nodeTo;					// Node name to navigate to
	private int iNodeFrom = 0;				// Selected index from spFrom
	private int iNodeTo = 0;				// Selected index from spTo
	private String[] rooms = null;			// Array of all room names added to drop down lists
	private LocationManager locationManager;
	
	// GUI
	private Spinner spFrom = null;			// Drop down lists
	private Spinner spTo = null;
	private Button bGo = null;				// Buttons
	private Button bLoad = null;
	private Button bSave = null;
	private Button bCalibrate = null;

	private EditText et01 = null;			// EditText, body height
//et01 WILL HELP US TO INPUT THE PERSON'S HEIGHT.. IN CENTIMETERS
	private CheckBox cbStairs = null;		// Check boxes, for route selection mode
	private CheckBox cbElevator = null;
	private CheckBox cbOutside = null;
	private CheckBox cbLog = null;
	private CheckBox cbAudio = null;
	private ArrayAdapter<String> adapter1 = null;	// Adapter to manage drop down lists
	private ArrayAdapter<String> adapter2 = null;
	
	// LISTENERS
//FOR LISTENING TO DROP DOWN MENU'S EVENTS
	OnItemSelectedListener spinnerListener = new OnItemSelectedListener() {
		//PRALHAD CHANGE@Override
		public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
			// List from: save selected name and position in list
			if (parent.equals(spFrom)) {
				nodeFrom = (String) spFrom.getSelectedItem();
				iNodeFrom = position;
			}
			// List from: save selected name and position in list
			if (parent.equals(spTo)) {				
				nodeTo = (String) spTo.getSelectedItem();
				iNodeTo = position;
			}

		}

		//PRALHAD CHANGE@Override
		public void onNothingSelected(AdapterView<?> arg0) {
			shortToast("You have selected nothing");
		}
	};
	
	OnClickListener onListener = new OnClickListener() {

		//PRALHAD CHANGE@Override
		public void onClick(View v) {
			// Distinguish which button was pressed
			if (v.equals(bGo)) {
				if(nodeFrom.equals(nodeTo)){
					shortToast("Please.... " + nodeFrom + " to " + nodeTo + "?");
					return;
				}
				startNavigation();	
//THIS METHOD FIRES MANY INTENTS TO THE NAVIGATION ACTIVITY FOR MANY PARAMETERS SELECTED

			} else if(v.equals(bLoad)){
				// Load values from settings, second parameter is passed if value is not found
				int tNodeFrom 	 = 	getSharedPreferences(LOADER_SETTINGS,0).getInt("nodeFrom", 		0);
				int tNodeTo 	 = 	getSharedPreferences(LOADER_SETTINGS,0).getInt("nodeTo", 		0);
				float sizeIncm 	 = 	getSharedPreferences(LOADER_SETTINGS,0).getFloat("sizeIncm",	191.0f);
				boolean stairs 	 = 	getSharedPreferences(LOADER_SETTINGS,0).getBoolean("stairs",	true);
				boolean elevator = 	getSharedPreferences(LOADER_SETTINGS,0).getBoolean("elevator",	true);
				boolean outside  = 	getSharedPreferences(LOADER_SETTINGS,0).getBoolean("outside",	true);
				// Update GUI elements corresponding to variables
				cbStairs.setChecked(stairs);
				cbElevator.setChecked(elevator);
				cbOutside.setChecked(outside);					
				spFrom.setSelection(tNodeFrom, true);
				spTo.setSelection(tNodeTo, true);
				et01.setText("" + sizeIncm);
			} else if(v.equals(bSave)){
				// Save current values to settings
				SharedPreferences settings = getSharedPreferences(LOADER_SETTINGS, 0);
			    SharedPreferences.Editor editor = settings.edit();
			    editor.putInt("nodeFrom", 		iNodeFrom);
			    editor.putInt("nodeTo", 		iNodeTo);
			    editor.putFloat("sizeIncm", 	Float.parseFloat(et01.getText().toString()));
			    editor.putBoolean("stairs", 	cbStairs.isChecked());
			    editor.putBoolean("elevator", 	cbElevator.isChecked());
			    editor.putBoolean("outside", 	cbOutside.isChecked());
			    // Apply changes
			    editor.commit();
			} else if(v.equals(bCalibrate)){
				Intent intenCalibrator = new Intent(Loader.this, Calibrator.class);
				startActivityForResult(intenCalibrator, 2);
			}
		}
	};
	
	
	
	
	// Navigator needs static access to graph
	public static Graph getGraph(){
		return g;
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.selectroom);
		setTitle("in-MAPS");
		// Create new graph
		g = new Graph();
		// And add layer(s) of ways
		try {
//THE MAIN PART WHERE ALL THE .osm DATA FILES ARE IMPORTED INTO THE DATA STRUCTURES OF THE PROGRAM...
//ALL THE FUNCTION CALLS HERE ARE EXTREMELY IMPORTANT
			g.addToGraphFromXMLResourceParser(this.getResources().getXml(R.xml.finallevel0));
			g.addToGraphFromXMLResourceParser(this.getResources().getXml(R.xml.finallevel1));
			g.addToGraphFromXMLResourceParser(this.getResources().getXml(R.xml.finallevel2));
			
//			g.addToGraphFromXMLResourceParser(this.getResources().getXml(R.xml.parkinglot));
			g.mergeNodes();
//GETTING THE ROOM'S LIST FOR POPULATING "FROM" AND "TO" DROP DOWN BOXES
			rooms = g.getRoomList();
		} catch (NotFoundException e) {
			longToast("Error: resource not found:\n\n" + e);
		} catch (XmlPullParserException e) {
			longToast("Error: xml error:\n\n" + e);
		} catch (IOException e) {
			longToast("Error: io error:\n\n" + e);
		}
		
		// GUI - Create references to elements on the screen
		spFrom 		= (Spinner)  findViewById(R.id.Spinner01);
		spTo 		= (Spinner)  findViewById(R.id.Spinner02);
		bGo 		= (Button)   findViewById(R.id.btnGo);
		bLoad	 	= (Button)   findViewById(R.id.btnLoad);
		bSave 		= (Button)   findViewById(R.id.btnSave);
		bCalibrate 	= (Button) 	 findViewById(R.id.btnCalibrate);
		
		et01 		= (EditText) findViewById(R.id.EditText01);
		cbStairs	= (CheckBox) findViewById(R.id.cbStairs);
		cbElevator 	= (CheckBox) findViewById(R.id.cbElevators);
		cbOutside 	= new CheckBox(this);
		cbLog		= (CheckBox) findViewById(R.id.cbLog);
		cbAudio		= new CheckBox(this);
		//PRALHAD CHANGEthis.setTitle("inMAPS r(" + Rev.rev.substring(0,8) + ")");
		
		// Drop down lists: create entries of room names from rooms
		adapter1 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, rooms);
		adapter1.setDropDownViewResource(android.R.layout.simple_spinner_item);
		adapter2 = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, rooms);
		adapter2.setDropDownViewResource(android.R.layout.simple_spinner_item);
	
		spFrom.setAdapter(adapter1);
		spTo.setAdapter(adapter2);
				
		// Set select/click listeners
		spFrom.setOnItemSelectedListener(spinnerListener);
		spTo.setOnItemSelectedListener(spinnerListener);
		bGo.setOnClickListener(onListener);
		bLoad.setOnClickListener(onListener);
		bSave.setOnClickListener(onListener);
		bCalibrate.setOnClickListener(onListener);
		
		
		locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
	}

	@Override
	public void onResume() {
		super.onResume();
	}
	
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		// Source: http://code.google.com/p/zxing/wiki/ScanningViaIntent
		
		Log.i("inMAPS", "requestCode = " + requestCode);
		Log.i("inMAPS", "resultCode = " + resultCode);
		
	    if (requestCode == 0) {
	        if (resultCode == RESULT_OK) {
	            String contents = intent.getStringExtra("SCAN_RESULT");
	            String format = intent.getStringExtra("SCAN_RESULT_FORMAT");

	            // Handle successful scan
	            // FORMAT: "http://......?....&fN=<roomname>&tN=<roomname>&.....
	            
	            if(!format.equals("QR_CODE")){
	            	this.longToast("Scan result was not a QR Code");
	            	return;
	            }
	            
	            String[] split = contents.split("\\?");
	            
	            if(split.length!=2){
	            	this.longToast("URL: " + contents + "\n\n is in the wrong format!");
	            	return;
	            }
	            
	            int progress = 0;
	            String sVarString = split[1];
	            String[] sVars = sVarString.split("\\&");
	            
	            for(String s : sVars){
	            	if(s.split("=")[0].equals("fN")){
	            		this.nodeFrom = s.split("=")[1];
	            		progress++;
	            	} else if(s.split("=")[0].equals("tN")){
	            		this.nodeTo = s.split("=")[1];
	            		progress++;
	            	}
	            }
	            
	            for(int i = 0; i < rooms.length; i++){
	            	if(rooms[i].equals(nodeFrom)){
	            		this.spFrom.setSelection(i);
	            	}
	            	if(rooms[i].equals(nodeTo)){
	            		this.spTo.setSelection(i);
	            	}
	            }
	            
	            if(progress==2){
	            	if(nodeFrom.equals(nodeTo)){
	            		longToast("Even if the URL was correct, you will not be going far from " + nodeFrom + " to " + nodeTo);
	            	}
	            	longToast("Starting navigation from " + nodeFrom + " to " + nodeTo);
	            	startNavigation();
	            }
	        } else if (resultCode == RESULT_CANCELED) {
	            // Handle cancel
	        }
	    } else if (requestCode == 1){
	    	if(resultCode == RESULT_CANCELED){
	    		longToast("No Route found!");
	    	}
	    }
	}
	
	@Override
	public void onPause() {
		super.onPause();
	}

	private void shortToast(String s) {
		Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
	}

	private void longToast(String s) {
		Toast.makeText(this, s, Toast.LENGTH_LONG).show();
	}

	
	private void startNavigation(){
		Log.i("inMAPS", "Starting navigation intent");
		// Create intent for navigation
		Intent intentNavigator = new Intent(Loader.this, Navigator.class);
		// Add values to be passed to navigator
		intentNavigator.putExtra("from",		nodeFrom);
		intentNavigator.putExtra("fromId",		closestNodeID);
		intentNavigator.putExtra("to",			nodeTo);
		intentNavigator.putExtra("stairs",		cbStairs.isChecked());
		intentNavigator.putExtra("elevator",	cbElevator.isChecked());
		intentNavigator.putExtra("outside",		cbOutside.isChecked());
		intentNavigator.putExtra("log", 		cbLog.isChecked());
		intentNavigator.putExtra("audio",		cbAudio.isChecked());
		// Source: http://www.pedometersaustralia.com/g/13868/measure-step-length-.html
		intentNavigator.putExtra("stepLength", 	Float.parseFloat(et01.getText().toString()) * 0.415f);
		// Start intent for navigation
		Log.i("inMAPS", "Starting the navigation module");
		startActivityForResult(intentNavigator, 1);
	}
}