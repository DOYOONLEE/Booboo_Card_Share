package com.doyun.ledger;

import java.time.*;
import java.util.regex.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class RcsParser {
    private static final Pattern TRANSACTION = Pattern.compile("삼성\\s*(?:카드\\s*)?(?:[0-9*]{2,8}\\s*)?(?:해외\\s*)?(?:승인|취소)");
    private static final Pattern DATE = Pattern.compile("(?<![0-9])(?:(20[0-9]{2})[./-])?([0-9]{1,2})[./-]([0-9]{1,2})\\s+([0-9]{1,2}):([0-9]{2})");
    private static final Pattern HEADER = Pattern.compile("(20[0-9]{2})년\\s*([0-9]{1,2})월\\s*([0-9]{1,2})일");
    public static String normalize(String body) {
        return body.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\r\n", "\n").replace('\r','\n').replace('\u00a0',' ').trim();
    }
    public static boolean isTransaction(String body) { return TRANSACTION.matcher(normalize(body)).find(); }
    public static LocalDate headerDate(String text) {
        Matcher m=HEADER.matcher(text);
        if(!m.find())return null;
        try{return LocalDate.of(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)));}
        catch(DateTimeException e){return null;}
    }
    // Read newest to oldest; use the last known year and roll over at a year boundary.
    public static LocalDateTime date(String body, LocalDate anchor, LocalDate header) {
        Matcher m=DATE.matcher(normalize(body)); if(!m.find())return null;
        int month=Integer.parseInt(m.group(2)),day=Integer.parseInt(m.group(3));
        int year=m.group(1)!=null?Integer.parseInt(m.group(1)):header!=null?header.getYear():anchor.getYear();
        if(m.group(1)==null && header==null && month-anchor.getMonthValue()>6)year--;
        try{return LocalDateTime.of(year,month,day,Integer.parseInt(m.group(4)),Integer.parseInt(m.group(5)));}
        catch(DateTimeException e){return null;}
    }
    public static String key(String body, long time) {
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest((time+"|"+normalize(body)).getBytes(StandardCharsets.UTF_8));
            StringBuilder s=new StringBuilder("rcs_");for(byte b:hash)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private RcsParser() {}
}
