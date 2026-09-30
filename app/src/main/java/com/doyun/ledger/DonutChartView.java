package com.doyun.ledger;

import android.content.Context;
import android.graphics.*;
import android.view.View;

final class DonutChartView extends View {
    private static final int[] COLORS={0xffff746c,0xffffb84d,0xff4588ff,0xff31c69c,0xffa884ee,0xffff8f5b,0xffaab2c2};
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private long[] values=new long[7];
    DonutChartView(Context context){super(context);}
    static int colorAt(int index){return COLORS[index];}
    void setValues(long[] input){values=input.clone();invalidate();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float size=Math.min(getWidth(),getHeight()),stroke=size*.18f,cx=getWidth()/2f,cy=getHeight()/2f,r=(size-stroke)/2f-dp(4);RectF oval=new RectF(cx-r,cy-r,cx+r,cy+r);long sum=0;for(int i=0;i<values.length-1;i++)sum+=Math.abs(values[i]);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setStrokeCap(Paint.Cap.BUTT);if(sum==0){paint.setColor(0xffe8ebf2);canvas.drawArc(oval,0,360,false,paint);return;}float start=-90;for(int i=0;i<values.length-1;i++){float sweep=360f*Math.abs(values[i])/sum;if(sweep<=0)continue;paint.setColor(COLORS[i]);canvas.drawArc(oval,start,sweep,false,paint);start+=sweep;}}
    private int dp(int value){return(int)(getResources().getDisplayMetrics().density*value+.5f);}
}
