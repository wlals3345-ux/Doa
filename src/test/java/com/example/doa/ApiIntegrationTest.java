package com.example.doa;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @MockBean JavaMailSender mail;
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->System.getenv().getOrDefault("DOA_TEST_DB_URL","jdbc:h2:mem:doatest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"));
        r.add("spring.datasource.username",()->System.getenv().getOrDefault("DOA_TEST_DB_USERNAME","sa"));
        r.add("spring.datasource.password",()->System.getenv().getOrDefault("DOA_TEST_DB_PASSWORD",""));
    }
    class Client {
        MockHttpSession session;String csrf;
        Client() throws Exception {refresh();}
        JsonNode refresh() throws Exception {
            MvcResult result=mvc.perform(get("/api/bootstrap").session(session==null?new MockHttpSession():session)).andExpect(status().isOk()).andReturn();
            session=(MockHttpSession)result.getRequest().getSession();JsonNode state=json.readTree(result.getResponse().getContentAsString());csrf=state.get("csrf").asText();return state;
        }
        JsonNode call(String method,String path,Object body,int expected) throws Exception {
            var request=request(org.springframework.http.HttpMethod.valueOf(method),"/api"+path).session(session).header("X-CSRF-TOKEN",csrf).contentType("application/json");
            if(body!=null)request.content(json.writeValueAsString(body));
            var result=mvc.perform(request).andExpect(status().is(expected)).andReturn();String content=result.getResponse().getContentAsString();return content.isBlank()?json.createObjectNode():json.readTree(content);
        }
        String signup() throws Exception {
            String id="user"+UUID.randomUUID().toString().replace("-","").substring(0,12);
            call("POST","/signup",Map.of("id",id,"password","Password123!","name",id,"email",id+"@example.com","phone","01012345678","address","Seoul"),200);return id;
        }
        long post() throws Exception {return call("POST","/outfits",Map.of("title","My outfit","text","A test outfit","image","assets/outer-0.svg","tags",List.of(1,5)),200).get("postId").asLong();}
    }
    @Test void registrationSessionDuplicateAndCartIsolation() throws Exception {
        Client a=new Client();String id=a.signup();assertEquals(id,a.refresh().path("profile").path("id").asText());
        mvc.perform(get("/api/members/availability").param("id",id)).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        a.call("POST","/cart",Map.of("productId",1,"color","IVORY","size","M","quantity",1),200);
        a.call("POST","/cart",Map.of("productId",1,"color","IVORY","size","M","quantity",1),200);
        assertEquals(2,a.refresh().path("cart").get(0).path("qty").asInt());
        Client b=new Client();b.signup();assertEquals(0,b.refresh().path("cart").size());
        long cartId=a.refresh().path("cart").get(0).path("cartid").asLong();b.call("DELETE","/cart/"+cartId,null,404);
        var anonymous=new Client();anonymous.call("POST","/cart",Map.of("productId",1,"color","IVORY","size","M","quantity",1),401);
        mvc.perform(post("/api/cart").session(a.session).contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }
    @Test void authorRightsLikesRepliesNotificationsAndDeleteCascade() throws Exception {
        Client a=new Client();a.signup();long post=a.post();Client b=new Client();b.signup();
        b.call("DELETE","/outfits/"+post,null,403);
        b.call("POST","/outfits/"+post+"/like",null,200);
        JsonNode bp=findPost(b.refresh(),post);assertTrue(bp.path("liked").asBoolean());assertEquals(1,bp.path("likes").asInt());
        var anon=new Client();anon.call("POST","/outfits/"+post+"/like",null,401);
        b.call("POST","/outfits/"+post+"/comments",Map.of("text","Hello"),200);
        long comment=findPost(b.refresh(),post).path("comments").get(0).path("id").asLong();
        a.call("DELETE","/comments/"+comment,null,403);
        a.call("POST","/outfits/"+post+"/comments",Map.of("text","Thanks","parentId",comment),200);
        assertEquals(1,findPost(a.refresh(),post).path("comments").get(0).path("replies").size());
        assertEquals(2,a.refresh().path("notifications").size());assertEquals(1,b.refresh().path("notifications").size());
        a.call("POST","/notifications/read",null,200);assertTrue(a.refresh().path("notifications").get(0).path("isread").asBoolean());
        b.call("DELETE","/comments/"+comment,null,200);assertEquals(0,findPost(b.refresh(),post).path("comments").size());
        b.call("POST","/outfits/"+post+"/like",null,200);assertEquals(0,findPost(b.refresh(),post).path("likes").asInt());
        a.call("DELETE","/outfits/"+post,null,200);assertEquals(0,a.refresh().path("notifications").size());
    }
    @Test void checkoutUsesServerPricesStoresItemsAndStockIsAtomic() throws Exception {
        Client a=new Client();a.signup();
        a.call("POST","/cart",Map.of("productId",2,"color","STONE","size","S","quantity",2),200);
        JsonNode result=a.call("POST","/orders",Map.of("name","Receiver","address","Seoul","phone","01012345678","email","test@example.com","payment","CARD_DEMO","total",1),200);
        JsonNode order=result.path("orders").get(0);assertEquals(278000,order.path("total").asInt());assertEquals(2,order.path("items").get(0).path("quantity").asInt());assertEquals(0,result.path("cart").size());
        assertEquals(18,findVariant(result,2,"STONE","S").path("stock").asInt());
        Client b=new Client();b.signup();b.call("POST","/cart",Map.of("productId",3,"color","STONE","size","L","quantity",20),200);
        a.call("POST","/cart",Map.of("productId",3,"color","STONE","size","L","quantity",1),200);
        a.call("POST","/orders",Map.of("name","Receiver","address","Seoul","phone","01012345678","email","test@example.com","payment","BANK_DEMO"),200);
        b.call("POST","/orders",Map.of("name","Receiver","address","Seoul","phone","01012345678","email","test@example.com","payment","BANK_DEMO"),409);
        assertEquals(0,b.refresh().path("orders").size());assertEquals(1,b.refresh().path("cart").size());
    }
    @Test void recoveryUsesSingleUseTokenAndInvalidatesExistingSessions() throws Exception {
        Client a=new Client();String id=a.signup();Client b=new Client();b.call("POST","/login",Map.of("id",id,"password","Password123!"),200);
        clearInvocations(mail);a.call("POST","/recovery/password",Map.of("email",id+"@example.com"),200);
        var message=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(mail).send(message.capture());
        String token=message.getValue().getText().split("/#reset/")[1].trim();
        a.call("POST","/reset",Map.of("token",token,"password","Changed123!"),200);
        assertTrue(a.refresh().path("profile").isNull());assertTrue(b.refresh().path("profile").isNull());
        a.call("POST","/reset",Map.of("token",token,"password","Changed123!"),400);
        a.call("POST","/login",Map.of("id",id,"password","Password123!"),401);a.call("POST","/login",Map.of("id",id,"password","Changed123!"),200);
    }
    @Test void rejectsInvalidImagesAndBlankPosts() throws Exception {
        Client a=new Client();a.signup();
        a.call("POST","/outfits",Map.of("title","Post","text","Text","image","data:image/png;base64,SGVsbG8=","tags",List.of()),400);
        a.call("POST","/outfits",Map.of("title","   ","text","Text","image","assets/outer-0.svg","tags",List.of()),400);
    }
    static JsonNode findPost(JsonNode state,long id){for(JsonNode p:state.path("posts"))if(p.path("id").asLong()==id)return p;throw new AssertionError("Post missing");}
    static JsonNode findVariant(JsonNode state,long id,String color,String size){for(JsonNode p:state.path("products"))if(p.path("id").asLong()==id)for(JsonNode v:p.path("variants"))if(v.path("color").asText().equals(color)&&v.path("size").asText().equals(size))return v;throw new AssertionError("Variant missing");}
}
