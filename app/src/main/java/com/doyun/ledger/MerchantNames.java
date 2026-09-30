package com.doyun.ledger;

import java.util.Locale;

final class MerchantNames {
    private static final String CATEGORY_PREFIX="merchant_category:";

    static String extract(String body){
        String[] lines=RcsParser.normalize(body).split("\n");
        if(lines.length>2){
            String value=lines[2].replaceFirst("^[0-9]{1,2}[/.-][0-9]{1,2}\\s+[0-9]{1,2}:[0-9]{2}\\s*","").trim();
            if(!value.isEmpty())return value;
        }
        return "삼성카드 이용내역";
    }

    static String categoryKey(String body){return CATEGORY_PREFIX+normalize(extract(body));}
    static boolean same(String first,String second){return normalize(extract(first)).equals(normalize(extract(second)));}
    private static String normalize(String value){return value.trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
}
