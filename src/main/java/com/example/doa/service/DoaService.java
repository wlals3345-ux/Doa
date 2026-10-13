package com.example.doa.service;

import com.example.doa.dto.Requests.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static com.example.doa.service.Database.number;

@Service
public class DoaService {
    private final Database db; private final PasswordEncoder passwords; private final com.example.doa.config.MoodCatalogue catalogue;
    public DoaService(Database db,PasswordEncoder passwords,com.example.doa.config.MoodCatalogue catalogue) {this.db=db;this.passwords=passwords;this.catalogue=catalogue;}
    public static ResponseStatusException error(int status,String message) {return new ResponseStatusException(HttpStatus.valueOf(status),message);}
    public static void validatePassword(String password) {
        if(password.getBytes(StandardCharsets.UTF_8).length>72)throw error(400,"비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
    }
    public Long current(HttpSession session) {
        Object id=session.getAttribute("memberId"); if(!(id instanceof Long memberId))return null;
        Map<String,Object> m=db.one("SELECT session_version FROM member WHERE member_id=?",memberId);
        if(m==null||!Objects.equals(session.getAttribute("version"),number(m,"session_version"))){session.removeAttribute("memberId");return null;}
        return memberId;
    }
    public long require(HttpSession session) {Long id=current(session);if(id==null)throw error(401,"로그인이 필요합니다.");return id;}
    public Map<String,Object> profile(Long id) {
        if(id==null)return null;
        Map<String,Object> m=db.one("SELECT member_id,login_id,name,email,phone,address,session_version FROM member WHERE member_id=?",id);
        Map<String,Object> p=new LinkedHashMap<>();p.put("memberId",id);p.put("id",m.get("login_id"));
        for(String k:List.of("name","email","phone","address"))p.put(k,m.get(k));return p;
    }
    private void session(long id,HttpSession session) {session.setAttribute("memberId",id);session.setAttribute("version",number(db.one("SELECT session_version FROM member WHERE member_id=?",id),"session_version"));}
    @Transactional public void signup(Signup r,HttpSession session) {
        validatePassword(r.password());
        if(!available(r.id())||db.one("SELECT member_id FROM member WHERE email=?",r.email().toLowerCase(Locale.ROOT))!=null)throw error(409,"이미 사용 중인 아이디 또는 이메일입니다.");
        long id=db.insert("INSERT INTO member(login_id,password,name,email,phone,address) VALUES(?,?,?,?,?,?)",r.id(),passwords.encode(r.password()),r.name().trim(),r.email().toLowerCase(Locale.ROOT),r.phone(),r.address());session(id,session);
    }
    public boolean available(String id) {return db.one("SELECT member_id FROM member WHERE LOWER(login_id)=LOWER(?)",id)==null;}
    public void login(Login r,HttpSession session) {
        validatePassword(r.password());
        Map<String,Object> m=db.one("SELECT member_id,password FROM member WHERE LOWER(login_id)=LOWER(?)",r.id());
        if(m==null||!passwords.matches(r.password(),(String)m.get("password")))throw error(401,"아이디 또는 비밀번호를 확인하세요.");session(number(m,"member_id"),session);
    }
    public List<Map<String,Object>> products() {
        List<Map<String,Object>> result=new ArrayList<>();
        for(Map<String,Object> p:db.rows("SELECT p.*,c.category_name FROM product p JOIN category c ON c.category_id=p.category_id ORDER BY product_id")) {
            Map<String,Object> item=new LinkedHashMap<>();item.put("id",p.get("product_id"));item.put("cat",p.get("category_name"));
            item.put("name",p.get("name"));item.put("price",p.get("price"));item.put("img",p.get("image"));item.put("description",p.get("description"));
            item.put("variants",db.rows("SELECT variant_id,color,size,stock FROM product_variant WHERE product_id=? ORDER BY variant_id",p.get("product_id")));catalogue.enrich(item);result.add(item);
        }return result;
    }
    public List<Map<String,Object>> cart(long id) {
        return db.rows("SELECT c.cart_id AS cartid,v.product_id AS id,v.variant_id AS variantid,v.color,v.size,c.quantity AS qty,v.stock FROM cart c JOIN product_variant v ON v.variant_id=c.variant_id WHERE c.member_id=? ORDER BY c.cart_id",id);
    }
    @Transactional public void addCart(long member,Cart r) {
        // Lock the member row to serialize inserts into a member's cart.
        db.one("SELECT member_id FROM member WHERE member_id=? FOR UPDATE",member);
        Map<String,Object> v=db.one("SELECT variant_id,stock FROM product_variant WHERE product_id=? AND color=? AND size=?",r.productId(),r.color(),r.size());
        if(v==null)throw error(400,"상품 옵션을 확인하세요.");
        Map<String,Object> existing=db.one("SELECT cart_id,quantity FROM cart WHERE member_id=? AND variant_id=?",member,v.get("variant_id"));
        long quantity=r.quantity()+(existing==null?0:number(existing,"quantity"));
        if(quantity>99||quantity>number(v,"stock"))throw error(409,"재고보다 많은 수량을 담을 수 없습니다.");
        if(existing==null)db.exec("INSERT INTO cart(member_id,variant_id,quantity) VALUES(?,?,?)",member,v.get("variant_id"),quantity);
        else db.exec("UPDATE cart SET quantity=? WHERE cart_id=?",quantity,existing.get("cart_id"));
    }
    @Transactional public void quantity(long member,long cartId,int quantity) {
        db.one("SELECT member_id FROM member WHERE member_id=? FOR UPDATE",member);
        Map<String,Object> item=db.one("SELECT v.stock FROM cart c JOIN product_variant v ON v.variant_id=c.variant_id WHERE c.cart_id=? AND c.member_id=?",cartId,member);
        if(item==null)throw error(404,"장바구니 상품이 없습니다.");if(quantity>number(item,"stock"))throw error(409,"재고가 부족합니다.");
        db.exec("UPDATE cart SET quantity=? WHERE cart_id=? AND member_id=?",quantity,cartId,member);
    }
    @Transactional public void removeCart(long member,long cartId) {
        db.one("SELECT member_id FROM member WHERE member_id=? FOR UPDATE",member);
        if(db.exec("DELETE FROM cart WHERE cart_id=? AND member_id=?",cartId,member)==0)throw error(404,"장바구니 상품이 없습니다.");
    }
    public Map<String,Object> outfit(long id) {
        Map<String,Object> p=db.one("SELECT * FROM outfit WHERE outfit_id=?",id);if(p==null)throw error(404,"게시물을 찾을 수 없습니다.");return p;
    }
    private void own(long member,Map<String,Object> row) {if(number(row,"member_id")!=member)throw error(403,"작성자만 변경할 수 있습니다.");}
    public List<Map<String,Object>> posts(Long member) {
        List<Map<String,Object>> result=new ArrayList<>();
        for(Map<String,Object> p:db.rows("SELECT o.*,m.name AS author FROM outfit o JOIN member m ON m.member_id=o.member_id ORDER BY o.created_at DESC,o.outfit_id DESC")) {
            long id=number(p,"outfit_id");Map<String,Object> item=new LinkedHashMap<>();
            item.put("id",id);item.put("title",p.get("title"));item.put("text",p.get("content"));item.put("image",p.get("image"));item.put("author",p.get("author"));
            item.put("mine",member!=null&&number(p,"member_id")==member);item.put("createdAt",p.get("created_at"));
            item.put("tags",db.rows("SELECT product_id FROM outfit_product WHERE outfit_id=? ORDER BY outfit_product_id",id).stream().map(x->x.get("product_id")).toList());
            item.put("likes",number(db.one("SELECT COUNT(*) AS n FROM outfit_like WHERE outfit_id=?",id),"n"));
            item.put("liked",member!=null&&db.one("SELECT like_id FROM outfit_like WHERE outfit_id=? AND member_id=?",id,member)!=null);
            List<Map<String,Object>> comments=new ArrayList<>();
            List<Map<String,Object>> rows=db.rows("SELECT c.*,m.name AS author FROM comment c JOIN member m ON m.member_id=c.member_id WHERE c.outfit_id=? ORDER BY c.created_at,c.comment_id",id);
            for(Map<String,Object> c:rows)if(!c.containsKey("parent_id")){Map<String,Object> comment=comment(c,member);comment.put("replies",rows.stream().filter(x->Objects.equals(x.get("parent_id"),c.get("comment_id"))).map(x->comment(x,member)).toList());comments.add(comment);}
            item.put("comments",comments);result.add(item);
        }return result;
    }
    private Map<String,Object> comment(Map<String,Object> c,Long member) {
        return new LinkedHashMap<>(Map.of("id",c.get("comment_id"),"author",c.get("author"),"text",c.get("content"),"mine",member!=null&&number(c,"member_id")==member));
    }
    @Transactional public long write(long member,Long id,Outfit r) {
        validateImage(r.image());
        for(Long tag:r.tags())if(db.one("SELECT product_id FROM product WHERE product_id=?",tag)==null)throw error(400,"태그 상품을 확인하세요.");
        if(id==null)id=db.insert("INSERT INTO outfit(member_id,title,content,image) VALUES(?,?,?,?)",member,r.title().trim(),r.text().trim(),r.image());
        else {own(member,outfit(id));db.exec("UPDATE outfit SET title=?,content=?,image=?,updated_at=CURRENT_TIMESTAMP WHERE outfit_id=?",r.title().trim(),r.text().trim(),r.image(),id);db.exec("DELETE FROM outfit_product WHERE outfit_id=?",id);}
        for(Long tag:new LinkedHashSet<>(r.tags()))db.exec("INSERT INTO outfit_product(outfit_id,product_id) VALUES(?,?)",id,tag);return id;
    }
    private void validateImage(String image) {
        if(image.matches("assets/(outer|top|pants|skirt|ops|acc)-[0-3]\\.svg"))return;
        String[] parts=image.split(",",2);if(parts.length!=2||!parts[0].matches("data:image/(png|jpeg|webp);base64"))throw error(400,"이미지 형식을 확인하세요.");
        try {byte[] b=Base64.getDecoder().decode(parts[1]);
            boolean png=b.length>8&&b[0]==(byte)137&&b[1]==80&&b[2]==78&&b[3]==71;
            boolean jpeg=b.length>3&&b[0]==(byte)255&&b[1]==(byte)216&&b[2]==(byte)255;
            boolean webp=b.length>12&&new String(b,0,4,StandardCharsets.US_ASCII).equals("RIFF")&&new String(b,8,4,StandardCharsets.US_ASCII).equals("WEBP");
            if(b.length>2*1024*1024||!(parts[0].contains("png")&&png||parts[0].contains("jpeg")&&jpeg||parts[0].contains("webp")&&webp))throw error(400,"2MB 이하 PNG/JPEG/WEBP 이미지를 선택하세요.");
        }catch(IllegalArgumentException e){throw error(400,"이미지를 읽을 수 없습니다.");}
    }
    @Transactional public void deletePost(long member,long id){own(member,outfit(id));db.exec("DELETE FROM outfit WHERE outfit_id=?",id);}
    @Transactional public void like(long member,long id) {
        Map<String,Object> post=db.one("SELECT * FROM outfit WHERE outfit_id=? FOR UPDATE",id);if(post==null)throw error(404,"게시물이 없습니다.");
        if(db.exec("DELETE FROM outfit_like WHERE member_id=? AND outfit_id=?",member,id)==0){db.exec("INSERT INTO outfit_like(member_id,outfit_id) VALUES(?,?)",member,id);notify(number(post,"member_id"),member,id,"LIKE");}
    }
    @Transactional public void addComment(long member,long id,Comment r) {
        Map<String,Object> post=outfit(id);
        if(r.parentId()!=null){Map<String,Object> parent=db.one("SELECT * FROM comment WHERE comment_id=? AND outfit_id=?",r.parentId(),id);
            if(parent==null||parent.containsKey("parent_id"))throw error(400,"답글 대상 댓글을 확인하세요.");if(number(parent,"member_id")!=number(post,"member_id"))notify(number(parent,"member_id"),member,id,"REPLY");}
        db.exec("INSERT INTO comment(member_id,outfit_id,parent_id,content) VALUES(?,?,?,?)",member,id,r.parentId(),r.text().trim());
        notify(number(post,"member_id"),member,id,r.parentId()==null?"COMMENT":"REPLY");
    }
    @Transactional public void deleteComment(long member,long id) {
        Map<String,Object> comment=db.one("SELECT * FROM comment WHERE comment_id=?",id);if(comment==null)throw error(404,"댓글이 없습니다.");own(member,comment);
        db.exec("DELETE FROM comment WHERE comment_id=?",id);
    }
    private void notify(long recipient,long actor,long post,String kind) {
        if(recipient!=actor)db.exec("INSERT INTO notification(member_id,actor_id,outfit_id,kind) VALUES(?,?,?,?)",recipient,actor,post,kind);
    }
    public List<Map<String,Object>> notifications(long member){return db.rows("SELECT n.notification_id AS id,n.outfit_id AS postid,n.kind,n.is_read AS isread,n.created_at AS createdat,m.name AS actor FROM notification n JOIN member m ON m.member_id=n.actor_id WHERE n.member_id=? ORDER BY n.notification_id DESC LIMIT 100",member);}
    public void readNotifications(long member){db.exec("UPDATE notification SET is_read=TRUE WHERE member_id=?",member);}
    @Transactional public long checkout(long member,Order r) {
        db.one("SELECT member_id FROM member WHERE member_id=? FOR UPDATE",member);
        List<Map<String,Object>> items=db.rows("SELECT c.quantity,v.variant_id,v.product_id,v.color,v.size FROM cart c JOIN product_variant v ON v.variant_id=c.variant_id WHERE c.member_id=? ORDER BY v.variant_id",member);
        if(items.isEmpty())throw error(400,"장바구니가 비어 있습니다.");
        long subtotal=0;
        for(Map<String,Object> i:items) {
            Map<String,Object> variant=db.one("SELECT stock FROM product_variant WHERE variant_id=? FOR UPDATE",i.get("variant_id"));
            if(number(variant,"stock")<number(i,"quantity"))throw error(409,"재고가 부족합니다. 장바구니를 확인하세요.");
            Map<String,Object> product=db.one("SELECT name,price FROM product WHERE product_id=?",i.get("product_id"));i.putAll(product);
            subtotal+=number(i,"price")*number(i,"quantity");
        }
        if(subtotal>Integer.MAX_VALUE-3000)throw error(400,"주문 금액이 너무 큽니다.");long shipping=subtotal>=100000?0:3000;
        long order=db.insert("INSERT INTO shop_order(member_id,recipient,address,phone,email,payment,subtotal,shipping,total) VALUES(?,?,?,?,?,?,?,?,?)",member,r.name(),r.address(),r.phone(),r.email(),r.payment(),subtotal,shipping,subtotal+shipping);
        for(Map<String,Object> i:items){db.exec("UPDATE product_variant SET stock=stock-? WHERE variant_id=?",i.get("quantity"),i.get("variant_id"));db.exec("INSERT INTO order_item(order_id,product_id,name,color,size,price,quantity) VALUES(?,?,?,?,?,?,?)",order,i.get("product_id"),i.get("name"),i.get("color"),i.get("size"),i.get("price"),i.get("quantity"));}
        db.exec("DELETE FROM cart WHERE member_id=?",member);return order;
    }
    public List<Map<String,Object>> orders(long member) {
        List<Map<String,Object>> result=new ArrayList<>();for(Map<String,Object> row:db.rows("SELECT * FROM shop_order WHERE member_id=? ORDER BY order_id DESC",member)) {
            Map<String,Object> item=new LinkedHashMap<>(row);item.put("id","DOA-"+row.get("order_id"));item.put("items",db.rows("SELECT * FROM order_item WHERE order_id=?",row.get("order_id")));result.add(item);
        }return result;
    }
}
