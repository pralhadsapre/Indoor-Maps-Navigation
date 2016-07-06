package comp.inmaps.gui;

import comp.inmaps.ToolBox;
import comp.inmaps.paintbox.PaintBox;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * 
 * @author 	Pralhad Sapre 
 * 			Sanket Shetye
 * 			Sachin Ghode
 *
 */
class PaintBoxHistory extends PaintBox {
	//private Context context;

	private int historySize;
	private double[] x_History;
	private double[] y_History;
	private double[] z_History;
	private long[] time_History;
	private int historyPtr = 0;
	private double valueRange = Double.NEGATIVE_INFINITY;
	private int drawWidth = 0;
	private int drawHeight = 0;
	//private double scale_x = 1;
	private double scale_y = 1;
	private int offset_y = 0;
	boolean once = true;
	
	private int seconds = 1; // # of seconds to show on screen
	private int num_steps = 0;

	/**
	 * 
	 * @param context
	 *            the context under which is painted
	 * @param valueRange
	 *            range of values to be display around zero
	 * @param seconds
	 * @param tvSteps 
	 * @param drawWidth
	 *            the width of the paintable area
	 * @param drawHeight
	 *            the height of the paintable area
	 */
	public PaintBoxHistory(Context context, double valueRange, int historySize, int seconds) {
		super(context);
		// save to have e.g. access to asserts
//		this.context = context;
		this.valueRange = valueRange;
		this.historySize = historySize;
		x_History = new double[historySize];
		y_History = new double[historySize];
		z_History = new double[historySize];
		time_History = new long[historySize];
		this.seconds = seconds;
	}

	private int getPosOnScreen(long x_ms, long current_ms) {
		long diff_ms = current_ms - x_ms;

		// addition because diff_ms is negative!
		int res = (int) (drawWidth - (((double) drawWidth / (double) seconds) / 1000.0) * diff_ms);
//IN THE ABOVE STATEMENT DIVISION BY 1000.0 IS DONE BECAUSE DRAWWIDTH HAS TO BE DIVIDED BY MILLISECONDS..(WHICH IS MADE POSSIBLE BY DIVIDING seconds WITH
//1000.0) AND THEN MULTIPLIED BY THE DIFFERENCE IN MILLISECONDS BETWEEN current_ms AND x_ms 
//AS current_ms WILL INCREASE.. SO WILL THE DIFFERENCE AND THE GRAPH WILL MOVE MORE TOWARDS THE LEFT.. SINCE IT IS SUBTRACTED FROM THE RIGHTBOUND OF WIDTH..
		
		return res;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (once) {
			setDimensions();
		}
		canvas.drawColor(Color.WHITE);
		canvas.drawLine(0, offset_y, drawWidth, offset_y, ToolBox.myPaint(1, Color.BLACK));
		Paint paint = ToolBox.myPaint(2, Color.RED);
		paint.setTextSize(40.0f);
		canvas.drawText("Steps: " + num_steps, 10, getHeight()-40, paint);
		long uptime_ms = System.currentTimeMillis();
//THE CURRENT TIME WHEN PAINTING IS BEING DONE...
		
		for(long ts : tenLastSteps){
			canvas.drawLine(getPosOnScreen(ts, uptime_ms), 0,
					getPosOnScreen(ts, uptime_ms), drawHeight, ToolBox.myPaint(2, Color.RED));
		}
//THE RED LINE BEING DRAWN ON THE CALIBRATION DATA GRAPH WHEN A STEP IS DETECTED...
//IT CAN ONLY SHOW TEN READINGS....IF TIMEOUT IS MADE VERY SMALL THE RED LINES DON'T EVEN MAKE IT TO THE LEFT END OF THE VIEW.. BECAUSE NEW STEP POINTS
//TAKE THEIR PLACE
	
		drawDataSet(canvas, x_History, ToolBox.myPaint(2, Color.RED), uptime_ms);
		drawDataSet(canvas, y_History, ToolBox.myPaint(2, Color.GREEN), uptime_ms);
		drawDataSet(canvas, z_History, ToolBox.myPaint(2, Color.BLUE), uptime_ms);

//YOU MAY CONSIDER REMOVING VARIANCE IN THE FINAL OUTPUT...
		Paint p = ToolBox.myPaint(2, Color.RED);
		p.setTextSize(22.0f);
		canvas.drawText("X axis", 10, 20, p);
		p = ToolBox.myPaint(2, Color.GREEN);
		p.setTextSize(22.0f);
		canvas.drawText("Y axis", 10, 42, p);
		p = ToolBox.myPaint(2, Color.BLUE);
		p.setTextSize(22.0f);
		canvas.drawText("Z axis", 10, 64, p);
	}

	private void drawDataSet(Canvas canvas, double[] set, Paint paint, long uptime_ms) {
		int item0, item1;
		for (int i = 0; i < historySize - 1; i++) {
			item0 = (historyPtr + 1 + i) % historySize;
			item1 = (historyPtr + 2 + i) % historySize;
//USING historyPtr AS A STARTING POINT FOR ACCESSING VALUES IS CRUCIAL BECAUSE historyPtr + 1 WILL BE THE OLDEST VALUE IN THE ARRAY...
			double y1 = -set[item0] * scale_y + offset_y;
			double y2 = -set[item1] * scale_y + offset_y;

			canvas.drawLine(getPosOnScreen(time_History[item0], uptime_ms), (int) y1,
					getPosOnScreen(time_History[item1], uptime_ms), (int) y2, paint);
			
		}		
	}

	public void setDimensions() {
		this.drawWidth = this.getWidth();
		this.drawHeight = this.getHeight();
//		scale_x = this.drawWidth / historySize;
		scale_y = this.drawHeight / valueRange;
//DIVIDING THE HEIGHT INTO QUANTIZATION LEVELS
		offset_y = this.drawHeight / 2;
	}

	/**
	 * Call this timed
	 * @param t
	 * @param x
	 * @param y
	 * @param z
	 */
	public void addTriple(long t, double[] acc) {
		x_History[(historyPtr + 1) % historySize] = acc[0];
		y_History[(historyPtr + 1) % historySize] = acc[1];
		z_History[(historyPtr + 1) % historySize] = acc[2];
		time_History[(historyPtr + 1) % historySize] = t;
		historyPtr++;
//BECAUSE OF THE ABOVE LOGIC OF MODULUS.. THE THREE AXES VALUES OF ACCELEROMETER WILL BE PUT IN HISTORY IN CYCLIC FASHION..
	}

	/**
	 * Calculate the mean of a given array
	 * 
	 * @param set
	 *            the input data
	 * @return mean of the input data
	 */
	private double meanOfSet(double[] set) {
		double res = 0.0;
		for (double i : set) {
			res += i;
		}
		return res / set.length;

	}

	/**
	 * Calculate the variance of a given array
	 * 
	 * @param set
	 *            the input data
	 * @return variance of the input data
	 */
	private double varianceOfSet(double[] set) {
		double res = 0.0;
		double mean = meanOfSet(set);
		for (double i : set) {
			res += (i - mean) * (i - mean);
		}
		return res / set.length;
	}

//YOU SHOULD DEFINITELY CONSIDER INCREASING THE stepHistorySize
	private int stepHistorySize = 10;
	private long[] tenLastSteps = new long[stepHistorySize];
	private int shPointer = 0;
	
	public void addStepTS(long ts){
		num_steps++;		
		tenLastSteps[shPointer % stepHistorySize] = ts;								// add value to values_history
		shPointer++;
		shPointer = shPointer % stepHistorySize;
	}
}
