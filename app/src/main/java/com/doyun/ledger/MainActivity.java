package com.doyun.ledger;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainActivity extends Activity {
    private static final String[] LABELS={"외식","쇼핑","쿠팡","배달","기타","공과금","제외"};
    private static final int NAVY=Color.rgb(24,35,58),BLUE=Color.rgb(48,94,255),INK=Color.rgb(31,41,55);
    private static final int MUTED=Color.rgb(107,114,128),SURFACE=Color.WHITE,BG=Color.rgb(246,247,251),LINE=Color.rgb(230,233,240);
    private final List<Item> items=new ArrayList<>();
    private LocalDate start=LocalDate.now().withDayOfMonth(1),end=LocalDate.now();
    private android.content.SharedPreferences saved;
    private LinearLayout page,list,summaryGrid;
    private TextView status,totalValue,totalCaption,transactionsTitle;
    private final TextView[] categoryValues=new TextView[7];
    private Button startButton,endButton;

    static final class Item { long time;String id,body;Long amount;boolean cancelled;int category=-1; }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        saved=getSharedPreferences("labels",MODE_PRIVATE);
        start=LocalDate.parse(saved.getString("ui_start",start.toString()));end=LocalDate.parse(saved.getString("ui_end",end.toString()));
        if(state!=null){start=LocalDate.parse(state.getString("start"));end=LocalDate.parse(state.getString("end"));}

        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(BG);page.setPadding(dp(20),0,dp(20),0);
        page.setOnApplyWindowInsetsListener((v,i)->{page.setPadding(dp(20),i.getSystemWindowInsetTop()+dp(16),dp(20),i.getSystemWindowInsetBottom());return i;});
        setContentView(page);

        TextView eyebrow=label("BOOBOO CARD",12,true,MUTED);eyebrow.setLetterSpacing(.14f);page.addView(eyebrow);
        page.addView(label("우리 카드 장부",30,true,NAVY),lp(-1,-2,0,0,0,dp(2)));
        page.addView(label("삼성카드 RCS를 기간별로 모아 한눈에 확인하세요",14,false,MUTED),lp(-1,-2,0,0,0,dp(18)));

        LinearLayout dateCard=card(dp(20),SURFACE,22);page.addView(dateCard,lp(-1,-2,0,0,0,dp(12)));
        dateCard.addView(label("조회 기간",13,true,MUTED));
        LinearLayout dates=new LinearLayout(this);dates.setOrientation(LinearLayout.HORIZONTAL);dateCard.addView(dates,lp(-1,-2,0,dp(8),0,0));
        startButton=dateButton();endButton=dateButton();
        dates.addView(dateCell("시작일",startButton),new LinearLayout.LayoutParams(0,-2,1));
        dates.addView(new Space(this),new LinearLayout.LayoutParams(dp(10),1));
        dates.addView(dateCell("종료일",endButton),new LinearLayout.LayoutParams(0,-2,1));
        startButton.setOnClickListener(v->pick(true));endButton.setOnClickListener(v->pick(false));refreshDates();

        Button loadButton=new Button(this);loadButton.setAllCaps(false);loadButton.setText("삼성카드 RCS 불러오기  →");loadButton.setTextSize(16);loadButton.setTypeface(null,Typeface.BOLD);loadButton.setTextColor(Color.WHITE);
        loadButton.setGravity(Gravity.CENTER);loadButton.setPadding(dp(18),0,dp(18),0);loadButton.setBackground(round(BLUE,16));loadButton.setElevation(dp(3));
        page.addView(loadButton,lp(-1,dp(58),0,0,0,dp(12)));
        loadButton.setOnClickListener(v->{if(start.isAfter(end)){toast("시작일은 종료일보다 늦을 수 없습니다.");return;}startRcsImport();});

        LinearLayout statusCard=card(dp(16),Color.rgb(237,242,255),16);page.addView(statusCard,lp(-1,-2,0,0,0,dp(12)));
        LinearLayout statusRow=new LinearLayout(this);statusRow.setGravity(Gravity.TOP);statusCard.addView(statusRow);
        statusRow.addView(label("●",10,true,BLUE),lp(dp(20),-2,0,0,0,0));
        status=label("내역을 준비하고 있습니다.",13,false,Color.rgb(55,71,112));status.setLineSpacing(0,1.2f);statusRow.addView(status,new LinearLayout.LayoutParams(0,-2,1));

        LinearLayout summaryCard=card(dp(20),NAVY,24);summaryCard.setElevation(dp(2));page.addView(summaryCard,lp(-1,-2,0,0,0,dp(16)));
        summaryCard.addView(label("실제 카드값",13,true,Color.rgb(177,188,214)));
        totalValue=label("0원",30,true,Color.WHITE);summaryCard.addView(totalValue,lp(-1,-2,0,dp(2),0,0));
        totalCaption=label("총 카드값 0원 · 제외 0원",12,false,Color.rgb(177,188,214));summaryCard.addView(totalCaption,lp(-1,-2,0,dp(2),0,dp(14)));
        summaryGrid=new LinearLayout(this);summaryGrid.setOrientation(LinearLayout.VERTICAL);summaryCard.addView(summaryGrid);buildSummaryGrid();

        LinearLayout listHeader=new LinearLayout(this);listHeader.setGravity(Gravity.CENTER_VERTICAL);page.addView(listHeader,lp(-1,-2,0,0,0,dp(6)));
        transactionsTitle=label("거래 내역",19,true,NAVY);listHeader.addView(transactionsTitle,new LinearLayout.LayoutParams(0,-2,1));
        TextView local=label("기기 내 저장",12,true,MUTED);local.setPadding(dp(10),dp(6),dp(10),dp(6));local.setBackground(round(Color.rgb(234,236,242),50));listHeader.addView(local);

        ScrollView scroll=new ScrollView(this);scroll.setClipToPadding(false);scroll.setPadding(0,0,0,dp(16));list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        load();
    }

    private LinearLayout dateCell(String title,Button button){LinearLayout box=card(dp(12),Color.rgb(248,249,252),14);box.addView(label(title,11,true,MUTED));box.addView(button,lp(-1,dp(38),0,dp(1),0,0));return box;}
    private Button dateButton(){Button b=new Button(this);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(null,Typeface.BOLD);b.setTextColor(INK);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setPadding(0,0,0,0);b.setMinHeight(0);b.setMinimumHeight(0);b.setBackgroundColor(Color.TRANSPARENT);return b;}
    private void buildSummaryGrid(){for(int first=0;first<7;first+=4){LinearLayout row=new LinearLayout(this);summaryGrid.addView(row,lp(-1,-2,0,0,0,first<4?dp(8):0));for(int i=first;i<Math.min(first+4,7);i++){LinearLayout cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);if(i>first)cell.setPadding(dp(10),0,0,0);cell.addView(label(LABELS[i],11,false,Color.rgb(177,188,214)));categoryValues[i]=label("0원",13,true,Color.WHITE);categoryValues[i].setSingleLine();cell.addView(categoryValues[i]);row.addView(cell,new LinearLayout.LayoutParams(0,-2,1));}}}
    private void refreshDates(){DateTimeFormatter f=DateTimeFormatter.ofPattern("yyyy.MM.dd");startButton.setText(start.format(f));endButton.setText(end.format(f));}
    private void pick(boolean first){LocalDate d=first?start:end;new DatePickerDialog(this,(p,y,m,day)->{if(first)start=LocalDate.of(y,m+1,day);else end=LocalDate.of(y,m+1,day);refreshDates();saved.edit().putString("ui_start",start.toString()).putString("ui_end",end.toString()).apply();load();},d.getYear(),d.getMonthValue()-1,d.getDayOfMonth()).show();}
    private void startRcsImport(){saved.edit().putString("ui_start",start.toString()).putString("ui_end",end.toString()).apply();if(!RcsAccessibilityService.ready()){new AlertDialog.Builder(this).setTitle("RCS 불러오기 설정").setMessage("접근성 설정의 설치된 앱에서 ‘삼성카드 RCS 불러오기’를 켜 주세요. 선택한 기간의 삼성카드 대화만 읽으며 내역은 휴대폰에 저장됩니다.").setNegativeButton("나중에",null).setPositiveButton("설정 열기",(d,w)->startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))).show();return;}if(start.isAfter(LocalDate.now())){toast("시작일은 오늘 이후일 수 없습니다.");return;}status.setText("삼성카드 대화를 읽고 있습니다. 화면을 켜 두세요.");RcsAccessibilityService.begin(start,end);}
    @Override public void onResume(){super.onResume();if(status!=null)load();}
    private void load(){ZoneId zone=ZoneId.of("Asia/Seoul");long from=start.atStartOfDay(zone).toInstant().toEpochMilli(),until=end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();try(RcsStore store=new RcsStore(this)){items.clear();items.addAll(store.read(from,until,saved));}String result=getSharedPreferences("rcs_import",0).getString("status","불러오기를 누르면 삼성카드 대화에서 내역을 수집합니다.");status.setText(result+"\n"+start+" ~ "+end+" · "+items.size()+"건 저장");render();}
    private void render(){list.removeAllViews();transactionsTitle.setText("거래 내역  "+items.size()+"건");if(items.isEmpty()){LinearLayout empty=card(dp(24),SURFACE,20);TextView icon=label("◎",32,true,Color.rgb(170,178,196));icon.setGravity(Gravity.CENTER);empty.addView(icon);TextView message=label("아직 저장된 내역이 없어요\n위 버튼을 눌러 RCS 내역을 불러오세요",14,false,MUTED);message.setGravity(Gravity.CENTER);message.setLineSpacing(0,1.3f);empty.addView(message);list.addView(empty,lp(-1,-2,0,dp(8),0,0));}for(Item item:items)addCard(item);updateSummary();}
    private void addCard(Item item){
        LinearLayout card=card(dp(17),SURFACE,20);card.setElevation(dp(1));list.addView(card,lp(-1,-2,0,dp(7),0,dp(7)));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);card.addView(top);
        TextView when=label(DateTimeFormatter.ofPattern("MM월 dd일  HH:mm").format(Instant.ofEpochMilli(item.time).atZone(ZoneId.of("Asia/Seoul"))),12,true,MUTED);top.addView(when,new LinearLayout.LayoutParams(0,-2,1));
        TextView state=label(item.cancelled?"취소":"승인",11,true,item.cancelled?Color.rgb(196,48,64):Color.rgb(26,134,89));state.setPadding(dp(9),dp(4),dp(9),dp(4));state.setBackground(round(item.cancelled?Color.rgb(255,235,238):Color.rgb(230,248,239),50));top.addView(state);
        LinearLayout amountRow=new LinearLayout(this);amountRow.setGravity(Gravity.CENTER_VERTICAL);card.addView(amountRow,lp(-1,-2,0,dp(7),0,0));
        LinearLayout merchantBox=new LinearLayout(this);merchantBox.setOrientation(LinearLayout.VERTICAL);amountRow.addView(merchantBox,new LinearLayout.LayoutParams(0,-2,1));merchantBox.addView(label(merchant(item.body),16,true,INK));merchantBox.addView(label(cardName(item.body),12,false,MUTED));
        TextView amount=label(item.amount==null?"확인 필요":money(SmsParser.signed(item.amount,item.cancelled)),22,true,item.cancelled?Color.rgb(196,48,64):NAVY);amountRow.addView(amount);
        View divider=new View(this);divider.setBackgroundColor(LINE);card.addView(divider,lp(-1,dp(1),0,dp(13),0,dp(10)));
        card.addView(label(item.category<0?"분류를 선택해 주세요":"선택됨 · "+LABELS[item.category],12,true,item.category<0?MUTED:BLUE));
        for(int first=0;first<7;first+=4){LinearLayout row=new LinearLayout(this);card.addView(row,lp(-1,-2,0,dp(7),0,0));for(int j=first;j<Math.min(first+4,7);j++){final int index=j;TextView chip=label(LABELS[j],12,true,item.category==j?Color.WHITE:Color.rgb(68,77,96));chip.setGravity(Gravity.CENTER);chip.setBackground(round(item.category==j?BLUE:Color.rgb(241,243,247),50));chip.setContentDescription(LABELS[j]+(item.category==j?" 선택됨":""));chip.setClickable(true);chip.setFocusable(true);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(38),1);if(j>first)cp.setMargins(dp(7),0,0,0);row.addView(chip,cp);chip.setOnClickListener(v->{item.category=index;saved.edit().putInt("category_"+item.id,index).apply();render();});}}
        TextView edit=label("금액 또는 승인 상태 수정",12,true,MUTED);edit.setGravity(Gravity.CENTER);edit.setPadding(0,dp(11),0,dp(4));edit.setClickable(true);edit.setFocusable(true);edit.setOnClickListener(v->edit(item));card.addView(edit);
    }
    private String merchant(String body){String[] lines=RcsParser.normalize(body).split("\n");if(lines.length>2){String value=lines[2].replaceFirst("^[0-9]{1,2}[/.-][0-9]{1,2}\\s+[0-9]{1,2}:[0-9]{2}\\s*","").trim();if(!value.isEmpty())return value;}return "삼성카드 이용내역";}
    private String cardName(String body){String[] lines=RcsParser.normalize(body).split("\n");return lines.length==0?"삼성카드":lines[0].replaceAll("\\s+[가-힣]\\*[^ ]+$","");}
    private void edit(Item item){LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);fields.setPadding(dp(20),0,dp(20),0);EditText input=new EditText(this);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);input.setHint("양수 원화 금액");if(item.amount!=null)input.setText(String.valueOf(item.amount));fields.addView(input);CheckBox cancelled=new CheckBox(this);cancelled.setText("취소 거래 (합계에서 차감)");cancelled.setChecked(item.cancelled);fields.addView(cancelled);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("거래 수정").setView(fields).setNegativeButton("닫기",null).setPositiveButton("저장",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{long value=Long.parseLong(input.getText().toString());if(value<0||value>1000000000000L)throw new NumberFormatException();item.amount=value;item.cancelled=cancelled.isChecked();saved.edit().putLong("amount_"+item.id,value).putBoolean("cancel_"+item.id,item.cancelled).apply();dialog.dismiss();render();}catch(NumberFormatException e){input.setError("0~1조 원 사이의 정수를 입력하세요.");}}));dialog.show();}
    private void updateSummary(){long[] totals=new long[7];long all=0;int pending=0,unresolved=0;for(Item item:items){if(item.amount==null){unresolved++;continue;}long amount=SmsParser.signed(item.amount,item.cancelled);all+=amount;if(item.category<0)pending++;else totals[item.category]+=amount;}for(int i=0;i<7;i++)categoryValues[i].setText(money(totals[i]));long actual=all-totals[6];totalValue.setText(money(actual));String note="총 카드값 "+money(all)+" · 제외 "+money(totals[6]);if(pending>0)note+=" · 미분류 "+pending+"건";if(unresolved>0)note+=" · 금액 확인 "+unresolved+"건";totalCaption.setText(note);}
    private LinearLayout card(int padding,int color,int radius){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(padding,padding,padding,padding);v.setBackground(round(color,radius));return v;}
    private TextView label(String value,int size,boolean bold,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(false);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout.LayoutParams lp(int w,int h,int left,int top,int right,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(left,top,right,bottom);return p;}
    private int dp(int n){return(int)(getResources().getDisplayMetrics().density*n+.5f);}
    private String money(long value){return NumberFormat.getIntegerInstance(Locale.KOREA).format(value)+"원";}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    @Override public void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("start",start.toString());state.putString("end",end.toString());}
}
