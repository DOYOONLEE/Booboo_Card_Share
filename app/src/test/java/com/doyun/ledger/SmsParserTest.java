package com.doyun.ledger;
import org.junit.Test;
import static org.junit.Assert.*;
public class SmsParserTest {
    @Test public void ignoresCumulativeAmount() {
        assertEquals(Long.valueOf(12500), SmsParser.parse("삼성카드 승인\n12,500원\n누적 1,000,000원").amount);
    }
    @Test public void unknownFormatsStayUnresolved() {
        assertNull(SmsParser.parse("삼성 승인 USD 12.00").amount);
        assertNull(SmsParser.parse("삼성 승인 1,000원 2,000원").amount);
    }
    @Test public void cancellationSubtractsAndOnlyCategorySevenIsExcluded() {
        assertEquals(-3000, SmsParser.signed(3000, true));
        assertEquals(21000, SmsParser.actual(new long[]{10000,0,8000,-2000,0,5000,7000}));
    }
}
