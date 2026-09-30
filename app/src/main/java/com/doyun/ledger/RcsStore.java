package com.doyun.ledger;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

final class RcsStore extends SQLiteOpenHelper {
    RcsStore(Context c){super(c,"rcs-ledger.db",null,1);}
    public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE messages (id TEXT PRIMARY KEY, time INTEGER NOT NULL, body TEXT NOT NULL)");db.execSQL("CREATE INDEX message_time ON messages(time)");}
    public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}
    void save(String body,long time){ContentValues v=new ContentValues();v.put("id",RcsParser.key(body,time));v.put("time",time);v.put("body",RcsParser.normalize(body));getWritableDatabase().insertWithOnConflict("messages",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    List<MainActivity.Item> read(long from,long until,SharedPreferences labels){
        List<MainActivity.Item> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("messages",new String[]{"id","time","body"},"time >= ? AND time < ?",new String[]{""+from,""+until},null,null,"time DESC, id")){
            while(c.moveToNext()){
                MainActivity.Item item=new MainActivity.Item();item.id=c.getString(0);item.time=c.getLong(1);item.body=c.getString(2);
                SmsParser.Parsed p=SmsParser.parse(item.body);item.amount=p.amount;item.cancelled=p.cancelled;
                item.category=labels.getInt("category_"+item.id,-1);
                if(labels.contains("amount_"+item.id))item.amount=labels.getLong("amount_"+item.id,0);
                if(labels.contains("cancel_"+item.id))item.cancelled=labels.getBoolean("cancel_"+item.id,p.cancelled);
                result.add(item);
            }
        }return result;
    }
}
