package comp.inmaps;

import java.util.ArrayList;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;

/**
 * 
 * @author 	Pralhad Sapre 
 * 			Sanket Shetye
 *
 */
public class ToolBox {
	public static float strokeWidth = 2.0f;

	public static Paint redPaint() {
		Paint p = new Paint();
		p.setStrokeWidth(strokeWidth);
		p.setStyle(Style.FILL);
		p.setColor(Color.RED);
		return p;
	}

	public static Paint redPaint(float textSize) {
		Paint p = new Paint();
		p.setTextSize(textSize);
		p.setStrokeWidth(strokeWidth);
		p.setStyle(Style.FILL);
		p.setColor(Color.RED);
		return p;
	}

	public static Paint greenPaint() {
		Paint p = new Paint();
		p.setStrokeWidth(strokeWidth);
		p.setStyle(Style.FILL);
		p.setColor(Color.GREEN);
		return p;
	}

	public static Paint greenPaint(float textSize) {
		Paint p = new Paint();
		p.setTextSize(textSize);
		p.setStrokeWidth(strokeWidth);
		p.setStyle(Style.FILL);
		p.setColor(Color.GREEN);
		return p;
	}

	public static Paint bluePaint() {
		Paint p = new Paint();
		p.setStrokeWidth(strokeWidth);
		p.setStyle(Style.FILL);
		p.setColor(Color.BLUE);
		return p;
	}
	
	public static Paint whitePaint(float textSize) {
		Paint p = new Paint();
		p.setTextSize(textSize);
		p.setStrokeWidth(strokeWidth);
		p.setStyle(Style.FILL);
		p.setColor(Color.WHITE);
		return p;
	}
	
	public static Paint red() {
		Paint p = new Paint();
		p.setStrokeWidth(2.5f);
		p.setStyle(Style.FILL);
		p.setColor(Color.RED);
		return p;
	}
	
	public static Paint transparentBluePaint() {
		Paint p = new Paint();
		p.setStrokeWidth(strokeWidth);
		p.setStyle(Style.STROKE);
		p.setColor(Color.BLUE);
		return p;
	}

	public static Paint bluePaint(float textSize) {
		Paint p = new Paint();
		p.setTextSize(textSize);
		p.setStrokeWidth(strokeWidth);
		p.setStyle(Style.FILL);
		p.setColor(Color.BLUE);
		return p;
	}
	

	public static Paint myPaint(int strokeWidth, int color) {
		Paint p = new Paint();
		p.setStrokeWidth(strokeWidth);
		p.setStyle(Style.FILL);
		p.setColor(color);
		return p;
	}
	
	public static Paint myPaint(int strokeWidth, int color, int alpha) {
		Paint p = new Paint();
		p.setStrokeWidth(strokeWidth);
		p.setStyle(Style.FILL);
		p.setColor(color);
		p.setAlpha(alpha);
		return p;
	}
	
	/**
	 * Creates a normal double array out of an ArrayList<Double>.
	 * 
	 * @param al
	 *            the ArrayList<Double> to convert
	 * @return the double[] array made out of array list
	 */
	public static double[] arrayListToArrayDouble(ArrayList<Double> al) {
		// create array with appropriate size
		double[] a = new double[al.size()];
		for (int i = 0; i < al.size(); i++) {
			// fill it
			a[i] = al.get(i).doubleValue();
		}
		// voila
		return a;
	}

	/**
	 * Creates a normal int array out of an ArrayList<Integer>.
	 * 
	 * @param al
	 *            the ArrayList<Integer> to convert
	 * @return the int[] array made out of array list
	 */
	public static int[] arrayListToArrayInt(ArrayList<Integer> al) {
		// create array with appropriate size
		int[] a = new int[al.size()];
		for (int i = 0; i < al.size(); i++) {
			// fill it
			a[i] = al.get(i).intValue();
		}
		// voila
		return a;
	}

	// precision to two decimal places behind .
	public static double tdp(double d) {
		return ((int) (d * 100)) / 100.0;
	}
//THE LOGIC OF ABOVE METHOD IS... (BASICALLY IT WILL MAKE THE PRECISION TO ONLY 2 DECIMAL PLACES)
//1. IT MULTIPLIES BY 100.. SO 100.1456.. WILL BECOME 10014.56...
//2. NOW THE DOUBLE VALUE IS TYPECASTED TO INT.. THUS MAKING THE VALUE 10014 ONLY
//3. NOW THIS INT VALUE IS CONVERTED TO DOUBLE AGAIN BY DIVIDING IT WITH 100.0..... MAKING THE VALUE 100.14 IE. A PRECISION OF 2 DIGITS AFTER DECIMAL PT.

	public static double[] arrayClone(double[] array) {
		double[] buf = new double[array.length];
		for (int i = 0; i < array.length; i++) {
			buf[i] = array[i];
		}
		return buf;
	}
	
	
	public static double lowpassFilter(double old_value, double new_value, double a) {
		return old_value + a * (new_value - old_value);
	}
	
}
