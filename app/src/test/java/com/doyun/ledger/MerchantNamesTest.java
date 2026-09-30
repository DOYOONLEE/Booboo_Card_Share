package com.doyun.ledger;

import org.junit.Test;
import static org.junit.Assert.*;

public class MerchantNamesTest {
    @Test public void extractsMerchantAfterTimestamp(){assertEquals("이삭토스트하남미",MerchantNames.extract("삼성1234승인\n5,500원\n09/30 16:29 이삭토스트하남미\n누적 10,000원"));}
    @Test public void matchingIgnoresCaseAndRepeatedWhitespace(){assertTrue(MerchantNames.same("승인\n1,000원\n09/30 10:00 Test   Shop","승인\n2,000원\n09/29 10:00 test shop"));}
    @Test public void categoryKeyIsStableAcrossTransactionDates(){assertEquals(MerchantNames.categoryKey("승인\n1,000원\n09/30 10:00 상점"),MerchantNames.categoryKey("승인\n2,000원\n09/29 11:00 상점"));}
}
