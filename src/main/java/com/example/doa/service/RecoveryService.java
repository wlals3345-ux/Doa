package com.example.doa.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.annotation.Transactional;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import static com.example.doa.service.Database.number;

@Service
public class RecoveryService {
    private final Database db;private final JavaMailSender mail;private final PasswordEncoder passwords;
    @Value("${app.public-url}") private String publicUrl;
    @Value("${app.mail-from}") private String from;
    public RecoveryService(Database db,JavaMailSender mail,PasswordEncoder passwords){this.db=db;this.mail=mail;this.passwords=passwords;}
    public static String hash(String token) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    @Transactional public void recover(String email,boolean reset) {
        var member=db.one("SELECT member_id,login_id,email FROM member WHERE email=?",email.toLowerCase(Locale.ROOT));if(member==null)return;
        String body;
        if(reset) {
            byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            db.exec("DELETE FROM recovery_token WHERE member_id=?",member.get("member_id"));
            db.exec("INSERT INTO recovery_token(token_hash,member_id,expires_at) VALUES(?,?,?)",hash(token),member.get("member_id"),java.sql.Timestamp.from(Instant.now().plusSeconds(1800)));
            body="30분 이내에 아래 링크에서 비밀번호를 변경하세요.\n"+publicUrl+"/#reset/"+token;
        }else body="DOA 가입 아이디: "+member.get("login_id");
        SimpleMailMessage message=new SimpleMailMessage();message.setFrom(from);message.setTo((String)member.get("email"));message.setSubject("DOA 계정 안내");message.setText(body);
        // A mail failure rolls back token creation. Never return recovery tokens to the browser.
        mail.send(message);
    }
    @Transactional public void reset(String token,String password) {
        DoaService.validatePassword(password);
        var row=db.one("SELECT member_id FROM recovery_token WHERE token_hash=? AND expires_at>CURRENT_TIMESTAMP FOR UPDATE",hash(token));
        if(row==null)throw DoaService.error(400,"만료되었거나 사용할 수 없는 링크입니다.");long member=number(row,"member_id");
        db.exec("UPDATE member SET password=?,session_version=session_version+1 WHERE member_id=?",passwords.encode(password),member);
        db.exec("DELETE FROM recovery_token WHERE member_id=?",member);
    }
}
