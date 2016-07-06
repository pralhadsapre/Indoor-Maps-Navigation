package comp.inmaps.gui;

import comp.inmaps.core.StepDetection;
import comp.inmaps.core.StepTrigger;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import comp.inmaps.R;

/**
 * This Activity is used to calibrate the parameters concerning step detection
 * 
 * @author 	Pralhad Sapre 
 * 			Sanket Shetye
 *
 */
public class Calibrator extends Activity implements StepTrigger {
	public static final String CALIB_DATA = "CALIBDATA";
	private StepDetection stepDetection;
//STEPDETECTION OBJECT BEING USED HERE.... THE STEPDETECTION IS THE MAIN CLASS WHICH WILL FEED US WITH SENSOR DATA
	
	PaintBoxHistory svHistory;

	// GUI
	TextView tvPeak = null;
	TextView tvFilter = null;
	TextView tvTimeout = null;
	SeekBar sbPeak = null;
	SeekBar sbFilter = null;
	SeekBar sbTimeout = null;
	
	float peak;				// threshold for step detection
	float a;				// value for low pass filter
	int step_timeout_ms;	// distance in ms between each step
		
	OnSeekBarChangeListener sbListener = new OnSeekBarChangeListener(){

		//PRALHAD CHANGE@Override
		public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
			if(arg0.equals(sbPeak)){
				peak = sbPeak.getProgress()/10.0f;
//THE SEEKBAR FOR PEAK VALUE HAS A MAX VALUE OF 100... SO DIVIDING IT BY 10.0f WILL GIVE A VALUE BETWEEN 0.0 TO 10.0
				stepDetection.setPeak(peak);
				tvPeak.setText("Set peak value: (" + peak + ")");
			} else if(arg0.equals(sbFilter)){
				a = sbFilter.getProgress()/100.0f;
//THE SEEKBAR FOR FILTER VALUE ALSO HAS A MAX VALUE OF 100... DIVIDING IT BY 100.0f WILL GIVE US A VALUE IN THE RANGE 0.00 TO 1.00...
				stepDetection.setA(a);
				tvFilter.setText("Set filter value: (" + a + ")");
			} else if(arg0.equals(sbTimeout)){
				step_timeout_ms = sbTimeout.getProgress();
//THE SEEKBAR FOR TIMEOUT HAS A MAX VALUE OF 1000... SINCE TIMEOUT IN MILLISECONDS IS CONSIDERED FROM 0 TO 1000... WE USE THE VALUE PROVIDED DIRECTLY
//A VERY KEY THING FOR THE CALIBRATOR'S GRAPH IS WORTH MENTIONING HERE...
				
//WHEN WE CHANGE THE TIMEOUT VALUE ON THE SEEKBAR... IT AFFECTS LINE NO. 112
				//public void trigger(long nowMs, double compDir) {svHistory.addStepTS(nowMs);}
//NOW WHEN THE TIMEOUT BETWEEN STEP CHANGES IT ALSO CHANGES WITH IT HOW FREQUENTLY STEPS ARE DETECTED BY CALIBRATOR .. WHICH IN TURN AFFECTS THE RATE
//AT WHICH DATA IS FED TO PaintBoxHistory CLASS.. AND THUS ULTIMATELY HOW OFTEN THE "RED LINE" WHICH INDICATES A STEP IS SHOWN
				
				stepDetection.setStep_timeout_ms(step_timeout_ms);
				tvTimeout.setText("Set step timeout: (" + step_timeout_ms + ")");
			}
		}

		//PRALHAD CHANGE@Override
		public void onStartTrackingTouch(SeekBar arg0) {}

		//PRALHAD CHANGE@Override
		public void onStopTrackingTouch(SeekBar arg0) {}
		
	};

	private void loadSettings(){
//PREFERENCES STORED ACROSS APPLICATION INSTANCES ARE PRELOADED.. SO EVERYTIME THE CALIBRATION DATA WILL BE THE 
//PREVIOUS SETTING
		a = getSharedPreferences(CALIB_DATA,0).getFloat("a", 0.5f);
		peak = getSharedPreferences(CALIB_DATA,0).getFloat("peak", 0.5f);
		step_timeout_ms = getSharedPreferences(CALIB_DATA,0).getInt("timeout", 666);
		
		// Update GUI elements
		sbPeak.setProgress((int)(peak*10));
		sbFilter.setProgress((int)(a*100));
		sbTimeout.setProgress(step_timeout_ms);
		
		tvPeak.setText("Set peak value: (" + peak + ")");
		tvFilter.setText("Set filter value: (" + a + ")");
		tvTimeout.setText("Set step timeout: (" + step_timeout_ms + ")");
	}
	
	private void saveSettings(){
		// Save current values to settings
		SharedPreferences settings = getSharedPreferences(CALIB_DATA, 0);
	    SharedPreferences.Editor editor = settings.edit();
	    editor.putFloat("a", a);
	    editor.putFloat("peak", peak);
	    editor.putInt("timeout",step_timeout_ms);
	    // Apply changes
	    editor.commit();
	}

	//PRALHAD CHANGE@Override
	public void dataHookAcc(long nowMs, double x, double y, double z) {}

	//PRALHAD CHANGE@Override
	public void dataHookComp(long nowMs, double x, double y, double z) {}

	//PRALHAD CHANGE@Override
	public void timedDataHook(long nowMs, double[] acc, double[] comp) {svHistory.addTriple(nowMs, acc);}

	//PRALHAD CHANGE@Override
	public void trigger(long nowMs, double compDir) {svHistory.addStepTS(nowMs);}
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.calibrator);
		setTitle("in-MAPS Calibrator");
		
		tvPeak = (TextView) findViewById(R.id.tvPeak);
		tvFilter = (TextView) findViewById(R.id.tvFilter);
		tvTimeout = (TextView) findViewById(R.id.tvTimeout);
		
		sbPeak = (SeekBar) findViewById(R.id.sbPeak);
		sbFilter = (SeekBar) findViewById(R.id.sbFilter);
		sbTimeout = (SeekBar) findViewById(R.id.sbTimeout);

		// Load settings after creation of GUI-elements, to set their values
		loadSettings();
		stepDetection = new StepDetection(this, this, a, peak, step_timeout_ms);
		// Add OnSeekBarChangeListener after creation of step detection, because object is used
		sbPeak.setOnSeekBarChangeListener(sbListener);
		sbFilter.setOnSeekBarChangeListener(sbListener);
		sbTimeout.setOnSeekBarChangeListener(sbListener);
		
		LinearLayout linLayout = (LinearLayout) findViewById(R.id.LinearLayout01);	// get pointer to layout
		SurfaceView svOld = (SurfaceView) findViewById(R.id.svHistory);			// get SurfaceView defined in xml
		LayoutParams lpHistory = svOld.getLayoutParams();						// get its layout params
		
		long samples_per_second = 1000/stepDetection.INTERVAL_MS;
		int history_in_seconds = 4;
		int samples_per_history = (int)(history_in_seconds * samples_per_second);
		
		// create PaintBox (-24.0 to 24.0, 100 entries)
		svHistory = new PaintBoxHistory(this, 48.0, samples_per_history, history_in_seconds);
				
		linLayout.removeView(svOld);								// and remove surface view from layout
		linLayout.addView(svHistory, lpHistory);					// add surface view clone to layout

	}

	@Override
	public void onPause() {
		super.onPause();
		saveSettings();
		stepDetection.unload();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}
	
	@Override
	public void onResume() {
		super.onResume();
		loadSettings();
		stepDetection.load();
	}

}