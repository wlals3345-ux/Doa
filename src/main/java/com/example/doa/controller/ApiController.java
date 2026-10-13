package com.example.doa.controller;

import com.example.doa.dto.Requests.*;
import com.example.doa.service.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final DoaService service;private final ExchangeService exchange;private final RecoveryService recovery;
    private final Map<String,Window> attempts=new ConcurrentHashMap<>();
    private record Window(long minute,int count) {}
    public ApiController(DoaService service,ExchangeService exchange,RecoveryService recovery){this.service=service;this.exchange=exchange;this.recovery=recovery;}
    private void limit(HttpServletRequest request,String action,int max) {
        long now=Instant.now().getEpochSecond()/60;String key=action+":"+request.getRemoteAddr();
        attempts.entrySet().removeIf(e->e.getValue().minute()<now-1);
        Window w=attempts.compute(key,(k,v)->v==null||v.minute()!=now?new Window(now,1):new Window(now,v.count()+1));
        if(w.count()>max)throw DoaService.error(429,"잠시 후 다시 시도하세요.");
    }
    private Map<String,Object> state(HttpSession session) {
        Long member=service.current(session);Map<String,Object> result=new LinkedHashMap<>();result.put("profile",service.profile(member));
        result.put("cart",member==null?List.of():service.cart(member));result.put("orders",member==null?List.of():service.orders(member));
        result.put("posts",service.posts(member));result.put("notifications",member==null?List.of():service.notifications(member));return result;
    }
    @GetMapping("/bootstrap") Map<String,Object> bootstrap(HttpSession session,CsrfToken csrf){var state=state(session);state.put("products",service.products());state.put("csrf",csrf.getToken());return state;}
    @GetMapping("/state") Map<String,Object> stateEndpoint(HttpSession session){return state(session);}
    @GetMapping("/exchange") Map<String,Object> exchange(){return exchange.current();}
    @GetMapping("/members/availability") Map<String,Object> available(@RequestParam String id){if(!id.matches("[A-Za-z0-9_]{4,50}"))throw DoaService.error(400,"아이디는 영문·숫자·밑줄 4~50자로 입력하세요.");return Map.of("available",service.available(id));}
    @PostMapping("/signup") Map<String,Object> signup(@Valid @RequestBody Signup body,HttpServletRequest request){limit(request,"signup",10);service.signup(body,request.getSession());request.changeSessionId();return state(request.getSession());}
    @PostMapping("/login") Map<String,Object> login(@Valid @RequestBody Login body,HttpServletRequest request){limit(request,"login",20);service.login(body,request.getSession());request.changeSessionId();return state(request.getSession());}
    @PostMapping("/logout") Map<String,Object> logout(HttpSession session){session.invalidate();return Map.of("ok",true);}
    @PostMapping("/cart") Map<String,Object> addCart(@Valid @RequestBody Cart body,HttpSession session){service.addCart(service.require(session),body);return state(session);}
    @PatchMapping("/cart/{id}") Map<String,Object> quantity(@PathVariable long id,@Valid @RequestBody Quantity body,HttpSession session){service.quantity(service.require(session),id,body.quantity());return state(session);}
    @DeleteMapping("/cart/{id}") Map<String,Object> deleteCart(@PathVariable long id,HttpSession session){service.removeCart(service.require(session),id);return state(session);}
    @PostMapping("/outfits") Map<String,Object> create(@Valid @RequestBody Outfit body,HttpSession session){long id=service.write(service.require(session),null,body);var state=state(session);state.put("postId",id);return state;}
    @PutMapping("/outfits/{id}") Map<String,Object> edit(@PathVariable long id,@Valid @RequestBody Outfit body,HttpSession session){service.write(service.require(session),id,body);return state(session);}
    @DeleteMapping("/outfits/{id}") Map<String,Object> delete(@PathVariable long id,HttpSession session){service.deletePost(service.require(session),id);return state(session);}
    @PostMapping("/outfits/{id}/like") Map<String,Object> like(@PathVariable long id,HttpSession session){service.like(service.require(session),id);return state(session);}
    @PostMapping("/outfits/{id}/comments") Map<String,Object> comment(@PathVariable long id,@Valid @RequestBody Comment body,HttpSession session){service.addComment(service.require(session),id,body);return state(session);}
    @DeleteMapping("/comments/{id}") Map<String,Object> deleteComment(@PathVariable long id,HttpSession session){service.deleteComment(service.require(session),id);return state(session);}
    @PostMapping("/notifications/read") Map<String,Object> read(HttpSession session){service.readNotifications(service.require(session));return state(session);}
    @PostMapping("/orders") Map<String,Object> order(@Valid @RequestBody Order body,HttpSession session){long id=service.checkout(service.require(session),body);var state=state(session);state.put("orderId",id);state.put("products",service.products());return state;}
    @PostMapping("/recovery/{kind}") Map<String,Object> recover(@PathVariable String kind,@Valid @RequestBody EmailRequest body,HttpServletRequest request){limit(request,"recover",5);if(!Set.of("id","password").contains(kind))throw DoaService.error(400,"잘못된 요청입니다.");recovery.recover(body.email(),kind.equals("password"));return Map.of("message","가입된 이메일이면 안내 메일을 발송했습니다.");}
    @PostMapping("/reset") Map<String,Object> reset(@Valid @RequestBody Reset body,HttpServletRequest request){limit(request,"reset",10);recovery.reset(body.token(),body.password());return Map.of("message","비밀번호가 변경되었습니다. 다시 로그인하세요.");}
}
