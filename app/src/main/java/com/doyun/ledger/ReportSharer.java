package com.doyun.ledger;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.*;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class ReportSharer {
    private static final int WIDTH=1080,HEIGHT=1920,MARGIN=64;
    private static final int BG=0xfff6f7fb,NAVY=0xff18233a,INK=0xff1f2937,MUTED=0xff6b7280,BLUE=0xff305eff,WHITE=Color.WHITE,LINE=0xffe6e9f0;
    private static final String[] LABELS={"외식","쇼핑","쿠팡","배달","기타","공과금","제외","미분류"};
    private static final int[] COLORS={0xffff746c,0xffffb84d,0xff4588ff,0xff31c69c,0xffa884ee,0xffff8f5b,0xffaab2c2,0xffd5dae3};
    private static final DateTimeFormatter DAY=DateTimeFormatter.ofPattern("MM.dd  HH:mm"),PERIOD=DateTimeFormatter.ofPattern("yyyy.MM.dd");

    static void share(Activity activity,List<MainActivity.Item> source,LocalDate start,LocalDate end)throws IOException{
        List<MainActivity.Item> items=new ArrayList<>(source);items.sort((a,b)->Long.compare(b.time,a.time));
        File directory=new File(activity.getCacheDir(),"shared_reports");if(!directory.exists()&&!directory.mkdirs())throw new IOException("공유 파일 폴더를 만들 수 없습니다.");
        File[] old=directory.listFiles();if(old!=null)for(File file:old)if(file.getName().endsWith(".png"))file.delete();
        ArrayList<File> files=new ArrayList<>();files.add(renderSummary(directory,items,start,end));files.addAll(renderCategories(directory,items,start,end));
        ArrayList<Uri> uris=new ArrayList<>();for(File file:files)uris.add(FileProvider.getUriForFile(activity,activity.getPackageName()+".fileprovider",file));
        String caption="우리 카드 장부 · "+start.format(PERIOD)+" ~ "+end.format(PERIOD)+" · "+items.size()+"건";
        activity.runOnUiThread(()->launch(activity,uris,caption));
    }

    private static File renderSummary(File directory,List<MainActivity.Item> items,LocalDate start,LocalDate end)throws IOException{
        Bitmap bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bitmap);c.drawColor(BG);Paint p=paint();
        text(c,p,"BOOBOO CARD",MARGIN,90,26,MUTED,true);text(c,p,"우리 카드 장부",MARGIN,156,54,NAVY,true);textRight(c,p,"공유 리포트",WIDTH-MARGIN,145,28,BLUE,true);
        round(c,p,MARGIN,205,WIDTH-MARGIN,315,30,WHITE);text(c,p,"조회 기간",MARGIN+30,250,24,MUTED,true);textRight(c,p,start.format(PERIOD)+"  —  "+end.format(PERIOD),WIDTH-MARGIN-30,278,34,INK,true);
        long[] totals=new long[8];long all=0;for(MainActivity.Item item:items)if(item.amount!=null){long value=SmsParser.signed(item.amount,item.cancelled);all+=value;totals[item.category>=0&&item.category<7?item.category:7]+=value;}long actual=all-totals[6];
        round(c,p,MARGIN,350,WIDTH-MARGIN,1470,42,NAVY);text(c,p,"실제 카드값",MARGIN+42,420,28,0xffb1bcd6,true);text(c,p,money(actual),MARGIN+42,505,62,WHITE,true);text(c,p,"총 카드값 "+money(all)+"   ·   제외 "+money(totals[6])+"   ·   "+items.size()+"건",MARGIN+42,558,26,0xffb1bcd6,false);
        drawDonut(c,p,292,850,190,totals,actual);
        float y=675;for(int i=0;i<8;i++){p.setColor(COLORS[i]);c.drawCircle(560,y-9,12,p);text(c,p,LABELS[i],590,y,28,0xffdce1ee,i<3);textRight(c,p,money(totals[i]),WIDTH-MARGIN-42,y,28,WHITE,true);y+=76;}
        text(c,p,"카테고리별 비율은 ‘제외’를 빼고 계산했습니다.",MARGIN+42,1415,24,0xffb1bcd6,false);
        round(c,p,MARGIN,1510,WIDTH-MARGIN,1760,30,WHITE);text(c,p,"리포트 구성",MARGIN+34,1570,27,NAVY,true);text(c,p,"1  전체 금액과 카테고리 그래프",MARGIN+34,1630,27,INK,false);text(c,p,"2  카테고리별 가맹점·일시·금액 목록",MARGIN+34,1685,27,INK,false);text(c,p,"서버에 업로드하지 않고 이 휴대폰에서 만든 이미지입니다.",MARGIN+34,1732,23,MUTED,false);
        File file=new File(directory,"booboo-report-01-summary.png");save(bitmap,file);return file;
    }

    private static ArrayList<File> renderCategories(File directory,List<MainActivity.Item> items,LocalDate start,LocalDate end)throws IOException{
        @SuppressWarnings("unchecked") List<MainActivity.Item>[] groups=new List[8];for(int i=0;i<8;i++)groups[i]=new ArrayList<>();for(MainActivity.Item item:items)groups[item.category>=0&&item.category<7?item.category:7].add(item);
        ArrayList<File> files=new ArrayList<>();Page page=new Page(start,end,2);boolean drew=false;
        for(int category=0;category<groups.length;category++){
            List<MainActivity.Item> rows=groups[category];if(rows.isEmpty())continue;drew=true;long subtotal=0;for(MainActivity.Item item:rows)if(item.amount!=null)subtotal+=SmsParser.signed(item.amount,item.cancelled);
            if(page.y+135+Math.min(1,rows.size())*80>HEIGHT-80){files.add(page.save(directory));page=new Page(start,end,page.number+1);}
            page.categoryHeader(category,rows.size(),subtotal,false);
            for(MainActivity.Item item:rows){if(page.y+80>HEIGHT-80){files.add(page.save(directory));page=new Page(start,end,page.number+1);page.categoryHeader(category,rows.size(),subtotal,true);}page.row(item);}
            page.y+=28;
        }
        if(!drew)page.empty();files.add(page.save(directory));return files;
    }

    private static final class Page{
        final Bitmap bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);final Canvas c=new Canvas(bitmap);final Paint p=paint();final LocalDate start,end;final int number;float y=210;
        Page(LocalDate start,LocalDate end,int number){this.start=start;this.end=end;this.number=number;c.drawColor(BG);text(c,p,"카테고리별 소비 내역",MARGIN,90,46,NAVY,true);text(c,p,start.format(PERIOD)+"  —  "+end.format(PERIOD),MARGIN,140,25,MUTED,false);textRight(c,p,String.format(Locale.KOREA,"%02d",number),WIDTH-MARGIN,115,30,BLUE,true);}
        void categoryHeader(int category,int count,long subtotal,boolean continued){round(c,p,MARGIN,y,WIDTH-MARGIN,y+92,26,WHITE);p.setColor(COLORS[category]);c.drawCircle(MARGIN+34,y+46,13,p);text(c,p,LABELS[category]+(continued?" · 계속":""),MARGIN+64,y+56,30,NAVY,true);textRight(c,p,count+"건  ·  "+money(subtotal),WIDTH-MARGIN-26,y+56,27,INK,true);y+=112;}
        void row(MainActivity.Item item){float top=y;text(c,p,DAY.format(Instant.ofEpochMilli(item.time).atZone(ZoneId.of("Asia/Seoul"))),MARGIN+12,top+31,23,MUTED,true);String merchant=fit(p,MerchantNames.extract(item.body),520,29,true);text(c,p,merchant,MARGIN+240,top+31,29,INK,true);String value=item.amount==null?"확인 필요":money(SmsParser.signed(item.amount,item.cancelled));textRight(c,p,value,WIDTH-MARGIN-12,top+31,29,item.cancelled?0xffc43040:NAVY,true);p.setColor(LINE);c.drawRect(MARGIN,top+65,WIDTH-MARGIN,top+67,p);y+=80;}
        void empty(){round(c,p,MARGIN,y,WIDTH-MARGIN,y+180,26,WHITE);text(c,p,"공유할 거래 내역이 없습니다.",MARGIN+35,y+100,30,MUTED,true);y+=200;}
        File save(File directory)throws IOException{File file=new File(directory,String.format(Locale.ROOT,"booboo-report-%02d-categories.png",number));ReportSharer.save(bitmap,file);return file;}
    }

    private static void drawDonut(Canvas c,Paint p,float cx,float cy,float radius,long[] totals,long actual){long sum=0;for(int i=0;i<6;i++)sum+=Math.abs(totals[i]);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(82);RectF oval=new RectF(cx-radius,cy-radius,cx+radius,cy+radius);if(sum==0){p.setColor(0xff39465f);c.drawArc(oval,0,360,false,p);}else{float from=-90;for(int i=0;i<6;i++){float sweep=360f*Math.abs(totals[i])/sum;if(sweep==0)continue;p.setColor(COLORS[i]);c.drawArc(oval,from,sweep,false,p);from+=sweep;}}p.setStyle(Paint.Style.FILL);textCenter(c,p,"실제 카드값",cx,cy-5,24,0xffb1bcd6,true);textCenter(c,p,money(actual),cx,cy+42,31,WHITE,true);}
    private static Paint paint(){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTypeface(Typeface.create("sans",Typeface.NORMAL));return p;}
    private static void text(Canvas c,Paint p,String value,float x,float baseline,float size,int color,boolean bold){p.setTextSize(size);p.setColor(color);p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(value,x,baseline,p);}
    private static void textRight(Canvas c,Paint p,String value,float x,float baseline,float size,int color,boolean bold){p.setTextSize(size);p.setColor(color);p.setTextAlign(Paint.Align.RIGHT);p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(value,x,baseline,p);}
    private static void textCenter(Canvas c,Paint p,String value,float x,float baseline,float size,int color,boolean bold){p.setTextSize(size);p.setColor(color);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(value,x,baseline,p);}
    private static void round(Canvas c,Paint p,float l,float t,float r,float b,float radius,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawRoundRect(l,t,r,b,radius,radius,p);}
    private static String fit(Paint p,String value,float width,float size,boolean bold){p.setTextSize(size);p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));if(p.measureText(value)<=width)return value;String suffix="…";int end=value.length();while(end>1&&p.measureText(value.substring(0,end)+suffix)>width)end--;return value.substring(0,end)+suffix;}
    private static String money(long value){return NumberFormat.getIntegerInstance(Locale.KOREA).format(value)+"원";}
    private static void save(Bitmap bitmap,File file)throws IOException{try(FileOutputStream out=new FileOutputStream(file)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("리포트 이미지를 저장할 수 없습니다.");}finally{bitmap.recycle();}}
    private static void launch(Activity activity,ArrayList<Uri> uris,String caption){
        Intent base=new Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/png").putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris).putExtra(Intent.EXTRA_TEXT,caption).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ClipData clip=ClipData.newUri(activity.getContentResolver(),"우리 카드 장부",uris.get(0));for(int i=1;i<uris.size();i++)clip.addItem(new ClipData.Item(uris.get(i)));base.setClipData(clip);
        try{Intent kakao=new Intent(base).setPackage("com.kakao.talk");activity.startActivity(kakao);}catch(ActivityNotFoundException missing){activity.startActivity(Intent.createChooser(base,"리포트 공유"));}
    }
    private ReportSharer(){}
}
