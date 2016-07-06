package comp.inmaps.gui;


import java.io.IOException;
import java.util.LinkedList;
import java.util.Stack;

import comp.inmaps.ToolBox;
import comp.inmaps.core.NPConfig;
import comp.inmaps.core.Positioner;
import comp.inmaps.core.Positioner_OnlineBestFit;
import comp.inmaps.core.Positioner_OnlineFirstFit;
import comp.inmaps.core.StepDetection;
import comp.inmaps.core.StepTrigger;
import comp.inmaps.graph.Graph;
import comp.inmaps.graph.GraphEdge;
import comp.inmaps.graph.GraphNode;
import comp.inmaps.graph.LatLonPos;
import comp.inmaps.log.AudioWriter;
import comp.inmaps.log.DataLogger;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import comp.inmaps.R;
/**
 * 
 * @author 	Pralhad Sapre 
 * 			Sanket Shetye
 *
 */
public class Navigator extends Activity implements StepTrigger {
	public static final String LOG_DIR = "routelog/";
	
	// #########################################################################
	// ######################### Fields 'n Listener ############################
	// #########################################################################
	
	// GUI Elements
	private PaintBoxMap pbMap;							// Objects to handle the graphics
	private Button btnRecalc;
	private Button btnSwitchFit;
	private Graph g;									// Reference to graph
	
	
	// Route information
	private String nodeFrom;							// Node we will start from, i.e. "5052"
	private int nodeFromId = 0;							// This is used if we choose nearest location from GPS fix
	private String nodeTo;								// Node we plan to end up with
	private boolean staircase;
	private boolean elevator;
	private boolean outside;
	private LinkedList<GraphEdge> navPathEdges;			// Contains path with corrected compass bearings
														// used by PaintBoxMap to paint the path
	private LinkedList<GraphEdge> simplifiedEdges;
	private LinkedList<GraphEdge> tempEdges;			// Stores the original edges on path
														// Needs to be global: is used for logging in onResume()
	private double navPathLen = 0.0;					// Total length of path
	private double naiveStairsWidth = 0.25;				// Naive amount in meters to use as stairs step length
	
	
	// Navigation
	private double acceptanceWidth = 42.0;				// Amount of deviation allowed for compassValue to path
	private StepDetection stepDetection;
	private Positioner posBestFit = null; 				// Object to do another progress estimation
	private Positioner posFirstFit = null;
	private NPConfig confBestFit = null;
	private NPConfig confFirstFit = null;
	private NPConfig conf = null;
	
	// Progress information
	private boolean isNavigating = false;				// Set to false when nodeTo is reached
	private int totalStepsWalked = 0;					// Total number of detected steps
	
	
	// Runtime information
	private double compassValue = -1.0;
	
	private LinkedList<Double> zVarHistory = new LinkedList<Double>();  // store the variance of each step
	private int historySize = 64;						// Back log of last 64 values to calculate variance
	private double[] x_History = new double[historySize];
	private double[] y_History = new double[historySize];
	private double[] z_History = new double[historySize];
	private int historyPtr = 0;
		
	
	// Logging
	private DataLogger logger;
	private boolean log = false;
	private boolean logAudio = false;
	private AudioWriter avwCapture;

	
	// Listeners
	OnClickListener onClick = new OnClickListener(){

		//PRALHAD CHANGE@Override
		public void onClick(View arg0) {
			if(arg0.equals(btnRecalc)){
				if(true){
					((Positioner_OnlineFirstFit) posFirstFit).recalcPos();
				}
				
			} else if (arg0.equals(btnSwitchFit)){
				if ( btnSwitchFit.getText().equals("Switch to First Fit Algorithm")){
					btnSwitchFit.setText("Switch to Best Fit Algorithm");
					conf = confFirstFit;
				} else {
					btnSwitchFit.setText("Switch to First Fit Algorithm");
					conf = confBestFit;
				}
			}
			
		}
		
	};
	
	// #########################################################################
	// ######################### Getters 'n Setters ############################
	// #########################################################################

	public LinkedList<GraphEdge> getNavPathEdges(){
		return navPathEdges;
	}
	public double getAcceptanceWidth() {
		return this.acceptanceWidth;
	}
	
	// last compass value
	public double getCompassValue() {
		return compassValue;
	}

//npPointer WILL BE POINTING TO THE CURRENT EDGE OF THE PATH...
//THAT MEANS IT WILL GIVE THE EDGE'S PLACE IN PATH/LINKED LIST
	public GraphEdge getCurrentEdge(NPConfig conf){
		if(conf.npPointer >= navPathEdges.size()){
			return navPathEdges.get(navPathEdges.size()-1);
		}
		GraphEdge ret = navPathEdges.get(conf.npPointer);
		return ret;
	}

//AN EXTRA METHOD TO DECIDE THE FLOOR WE ARE ON
	public float getLevel(){
		if(conf.npPointer >= navPathEdges.size()){
			return navPathEdges.get(navPathEdges.size()-1).getLevel();
		}
		return navPathEdges.get(conf.npPointer).getLevel();
	}
	
	public float getCurrentFloorLevel() {
		return getLastSeenNode(conf).getLevel();
	}
	
	public double getEstimatedStepLength(){
		double pathLength = getNavPathWalked();
		double totalSteps = getTotalStepsWalked();
		for(int i = 0; i < conf.npPointer; i++){
			GraphEdge edge = navPathEdges.get(i);
			if(edge.isStairs()){
				if(edge.getSteps()==-1){
					pathLength -= edge.getLen();
					totalSteps -= edge.getLen()/naiveStairsWidth; 
				} else if(edge.getSteps() > 0){
					pathLength -= edge.getLen();
					totalSteps -= edge.getSteps();
				}
			}
		}
		
		return pathLength/totalSteps;
		
	}
	
	public double getNaiveStairsWidth(){
		return naiveStairsWidth;
	}
	
	public GraphNode getLastSeenNode(NPConfig conf){
		GraphEdge currentEdge = getCurrentEdge(conf);
		GraphEdge previousEdge = getPreviousEdge(conf);
		if(previousEdge == null){ // no previous edge, thus this is the first edge
			return getRouteBegin();
		}
		// last seen node is node which is in current and previous edge
		GraphNode ret = currentEdge.getNode0();
		if(!previousEdge.contains(ret)){
			ret = currentEdge.getNode1();
		}
		return ret;
	}
	
//AN EXTRA METHOD TO GET THE NEAREST NODE
	public GraphNode nearby() {
		GraphNode ret, n0, n1;
		GraphEdge e;
		e = getCurrentEdge(conf);
		n0 = e.getNode0();
		n1 = e.getNode1();
		
		if(g.getDistance(getPosition(), n0) <= g.getDistance(getPosition(), n1))
			ret = n1;
		else
			ret = n0;
		
		return ret;
	}
//EXTRA METHOD TO SHOW THE NEARBY NODE TO A CURRENT POSITION
	public GraphNode getNearbyNode(LatLonPos pos){
		double minDistance = Double.MAX_VALUE;
		double tempDistance = Double.MAX_VALUE;
			
		GraphNode n = new GraphNode();
		for(GraphNode node: g.getRoomNodes()){
			// First: node has to be at the same level
			// Second: if indoor = true, then take all nodes
			// Third: if indoor = false check if node is not indoors!
			if(node.getLevel() == pos.getLevel()){
//Log.w("node", node.getName());
				tempDistance = g.getDistance(pos, node);
				if(tempDistance < minDistance){
					minDistance = tempDistance;
					n = node;
//Log.w("node considered", node.getName());
				}
			}
		}
		return n;
	}
	
	public double getNavPathLen() {
		return navPathLen;
	}
	
	public double getNavPathLenLeft(){
		return navPathLen - getNavPathWalked();
	}

	public double getNavPathDir() {
		if(conf.npPointer >= navPathEdges.size()){
			return navPathEdges.get(navPathEdges.size()-1).getCompDir();
		}
		return navPathEdges.get(conf.npPointer).getCompDir();
	}

	// returns remaining meters on edge
	public double getNavPathEdgeLenLeft() {
		// catch route end, return -1.0
		if (conf.npPointer >= navPathEdges.size())
			return -1.0;
//THE RETURNING OF -1.0 INDICATES WE HAVE TRAVELLED ALL EDGES ON THE PATH
		return navPathEdges.get(conf.npPointer).getLen() - conf.npCurLen;
	}

	
//FOR THIS METHOD TO WORK.. THERE MUST BE A NECESSITY THAT ALL EDGES IN navPathEdges MUST BE STORED SEQUENTIALLY FROM
//SOURCE TO DESTINATION
	// return show far we have walked on the path
	public double getNavPathWalked(){
		double len = 0.0;
		// sum all traversed edges
		for(int i = 0; i < conf.npPointer; i++){
			len += navPathEdges.get(i).getLen();
		}
		// and how far we have walked on current edge
		len += conf.npCurLen;
		return len;
	}
	
	public LatLonPos getPosition() {
		return getPosition(conf);
	}

	// estimated(?) position of user
	public LatLonPos getPosition(NPConfig conf) {
		LatLonPos ret = new LatLonPos();
		GraphNode lastSeenNode = getLastSeenNode(conf);
		GraphEdge currentEdge = getCurrentEdge(conf);
//THE NEXT NODE CAN BE NODE0 WHEN TRAVERSING IN THE NATURAL ORDER OR NODE1 FOR REVERSE ORDER
		GraphNode nextNode = currentEdge.getNode0().equals(lastSeenNode)? currentEdge.getNode1() : currentEdge.getNode0();
		
		// catch route end, return destination
		if (conf.npPointer >= navPathEdges.size()) {
			GraphNode lastNode = this.getRouteEnd();
			ret.setLat(lastNode.getLat());
			ret.setLon(lastNode.getLon());
			ret.setLevel(lastNode.getLevel());
			return ret;
		}
		
		ret.setLevel(lastSeenNode.getLevel());
		ret.setLat(lastSeenNode.getLat());
		ret.setLon(lastSeenNode.getLon());
		
		// move pos into direction; amount of traveled m on edge
		ret.moveIntoDirection(nextNode.getPos(), conf.npCurLen/navPathEdges.get(conf.npPointer).getLen());
		return ret;
//THUS THIS FUNCTION WILL GIVE US THE LAT AND LON OF THE NEAREST NODE... ADDING TO IT THE AMOUNT WE HAVE TRAVELLED 
//ON THE CURRENT EDGE... GIVEN BY ABOVE FUNCTION CALL
	}

	public GraphEdge getPreviousEdge(NPConfig conf){
		if(conf.npPointer == 0){
			// no previous edge
			return null;
		}
		return navPathEdges.get(conf.npPointer - 1);
	}

	public GraphNode getRouteBegin() {
		if(nodeFromId == 0){
			return g.getNodeFromName(nodeFrom);
		} else {
			return g.getNode(nodeFromId);
		}
	}

	public GraphNode getRouteEnd() {
		return g.getNodeFromName(nodeTo);
	}

	// length of each step
	public double getStepLengthInMeters() {
		return conf.npStepSize;
	}

	// total steps walked
	public int getTotalStepsWalked() {
		return totalStepsWalked;
	}

	// total steps not roughly in correct direction
	public int getUnmatchedSteps() {
		return conf.npUnmatchedSteps;
	}
	
	// are we navigating?
	public boolean isNavigating() {
		return isNavigating;
	}
	
	/**
	 * Returns the variance from the back log for x values
	 * @return the variance for x
	 */
	public double getVarianceOfX(){
		return varianceOfSet(x_History);
	}
	/**
	 * Returns the variance from the back log for y values
	 * @return the variance for y
	 */
	public double getVarianceOfY(){
		return varianceOfSet(y_History);
	}
	/**
	 * Returns the variance from the back log for z values
	 * @return the variance for z
	 */
	public double getVarianceOfZ(){
		return varianceOfSet(z_History);
	}
	
	public void setNavigating(boolean b){
		this.isNavigating = b;
	}

	// #########################################################################
	// ########################## Step/Data Callbacks ##########################
	// #########################################################################
	
	//PRALHAD CHANGE@Override
	public void trigger(long now_ms, double compDir) {
		this.totalStepsWalked++;
		if (!isNavigating) {
			// Destination was reached
			return;
		}
		
		if(log){
			logger.logStep(now_ms, compDir);
		}
		
//addStep IS WHERE ALL THE MAPMATCHING MYSTERY WILL UNFOLD		
		
		posBestFit.addStep(compDir);
		posFirstFit.addStep(compDir);
		
//YOU MAY CONSIDER REMOVING THESE LOG STATEMENTS.. ESPECIALLY THE NAME inMAPS		
		Log.i("inMAPS", "posBestFit: " + posBestFit.getProgress());
		Log.i("inMAPS", "posFirstFit: " + posFirstFit.getProgress());
		if(log){
			// Write location to file after detected step
			LatLonPos bestPos = getPosition(confBestFit);
			LatLonPos firstPos = getPosition(confFirstFit);
			logger.logPosition(now_ms, bestPos.getLat(), bestPos.getLon(), posBestFit.getProgress()/this.navPathLen
					, firstPos.getLat(), firstPos.getLon(), posFirstFit.getProgress()/this.navPathLen);
//navPathLen MUST BE THE TOTAL LENGTH OF PATH FROM SOURCE TO DESTINATION
		}
	}
	
	//PRALHAD CHANGE@Override
//FOR ACCELEROMETER
	public void dataHookAcc(long now_ms, double x, double y, double z){
		// add values to history (for variance)
		addTriple(x, y, z);
		if(log){
			logger.logRawAcc(now_ms, x, y, z);
		}
	}
	
	//PRALHAD CHANGE@Override
//FOR COMPASS
	public void dataHookComp(long now_ms, double x, double y, double z){
		if(log){
			logger.logRawCompass(now_ms, x, y, z);
		}
		compassValue = ToolBox.lowpassFilter(compassValue,  x, 0.5);
//THIS IS WHERE THE compassValue VARIABLE IS UPDATED TO HOLD THE CURRENT COMPASS VALUE
//THE ARGUMENT compassValue TO FUNCTION WILL SERVE AS OLDVALUE
	}
	
	//PRALHAD CHANGE@Override
	public void timedDataHook(long now_ms, double[] acc, double[] comp){
		double varZ = getVarianceOfZ();
		zVarHistory.add(new Double(acc[2]));
		
		if(log){
			logger.logTimedVariance(now_ms, varZ);
		}
		if(log){
			// Write Compass and Accelerometer data
			logger.logTimedAcc(now_ms, acc[2]);
			logger.logTimedCompass(now_ms, comp[0]);
		}
	}
//TO SPECIFY THIS AGAIN ... WE TAKE THE Z AXIS READING FOR ACCELEROMETER AND X AXIS READING FOR THE COMPASS...
//SINCE FOR USE THE PHONE WILL ALWAYS BE HELD UPRIGHT.. REFER ANDROID DEVELOPERS FOR DIAGRAM.. CLASS=SENSOREVENT

	// #########################################################################
	// ######################## Activity Life Cycle ############################
	// #########################################################################


	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
//THIS ACTIVITY HAS NO TITLEBAR BECAUSE OF THIS
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,   
                WindowManager.LayoutParams.FLAG_FULLSCREEN); 
		setContentView(R.layout.displayroute);
		
		Stack<GraphNode> navPathStack;
//getShortestPath WILL RETURN A STACK OF NODES WITH DESTINATION AT BOTTOM... THE navPathStack WILL BE USED TO HOLD IT
		// Get location and destination

//THESE ARE THE VALUES SENT VIA INTENT BY THE 1ST ACTIVITY
		nodeFrom 	= 	this.getIntent().getStringExtra("from");
		nodeFromId 	= 	this.getIntent().getIntExtra("fromId",0);
		nodeTo 		= 	this.getIntent().getStringExtra("to");

		// Get reference to graph object
		this.g = Loader.getGraph();
		staircase 	= 		this.getIntent().getBooleanExtra("stairs", true);
		elevator 	= 		this.getIntent().getBooleanExtra("elevator", false);
		outside 	= 		this.getIntent().getBooleanExtra("outside", true);
		log 		= 		this.getIntent().getBooleanExtra("log", false);
		logAudio	= 		this.getIntent().getBooleanExtra("audio", false);

Toast.makeText(this, "All intents read", Toast.LENGTH_SHORT).show();
//SINCE INTENTS ARE USED TO PASS DATA BETWEEN ACTIVITIES THIS SIGNIFICANTLY SLOWS DOWN LAUNCHING OF ACTIVITIES..
//THIS IS DEFINITELY AN AREA WHERE YOU CAN IMPROVISE
		
		// calculate route
		if(nodeFromId==0){
			navPathStack = this.g.getShortestPath(nodeFrom, nodeTo, staircase, elevator, outside);
//ITS REALLY WORTH CONSIDERING THE REMOVAL OF THE OUTSIDE ARGUMENT
			logger = new DataLogger(this, System.currentTimeMillis(), nodeFrom, nodeTo);
		} else {
			navPathStack = this.g.getShortestPath(nodeFromId, nodeTo, staircase, elevator, outside);
			logger = new DataLogger(this, System.currentTimeMillis(), "" + nodeFromId, nodeTo);
		}
		
		if(navPathStack != null){											// no route found!
			// The navPathStack consists of the correct order of nodes on the path
			// From these nodes the corresponding edge is used to get the original
			// data connected to it. What has to be recalculated is the initial bearing
			// because this is depending on the order of nodes being passed.
			
			// List to store the new edges in
Toast.makeText(this, "Route Calculated", Toast.LENGTH_SHORT).show();
Log.i("navPathStack", "The nodes are: " + navPathStack);
			tempEdges = new LinkedList<GraphEdge>();
			// Get first node. This is always the 'left' node, when considering
			// a path going from left to right.
			GraphNode node0 = navPathStack.pop();
			
			while(!navPathStack.isEmpty()){
				// Get 'right' node
				GraphNode node1 = navPathStack.pop();
				// Get Edge connecting 'left' and 'right' nodes
				GraphEdge origEdge = g.getEdge(node0,node1);
				
				// Get data which remains unchanged
				double len = origEdge.getLen();					
				short wheelchair = origEdge.getWheelchair();
				float level = origEdge.getLevel();
				boolean indoor = origEdge.isIndoor();
				
				// Direction has to be recalculated
				double dir = g.getInitialBearing(node0.getLat(), node0.getLon(), node1.getLat(), node1.getLon());
				
				// Create new edge
				GraphEdge tempEdge = new GraphEdge(node0, node1,len,dir,wheelchair,level,indoor);
				// Update additional values
				tempEdge.setElevator(origEdge.isElevator());
				tempEdge.setStairs(origEdge.isStairs());
				tempEdge.setSteps(origEdge.getSteps());
				tempEdges.add(tempEdge);
				
				// Update path length
				navPathLen += origEdge.getLen();			            
//IF YOU ARE NOT GOING TO CREATE A NEW ACTIVITY FOR EACH MODULE... REMEMBER TO SET navPathLen = 0 BEFORE TAKING INPUT
//FOR THE SECOND ROUTE... (SECOND RUN)
				// 'right' node is new 'left' node
				node0 = node1;
//VERY IMPORTANT LOGICALLY
			}
			
			Log.i("inMAPS", "Number of edges before merge: " + tempEdges.size());
			
			// Now that we have the correct order of nodes, and initial bearings of edges
			// we look for successive edges with little difference in their bearing
			// to simplify the path, having less but longer edges
			
			// Allow a difference of 5 degrees to both sides
			double diff = 8.0;
			simplifiedEdges = new LinkedList<GraphEdge>();
			// The current edge to find equaling edges to
			GraphEdge edge_i = null;
			// The first node of current edge
			GraphNode node_i_0 = null;
			// This will be the last node of the last edge equaling edge_i
			GraphNode node_x_1 = null;
			
			// Data to sum up for needed merge;
			short wheelchair;
			float level;
			boolean indoor;
			boolean stairs;
			boolean elevator;
			int steps;
			int last_i = -1;
			// Iterate over all edges
			for (int i = 0; i < tempEdges.size(); i++){
				edge_i = tempEdges.get(i);
				node_i_0 = tempEdges.get(i).getNode0();
				level = edge_i.getLevel();
				indoor = edge_i.isIndoor();
				stairs = edge_i.isStairs();
				elevator = edge_i.isElevator();
				steps = edge_i.getSteps();
				wheelchair = edge_i.getWheelchair();
				Log.i("inMAPS", "Edge (" + (i+1) + "/" + tempEdges.size() + ") dir: " + edge_i.getCompDir());
				last_i = i;
//THE SECOND LOOP TO CHECK FOR MATCHING EDGES TO THE CURRENT EDGE
				for (int j = i + 1; j < tempEdges.size(); j++){
					GraphEdge edge_j = tempEdges.get(j);
					// Only merge edges if they are identical in their characteristics
					if(edge_i.getLevel() == edge_j.getLevel()
							&& edge_i.isElevator() == edge_j.isElevator()
							&& edge_i.isIndoor() == edge_j.isIndoor()
							&& edge_i.isStairs() == edge_j.isStairs()
							&& Positioner.isInRange(edge_i.getCompDir(), tempEdges.get(j).getCompDir(), diff)){
						Log.i("inMAPS", "Adding " + edge_j.getCompDir());
						// Edge_i and edge_j can be merged
						// Save last node1 of last edge_j equaling edge_i
						node_x_1 = edge_j.getNode1();
						
						// Set number of steps only if defined (-1 := undefined, but steps)
						if(steps != -1){
							// only change 0 or defined steps
							if(edge_j.getSteps() != -1){
								steps += edge_j.getSteps();
							} else {
								// edge_j has no defined step count, thus set to undefined
								steps = -1;
							}
						}
					} else {
						Log.i("inMAPS", "Not Merging " + edge_j.getCompDir());
						// Edge_i and edge_j can not be merged
						// Merge possible previously found edges and add them
						
						// Point to latest edge to try matching from
						i = j-1;
//THE ABOVE LINE IS OF GREAT IMPORTANCE, SINCE THE OUTER LOOP COUNTER VARIABLE IS CHANGED DEPENDING ON HOW MANY MATCHING
//EDGES WERE FOUND (SIMILAR EDGES)
						
						// Nothing can be merged, leave edge_i as is
						if(node_x_1 == null){
							Log.i("inMAPS", "Created same edge i " + edge_i.getLen() + " and direction " + edge_i.getCompDir());
							// Add same edge_i
							simplifiedEdges.add(edge_i);
							break;
						} else {
							// Add modified new edge
							double bearing = g.getInitialBearing(node_i_0.getLat(), node_i_0.getLon(), node_x_1.getLat(), node_x_1.getLon());
							double len = g.getDistance(node_i_0.getLat(), node_i_0.getLon(), node_x_1.getLat(), node_x_1.getLon());
//LENGTH OF THE EDGE IS COMPUTED DEPENDING ON THE TWO END-POINT NODES
//WE ARE ALSO FINDING OUT THE ANGLE OF THE EDGE....
							
							GraphEdge tempEdge = new GraphEdge(node_i_0, node_x_1, len, bearing, wheelchair, level, indoor);
							tempEdge.setElevator(elevator);
							tempEdge.setStairs(stairs);
							tempEdge.setSteps(steps);
							simplifiedEdges.add(tempEdge);
							Log.i("inMAPS", "Created edge with length of " + tempEdge.getLen() + " and direction " + tempEdge.getCompDir());
							// Reset last node to null to distinguish if something has to be merged
							node_x_1 = null;
//AFTER MERGING node_x_1 IS SET TO NULL FOR THE NEXT MATCHING...
							break;
						}
					}
				}
			}
			
			if(last_i != -1){
//THIS LOOP LOOKS TO ME LIKE A SERIOUS ISSUE WHICH IS NOT UNCOVERED IN NORMAL EXECUTION.. IF ALL EDGES IN A ROUTE ARE
//MERGED.. THEN THIS LOOP WOULD ADD REDUNDANT EDGES
				for(int i = last_i; i < tempEdges.size(); i++){
					Log.i("inMAPS", "Adding missing edges");
					simplifiedEdges.add(tempEdges.get(i));
				}
			}
			
			// Set current path
			navPathEdges = simplifiedEdges;
Toast.makeText(this, "Simplified Edges created", Toast.LENGTH_SHORT).show();
			Log.i("inMAPS", "EDGES: " + navPathEdges);
			Log.i("inMAPS", "Number of edges after merge: " + navPathEdges.size());	
//THIS IS THE POINT WHERE ALL MAJOR INITIALIZATIONS FOR NAVIGATION ENDS.. ALL THAT FOLLOWS IS THE GRAPHICS LOADING
//PART FOR THE ACTIVITY

			// Get handles to button and zoom controls and save their configuration
			
			btnRecalc = (Button) findViewById(R.id.btnRecalc);
			btnSwitchFit = (Button) findViewById(R.id.btnSwitchFit);
			btnRecalc.setVisibility(Button.INVISIBLE);
			//btnSwitchFit.setVisibility(Button.INVISIBLE);
			
			// Load fancy graphics
			pbMap = new PaintBoxMap(this, this);
			// REPLACING :: has to be done in order of appearance on display (top to bottom)
			replaceSurfaceView(pbMap, (SurfaceView) findViewById(R.id.svPath));			// svPath with pbNavigator

			btnRecalc.setOnClickListener(onClick);
			btnSwitchFit.setOnClickListener(onClick);
			
Toast.makeText(this, "Graphics Loaded", Toast.LENGTH_SHORT).show();
//PLAIN INITIALIZATIONS HERE... BUT IMPORTANT ONE'S FOR POSITIONING!!!
			confBestFit = new NPConfig();
			confBestFit.npCurLen = 0.0;
			confBestFit.npLastMatchedStep = -1;
			confBestFit.npMatchedSteps = 0;
			confBestFit.npPointer = 0;
			// /100.0f -> cm to m
			confBestFit.npStepSize = this.getIntent().getFloatExtra("stepLength", 191.0f/0.415f/100.0f)/100.0f;
		
//THE DEFAULT VALUE IS A SEQUENCE OF DIVISIONS... 191.0f IS THE DEFAULT HEIGHT IN CENTIMETERS
			confBestFit.npUnmatchedSteps = 0;
			
			confFirstFit = new NPConfig(confBestFit);
//AS WE CAN SEE THE confFirstFit SHARES THE SAME DEFAULTS WITH confBestFit
				
			// Create correct pointer to chosen positioner
			conf = confBestFit;
			//conf = confFirstFit;
			
//IN THE LINES THAT FOLLOW ... 0.5f, 0.5f, 666 REPRESENT THE DEFAULT VALUES
			double a = getSharedPreferences(Calibrator.CALIB_DATA,0).getFloat("a", 0.5f);
			double peak = getSharedPreferences(Calibrator.CALIB_DATA,0).getFloat("peak", 0.5f);
			int step_timeout_ms = getSharedPreferences(Calibrator.CALIB_DATA,0).getInt("timeout", 666);
			
			stepDetection = new StepDetection(this, this, a, peak, step_timeout_ms);
//THE THREE CALIBRATED PARAMETERS BEING PASSED TO stepDetection
			
			posBestFit = new Positioner_OnlineBestFit(this, this.navPathEdges, confBestFit);
			posFirstFit = new Positioner_OnlineFirstFit(this, this.navPathEdges, confFirstFit);
//THE navPathEdges IS BEING PASSED TO THE BESTFIT AND FIRSTFIT POSITIONERS... IT CONTAINS THE EDGES FROM SOURCE
//TO DESTINATION
			
			
			setNavigating( true );
		} else { // navPathStack was null
			this.setResult(RESULT_CANCELED);
			this.finish();
		}
	}
	
	@Override
	public void onPause() {
		super.onPause();
		stepDetection.unload();
		pbMap.stopPainting();
//PAUSING CAUSES MANY PROBLEMS... MAYBE BECAUSE THE PAINTING THREAD ISN'T STOPPED...
		if(log){
			// Log to info file
			logger.logInfo("a: " + stepDetection.getA());
			logger.logInfo("peak: " + stepDetection.getPeak());
			logger.logInfo("step timeout (ms): " + stepDetection.getStep_timeout_ms());
			logger.logInfo("Recognised steps: " + this.totalStepsWalked);
			logger.logInfo("Estimated stepsize: " + this.getEstimatedStepLength());
			logger.logInfo("Output of columns:");
			logger.stopLogging();
			if(logAudio){
				avwCapture.stopCapture();
				avwCapture.unregisterCapture();
			}
		}
		
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onResume() {
		super.onResume();
		if(log){
			logger.startLogging();
			// Only log route if files opened correctly
			if(logger.started()){
				
				if(log){
					for(GraphEdge e: navPathEdges){
						logger.logSimpleRoute(e.getNode0().getLat(), e.getNode0().getLon());
					}
					GraphEdge e = navPathEdges.get(navPathEdges.size()-1);
					logger.logSimpleRoute(e.getNode1().getLat(), e.getNode1().getLon());
				}
				if(log){
					for(GraphEdge e: tempEdges){
						logger.logRoute(e.getNode0().getLat(), e.getNode0().getLon());
					}
					GraphEdge e = tempEdges.get(tempEdges.size()-1);
					logger.logRoute(e.getNode1().getLat(), e.getNode1().getLon());
				}
				
				
				
				
				// Create files for AudioWrite here, with correct file name as other log files
				if(nodeFromId==0){
					if(logAudio){
						avwCapture = new AudioWriter("" + logger.getRouteId() + "_" + nodeFrom + "_" + nodeTo +"/", "video.3gp");
					}
				} else {
					if(logAudio){
						avwCapture = new AudioWriter("" + logger.getRouteId() + "_" + nodeFromId + "_" + nodeTo +"/", "video.3gp");
					}
				}
				
				if(logAudio){
					try {
						avwCapture.registerCapture();
						avwCapture.startCapture();
					} catch (IllegalStateException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		
		}
		
		stepDetection.load();
		
	}

	

	// #########################################################################
	// ############################## Functions ################################
	// #########################################################################

	private void replaceSurfaceView(SurfaceView svNew, SurfaceView svOld) {
		LayoutParams layParam = svOld.getLayoutParams();
		LinearLayout ll = (LinearLayout) findViewById(R.id.ll01);
		ll.removeView(svOld);
		ll.addView(svNew, layParam);
	}

	
	/**
	 * Add values to backlog (for variance)
	 * @param x	Sensor x value
	 * @param y	Sensor y value
	 * @param z Sensor z value
	 */
	private void addTriple(double x, double y, double z) {
		x_History[(historyPtr + 1) % historySize] = x;
		y_History[(historyPtr + 1) % historySize] = y;
		z_History[(historyPtr + 1) % historySize] = z;
		historyPtr++;
	}
	/**
	 * Calculates the mean of a given set
	 * @param set the set
	 * @return	the mean value
	 */
	private double meanOfSet(double[] set) {
		double res = 0.0;
		for (double i : set) {
			res += i;
		}
		return res / set.length;

	}
	/**
	 * Calculates the variance of a given set
	 * @param set the set
	 * @return	the variance value
	 */
	private double varianceOfSet(double[] set) {
		double res = 0.0;
		double mean = meanOfSet(set);
		for (double i : set) {
			res += (i - mean) * (i - mean);
		}
		return res / set.length;
	}
	
	// -1 := left
	//  0 := straight on
	//  1 := right
//THIS FUNCTION IS NOT USED ANYWHERE IN THIS CLASS... 
	public int getNextTurn(){
		if(conf.npPointer == navPathEdges.size()-1){
			// Walking on the last edge, go straight on
			return 0;
		}
		
		if(Positioner.isInRange(navPathEdges.get(conf.npPointer).getCompDir(),navPathEdges.get(conf.npPointer+1).getCompDir(),10)){
			// +- 10 degrees is straight on
			return 0;
		}
		if(Positioner.isInRange(navPathEdges.get(conf.npPointer).getCompDir()-90,navPathEdges.get(conf.npPointer+1).getCompDir(),90)){
			// This edge -90 degrees is in range of next edge
			// -> next turn is left turn
			return -1;
		}
		// Else its a right turn
		return 1;
	}
	
}
