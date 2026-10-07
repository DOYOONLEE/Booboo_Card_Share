package com.doyun.ledger;

import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import java.time.*;
import java.util.*;

public class RcsAccessibilityService extends AccessibilityService {
    static RcsAccessibilityService instance;
    private static final String MESSAGES="com.samsung.android.messaging", PREFIX=MESSAGES+":id/";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Set<String> seen=new HashSet<>(), undated=new HashSet<>();
    private boolean running, positioned, endButtonAttempted;
    private boolean scrollAccepted=true;
    private Map<String,LocalDateTime> previousDates=new HashMap<>();
    private LocalDate from,to,anchor;
    private long began, missingSince;
    private int pages, count, stuck, positioningStuck;
    private String previous="", stable="", positioningPrevious="";
    private RcsStore store;
    private LinearLayout overlay;
    private TextView progress;
    private final Runnable tick=this::step;
    private static final class Row { String body; LocalDate header; Row(String b,LocalDate h){body=b;header=h;} }

    @Override protected void onServiceConnected(){
        instance=this;
        SharedPreferences p=getSharedPreferences("rcs_import",0);
        if(p.getBoolean("running",false))p.edit().putBoolean("running",false).putString("status","이전 수집이 중단되었습니다. 불러오기를 다시 눌러 주세요.").apply();
    }
    static boolean ready(){return instance!=null;}
    static void begin(LocalDate from,LocalDate to){if(instance!=null)instance.startImport(from,to);}
    private void startImport(LocalDate start,LocalDate end){
        if(running)return;
        from=start;to=end;anchor=LocalDate.now(ZoneId.of("Asia/Seoul"));
        if(to.isAfter(anchor))to=anchor;
        seen.clear();undated.clear();previousDates.clear();scrollAccepted=true;pages=0;count=0;stuck=0;positioningStuck=0;previous="";stable="";positioningPrevious="";positioned=false;endButtonAttempted=false;
        began=SystemClock.elapsedRealtime();missingSince=began;running=true;store=new RcsStore(this);
        getSharedPreferences("rcs_import",0).edit().putBoolean("running",true).putString("status","삼성카드 대화를 여는 중…").apply();
        showOverlay();
        try {
            Intent open=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("sms:15888900"));
            open.setPackage(MESSAGES);open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(open);
            handler.postDelayed(tick,1000);
        }catch(Exception e){finish("삼성 메시지 앱에서 대화를 열지 못했습니다.",false);}
    }
    private void showOverlay(){
        overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.HORIZONTAL);overlay.setPadding(18,8,18,8);overlay.setGravity(Gravity.CENTER_VERTICAL);overlay.setBackgroundColor(Color.rgb(25,42,68));
        progress=new TextView(this);progress.setTextColor(Color.WHITE);progress.setTextSize(14);progress.setText("삼성카드 RCS 불러오는 중…");overlay.addView(progress,new LinearLayout.LayoutParams(0,-2,1));
        Button stop=new Button(this);stop.setText("중지");stop.setOnClickListener(v->finish("사용자가 중지했습니다. 수집한 내역만 표시합니다.",false));overlay.addView(stop);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,android.graphics.PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.BOTTOM;
        try{getSystemService(WindowManager.class).addView(overlay,p);}catch(Exception e){overlay=null;}
    }
    private AccessibilityNodeInfo messageRoot(){
        AccessibilityNodeInfo active=getRootInActiveWindow();
        if(active!=null && MESSAGES.contentEquals(active.getPackageName()==null?"":active.getPackageName()))return active;
        return null;
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo root,String id){
        List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByViewId(PREFIX+id);return nodes.isEmpty()?null:nodes.get(0);
    }
    private boolean target(AccessibilityNodeInfo root){
        AccessibilityNodeInfo title=find(root,"composer_title"), number=find(root,"composer_title_number_sub");
        String t=title==null||title.getText()==null?"":title.getText().toString();
        String n=number==null||number.getText()==null?"":number.getText().toString().replaceAll("[^0-9]","");
        return n.equals("15888900") && t.contains("삼성카드");
    }
    private void flatten(AccessibilityNodeInfo node,List<Row> rows,LocalDate[] header){
        String id=node.getViewIdResourceName();CharSequence value=node.getText();
        if((PREFIX+"bubble_list_date").equals(id) && value!=null)header[0]=RcsParser.headerDate(value.toString());
        if(value!=null && RcsParser.isTransaction(value.toString()))rows.add(new Row(RcsParser.normalize(value.toString()),header[0]));
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)flatten(child,rows,header);}
    }
    private String fingerprint(AccessibilityNodeInfo node){
        StringBuilder out=new StringBuilder();appendTexts(node,out);return out.toString();
    }
    private void appendTexts(AccessibilityNodeInfo node,StringBuilder out){
        if(node.getText()!=null)out.append(node.getText()).append('|');
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)appendTexts(child,out);}
    }
    private void next(long delay){if(running)handler.postDelayed(tick,delay);}
    private void step(){
        if(!running)return;
        if(SystemClock.elapsedRealtime()-began>20*60*1000L || pages>=1500){finish("수집 시간 한도에 도달했습니다. 기간을 나누어 다시 조회해 주세요.",false);return;}
        AccessibilityNodeInfo root=messageRoot();
        if(root==null || !target(root)){
            if(missingSince==0)missingSince=SystemClock.elapsedRealtime();
            if(SystemClock.elapsedRealtime()-missingSince>30000){finish("삼성카드 승인안내 대화가 열려 있지 않아 수집을 중단했습니다.",false);return;}
            if(progress!=null)progress.setText("삼성카드 승인안내 대화를 기다리는 중…");next(600);return;
        }
        missingSince=0;
        AccessibilityNodeInfo list=find(root,"bubble_list_view");
        if(list==null){next(600);return;}
        if(!positioned){positionAtNewest(root,list);return;}
        String signature=fingerprint(list);
        if(signature.isEmpty()){next(600);return;}
        if(!signature.equals(stable)){stable=signature;next(350);return;}
        if(signature.equals(previous)){
            stuck++;
            if(!scrollAccepted && stuck>=3){finish("대화 시작까지 불러오기 완료",true);return;}
            if(stuck>=8){finish("화면이 더 이상 이동하지 않아 수집을 마쳤습니다. 기간 전체 수집 여부는 확인이 필요합니다.",false);return;}
            scrollAccepted=list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);next(750);return;
        }
        previous=signature;stuck=0;pages++;
        List<Row> rows=new ArrayList<>();flatten(list,rows,new LocalDate[]{null});
        Collections.reverse(rows);LocalDate oldest=null;
        Map<String,LocalDateTime> currentDates=new HashMap<>();
        for(Row row:rows){
            LocalDateTime date=previousDates.get(row.body);
            if(date==null)date=RcsParser.date(row.body,anchor,row.header);
            if(date==null){undated.add(row.body);continue;}
            currentDates.put(row.body,date);
            String key=RcsParser.key(row.body,date.atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli());
            if(!seen.add(key))continue;
            LocalDate day=date.toLocalDate();
            if(day.isBefore(anchor))anchor=day;
            if(oldest==null||day.isBefore(oldest))oldest=day;
            if(!day.isBefore(from)&&!day.isAfter(to)){
                store.save(row.body,date.atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli());count++;
            }
        }
        previousDates=currentDates;
        if(progress!=null)progress.setText("삼성카드 "+count+"건 · "+anchor+" 확인 중\n화면을 켜 두세요 · 중지 가능");
        if(oldest!=null && oldest.isBefore(from)){finish("선택 기간 불러오기 완료",true);return;}
        scrollAccepted=list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);next(650);
    }
    private void positionAtNewest(AccessibilityNodeInfo root,AccessibilityNodeInfo list){
        if(progress!=null)progress.setText("최신 메시지로 이동하는 중…\n이동이 끝나면 자동으로 읽기 시작합니다.");
        if(!endButtonAttempted){
            endButtonAttempted=true;AccessibilityNodeInfo bottom=find(root,"composer_scroll_to_end");
            if(bottom!=null&&bottom.performAction(AccessibilityNodeInfo.ACTION_CLICK)){next(900);return;}
        }
        String signature=fingerprint(list);if(signature.isEmpty()){next(500);return;}
        if(signature.equals(positioningPrevious))positioningStuck++;else{positioningPrevious=signature;positioningStuck=0;}
        boolean moved=list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        if(!moved||positioningStuck>=3){
            positioned=true;previous="";stable="";stuck=0;scrollAccepted=true;
            if(progress!=null)progress.setText("최신 내역부터 읽는 중…");next(450);return;
        }
        next(350);
    }
    private void finish(String message,boolean complete){
        if(!running)return;running=false;handler.removeCallbacks(tick);
        if(!undated.isEmpty()){message+=" · 날짜 미확인 "+undated.size()+"건 제외";complete=false;}
        getSharedPreferences("rcs_import",0).edit().putBoolean("running",false).putBoolean("complete",complete).putString("status",message+" · 이번 수집 "+count+"건").apply();
        if(store!=null){store.close();store=null;}
        if(overlay!=null){try{getSystemService(WindowManager.class).removeView(overlay);}catch(Exception ignored){}overlay=null;progress=null;}
        Intent back=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(back);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){if(running)finish("접근성 서비스가 중단되었습니다. 수집한 내역만 표시합니다.",false);}
    @Override public void onDestroy(){if(running)finish("수집이 중단되었습니다. 다시 불러와 주세요.",false);instance=null;super.onDestroy();}
}
