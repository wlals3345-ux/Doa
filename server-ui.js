// API adapter and server-backed views. The browser never owns authentication or author permissions.
let ready=false;
const serverDate=v=>new Date(v).toLocaleString(language==='ja'?'ja-JP':'ko-KR');
function applyState(data){
  if('profile'in data){profile=data.profile;logged=!!profile;}
  if(data.cart)cart=data.cart;if(data.posts)posts=data.posts;if(data.orders)orders=data.orders;
  if(data.notifications)notifications=data.notifications;if(data.products)products=data.products;
  if(data.csrf)csrf=data.csrf;updateHeader();
}
async function api(path,method='GET',body){
  const response=await fetch('/api'+path,{method,credentials:'same-origin',headers:{'Content-Type':'application/json','X-CSRF-TOKEN':csrf},body:body===undefined?undefined:JSON.stringify(body)});
  const data=await response.json().catch(()=>({}));
  if(!response.ok){if(response.status===401){profile=null;logged=false;cart=[];orders=[];notifications=[];updateHeader();}throw new Error(t(data.message||(response.status===403?'요청이 만료되었습니다. 새로고침 후 다시 시도하세요.':'요청을 처리하지 못했습니다.')));}
  return data;
}
function needLogin(){if(logged)return true;toast(t('로그인이 필요합니다.'));location.hash='login';return false;}
function go(hash){if(location.hash==='#'+hash)route();else location.hash=hash;}
function action(el,handler){el.onclick=async()=>{if(el.disabled)return;el.disabled=true;try{await handler();}catch(e){toast(e.message);}finally{el.disabled=false;}};}
function form(id,handler){const el=document.getElementById(id);el.onsubmit=async event=>{event.preventDefault();if(el.dataset.busy)return;el.dataset.busy='true';document.querySelector('#language').disabled=true;const buttons=[...el.querySelectorAll('button:not([type=button])')];buttons.forEach(b=>b.disabled=true);try{await handler(new FormData(el),event);}catch(e){const error=el.querySelector('.error');if(error)error.textContent=e.message;else toast(e.message);}finally{delete el.dataset.busy;document.querySelector('#language').disabled=false;buttons.forEach(b=>b.disabled=false);}};}
function fields(items,values={}){return items.map(([name,label,type='text'])=>`<label>${t(label)}<input name="${name}" type="${type}" required ${type==='password'?'minlength="8" maxlength="72"':''} value="${type==='password'?'':esc(values[name]||'')}"></label>`).join('');}
const originalHeader=updateHeader;
updateHeader=function(){
  originalHeader();const link=document.querySelector('#notifications-link');link.hidden=!logged;
  link.textContent=t('알림')+' '+notifications.filter(n=>!n.isread).length;

};
const originalDetail=detail;
detail=function(id){
  originalDetail(id);const p=products.find(x=>x.id===id);if(!p)return;
  const el=document.querySelector('#options');el.insertAdjacentHTML('beforeend','<p id="stock" class="muted"></p><p class="error"></p>');
  el.elements.color.onchange=()=>{el.elements.size.innerHTML=`<option value="">${t('사이즈를 선택하세요')}</option>`+p.variants.filter(v=>v.color===el.elements.color.value).map(v=>`<option value="${esc(v.size)}" ${v.stock===0?'disabled':''}>${esc(t(v.size))}${v.stock===0?' / '+t('품절'):''}</option>`).join('');document.querySelector('#stock').textContent='';};
  el.elements.size.innerHTML=`<option value="">${t('사이즈를 선택하세요')}</option>`;
  el.elements.size.onchange=()=>{const v=p.variants.find(v=>v.color===el.elements.color.value&&v.size===el.elements.size.value);document.querySelector('#stock').textContent=v?t('재고')+' '+v.stock:'';};
  form('options',async(d,e)=>{if(!needLogin())return;applyState(await api('/cart','POST',{productId:id,color:d.get('color'),size:d.get('size'),quantity:1}));if(e.submitter.value==='buy')go('checkout');else toast(t('장바구니에 담았습니다.'));});
  // Shipping thresholds are stored in KRW, but displayed in the selected currency.
  const details=app.querySelectorAll('details p');details[1].textContent=`${t('배송비')} ${money(3000)} · ${money(100000)} ${t('이상 무료배송')}`;
};
function cartPage(){
  if(!needLogin())return;
  app.innerHTML=`<section class="section"><h1>${t('Shopping bag')}</h1>${cart.length?cart.map(x=>{const p=products.find(p=>p.id===x.id);return `<div class="cart-item"><a href="#product/${p.id}"><img src="${esc(p.img)}" alt="${esc(productName(p))}"></a><div>${esc(productName(p))}<p>${esc(t(x.color))} / ${esc(t(x.size))}</p><div class="quantity"><button data-qty="${x.cartid}" data-delta="-1" aria-label="${t('수량 감소')}">−</button>${x.qty}<button data-qty="${x.cartid}" data-delta="1" aria-label="${t('수량 증가')}">+</button><button data-remove="${x.cartid}" aria-label="${t('상품 삭제')}">✕</button></div></div><strong>${money(p.price*x.qty)}</strong></div>`;}).join('')+totals():`<div class="empty"><p>${t('장바구니가 비어 있습니다.')}</p><a href="#shop" class="primary">${t('쇼핑하러 가기')}</a></div>`}</section>`;
  app.querySelectorAll('[data-qty]').forEach(b=>action(b,async()=>{const x=cart.find(x=>x.cartid===+b.dataset.qty);applyState(await api('/cart/'+x.cartid,'PATCH',{quantity:Math.max(1,x.qty+(+b.dataset.delta))}));cartPage();}));
  app.querySelectorAll('[data-remove]').forEach(b=>action(b,async()=>{applyState(await api('/cart/'+b.dataset.remove,'DELETE'));cartPage();}));
}
function login(){
  if(logged){go('account');return;}
  app.innerHTML=`<section class="form-page"><h1>${t('LOGIN')}</h1><form id="login-form">${fields([['id','아이디'],['password','비밀번호','password']])}<p class="error"></p><button class="primary">${t('로그인')}</button></form><a class="secondary" href="#signup">${t('회원가입')}</a><div class="form-links"><a href="#find/id">${t('아이디 찾기')}</a><a href="#find/password">${t('비밀번호 찾기')}</a></div></section>`;
  form('login-form',async d=>{applyState(await api('/login','POST',Object.fromEntries(d)));go('account');});
}
function signup(){
  app.innerHTML=`<section class="form-page"><h1>${t('JOIN DOA')}</h1><form id="signup-form">${fields([['id','아이디'],['password','비밀번호 (8자 이상)','password'],['confirm','비밀번호 확인','password'],['name','이름'],['phone','휴대폰 번호','tel'],['email','이메일','email'],['address','주소']])}<button type="button" id="check-id" class="secondary">${t('아이디 중복 확인')}</button><p id="id-result" role="status"></p><p class="error"></p><button class="primary">${t('회원가입')}</button></form></section>`;
  action(document.querySelector('#check-id'),async()=>{const id=document.querySelector('#signup-form').elements.id.value;const r=await api('/members/availability?id='+encodeURIComponent(id));document.querySelector('#id-result').textContent=t(r.available?'사용 가능한 아이디입니다.':'이미 사용 중인 아이디입니다.');});
  form('signup-form',async d=>{if(d.get('password')!==d.get('confirm'))throw new Error(t('비밀번호가 일치하지 않습니다.'));const body=Object.fromEntries(d);delete body.confirm;applyState(await api('/signup','POST',body));renderSignupComplete();});
}
function account(tab='info'){
  if(!needLogin())return;let content='';
  if(tab==='orders')content=`<h2>${t('주문조회')}</h2>`+(orders.map(o=>`<details><summary>${esc(o.id)} · ${money(o.total)} · ${serverDate(o.created_at)}</summary><p>${t('받는 사람')} · ${esc(o.recipient)}<br>${t('배송지')} · ${esc(o.address)}</p>${o.items.map(x=>`<p>${esc(productName({name:x.name}))} / ${esc(t(x.color))} / ${esc(t(x.size))} × ${x.quantity} · ${money(x.price*x.quantity)}</p>`).join('')}<p>${t('상품 금액')} ${money(o.subtotal)} / ${t('배송비')} ${money(o.shipping)}</p><p>${t('실제 결제는 진행되지 않습니다.')}</p></details>`).join('')||`<p>${t('주문 내역이 없습니다.')}</p>`);
  else if(tab==='posts'||tab==='likes'){const list=posts.filter(p=>tab==='posts'?p.mine:p.liked);content=`<h2>${t(tab==='posts'?'내 게시물':'좋아요한 Outfit')}</h2><div class="outfits">${list.map(p=>`<article class="outfit"><a href="#post/${p.id}"><img src="${esc(p.image)}" alt="${esc(p.title)}"><h3>${esc(p.title)}</h3></a></article>`).join('')||`<p>${t('게시물이 없습니다.')}</p>`}</div>`;}
  else if(tab==='notifications')content=`<div class="row"><h2>${t('알림')}</h2><button id="read-notifications">${t('모두 읽음')}</button></div>${notifications.map(n=>`<p class="notification ${n.isread?'':'unread'}"><a href="#post/${n.postid}">${esc(n.actor)} · ${t(n.kind==='LIKE'?'좋아요':n.kind==='REPLY'?'답글':'댓글')} · ${serverDate(n.createdat)}</a></p>`).join('')||`<p>${t('새 알림이 없습니다.')}</p>`}`;
  else content=`<h2>${t('회원정보')}</h2>${['id','name','email','phone','address'].map((k,i)=>`<p>${t(['아이디','이름','이메일','휴대폰 번호','주소'][i])} · ${esc(profile[k])}</p>`).join('')}`;
  app.innerHTML=`<section class="section"><h1>${esc(profile.name)}${t('님, 안녕하세요.')}</h1><div class="category-bar">${[['info','회원정보'],['orders','주문조회'],['posts','내 게시물'],['likes','좋아요한 Outfit'],['notifications','알림']].map(([k,label])=>`<a href="#account/${k}" class="${tab===k?'active':''}">${t(label)}</a>`).join('')}<a href="#cart">${t('장바구니')}</a><button id="logout">${t('로그아웃')}</button></div>${content}</section>`;
  action(document.querySelector('#logout'),async()=>{await api('/logout','POST');applyState(await api('/bootstrap'));go('home');});
  const read=document.querySelector('#read-notifications');if(read)action(read,async()=>{applyState(await api('/notifications/read','POST'));account('notifications');});
}
function find(kind='id'){
  app.innerHTML=`<section class="form-page"><h1>${t(kind==='id'?'아이디 찾기':'비밀번호 찾기')}</h1><form id="find-form">${fields([['email','이메일','email']])}<p class="error"></p><button class="primary">${t('안내 메일 받기')}</button></form><p id="find-result" role="status"></p><a href="#login">${t('로그인으로 돌아가기')}</a></section>`;
  form('find-form',async d=>{const r=await api('/recovery/'+kind,'POST',{email:d.get('email')});document.querySelector('#find-result').textContent=t(r.message);});
}
function resetPassword(token){
  app.innerHTML=`<section class="form-page"><h1>${t('비밀번호 변경')}</h1><form id="reset-form">${fields([['password','비밀번호 (8자 이상)','password'],['confirm','비밀번호 확인','password']])}<p class="error"></p><button class="primary">${t('비밀번호 변경')}</button></form></section>`;
  form('reset-form',async d=>{if(d.get('password')!==d.get('confirm'))throw new Error(t('비밀번호가 일치하지 않습니다.'));await api('/reset','POST',{token,password:d.get('password')});applyState(await api('/bootstrap'));go('login');toast(t('비밀번호가 변경되었습니다. 다시 로그인하세요.'));});
}
function checkout(){
  if(!needLogin())return;if(!cart.length){go('cart');return;}
  app.innerHTML=`<section class="form-page"><h1>${t('주문서')}</h1><p>${t('실제 결제는 진행되지 않습니다.')}</p><form id="checkout-form">${fields([['name','받는 사람'],['address','배송지'],['phone','전화번호','tel'],['email','이메일','email']],profile)}<label>${t('결제 방법')}<select name="payment"><option value="CARD_DEMO">${t('신용카드 (시연)')}</option><option value="BANK_DEMO">${t('무통장 입금 (시연)')}</option></select></label><p>${t('총 금액 ')}${money(subtotal()+shipping())}</p><p class="error"></p><button class="primary">${t('주문 등록')}</button></form></section>`;
  form('checkout-form',async d=>{applyState(await api('/orders','POST',Object.fromEntries(d)));renderCheckoutComplete();});
}
function renderCheckoutComplete(){app.dataset.completion='checkout';app.innerHTML=`<section class="empty"><h1>${t('주문이 저장되었습니다.')}</h1><p>${t('실제 결제는 진행되지 않습니다.')}</p><a class="primary" href="#account/orders">${t('주문조회')}</a></section>`;updateHeader();}
function bindLikes(){app.querySelectorAll('[data-like]').forEach(b=>action(b,async()=>{if(!needLogin())return;applyState(await api('/outfits/'+b.dataset.like+'/like','POST'));if(location.hash.startsWith('#post/'))post(+b.dataset.like);else community();}));}
function commentMarkup(c){return `<div class="comment"><strong>${esc(c.author)}</strong><p>${esc(c.text)}</p>${c.mine?`<button data-delete-comment="${c.id}">${t('삭제')}</button>`:''}</div>`;}
function post(id){
  const p=posts.find(p=>p.id===id);if(!p){go('community');return;}
  const oldReply=app.dataset.reply;delete app.dataset.reply;
  app.innerHTML=`<section class="section post-view"><a href="#community">${t('← ALL OUTFITS')}</a><h1>${esc(p.title)}</h1><p>${esc(p.author)} · ${serverDate(p.createdAt)}</p><img src="${esc(p.image)}" alt="${esc(p.title)}"><p class="post-text">${esc(p.text)}</p><button class="like" data-like="${id}">${p.liked?'♥':'♡'} ${p.likes}</button>${p.mine?` <a class="secondary" href="#write/${id}">${t('수정')}</a> <button id="delete-post" class="secondary">${t('삭제')}</button>`:''}<h3>${t('WORN IN THIS OUTFIT')}</h3>${p.tags.map(tag=>`<button class="tag" data-tag="${tag}">${esc(productName(products.find(x=>x.id===tag)))}</button>`).join('')}<div id="tag-info"></div><h3>${t('COMMENTS')}</h3>${p.comments.map(c=>`${commentMarkup(c)}<button data-reply="${c.id}">${t('답글 달기')}</button><div class="reply">${c.replies.map(commentMarkup).join('')}</div>`).join('')}<form id="comment-form"><label id="comment-label">${t('댓글 작성')}<input name="text" maxlength="500" required></label><p class="error"></p><button class="primary">${t('등록')}</button><button type="button" id="cancel-reply" hidden>${t('답글 취소')}</button></form></section>`;
  bindLikes();let reply=null;
  app.querySelectorAll('[data-tag]').forEach(b=>b.onclick=()=>{const x=products.find(x=>x.id===+b.dataset.tag);document.querySelector('#tag-info').innerHTML=`<div class="tag-info"><strong>${esc(productName(x))}</strong><p>${money(x.price)}</p><a class="primary" href="#product/${x.id}">${t('상품 상세보기 ↗')}</a></div>`;});
  app.querySelectorAll('[data-reply]').forEach(b=>b.onclick=()=>{if(!needLogin())return;reply=+b.dataset.reply;app.dataset.reply=String(reply);document.querySelector('#comment-label').firstChild.textContent=t('답글 작성');document.querySelector('#cancel-reply').hidden=false;document.querySelector('#comment-form input').focus();});
  document.querySelector('#cancel-reply').onclick=()=>{reply=null;delete app.dataset.reply;document.querySelector('#comment-label').firstChild.textContent=t('댓글 작성');document.querySelector('#cancel-reply').hidden=true;};
  form('comment-form',async d=>{if(!needLogin())return;const text=d.get('text').trim();if(!text)return;applyState(await api('/outfits/'+id+'/comments','POST',{text,parentId:reply}));delete app.dataset.reply;post(id);});
  app.querySelectorAll('[data-delete-comment]').forEach(b=>action(b,async()=>{if(confirm(t('댓글과 해당 답글을 삭제할까요?'))){applyState(await api('/comments/'+b.dataset.deleteComment,'DELETE'));post(id);}}));
  if(p.mine)action(document.querySelector('#delete-post'),async()=>{if(confirm(t('게시물을 삭제할까요?'))){applyState(await api('/outfits/'+id,'DELETE'));go('community');}});
  if(oldReply)app.querySelector(`[data-reply="${oldReply}"]`)?.click();
}
function write(id){
  if(!needLogin())return;const p=id?posts.find(p=>p.id===+id):null;if(id&&(!p||!p.mine)){go('community');return;}
  app.innerHTML=`<section class="form-page"><h1>${t(p?'EDIT':'SHARE')}${t(' YOUR OUTFIT')}</h1><form id="write-form"><label>${t('제목')}<input name="title" maxlength="100" required value="${esc(p?.title||'')}"></label><label>${t('이미지 업로드')}<input name="image" type="file" accept="image/png,image/jpeg,image/webp"></label><p>${t('최대 2MB. 업로드하지 않으면 DOA 일러스트가 사용됩니다.')}</p><label>${t('오늘의 이야기')}<textarea name="text" maxlength="2000" required>${esc(p?.text||'')}</textarea></label><label>${t('상품 태그 (여러 개 선택 가능)')}<select name="tags" multiple size="8">${products.map(x=>`<option value="${x.id}" ${p?.tags.includes(x.id)?'selected':''}>${t(x.cat)} / ${esc(productName(x))}</option>`).join('')}</select></label><p class="error"></p><button class="primary">${t(p?'수정':'등록')}</button></form></section>`;
  form('write-form',async d=>{const file=d.get('image');if(file.size>2097152)throw new Error(t('2MB 이하 이미지를 선택하세요.'));if(file.size&&!['image/png','image/jpeg','image/webp'].includes(file.type))throw new Error(t('PNG, JPEG, WEBP 이미지를 선택하세요.'));
    const image=file.size?await new Promise((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(r.result);r.onerror=()=>reject(new Error(t('이미지를 읽을 수 없습니다.')));r.readAsDataURL(file);}):p?.image||'assets/outer-0.svg';
    const state=await api('/outfits'+(p?'/'+id:''),p?'PUT':'POST',{title:d.get('title').trim(),text:d.get('text').trim(),image,tags:d.getAll('tags').map(Number)});applyState(state);go('post/'+(p?id:state.postId));
  });
}
async function startServerUI(){
  app.innerHTML=`<section class="empty">${t('서버에 연결하는 중입니다.')}</section>`;
  try{applyState(await api('/bootstrap'));ready=true;route();api('/exchange').then(data=>{exchange=data;updateHeader();if(language==='ja')refreshCurrency();}).catch(()=>{});}
  catch(e){app.innerHTML=`<section class="empty"><h1>${t('서버에 연결하지 못했습니다.')}</h1><p>${t('Spring Boot 서버 주소에서 사이트를 열어주세요.')}</p><button class="primary" id="retry">${t('다시 시도')}</button></section>`;document.querySelector('#retry').onclick=startServerUI;}
}
function refreshCurrency(){if(app.querySelector('form[data-busy]')){setTimeout(refreshCurrency,250);return;}document.querySelector('#language').dispatchEvent(new Event('change'));}
startServerUI();
