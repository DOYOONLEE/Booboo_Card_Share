package com.doyun.ledger;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
public class RcsParserTest {
 @Test public void escapedSamsungRcsLinesParseWithoutCumulativeAmount(){
  String b="삼성1234승인 김*수\\r\\n5,500원 일시불\\r\\n09/30 16:29 테스트가맹점\\r\\n누적9,593,590원";
  assertTrue(RcsParser.isTransaction(b));assertEquals(Long.valueOf(5500),SmsParser.parse(b).amount);
  assertEquals(LocalDateTime.of(2026,9,30,16,29),RcsParser.date(b,LocalDate.of(2026,9,30),null));
 }
 @Test public void unrelatedSamsungMessagesAreRejected(){assertFalse(RcsParser.isTransaction("삼성전자 상품권 안내\n환불 및 취소 불가"));}
 @Test public void cancellationAndOverseasStaySupported(){assertTrue(RcsParser.isTransaction("삼성1234승인취소\n5,500원\n09/30 16:29 상점"));assertTrue(SmsParser.parse("삼성1234승인취소\n5,500원").cancelled);assertNull(SmsParser.parse("삼성1234해외승인\nUSD 5.00").amount);}
 @Test public void chronologicalYearRollover(){assertEquals(2025,RcsParser.date("12/31 23:59",LocalDate.of(2026,1,1),null).getYear());assertEquals(2024,RcsParser.date("12/31 23:59",LocalDate.of(2026,9,30),LocalDate.of(2024,12,31)).getYear());}
 @Test public void explicitYearAndInvalidDate(){assertEquals(2023,RcsParser.date("2023/09/30 12:30",LocalDate.of(2026,9,30),null).getYear());assertNull(RcsParser.date("02/30 12:00",LocalDate.of(2026,3,1),null));assertNull(RcsParser.date("금액만 있음",LocalDate.now(),null));}
 @Test public void keysStableAcrossLineBreaksAndDistinctAcrossTime(){String a="삼성1234승인\\r\\n5,500원",b="삼성1234승인\n5,500원";assertEquals(RcsParser.key(a,100),RcsParser.key(b,100));assertNotEquals(RcsParser.key(a,100),RcsParser.key(a,200));}
 @Test public void historicalHeaderHasExplicitYear(){assertEquals(LocalDate.of(2024,12,31),RcsParser.headerDate("2024년 12월 31일 화요일"));assertNull(RcsParser.headerDate("9월 30일 수요일"));}
}
