// Procedural 3D styling preview. Measurements describe a visual approximation, not fit prediction.
let disposeFitting=()=>{};
const fitState={height:170,weight:55,shoulder:38,waist:68,rotation:0,zoom:3.6,auto:false,selected:{},colors:{}};
const fitText=(ko,ja)=>language==='ja'?ja:ko;
function fittingRoom(id){
  disposeFitting();
  const slots=['TOP','OUTER','PANTS','SKIRT','OPS','ACC'];
  const target=products.find(p=>p.id===id);
  if(target)fitState.selected[target.cat]=target.id;
  if(!Object.keys(fitState.selected).length){for(const cat of ['TOP','PANTS'])fitState.selected[cat]=products.find(p=>p.cat===cat&&p.featured)?.id||products.find(p=>p.cat===cat)?.id;}
  app.innerHTML=`<section class="section fitting"><div class="eyebrow">DOA / VIRTUAL STUDIO</div><h1>${fitText('나만의 3D 피팅룸','自分だけの3Dフィッティング')}</h1><p class="muted">${fitText('상품을 조합하고, 모델을 돌려 전체 실루엣을 확인해 보세요.','商品を組み合わせ、モデルを回してシルエットを確認できます。')}</p><div class="fit-layout"><div class="fit-stage-wrap"><div id="fit-stage" tabindex="0" role="img" aria-label="${fitText('3D 모델. 좌우 방향키 또는 드래그로 회전','3Dモデル。左右キーまたはドラッグで回転')}"></div><div class="fit-views"><button data-angle="0">${fitText('앞','前')}</button><button data-angle="90">${fitText('옆','横')}</button><button data-angle="180">${fitText('뒤','後')}</button><button id="fit-auto" aria-pressed="${fitState.auto}">${fitText('자동 회전','自動回転')}</button><button id="fit-reset">${fitText('시점 초기화','視点をリセット')}</button></div><label class="fit-angle">360° <input id="fit-angle" type="range" min="0" max="360" value="${fitState.rotation}"><output id="fit-degrees">${Math.round(fitState.rotation)}°</output></label><p class="muted">${fitText('드래그로 회전 · 휠로 확대 · 방향키로 회전','ドラッグで回転・ホイールでズーム・矢印キーで回転')}</p></div><aside class="fit-controls"><h2>${fitText('모델 설정','モデル設定')}</h2>${[['height','키','身長',145,195,'cm'],['weight','몸무게','体重',40,100,'kg'],['shoulder','어깨 너비','肩幅',32,50,'cm'],['waist','허리둘레','ウエスト',55,110,'cm']].map(([key,ko,ja,min,max,unit])=>`<label>${fitText(ko,ja)} <output id="fit-${key}-value">${fitState[key]} ${unit}</output><input data-measure="${key}" type="range" min="${min}" max="${max}" value="${fitState[key]}"></label>`).join('')}<h2>${fitText('옷장','ワードローブ')}</h2><p class="muted">${fitText('원피스를 선택하면 상의·하의 대신 착용합니다.','ワンピースはトップス・ボトムスの代わりに着用します。')}</p>${slots.map(cat=>`<label>${t(cat)}<select data-slot="${cat}"><option value="">${fitText('선택 안 함','なし')}</option>${products.filter(p=>p.cat===cat).map(p=>`<option value="${p.id}" ${fitState.selected[cat]===p.id?'selected':''}>${esc(productName(p))}</option>`).join('')}</select><select data-color="${cat}" aria-label="${t(cat)} ${fitText('색상','カラー')}"></select><a data-product-link="${cat}"></a></label>`).join('')}<p class="fit-note">${fitText('상품의 색상과 카테고리별 형태를 반영한 가상 스타일링입니다. 상품 사진과 동일한 3D 의상이나 실제 체형·사이즈 적합도를 보장하지 않습니다.','商品の色とカテゴリ別の形を反映した仮想スタイリングです。写真と同一の3D衣服や実際の体型・サイズ適合を保証するものではありません。')}</p></aside></div></section>`;
  const host=document.querySelector('#fit-stage');
  let cancelled=false,cleanup=()=>{};
  disposeFitting=()=>{cancelled=true;cleanup();};
  const observer=new MutationObserver(()=>{if(!host.isConnected){disposeFitting();observer.disconnect();}});observer.observe(app,{childList:true});
  import('./assets/vendor/three.module.js').then(THREE=>{if(cancelled)return;cleanup=mountFitting(THREE,host);}).catch(()=>{if(host.isConnected)host.textContent=fitText('3D 화면을 불러오지 못했습니다. 새로고침해 주세요.','3Dを読み込めませんでした。再読み込みしてください。');});
}
function mountFitting(T,host){
  let renderer;try{renderer=new T.WebGLRenderer({antialias:true,alpha:true});}catch{host.textContent=fitText('이 브라우저에서 WebGL을 사용할 수 없습니다. 하드웨어 가속을 켜고 다시 시도해 주세요.','WebGLが利用できません。ハードウェアアクセラレーションを有効にしてください。');return()=>{};}
  renderer.setPixelRatio(Math.min(devicePixelRatio,2));renderer.setClearColor(0xeeeeea);host.append(renderer.domElement);
  const scene=new T.Scene(),camera=new T.PerspectiveCamera(33,1,.01,30),model=new T.Group();scene.add(model);
  scene.add(new T.HemisphereLight(0xffffff,0x777775,3));const light=new T.DirectionalLight(0xffffff,3);light.position.set(3,4,5);scene.add(light);const rim=new T.DirectionalLight(0xd9e2ff,2);rim.position.set(-3,2,-4);scene.add(rim);
  const material=(color,roughness=.8)=>new T.MeshStandardMaterial({color,roughness,side:T.DoubleSide});
  const skin=material(0xc9aaa0),hair=material(0x252321),shoe=material(0x222222),base=material(0xa6a6a0);
  function mesh(geometry,mat,x,y,z,sx=1,sy=1,sz=1){const m=new T.Mesh(geometry,mat);m.position.set(x,y,z);m.scale.set(sx,sy,sz);model.add(m);return m;}
  function oval(mat,x,y,z,sx,sy,sz){return mesh(new T.SphereGeometry(1,32,24),mat,x,y,z,sx,sy,sz);}
  function tube(mat,a,b,r1,r2){const start=new T.Vector3(...a),end=new T.Vector3(...b),delta=end.clone().sub(start);const m=mesh(new T.CylinderGeometry(r2,r1,delta.length(),32),mat,...start.clone().add(end).multiplyScalar(.5).toArray());m.quaternion.setFromUnitVectors(new T.Vector3(0,1,0),delta.normalize());return m;}
  function shell(mat,points,depth=0.65){const m=mesh(new T.LatheGeometry(points.map(p=>new T.Vector2(...p)),48),mat,0,0,0);m.scale.z=depth;return m;}
  const palette={'BLACK':0x242427,'WASHED BLACK':0x333438,'CHARCOAL':0x4a4a4e,'IVORY':0xe5e0d6,'STONE':0xaaa398,'SMOKE GREY':0x797a80,'ESPRESSO':0x62534e,'VINTAGE BLUE':0x52677b,'FADED BLACK':0x45474b};
  function selected(cat){return products.find(p=>p.id===fitState.selected[cat]);}
  function fabric(cat){const p=selected(cat);return material(palette[fitState.colors[cat]]??(cat==='PANTS'?0x52677b:0x333337),p?.name?.includes('Leather')?.45:.9);}
  function rebuild(){
    while(model.children.length){const child=model.children.pop();child.geometry.dispose();if(![skin,hair,shoe,base].includes(child.material))child.material.dispose();}
    const h=fitState.height/170,b=Math.max(.8,Math.min(1.35,Math.sqrt(fitState.weight/55)/Math.sqrt(h))),w=fitState.waist/68,s=fitState.shoulder/38;
    model.scale.set(1,h,1);
    oval(skin,0,1.54,0,.091,.119,.086);oval(hair,0,1.577,-.018,.096,.09,.086);oval(hair,0,1.48,-.066,.094,.13,.046);
    tube(skin,[0,1.36,0],[0,1.46,0],.042,.039);
    shell(skin,[[.15*b,.84],[.17*b,.94],[.115*w,1.07],[.15*b,1.25],[.185*s,1.32],[.08,1.36]]);
    for(const side of [-1,1]){tube(skin,[side*.09*b,.86,0],[side*.095*b,.48,.015],.062*b,.085*b);tube(skin,[side*.095*b,.48,.015],[side*.1*b,.10,0],.035*b,.055*b);oval(shoe,side*.1*b,.055,.04,.052,.045,.115);tube(skin,[side*.19*s,1.30,0],[side*.245*s,1.04,.01],.041*b,.052*b);tube(skin,[side*.245*s,1.04,.01],[side*.26*s,.83,.025],.026*b,.038*b);oval(skin,side*.26*s,.79,.026,.03,.055,.021);}
    const dress=selected('OPS'),top=selected('TOP'),outer=selected('OUTER'),pants=selected('PANTS'),skirt=selected('SKIRT');
    if(dress){shell(fabric('OPS'),[[.25*b,.33],[.19*b,.85],[.124*w,1.07],[.16*b,1.25],[.18*s,1.30]]);}
    else{
      const bottom=fabric('PANTS');for(const side of [-1,1]){tube(bottom,[side*.10*b,.12,0],[side*.09*b,.88,0],pants?.name?.match(/Wide|Denim|denim/)? .095*b:.068*b,.094*b);}
      shell(bottom,[[.17*b,.81],[.17*b,.96],[.135*w,1.0]]);
      if(skirt)shell(fabric('SKIRT'),[[.29*b,.42],[.22*b,.68],[.18*b,.91],[.14*w,1.01]]);
      const upper=top?fabric('TOP'):material(0xb2aba6);shell(upper,[[.13*w,1.00],[.13*w,1.09],[.16*b,1.25],[.19*s,1.31],[.07,1.35]]);
      if(top&&!/sleeveless|Sleeveless/.test(top.name))for(const side of [-1,1])tube(upper,[side*.19*s,1.29,0],[side*.25*s,/tee|Tee/.test(top.name)?1.13:.87,.02],.035*b,.059*b);
    }
    if(outer){const coat=fabric('OUTER'),long=/trench/i.test(outer.name);shell(coat,[[.205*b,long?.55:.99],[.18*b,1.10],[.20*b,1.27],[.205*s,1.32],[.085,1.37]]);for(const side of [-1,1])tube(coat,[side*.20*s,1.29,0],[side*.265*s,.86,.02],.044*b,.066*b);tube(material(0xb5b5b6,.3),[0,1.02,.136*b],[0,1.32,.136*b],.004,.004);}
    if(selected('ACC')){const bag=fabric('ACC');oval(bag,.24*b,.94,.07,.10,.11,.045);const strap=mesh(new T.TorusGeometry(.12,.009,10,48),bag,.23*b,1.08,.07);strap.scale.set(.65,1,1);}
    const platform=mesh(new T.CylinderGeometry(.42,.45,.035,64),base,0,-.023,0);
    host.dataset.height=fitState.height;host.dataset.weight=fitState.weight;host.dataset.outfit=JSON.stringify(fitState.selected);
  }
  function updateSlot(cat){const p=selected(cat),select=document.querySelector(`[data-color="${cat}"]`),colors=[...new Set((p?.variants||[]).map(v=>v.color))];if(!colors.includes(fitState.colors[cat]))fitState.colors[cat]=colors[0];select.innerHTML=colors.map(c=>`<option value="${esc(c)}">${t(c)}</option>`).join('');select.value=fitState.colors[cat]||'';select.hidden=!p;const link=document.querySelector(`[data-product-link="${cat}"]`);link.textContent=p?fitText('상품 상세 ↗','商品詳細 ↗'):'';if(p)link.href='#product/'+p.id;else link.removeAttribute('href');}
  document.querySelectorAll('[data-slot]').forEach(el=>{updateSlot(el.dataset.slot);el.onchange=()=>{fitState.selected[el.dataset.slot]=Number(el.value)||undefined;if(el.value){if(el.dataset.slot==='OPS'){for(const c of ['TOP','PANTS','SKIRT']){delete fitState.selected[c];document.querySelector(`[data-slot="${c}"]`).value='';updateSlot(c);}}else if(['TOP','PANTS','SKIRT'].includes(el.dataset.slot)){delete fitState.selected.OPS;document.querySelector('[data-slot="OPS"]').value='';updateSlot('OPS');}}updateSlot(el.dataset.slot);rebuild();};});
  document.querySelectorAll('[data-color]').forEach(el=>el.onchange=()=>{fitState.colors[el.dataset.color]=el.value;rebuild();});
  document.querySelectorAll('[data-measure]').forEach(el=>el.oninput=()=>{fitState[el.dataset.measure]=Number(el.value);document.querySelector(`#fit-${el.dataset.measure}-value`).textContent=el.value+(el.dataset.measure==='weight'?' kg':' cm');rebuild();});
  const angle=document.querySelector('#fit-angle'),degrees=document.querySelector('#fit-degrees');
  function rotate(value){fitState.rotation=(value+360)%360;angle.value=fitState.rotation;degrees.value=Math.round(fitState.rotation)+'°';host.dataset.rotation=fitState.rotation;}
  angle.oninput=()=>rotate(Number(angle.value));document.querySelectorAll('[data-angle]').forEach(el=>el.onclick=()=>rotate(Number(el.dataset.angle)));
  document.querySelector('#fit-auto').onclick=e=>{fitState.auto=!fitState.auto;e.target.setAttribute('aria-pressed',fitState.auto);};document.querySelector('#fit-reset').onclick=()=>{rotate(0);fitState.zoom=3.6;};
  let previous=null;host.onpointerdown=e=>{previous=e.clientX;host.setPointerCapture(e.pointerId);};host.onpointermove=e=>{if(previous!==null){rotate(fitState.rotation+(e.clientX-previous)*.6);previous=e.clientX;}};host.onpointerup=host.onpointercancel=()=>previous=null;
  host.onkeydown=e=>{if(['ArrowLeft','ArrowRight'].includes(e.key)){e.preventDefault();rotate(fitState.rotation+(e.key==='ArrowLeft'?-10:10));}};
  const wheel=e=>{e.preventDefault();fitState.zoom=Math.max(2.5,Math.min(5,fitState.zoom+e.deltaY*.002));};host.addEventListener('wheel',wheel,{passive:false});
  const resize=new ResizeObserver(()=>{const width=host.clientWidth,height=host.clientHeight;renderer.setSize(width,height);camera.aspect=width/height;camera.updateProjectionMatrix();});resize.observe(host);
  rebuild();let frame,last=performance.now();function render(now){const dt=Math.min((now-last)/1000,.1);last=now;if(fitState.auto)rotate(fitState.rotation+dt*18);model.rotation.y=fitState.rotation*Math.PI/180;camera.position.set(0,.95,fitState.zoom);camera.lookAt(0,.88,0);renderer.render(scene,camera);frame=requestAnimationFrame(render);}frame=requestAnimationFrame(render);
  return()=>{cancelAnimationFrame(frame);resize.disconnect();host.removeEventListener('wheel',wheel);scene.traverse(o=>{o.geometry?.dispose();o.material?.dispose();});renderer.dispose();renderer.forceContextLoss();};
}
// Add a fitting entry to every server-rendered product page.
const fitDetail=detail;
detail=function(id){fitDetail(id);const actions=app.querySelector('.detail .actions');if(actions){const a=document.createElement('a');a.className='secondary';a.href='#fitting/'+id;a.textContent=fitText('3D로 입어보기 ↗','3Dで試着 ↗');actions.after(a);}};
