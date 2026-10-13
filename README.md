# DOA 쇼핑몰과 Outfit 커뮤니티

Java 21, Spring Boot 3.5, MyBatis, MySQL 8 기반 웹 애플리케이션입니다. 기존 HTML/CSS/JavaScript 화면이 같은 서버의 REST API를 사용합니다. 회원·장바구니·Outfit·주문 데이터는 MySQL에 저장하며, 실제 결제는 연동하지 않습니다.

## 실행

1. JDK 21과 Maven 3.9를 설치하고 `JAVA_HOME`과 Maven `PATH`를 설정합니다. Eclipse에서는 Maven 프로젝트로 가져오고 JDK 21로 실행해도 됩니다.
2. `.env.example`을 `.env`로 복사하고 DB 접속 정보와 비밀번호를 설정합니다. `.env`는 Git에 포함되지 않습니다.
3. MySQL에 **새로운 빈 `doa` 데이터베이스**를 만들고 설정한 사용자에게 해당 DB의 테이블 생성·조회·변경 권한을 부여합니다. Docker가 있다면 `.env` 설정 후 `docker compose up -d db`로 대신 준비할 수 있습니다. 기존 8개 테이블 초안 DB에 자동 마이그레이션하는 스크립트가 아니므로 기존 DB를 그대로 지정하지 마세요.
4. PowerShell에서 `powershell -ExecutionPolicy Bypass -File .\run.ps1`을 실행합니다. 이 스크립트가 `.env` 값을 프로세스 환경 변수로 읽습니다.
5. 브라우저에서 `http://localhost:8080`을 엽니다. `index.html` 더블클릭이나 별도 Live Server에서는 서버 API가 연결되지 않습니다.

직접 Maven을 실행할 때는 DB_URL, DB_USERNAME, DB_PASSWORD 등을 환경 변수로 설정한 후 `mvn spring-boot:run`을 사용합니다. `.env`는 Spring Boot가 자동으로 읽는 파일이 아닙니다. 배포 파일은 `mvn clean package` 후 `target/doa-1.0.0.jar`이며 `java -jar target/doa-1.0.0.jar`로 실행합니다.

## 구현 기능

- 회원가입, 아이디 중복 확인, 서버 입력 검증, BCrypt 비밀번호 해시, 세션 로그인·로그아웃. 새로고침해도 세션은 유지됩니다. 서버를 재시작하면 다시 로그인합니다.
- 회원별 장바구니·주문·내 게시물·좋아요한 Outfit, 상품 옵션별 수량 변경 및 삭제.
- DB 상품 목록/상세·검색·가격 정렬·실제 상품 수에 맞춘 페이지 이동. 기본 예시 상품 24개와 DOA ALTER 상품 8개를 등록합니다. 기본 카테고리는 화면과 동일한 OUTER/TOP/PANTS/SKIRT/OPS/ACC 6개입니다.
- Outfit 작성·수정·삭제, 여러 상품 태그, PNG/JPEG/WEBP 2MB 이하 이미지, 작성자 권한을 서버에서 검증합니다.
- 로그인 사용자 좋아요·취소, 댓글·답글 작성, 본인 댓글·답글 삭제. 원댓글 삭제 시 답글도 삭제합니다.
- 좋아요·댓글·답글 수신 알림과 모두 읽음. 자신의 행동에는 알림을 보내지 않습니다. 알림은 페이지 새로고침 또는 API 작업 후 갱신되며 실시간 푸시가 아닙니다.
- 주문 등록 시 서버 가격 계산, 재고 잠금·차감, 주문상품/가격/배송정보 스냅샷, 장바구니 비우기를 한 트랜잭션으로 처리합니다. 주문에는 CARD_DEMO/BANK_DEMO가 기록되며 결제 승인은 수행하지 않습니다.
- 이메일로 아이디 안내·비밀번호 재설정. SMTP 설정이 필요합니다. 재설정 토큰은 해시로 저장하고 30분 후 만료하며 한 번만 사용할 수 있습니다. 변경 후 기존 로그인 세션도 무효화됩니다. SMTP 미설정 시 발송 불가 메시지를 표시하며 성공한 것처럼 처리하지 않습니다.
- 한국어/일본어 UI. 일본어에서는 ドア 로고를 DOA보다 크게 표시합니다. 사용자 게시글·댓글은 자동 번역하지 않습니다.

## DOA ALTER 상품

`src/main/resources/mood-collection.json`에 아우터 2개, 상의 2개, 데님 2개, 스커트와 가방 각 1개의 상품정보를 저장합니다. 이미지 파일은 `assets/catalog/`에 있습니다. Pinterest 보드의 스타일 비율과 하이파이펑크의 촬영 분위기를 참고한, 새롭게 생성한 성인 한국인 가상 모델과 가상 의류입니다. 실제 타 브랜드 상품 사진이나 모델 사진을 그대로 사용하지 않습니다. 제작 프롬프트와 참고 주소는 `assets/catalog/README.md`에 기록했습니다.

서버 시작 시 상품 이미지 경로를 기준으로 새 상품만 추가하므로 재시작해도 중복 등록하거나 기존 상품·주문·재고를 초기화하지 않습니다. 상품별 실제 이미지 색상만 옵션으로 등록하며, 최신순에서는 이 컬렉션을 먼저 표시합니다. 한국어·일본어 상품명, 설명, 옵션 안내를 지원하고 모든 판매가는 포트폴리오용 예시입니다.

## 엔화 표시

`/api/exchange`는 [Frankfurter API](https://frankfurter.dev/)의 ECB 제공 일별 KRW→JPY 환율을 가져옵니다. 서울 날짜 기준 이번 달 1일부터 현재까지 실제 발표된 값의 **산술평균**을 사용합니다. 휴일은 누락된 자료를 임의 보간하지 않습니다. 아직 월말이 아니면 월 전체 확정 평균이 아닌 월초부터 현재까지의 평균입니다.

일본어 화면의 상품·배송비·합계·주문내역에 `원화 금액 × 평균 JPY/KRW`를 적용하고 1엔 단위로 반올림합니다. 상단 환율 안내문은 표시하지 않으며, `/api/exchange` 응답에서 기준 기간·자료 수·환율·출처를 확인할 수 있습니다. 서버는 6시간 캐시합니다. 이번 달 자료가 없으면 전월 환율을 이번 달 평균으로 사용하지 않고 원화로 표시합니다. 같은 달에 성공한 캐시가 있으면 외부 장애 시 해당 캐시를 사용하고 API 응답의 stale 값을 true로 반환합니다. 상품 원가와 주문 확정 금액은 KRW로 저장하며 엔화는 표시용이므로 과거 주문도 현재 표시 환율로 환산됩니다.

## DB와 API

테이블 정의는 `src/main/resources/schema.sql`에 있습니다. 기존 설계의 8개 테이블에 product_variant, shop_order, order_item, notification, recovery_token을 추가했습니다. PRODUCT.stock 대신 옵션별 PRODUCT_VARIANT.stock으로 재고를 관리하며 CART도 variant_id를 참조합니다. COMMENT.parent_id로 답글을 연결합니다. MEMBER에는 전화번호·주소·세션 무효화 버전을 추가했습니다.

`src/main/java/com/example/doa` 아래 controller, service, mapper, dto, config를 사용합니다. SQL은 서비스 내부의 고정 문장만 MyBatis Mapper로 전달하며 모든 사용자 값은 바인딩합니다. 브라우저 인증은 HttpOnly 세션 쿠키와 CSRF 토큰을 사용합니다. 로그인·가입·복구 요청은 IP별 분당 제한이 있습니다.

읽기: GET /api/bootstrap, /api/state, /api/exchange, /api/members/availability?id=...

변경: POST /api/signup, /api/login, /api/logout, /api/cart, /api/outfits, /api/outfits/{id}/like, /api/outfits/{id}/comments, /api/notifications/read, /api/orders, /api/recovery/{id|password}, /api/reset. PATCH /api/cart/{id}, PUT /api/outfits/{id}, DELETE /api/cart/{id}, /api/outfits/{id}, /api/comments/{id}.

변경 API에는 bootstrap에서 받은 CSRF 값을 `X-CSRF-TOKEN` 헤더에 전달해야 합니다. API는 서버가 확인한 세션 회원의 데이터만 변경합니다.

## 검증

`mvn test`는 MySQL 호환 모드의 H2 테스트 DB에서 권한·CSRF·계정별 데이터·재고·주문 원자성·알림·복구 토큰·이미지 검증·월평균 환율 계산을 검사합니다. 실제 MySQL 테스트 DB에서는 DOA_TEST_DB_URL, DOA_TEST_DB_USERNAME, DOA_TEST_DB_PASSWORD를 설정해 같은 테스트를 실행합니다. 테스트는 예시 상품과 회원을 생성하므로 운영 DB를 지정하면 안 됩니다.

브라우저 통합 테스트는 독립적인 테스트 서버를 실행한 뒤 `npm install` 및 `npm run test:ui`로 실행합니다. 기본 주소는 `http://127.0.0.1:8081`, 브라우저는 설치된 Microsoft Edge이며 DOA_UI_URL과 DOA_BROWSER_CHANNEL 환경 변수로 변경할 수 있습니다. 테스트 안에서는 엔화 계산을 재현하기 위해 환율 응답을 고정하지만 실제 사이트의 환율은 외부 API로 조회합니다. 이 테스트도 테스트 계정·게시물·주문을 생성하므로 운영 서버에서 실행하지 마세요.

외부 공개 시 HTTPS와 COOKIE_SECURE=true, 실제 PUBLIC_URL, SMTP 설정을 적용해야 합니다. 결제 PG, Instagram, 관리자 상품관리, 실시간 푸시 알림은 이 버전에 포함하지 않습니다.
# 3D fitting studio

Open `#fitting` from the navigation or a product's 3D fitting link. A procedural mannequin supports height, weight, shoulder and waist controls; category-based clothes use real catalogue selections and available variant colours. Drag, keyboard arrows, angle slider, preset views, zoom and automatic rotation work locally in WebGL. Three.js 0.170.0 is bundled with its MIT license, with no paid API calls or photo uploads.

This is an approximate styling preview, not a garment digital twin, body scan, cloth simulation or size recommendation. The procedural mesh represents category silhouettes rather than exact product construction. Settings stay in memory during the current page session. Precise product rendering requires per-product 3D garment assets and measured size data.
