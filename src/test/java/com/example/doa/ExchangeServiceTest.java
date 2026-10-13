package com.example.doa;

import com.example.doa.service.ExchangeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class ExchangeServiceTest {
    @Test void averagesOnlyPublishedRatesInCurrentMonth() throws Exception {
        var rows=new ObjectMapper().readTree("""
          [{"date":"2026-09-30","rate":0.8},{"date":"2026-10-01","rate":0.10},
           {"date":"2026-10-02","rate":0.12},{"date":"2026-10-14","rate":0.9}]
          """);
        var result=ExchangeService.average(rows,LocalDate.of(2026,10,13));
        assertEquals(0.11,(double)result.get("rate"),0.000001);assertEquals(2,result.get("days"));assertEquals("2026-10-02",result.get("to"));
    }
    @Test void neverUsesPreviousMonthAsCurrentMonthRate() throws Exception {
        var rows=new ObjectMapper().readTree("[{\"date\":\"2026-09-30\",\"rate\":0.1}]");
        assertThrows(IllegalArgumentException.class,()->ExchangeService.average(rows,LocalDate.of(2026,10,1)));
    }
}
