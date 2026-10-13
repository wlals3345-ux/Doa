package com.example.doa.service;

import com.example.doa.mapper.DoaMapper;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class Database {
    private final DoaMapper mapper;
    public Database(DoaMapper mapper) { this.mapper=mapper; }
    public List<Map<String,Object>> rows(String sql,Object...args) {
        return mapper.rows(sql,args).stream().map(row->{Map<String,Object> normalized=new LinkedHashMap<>();
            row.forEach((k,v)->normalized.put(k.toLowerCase(Locale.ROOT),v));return normalized;}).toList();
    }
    public Map<String,Object> one(String sql,Object...args) { return rows(sql,args).stream().findFirst().orElse(null); }
    public int exec(String sql,Object...args) { return mapper.execute(sql,args); }
    public long insert(String sql,Object...args) {
        Map<String,Object> key=new HashMap<>();mapper.insert(sql,key,args);return ((Number)key.get("id")).longValue();
    }
    public static long number(Map<String,Object> row,String key) {return ((Number)row.get(key)).longValue();}
}
