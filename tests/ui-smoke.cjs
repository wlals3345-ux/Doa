const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const base=process.env.DOA_UI_URL||'http://127.0.0.1:8081';
async function main(){
  const browser=await chromium.launch({channel:process.env.DOA_BROWSER_CHANNEL||'msedge',headless:true});
  const errors=[];
  async function page(){
    const context=await browser.newContext();const page=await context.newPage();
    page.on('pageerror',e=>errors.push(e.message));
    // Deterministic fixture only inside this test; live rates are tested separately.
    await page.route('**/api/exchange',r=>r.fulfill({json:{available:true,rate:0.1,from:'2026-10-01',to:'2026-10-13',month:'2026-10',days:9,source:'TEST FIXTURE',stale:false}}));
    await page.goto(base);await page.locator('.hero').waitFor();return page;
  }
  async function signup(p,id,japanese=false){
    await p.goto(base+'/#signup');await p.locator('#signup-form').waitFor();
    for(const [key,value]of Object.entries({id,password:'Password123!',confirm:'Password123!',name:id,phone:'01012345678',email:id+'@example.com',address:'Seoul'}))await p.locator(`[name="${key}"]`).fill(value);
    await p.locator('#check-id').click();await p.getByText(japanese?'使用できるIDです。':'사용 가능한 아이디입니다.',{exact:true}).waitFor();
    await p.locator('#signup-form button.primary').click();await p.getByText(japanese?'会員登録が完了しました。':'회원가입이 완료되었습니다.',{exact:true}).waitFor();
  }
  try{
    const a=await page(),b=await page();const suffix=Date.now().toString();
    await signup(a,'alpha'+suffix);await a.reload();await a.locator('#account-link').filter({hasText:'ACCOUNT'}).waitFor();
    await a.goto(base+'/#product/1');await a.locator('#options').waitFor();await a.locator('[name=color]').selectOption('IVORY');await a.locator('[name=size]').selectOption('M');
    await a.locator('#language').selectOption('ja');assert.equal(await a.locator('[name=color]').inputValue(),'IVORY');assert.equal(await a.locator('[name=size]').inputValue(),'M');
    await a.locator('.price').filter({hasText:'¥12,900'}).waitFor();
    const sizes=await a.locator('header .logo').evaluate(el=>[parseFloat(getComputedStyle(el.querySelector('.logo-ja')).fontSize),parseFloat(getComputedStyle(el.querySelector('.logo-en')).fontSize)]);assert(sizes[0]>sizes[1]);
    await a.locator('button[value=cart]').click();await a.locator('#cart-count').filter({hasText:'1'}).waitFor();
    await a.goto(base+'/#checkout');await a.locator('#checkout-form').waitFor();await a.locator('#checkout-form button.primary').click();await a.getByText('注文を保存しました。',{exact:true}).waitFor();
    await a.goto(base+'/#account/orders');await a.locator('summary').filter({hasText:'DOA-'}).waitFor();
    await a.locator('#language').selectOption('ko');
    await a.goto(base+'/#write');await a.locator('#write-form').waitFor();await a.locator('[name=title]').fill('UI outfit '+suffix);await a.locator('[name=text]').fill('<script>window.bad=1</script>');await a.locator('[name=tags]').selectOption(['1','5']);await a.locator('#write-form button.primary').click();await a.locator('.post-view').waitFor();
    const postUrl=a.url();assert.equal(await a.evaluate(()=>window.bad),undefined);const postId=postUrl.split('/').pop();
    await signup(b,'beta'+suffix);await b.goto(postUrl);await b.locator('.post-view').waitFor();assert.equal(await b.locator('#delete-post').count(),0);
    await b.locator('[data-like]').click();await b.locator('[data-like]').filter({hasText:'♥ 1'}).waitFor();
    await b.locator('#comment-form input').fill('Great outfit');await b.locator('#comment-form button.primary').click();await b.getByText('Great outfit',{exact:true}).waitFor();
    await a.reload();await a.locator('.post-view').waitFor();assert.equal(await a.locator('[data-delete-comment]').count(),0);
    await a.locator('[data-reply]').click();await a.locator('#comment-form input').fill('Thank you');await a.locator('#comment-form button.primary').click();await a.getByText('Thank you',{exact:true}).waitFor();
    await b.reload();await b.getByText('Thank you',{exact:true}).waitFor();assert.equal(await b.locator('[data-delete-comment]').count(),1);
    b.once('dialog',d=>d.accept());await b.locator('[data-delete-comment]').click();await b.getByText('Great outfit',{exact:true}).waitFor({state:'detached'});
    await a.goto(base+'/#account/notifications');await a.locator('#read-notifications').waitFor();await a.locator('#read-notifications').click();await a.locator('#notifications-link').filter({hasText:'알림 0'}).waitFor();
    await b.goto(base+'/#account/likes');await b.getByText('UI outfit '+suffix,{exact:true}).waitFor();
    await a.goto(base+'/#account/info');await a.locator('#logout').click();await a.locator('.hero').waitFor();
    await a.goto(base+'/#login');await a.locator('#login-form').waitFor();await a.locator('[name=id]').fill('alpha'+suffix);await a.locator('[name=password]').fill('Password123!');await a.locator('#login-form button.primary').click();await a.locator('#logout').waitFor();
    await a.goto(base+'/#shop');await a.locator('.grid').waitFor();
    for(const width of [1440,968,720,390,360]){
      await a.setViewportSize({width,height:844});
      await a.waitForFunction(()=>[...document.querySelectorAll('.product img')].every(img=>img.complete&&img.naturalWidth>0));
      assert(await a.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));
      assert(await a.locator('.product .image').evaluateAll(els=>els.every(el=>el.getBoundingClientRect().height>100)));
    }
    await a.setViewportSize({width:390,height:844});
    fs.mkdirSync('test-results',{recursive:true});await a.screenshot({path:'test-results/mobile-shop.png',fullPage:true});
    await a.setViewportSize({width:1440,height:1000});await a.locator('#language').selectOption('ja');await a.screenshot({path:'test-results/japanese-shop.png',fullPage:true});
    const c=await page();await c.locator('#language').selectOption('ja');await signup(c,'japan'+suffix,true);
    await c.goto(base+'/#account/info');await c.locator('#logout').click();await c.locator('.hero').waitFor();
    await c.goto(base+'/#login');await c.locator('#login-form').waitFor();await c.locator('[name=id]').fill('japan'+suffix);await c.locator('[name=password]').fill('Password123!');await c.locator('#login-form button.primary').click();await c.locator('#logout').waitFor();
    await c.locator('#language').selectOption('ko');await c.getByText('아이디 · japan'+suffix,{exact:true}).waitFor();
    assert.deepEqual(errors,[]);console.log('UI PASS: Korean/Japanese signup/login and cross-language accounts, sessions, options, logo/yen, orders, authorship, likes, replies, notifications, mobile layout.');
  }finally{await browser.close();}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
