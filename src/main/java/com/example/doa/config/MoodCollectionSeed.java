package com.example.doa.config;

import com.example.doa.service.Database;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import static com.example.doa.service.Database.number;

@Component
@Order(1)
public class MoodCollectionSeed implements ApplicationRunner {
    private final Database db;private final MoodCatalogue catalogue;
    public MoodCollectionSeed(Database db,MoodCatalogue catalogue){this.db=db;this.catalogue=catalogue;}
    @Transactional public void run(ApplicationArguments args) {
        for(var item:catalogue.items()) {
            if(db.one("SELECT product_id FROM product WHERE image=?",item.image())!=null)continue;
            var category=db.one("SELECT category_id FROM category WHERE category_name=?",item.category());
            long cat=category==null?db.insert("INSERT INTO category(category_name) VALUES(?)",item.category()):number(category,"category_id");
            long product=db.insert("INSERT INTO product(category_id,name,price,description,image) VALUES(?,?,?,?,?)",cat,item.name(),item.price(),item.descriptionKo(),item.image());
            for(String color:item.colors())for(String size:item.sizes())db.exec("INSERT INTO product_variant(product_id,color,size,stock) VALUES(?,?,?,?)",product,color,size,20);
        }
    }
}
