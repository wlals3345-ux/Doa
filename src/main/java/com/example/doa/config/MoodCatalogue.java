package com.example.doa.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

@Component
public class MoodCatalogue {
    public record Item(String slug,String category,String name,String nameKo,String nameJa,int price,
        String descriptionKo,String descriptionJa,String sizeGuideKo,String sizeGuideJa,List<String> colors,List<String> sizes) {
        public String image(){return "assets/catalog/"+slug+".png";}
    }
    private final List<Item> items;
    public MoodCatalogue(ObjectMapper json) throws IOException {
        try(var stream=new ClassPathResource("mood-collection.json").getInputStream()) {
            items=List.copyOf(json.readValue(stream,new TypeReference<List<Item>>(){}));
        }
    }
    public List<Item> items(){return items;}
    public void enrich(Map<String,Object> product) {
        items.stream().filter(i->i.image().equals(product.get("img"))).findFirst().ifPresent(i->{
            product.put("nameKo",i.nameKo());product.put("nameJa",i.nameJa());product.put("descriptionJa",i.descriptionJa());
            product.put("sizeGuideKo",i.sizeGuideKo());product.put("sizeGuideJa",i.sizeGuideJa());product.put("featured",true);
        });
    }
}
