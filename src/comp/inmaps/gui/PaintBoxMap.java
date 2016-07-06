package comp.inmaps.gui;



import java.util.LinkedList;

import comp.inmaps.ToolBox;
import comp.inmaps.core.Positioner;
import comp.inmaps.graph.GraphEdge;
import comp.inmaps.graph.GraphNode;
import comp.inmaps.graph.LatLonPos;
import comp.inmaps.paintbox.PaintBox;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Paint.Style;
import android.os.Environment;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Toast;
import comp.inmaps.R;
import android.widget.Toast;
/**
 * 
 * @author 	Pralhad Sapre 
 * 			Sanket Shetye
 * 			Sachin Ghode
 * 
 */
class PaintBoxMap extends PaintBox {
	private static final String MAP_SETTINGS = "PaintBoxMap";
	
	
	private Bitmap arrow;								// png, this is the user position
	private Bitmap arrowred;							// user position in red

	private Context context;							
	private Navigator navigator;						// object to get data from (location, bearing,..)

	private LinkedList<GraphEdge> edges;				// all edges on the path, in right order

	private boolean runOnce = true;						// needed to create/load resources once
	
	//VERY IMPORTANT BOUNDS FOR OUR MAP.. 
    private double long1 = 72.9909555;
    private double lat2 = 19.0757600;
    private double lat1 = 19.0753380;
    private double long2 = 72.9920295;
    private double ratioW, ratioH;
    
	private int levels[];	//THIS ARRAY WILL HOLD ALL THE RESOURCE_ID'S OF MAPS OF DIFFERENT FLOORS
	private Bitmap shown_level;
	private int level_no;
	//private Canvas arrow_canvas;
	
	public Display display;
	
	private int x = 0;		//CO-ORDINATES WITHIN THE BITMAP
    private int y = 0;		//THEY REMAIN THE SAME EVEN WHEN THE UNDERLYING BITMAP CHANGES
    private int screenW, screenH;
    private int picW, picH;
    
    private Paint p;
    
    private boolean pan = true;

    //These two variables keep track of the X and Y coordinate of the finger when it first
    //touches the screen
    private static float startX = 0f;
    private static float startY = 0f;

    //These two variables keep track of the amount we need to translate the canvas along the X
    //and the Y coordinate
    private static float translateX = 0f;
    private static float translateY = 0f;

    
	public PaintBoxMap(Context context, Navigator navigator) {
		super(context);
		this.context = context;
		this.navigator = navigator;
		
		display = ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		screenW = display.getWidth();              
        screenH = display.getHeight(); 
        
        ratioW = (long2 - long1);
    	ratioH = (lat2 - lat1);
	}
	
	public float lonToX(double longitude) {
    	longitude=(longitude-long1)/ratioW;
    	return ((float)((longitude*picW)));
    }
    
    public float latToY(double latitude) {
    	latitude=(lat2-latitude)/ratioH;
    	return((float)((latitude*(picH-50))));
    }

	@Override
	protected void onDraw(Canvas canvas) {
		if(runOnce){
			edges = this.navigator.getNavPathEdges();
//IN EFFECT THIS IS RETURNING THE simplifiedEdges IN NAVIGATOR
			

			levels = new int[3];
			arrow = BitmapFactory.decodeResource(getResources(), R.drawable.arrow);
			arrowred = BitmapFactory.decodeResource(getResources(), R.drawable.arrowred);
			levels[0] = R.drawable.level0;
			levels[1] = R.drawable.level1;
			levels[2] = R.drawable.level2;
			 
			
			picW = BitmapFactory.decodeResource(getResources(), R.drawable.level0).getWidth();
	        picH = BitmapFactory.decodeResource(getResources(), R.drawable.level0).getHeight() + 50;		//AGAIN A DIFFERENCE OF 50 ARISES.. DON'T KNOW THE REASON BUT ADDING 50 WORKS
	        
	        //paintWhite();
			//arrow_canvas = new Canvas();
			level_no = -1;
//IT'S IMPORTANT TO INITIALIZE THE CANVAS
	        
			runOnce = false;
			Log.w("Points", screenW +" ");
			Log.w("Points", screenH +" ");
			Log.w("Points", picW +" ");
			Log.w("Points", picH +" ");
		}		
		
		LatLonPos pos = navigator.getPosition();			// get position
//THE ABOVE LINE WILL INDEED GIVE US A VERY CLOSE APPROX. OF OUR CURRENT LAT, LON POSITION		
		// draw arrow.png to the screen (user position indicator)
		if(level_no != (int) navigator.getLevel()) {
			level_no = (int) navigator.getLevel();
			shown_level = BitmapFactory.decodeResource(getResources(), levels[level_no]).copy(Bitmap.Config.ARGB_8888, true);
//USING copy SOLVED A VERY TRIVIAL BUT HARD TO TRACE ERROR... THE PROBLEM IS THAT WITHOUT USING COPY, THE BITMAP WHICH IS RETURNED IS IMMUTABLE
//copy WILL GIVE US AN EDITABLE BITMAP TO DRAW ON..
			
			drawPath(navigator.getLevel());
		}
			
		//arrow_canvas.setBitmap(shown_level);
		
		//pan=false;
//DISABLING PANNING FOR A SHORT PERIOD OF TIME GAVE A LAGGY FEELING... ITS BETTER NOT TO HAVE IT..
		
		canvas.drawBitmap(shown_level, new Rect(x,y,x + screenW,y + screenH), new Rect(0,0,screenW,screenH), null); 
		
		if(Positioner.isInRange(navigator.getCompassValue(), navigator.getNavPathDir(), navigator.getAcceptanceWidth())){
			Matrix m = new Matrix();
			m = new Matrix();
//REDUNDANT OBJECT CREATIONS !!
			m.setRotate((float) (navigator.getCompassValue()),arrow.getWidth()/2.0f,arrow.getHeight()/2.0f);
//A KEY POINT TO UNDERSTAND HERE...
//1. THE ARROW ICON IS BY DEFAULT IN THE UPRIGHT DIRECTION.. WHICH IS THE 0 DEGREE FROM THE PERSPECTIVE OF COMPASS AND EARTH'S NORTH POLE..
//2. THE MATHEMATICAL 0 DEGREE LAGS THIS VALUE BY 90 DEGREE... AND CAN BE IMAGINED AS A LINE WHICH IS LYING FLAT ON A SURFACE...
//3. SINCE WE ARE CONCERNED WITH THE USER'S POSITION W.R.T NORTH POLE.. KEEPING THE ARROW UPRIGHT IS JUSTIFIED AND CORRECT..
//4. ANOTHER EXAMPLE TO SUPPORT THIS IS THE FACT THAT THERE ARE 2 INTERSECTING LINES SHOWN ON THE CORNER OF ANY MAP.. WITH AN ARROW POINTING NORTH
			
			m.postTranslate((lonToX(pos.getLon()) - arrow.getWidth()/2.0f) - x , (latToY(pos.getLat()) - arrow.getHeight()/2.0f) - y);
//WHY DIVIDE BY screenW AND screenH WHEN YOU CAN SUBTRACT FROM IT X AND Y VALUES..
			
//Log.w("Points", (lonToX(pos.getLon()) - arrow.getWidth()/2.0f) +" ");
//Log.w("Points", (latToY(pos.getLat()) - arrow.getHeight()/2.0f) +" ");

			if(lonToX(pos.getLon()) >= x && lonToX(pos.getLon()) <= (x + screenW))
				if(latToY(pos.getLat()) >= y && latToY(pos.getLat()) <= (y + screenH))
					canvas.drawBitmap(arrow,m,null);					// draw arrow.png to the screen (user position indicator)
			//canvas.drawBitmap(arrow,lonToX(pos.getLon()) - arrow.getWidth()/2.0f, latToY(pos.getLat()) - arrow.getHeight()/2.0f, null);
			//canvas.drawBitmap(arrow, screenW - 200, screenH - 100, null);
		
		}else {
			Matrix m = new Matrix();
			m.setRotate((float) (navigator.getNavPathDir()),arrow.getWidth()/2.0f,arrow.getHeight()/2.0f);
			m.postTranslate((lonToX(pos.getLon()) - arrow.getWidth()/2.0f) - x, (latToY(pos.getLat()) - arrow.getHeight()/2.0f) - y);
			
			if(lonToX(pos.getLon()) >= x && lonToX(pos.getLon()) <= (x + screenW))
				if(latToY(pos.getLat()) >= y && latToY(pos.getLat()) <= (y + screenH))
					canvas.drawBitmap(arrow,m,null);	
			//canvas.drawBitmap(arrow,lonToX(pos.getLon()) - arrow.getWidth()/2.0f, latToY(pos.getLat()) - arrow.getHeight()/2.0f, null);
			//canvas.drawBitmap(arrow, screenW - 200, screenH - 100, null);
			m = new Matrix();
			m.setRotate((float) (navigator.getCompassValue()),arrow.getWidth()/2.0f,arrow.getHeight()/2.0f);
			m.postTranslate((lonToX(pos.getLon()) - arrow.getWidth()/2.0f) - x , (latToY(pos.getLat()) - arrow.getHeight()/2.0f) - y);
			
			if(lonToX(pos.getLon()) >= x && lonToX(pos.getLon()) <= (x + screenW))
				if(latToY(pos.getLat()) >= y && latToY(pos.getLat()) <= (y + screenH))
					canvas.drawBitmap(arrowred,m,null);					// draw arrowred.png to the screen, meaning wrong direction
			//canvas.drawBitmap(arrow,lonToX(pos.getLon()) - arrow.getWidth()/2.0f, latToY(pos.getLat()) - arrow.getHeight()/2.0f, null);
			//canvas.drawBitmap(arrow, screenW - 200, screenH - 100, null);
//Log.w("Points_else", (lonToX(pos.getLon()) - arrow.getWidth()/2.0f) +" ");
//Log.w("Points_else", (latToY(pos.getLat()) - arrow.getHeight()/2.0f) +" ");
		}
		
		
		// draw additional text + background (readability)
		canvas.drawRect(0, 0, getWidth(), 148, ToolBox.myPaint(1, Color.BLACK, 128));
//THIS ABOVE LINE WILL DRAW A RECTANGLE WHICH WILL BE SEMI-TRANSPERANT... AND ON THIS RECTANGLE WILL WE PAINT THE TEXT WHICH INDICATES AMONG OTHER THINGS..
//1. HOW MUCH DISTANCE WE HAVE TRAVELLED.. AND HOW MUCH IS LEFT..
//2. THE ACTUAL AND EXPECTED BEARING... THE COLOR CHANGES TO RED IF THE DIFF INCREASES BEYOND THRESHOLD
//3. THE NEXT TURN WE HAVE TO TAKE IF ANY

		// check if route end reached
		if (navigator.getNavPathEdgeLenLeft() != -1 || navigator.isNavigating()) {
			// draw information
			canvas.drawText(
					"Distance: " + ToolBox.tdp(navigator.getNavPathLen() - navigator.getNavPathLenLeft()) + "m of " +
					ToolBox.tdp(navigator.getNavPathLen()) + "m" , 10, 42, ToolBox.greenPaint(32.0f));
			
			String nextPath = "";
			switch(navigator.getNextTurn()){
			case -1:
				nextPath = "turn left";
				break;
			case 0:
				nextPath = "straight on";
				break;
			case 1:
				nextPath = "turn right";
				break;
			}
//IN THE ABOVE SECTION WE NEED TO HAVE CONDITIONS ABOUT 45 DEGREE TURNS AS WELL
			canvas.drawText(
					"Go " + ToolBox.tdp(navigator.getNavPathEdgeLenLeft())
					+ "m then " + nextPath, 10, 74, ToolBox.greenPaint(32.0f));
//TRY REDUCING THE FONT SIZE FROM 32.0f WHEN RUNNING ON EMULATOR
			
			Paint p = ToolBox.greenPaint(32.0f);
			if(!Positioner.isInRange(navigator.getNavPathDir(),
					navigator.getCompassValue(),
					navigator.getAcceptanceWidth())){
				p = ToolBox.redPaint(32.0f);
			}
//RED PAINT WILL BE USED FOR BEARING.. IF DIFF BETWEEN PATH DIRECTION AND COMPASS VALUE > ACCEPTANCE WIDTH
			canvas.drawText(
					"Bearing: " + ToolBox.tdp(navigator.getCompassValue()) + "/" + (ToolBox.tdp(navigator.getNavPathDir())), 10, 106, p);
//			canvas.drawText(
//					"Variances: " + ToolBox.tdp(navigator.getVarianceOfX()) + "/" + ToolBox.tdp(navigator.getVarianceOfY()) 
//					+ "/" + ToolBox.tdp(navigator.getVarianceOfZ()), 10, 138, ToolBox.greenPaint(32.0f));
//			canvas.drawText("Est. step length: " 
//					+ ToolBox.tdp(navigator.getEstimatedStepLength()) + " vs " + ToolBox.tdp(navigator.getStepLengthInMeters()) ,10, 138, ToolBox.greenPaint(32.0f));
			if(navigator.getNearbyNode(pos).getName() != null) {
				canvas.drawText("Near: " + navigator.getNearbyNode(pos).getName(),10, 138, ToolBox.whitePaint(32.0f));
//THE FUNCTION CHANGES IN IF SHOULD ALSO BE DONE INSIDE IT..
			}
			
			pan=true;
		} else {
// THIS BLOCK IS NOT GETTING ENTERED AS SOON AS WE REACH NEAR OUR DESTINATION WHEN RUNNING THE APPLICATION..
Log.w("done", "destination reached");
			canvas.drawText("Destination ( " + navigator.getRouteEnd().getName() + ") reached", 10, 32, ToolBox.redPaint(32.0f));
		}
	}

	public void drawPath(float level) {
Log.w("Painting", "Drawing the path now...");
		for(GraphEdge e : edges) {
			if(e.getLevel() == level)
				drawEdge(e);
		}
	}
	
	public void paintWhite() {
    	p = new Paint();
    	p.setARGB(255, 255, 255, 255);
    	p.setStyle(Style.STROKE);
    }
    
	public void drawEdge(GraphEdge e) {
Log.w("Painting", "Drawing the edge" + e.getNode0().getId() + " to " + e.getNode1().getId());
    	Canvas c = new Canvas();
    	c.setBitmap(shown_level);
    
    	//p.setPathEffect(new DashPathEffect(new float[] {20,20}, 0));
    	//p.setStrokeWidth(2.5f);
    	c.drawLine(lonToX(e.getNode0().getLon()), latToY(e.getNode0().getLat()), lonToX(e.getNode1().getLon()), latToY(e.getNode1().getLat()), ToolBox.red());
    }
	
	public boolean onTouchEvent(MotionEvent event) {

		if(pan) {
			
        switch (event.getAction() & MotionEvent.ACTION_MASK) {

            case MotionEvent.ACTION_DOWN:
                
                //We assign the current X and Y coordinate of the finger to startX and startY minus the previously translated
                //amount for each coordinates This works even when we are translating the first time because the initial
                //values for these two variables is zero.
                startX = event.getX();
                startY = event.getY();
                break;

            case MotionEvent.ACTION_MOVE:
                translateX = -(event.getX() - startX);
                translateY = -(event.getY() - startY);

                //We cannot use startX and startY directly because we have adjusted their values using the previous translation values.
                //This is why we need to add those values to startX and startY so that we can get the actual coordinates of the finger.
                startX-=translateX;			//IN EFFECT WE ARE ASSIGNING getX() and getY()
                startY-=translateY;
                
                if((x+translateX) > 0)		//DISCONNECTING X AND Y CONDITIONS WAS ESSENTIAL FOR SMOOTH SCROLLING
                	if((x+translateX) <= (picW-screenW))
                		x+=translateX;
                
                if((y+translateY) > 0)
                	if((y+translateY) <= (picH-screenH))
                		y+=translateY;

            case MotionEvent.ACTION_POINTER_DOWN:
                break;

            case MotionEvent.ACTION_UP:
                //All fingers went up, so let's save the value of translateX and translateY into previousTranslateX and
                //previousTranslate
                break;

            case MotionEvent.ACTION_POINTER_UP:
                //This is not strictly necessary; we save the value of translateX and translateY into previousTranslateX
                //and previousTranslateY when the second finger goes up
                break;
        }

        //detector.onTouchEvent(event);

        //We redraw the canvas only in the following cases:
        //
        // o The mode is ZOOM
        //        OR
        // o The mode is DRAG and the scale factor is not equal to 1 (meaning we have zoomed) and dragged is
        //   set to true (meaning the finger has actually moved)
        //if ((mode == DRAG) || mode == ZOOM) {
        
//NO NEED TO CALL invalidate() SINCE A THREAD IS CONSTANTLY REPAINTING THE MAP
            //invalidate();
		}
        return true;
    }

}


