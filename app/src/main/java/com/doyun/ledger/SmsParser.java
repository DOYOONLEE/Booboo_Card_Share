package com.doyun.ledger;

import java.util.regex.*;

/** Keeps unknown formats visible instead of inventing a transaction amount. */
public final class SmsParser {
    private static final Pattern MONEY = Pattern.compile("(?<![\\d,.])([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)\\s*원");
    public static final class Parsed {
        public final Long amount;
        public final boolean cancelled;
        public Parsed(Long amount, boolean cancelled) { this.amount = amount; this.cancelled = cancelled; }
    }
    public static boolean isSamsungTransaction(String body) {
        return body.contains("삼성") && (body.contains("승인") || body.contains("취소"));
    }
    public static Parsed parse(String body) {
        body = RcsParser.normalize(body);
        boolean cancel = body.contains("취소");
        Long amount = null;
        for (String line : body.split("\\r?\\n")) {
            if (line.contains("누적") || line.contains("한도") || line.contains("잔액") || line.contains("청구")) continue;
            Matcher matcher = MONEY.matcher(line);
            while (matcher.find()) {
                if (amount != null) return new Parsed(null, cancel);
                try { amount = Long.parseLong(matcher.group(1).replace(",", "")); }
                catch (NumberFormatException ignored) { return new Parsed(null, cancel); }
            }
        }
        // Foreign-currency and ambiguous messages require manual confirmation.
        return new Parsed(amount, cancel);
    }
    public static long signed(long amount, boolean cancelled) { return cancelled ? -amount : amount; }
    public static long actual(long[] categoryTotals) {
        long sum = 0;
        for (long value : categoryTotals) sum = Math.addExact(sum, value);
        return Math.subtractExact(sum, categoryTotals[6]);
    }
}
