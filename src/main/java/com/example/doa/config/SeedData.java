package com.example.doa.config;

import com.example.doa.service.Database;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import static com.example.doa.service.Database.number;

@Component
@org.springframework.core.annotation.Order(0)
public class SeedData implements ApplicationRunner {
    private final Database db;
    public SeedData(Database db){this.db=db;}
    @Transactional public void run(ApplicationArguments args) {
        if(number(db.one("SELECT COUNT(*) AS n FROM product"),"n")>0)return;
        String[] cats={"OUTER","TOP","PANTS","SKIRT","OPS","ACC"};
        String[][] names={{"Structured jacket","Relaxed trench coat","Minimal zip jacket","Wool short coat"},{"Essential cotton tee","Fine knit pullover","Oversized poplin shirt","Ribbed sleeveless"},{"Wide pleated trousers","Straight denim","Cotton easy pants","Tailored slacks"},{"Long column skirt","Pleated midi skirt","Denim skirt","Wrap skirt"},{"Minimal slip dress","Cotton shirt dress","Layered knit dress","Sleeveless long dress"},{"Everyday tote","Canvas shoulder bag","Leather belt","Soft knit scarf"}};
        for(int ci=0;ci<cats.length;ci++) {
            var existing=db.one("SELECT category_id FROM category WHERE category_name=?",cats[ci]);
            long category=existing==null?db.insert("INSERT INTO category(category_name) VALUES(?)",cats[ci]):number(existing,"category_id");
            for(int i=0;i<4;i++) {
                int price=ci==0?129000+i*10000:ci==5?29000+i*10000:49000+i*15000;
                long product=db.insert("INSERT INTO product(category_id,name,price,description,image) VALUES(?,?,?,?,?)",category,names[ci][i],price,"절제된 디테일과 자연스러운 실루엣.","assets/"+cats[ci].toLowerCase()+"-"+i+".svg");
                for(String color:new String[]{"CHARCOAL","IVORY","STONE"})for(String size:new String[]{"OS (ONE SIZE)","S","M","L"})db.exec("INSERT INTO product_variant(product_id,color,size,stock) VALUES(?,?,?,?)",product,color,size,20);
            }
        }
    }
}
