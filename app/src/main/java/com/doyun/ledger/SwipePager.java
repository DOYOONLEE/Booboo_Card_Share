package com.doyun.ledger;

import android.content.Context;
import android.view.MotionEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.ViewFlipper;

final class SwipePager extends ViewFlipper {
    interface Listener { void onPageChanged(int page); }
    private float downX, downY;
    private boolean swiping;
    private Listener listener;

    SwipePager(Context context) { super(context); }
    void setListener(Listener value) { listener=value; }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();swiping=false;}
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=event.getX()-downX,dy=event.getY()-downY;if(Math.abs(dx)>dp(18)&&Math.abs(dx)>Math.abs(dy)*1.35f){swiping=true;return true;}}
        return false;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){
            float dx=event.getX()-downX;if(swiping&&Math.abs(dx)>dp(60)){if(dx<0)showPage(getDisplayedChild()+1,true);else showPage(getDisplayedChild()-1,false);}swiping=false;return true;
        }return true;
    }
    void showPage(int page,boolean forward){
        if(page<0||page>=getChildCount()||page==getDisplayedChild())return;
        int width=Math.max(getWidth(),1);TranslateAnimation in=new TranslateAnimation(forward?width:-width,0,0,0),out=new TranslateAnimation(0,forward?-width:width,0,0);
        in.setDuration(220);out.setDuration(220);in.setInterpolator(new AccelerateDecelerateInterpolator());out.setInterpolator(new AccelerateDecelerateInterpolator());setInAnimation(in);setOutAnimation(out);setDisplayedChild(page);if(listener!=null)listener.onPageChanged(page);
    }
    private int dp(int value){return(int)(getResources().getDisplayMetrics().density*value+.5f);}
}
