package com.example.doa.service;

import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;

@Service
public class ExchangeService {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final ObjectMapper json=new ObjectMapper();
    private Map<String,Object> cached; private Instant fetched=Instant.EPOCH;
    public static Map<String,Object> average(JsonNode rows,LocalDate today) {
        double sum=0;int count=0;LocalDate first=null,last=null;
        for(JsonNode row:rows) {
            LocalDate date=LocalDate.parse(row.path("date").asText());double rate=row.path("rate").asDouble();
            if(date.isBefore(today.withDayOfMonth(1))||date.isAfter(today)||!Double.isFinite(rate)||rate<=0)continue;
            sum+=rate;count++;if(first==null||date.isBefore(first))first=date;if(last==null||date.isAfter(last))last=date;
        }
        if(count==0)throw new IllegalArgumentException("No rates this month");
        return Map.of("available",true,"rate",sum/count,"from",first.toString(),"to",last.toString(),"month",today.toString().substring(0,7),"days",count,"source","Frankfurter / ECB","sourceUrl","https://frankfurter.dev/","stale",false);
    }
    public synchronized Map<String,Object> current() {
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Seoul"));String month=today.toString().substring(0,7);
        if(cached!=null&&month.equals(cached.get("month"))&&fetched.plus(Boolean.TRUE.equals(cached.get("available"))?Duration.ofHours(6):Duration.ofMinutes(5)).isAfter(Instant.now()))return cached;
        try {
            String url="https://api.frankfurter.dev/v2/rates?base=KRW&quotes=JPY&providers=ecb&from="+today.withDayOfMonth(1)+"&to="+today;
            HttpResponse<String> response=client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6)).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)throw new IllegalStateException("Rates unavailable");
            cached=average(json.readTree(response.body()),today);fetched=Instant.now();return cached;
        } catch(Exception e) {
            if(e instanceof InterruptedException)Thread.currentThread().interrupt();
            if(cached!=null&&month.equals(cached.get("month"))){var stale=new LinkedHashMap<>(cached);stale.put("stale",true);return stale;}
            cached=Map.of("available",false,"month",month,"message","이번 달 환율을 불러오지 못해 원화로 표시합니다.");fetched=Instant.now();return cached;
        }
    }
}
