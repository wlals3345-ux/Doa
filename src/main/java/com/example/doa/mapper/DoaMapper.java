package com.example.doa.mapper;

import org.apache.ibatis.annotations.*;
import java.util.*;

/** Internal SQL comes only from the service; all request values use bound parameters. */
@Mapper
public interface DoaMapper {
    @SelectProvider(type=Sql.class,method="statement") List<Map<String,Object>> rows(@Param("sql") String sql,@Param("args") Object... args);
    @UpdateProvider(type=Sql.class,method="statement") int execute(@Param("sql") String sql,@Param("args") Object... args);
    @InsertProvider(type=Sql.class,method="statement")
    @Options(useGeneratedKeys=true,keyProperty="key.id")
    int insert(@Param("sql") String sql,@Param("key") Map<String,Object> key,@Param("args") Object... args);
    class Sql {
        public String statement(Map<String,Object> params) {
            String sql=(String)params.get("sql");
            StringBuilder result=new StringBuilder(); int index=0;
            for(char c:sql.toCharArray()) { if(c=='?') result.append("#{args[").append(index++).append("]}"); else result.append(c); }
            return result.toString();
        }
    }
}
