import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {chromium} from '@playwright/test';
import {spawn} from 'node:child_process';
let browser,server;
before(async()=>{
 try{await fetch('http://127.0.0.1:1420')}catch{server=spawn('npm',['run','dev'],{stdio:'ignore'});for(let i=0;i<50;i++){try{await fetch('http://127.0.0.1:1420');break}catch{await new Promise(r=>setTimeout(r,100))}}}
 browser=await chromium.launch();
});
after(async()=>{await browser?.close();server?.kill()});
test('profile wizard validates input, retains draft on Back, and persists only on completion',async()=>{
 const p=await browser.newPage();await p.goto('http://127.0.0.1:1420');
 await p.getByRole('button',{name:'Create profile',exact:true}).first().click();
 assert.equal(await p.getByRole('button',{name:'Confirm',exact:true}).isDisabled(),true);
 await p.getByRole('textbox',{name:'Profile name'}).fill('  Spruce Valley  ');
 await p.getByRole('button',{name:'Confirm',exact:true}).click();
 assert.deepEqual(await p.getByLabel('Minecraft version').locator('option').allTextContents(),['26.3 · Java Edition','26.1.2 · Java Edition','26.1 · Java Edition']);
 await p.getByRole('button',{name:'Back',exact:true}).click();
 assert.equal(await p.getByRole('textbox',{name:'Profile name'}).inputValue(),'  Spruce Valley  ');
 await p.getByRole('button',{name:'Confirm',exact:true}).click();await p.getByRole('button',{name:'Confirm',exact:true}).click();
 assert.equal(await p.getByRole('button',{name:'Forge Integration pending',exact:true}).isDisabled(),true);assert.equal(await p.getByRole('button',{name:'NeoForge Native client modules',exact:true}).isDisabled(),false);
 await p.getByRole('button',{name:'Confirm',exact:true}).click();await p.getByRole('button',{name:'Stone',exact:true}).click();
 assert.equal(await p.evaluate(()=>JSON.parse(localStorage.getItem('unbox-preview')||'{}').profiles?.length||0),0);
 await p.getByRole('button',{name:'Create profile',exact:true}).last().click();await p.getByRole('button',{name:'LAUNCH 26.3',exact:true}).waitFor();await p.reload();
 await p.getByRole('button',{name:'Spruce Valley · fabric 26.3'}).waitFor();
 assert.equal(await p.evaluate(()=>JSON.parse(localStorage.getItem('unbox-preview')).profiles[0].icon),'stone');
 assert.equal(await p.getByRole('button',{name:'Client Mods',exact:true}).count(),0);
 assert.equal(await p.getByRole('tab',{name:'News',exact:true}).getAttribute('aria-selected'),'true');await p.close();
});
test('home panel adapts to compact, windowed and fullscreen dimensions without blank lower half',async()=>{
 const p=await browser.newPage();await p.addInitScript(()=>localStorage.setItem('unbox-preview',JSON.stringify({profiles:[{id:'layout',name:'Layout',version:'26.1.2',loader:'fabric',icon:'stone'}],selected:'layout',settings:{memory:4096,minimize:false},modules:{},account:null})));await p.goto('http://127.0.0.1:1420');await p.locator('.hero').waitFor();
 // Resize the same live page to catch stale sizing when entering and leaving fullscreen.
 for(const [width,height]of [[1000,720],[1280,800],[1920,1080],[3840,2160],[1280,800]]){
  await p.setViewportSize({width,height});
  const layout=await p.evaluate(()=>{const rect=s=>{const el=document.querySelector(s);if(!el)throw Error(`Missing ${s}`);const r=el.getBoundingClientRect();return{width:r.width,height:r.height,right:r.right,bottom:r.bottom}};return{panel:rect('.panel'),hero:rect('.hero'),card:rect('.empty-state'),bottom:rect('.home-bottom'),version:!!document.querySelector('select[aria-label="Minecraft version"]'),loader:!!document.querySelector('select[aria-label="Mod loader"]'),overflow:document.documentElement.scrollWidth>innerWidth,broken:[...document.images].filter(i=>i.complete&&!i.naturalWidth).map(i=>i.src)}});
  assert.equal(layout.overflow,false);assert.equal(layout.broken.length,0);assert.ok(layout.hero.height/layout.panel.height>.39&&layout.hero.height/layout.panel.height<.49);assert.ok(height-layout.panel.bottom>=10&&height-layout.panel.bottom<=32);assert.ok(layout.panel.bottom-layout.card.bottom<60);assert.ok(layout.card.height>=150);assert.equal(layout.version,false);assert.equal(layout.loader,false);
 }
 await p.close();
});
test('reduced motion and cloud status communicate real implementation state',async()=>{
 const p=await browser.newPage({reducedMotion:'reduce'});await p.goto('http://127.0.0.1:1420');await p.locator('.hero').waitFor();assert.equal(await p.locator('.scenery img').first().evaluate(e=>getComputedStyle(e).animationName),'none');
 await p.getByRole('button',{name:'Settings',exact:true}).click();await p.getByRole('button',{name:'Multiplayer',exact:true}).click();await p.getByText('Invites use your local network first', {exact:false}).waitFor();assert.equal(await p.getByRole('button',{name:'Add friend',exact:true}).count(),1);await p.close();
});
test('running game has orange styling and pause glyph, then returns to Launch on exit',async()=>{
 const p=await browser.newPage();
 await p.addInitScript(()=>{
  window.__job={busy:true,stage:'Playing',progress:100,profileId:'test'};
  window.__TAURI_INTERNALS__={invoke:async cmd=>cmd==='bootstrap'?{state:{profiles:[{id:'test',name:'Test',version:'26.1',loader:'fabric',icon:'stone'}],selected:'test',settings:{memory:4096,minimize:false},account:{name:'Tester'}},native:true,dataDirectory:'/test'}:cmd==='status'?window.__job:cmd==='read_modules'?{}:[]};
 });
 await p.goto('http://127.0.0.1:1420');await p.getByRole('button',{name:'PLAYING',exact:true}).waitFor();
 assert.equal(await p.locator('.launch-group.playing .lucide-pause').count(),1);
 assert.equal(await p.locator('.launch-progress').count(),0);
 assert.ok((await p.locator('.launch').evaluate(e=>getComputedStyle(e).backgroundImage)).includes('255, 181, 107'));
 await p.evaluate(()=>{window.__job={busy:false,stage:'Game closed'}});
 await p.getByRole('button',{name:'LAUNCH 26.1',exact:true}).waitFor();assert.equal(await p.locator('.launch-group.playing').count(),0);await p.close();
});
test('Microsoft sign-in completes through IPC without storing tokens in frontend state and renders the returned skin',async()=>{
 const p=await browser.newPage();
 await p.addInitScript(()=>{
  const c=document.createElement('canvas');c.width=c.height=64;const g=c.getContext('2d');g.fillStyle='#4989b3';g.fillRect(0,0,64,64);g.fillStyle='#d9a174';g.fillRect(8,8,8,8);g.clearRect(32,0,32,16);
  window.__authCalls=[];window.__polls=0;window.__saved=null;
  window.__TAURI_INTERNALS__={invoke:async(cmd,args)=>{window.__authCalls.push(cmd);
   if(cmd==='bootstrap')return {state:{profiles:[],selected:null,settings:{memory:4096,minimize:false},account:null},dataDirectory:'/test'};
   if(cmd==='status')return {busy:false,stage:'Idle'};
   if(cmd==='list_worlds')return [];
   if(cmd==='auth_info')return {configured:true,provider:'Unbox'};if(cmd==='auth_accounts')return {accounts:[],account:null};
   if(cmd==='auth_begin')return {id:'test',code:'TEST-CODE',interval:1,expiresIn:900,url:'https://www.microsoft.com/link'};
   if(cmd==='auth_poll')return ++window.__polls<2?{status:'pending'}:{status:'complete',accounts:[],account:{accountId:'fixture',provider:'Unbox',name:'SkinFixture',type:'microsoft',uuid:'00000000000040008000000000000001',skin:c.toDataURL(),skinModel:'slim'}};
   if(cmd==='asset_action')return {items:[],features:[],equipment:[]};if(cmd==='skin_library_list')return [];
   if(cmd==='save_state'){window.__saved=args.state;return;}
  }};
 });
 await p.goto('http://127.0.0.1:1420');await p.getByRole('button',{name:'Sign in',exact:true}).click();await p.getByRole('button',{name:'Sign in with Microsoft',exact:true}).click();
 await p.getByText('TEST-CODE',{exact:true}).waitFor();assert.ok(await p.evaluate(()=>window.__authCalls.includes('auth_open')));assert.equal(await p.getByRole('button',{name:'Application setup'}).count(),0);
 await p.locator('.account').getByText('SkinFixture',{exact:true}).waitFor();await p.getByLabel("SkinFixture's Minecraft skin. Drag to rotate.").waitFor();
 assert.equal(await p.locator('.avatar .skin-face').count(),1);
 const result=await p.evaluate(()=>({saved:window.__saved,calls:window.__authCalls}));assert.equal(result.saved.account.type,'microsoft');assert.equal('access_token' in result.saved.account,false);assert.equal('refresh_token' in result.saved.account,false);assert.ok(result.calls.includes('auth_open'));
 await p.getByRole('button',{name:'Skins',exact:true}).click();await p.locator('.skin-identity').getByText('SkinFixture',{exact:true}).waitFor();assert.ok((await p.evaluate(()=>window.__authCalls)).includes('skin_library_list'));await p.close();
});
test('canceling Microsoft authorization leaves the selected account unchanged',async()=>{
 const p=await browser.newPage();await p.addInitScript(()=>{window.__cancelled=0;window.__TAURI_INTERNALS__={invoke:async cmd=>{
  if(cmd==='bootstrap')return {state:{profiles:[],selected:null,settings:{memory:4096},account:{name:'LocalUser',type:'local'}},dataDirectory:'/test'};
  if(cmd==='status')return {busy:false,stage:'Idle'};if(cmd==='list_worlds'||cmd==='skin_library_list')return [];if(cmd==='auth_info')return {configured:true,provider:'Unbox'};if(cmd==='auth_accounts')return {accounts:[],account:{name:'LocalUser',type:'local'}};if(cmd==='auth_begin')return {id:'test',code:'CANCEL',interval:5,expiresIn:900};if(cmd==='auth_poll')return {status:'pending'};if(cmd==='auth_cancel')window.__cancelled++;
 }}});
 await p.goto('http://127.0.0.1:1420');await p.locator('.account').click();await p.getByRole('button',{name:'Sign in with Microsoft',exact:true}).click();await p.getByRole('button',{name:'Cancel sign-in'}).click();
 assert.equal(await p.locator('.account strong').textContent(),'LocalUser');assert.equal(await p.evaluate(()=>window.__cancelled),1);await p.getByRole('dialog',{name:'Your account'}).getByRole('button',{name:'Close'}).click();await p.getByRole('button',{name:'Skins',exact:true}).click();await p.getByRole('button',{name:'Import skin',exact:true}).waitFor();await p.close();
});

async function accountFixture(p){
 await p.addInitScript(()=>{
  const c=document.createElement('canvas');c.width=c.height=64;const g=c.getContext('2d');g.fillStyle='#467bac';g.fillRect(0,0,64,64);g.fillStyle='#d9a174';g.fillRect(8,8,8,8);g.clearRect(32,0,32,16);
  const a={accountId:'a',name:'AlexFixture',type:'microsoft',uuid:'00000000000040008000000000000001',skin:c.toDataURL(),skinModel:'slim',skinStatus:'ready',provider:'Unbox',needsSignIn:false};
  const b={...a,accountId:'b',name:'SteveFixture',uuid:'00000000000040008000000000000002',skinModel:'classic'};
  window.__accounts=[a,b];window.__selected='a';window.__calls=[];window.__uploadFail=false;window.__savedSkins=[];window.__info={configured:true,provider:'Unbox'};
  const snapshot=()=>({accounts:window.__accounts,account:window.__accounts.find(a=>a.accountId===window.__selected)||null});
  window.__TAURI_INTERNALS__={invoke:async(cmd,args)=>{window.__calls.push({cmd,args});
   if(cmd==='bootstrap')return {state:{profiles:[],selected:null,settings:{memory:4096,minimize:false},account:snapshot().account},dataDirectory:'/test'};
   if(cmd==='status')return {busy:false,stage:'Idle'};
   if(cmd==='auth_info')return window.__info;
   if(cmd==='auth_accounts')return snapshot();
   if(cmd==='auth_select'){window.__selected=args.accountId;return snapshot();}
   if(cmd==='auth_sign_out'){window.__accounts=window.__accounts.filter(a=>a.accountId!==args.accountId);return snapshot();}
   if(cmd==='auth_begin')return {id:'fresh',code:'NEW-UNBOX',interval:5,expiresIn:900,provider:'Unbox'};
   if(cmd==='auth_poll')return {status:'pending'};
   if(cmd==='asset_action')return {items:[],features:[],equipment:[]};if(cmd==='skin_library_list')return window.__savedSkins;
   if(cmd==='skin_library_import'){const skin={id:'saved-'+(window.__savedSkins.length+1),name:args.name,model:args.model,created:Date.now(),skin:'data:image/png;base64,'+args.pngBase64};window.__savedSkins.push(skin);return window.__savedSkins;}
   if(cmd==='skin_library_apply'){if(window.__uploadFail)throw Error('Skin upload: connection failed');const skin=window.__savedSkins.find(s=>s.id===args.id);Object.assign(window.__accounts.find(a=>a.accountId===args.accountId),{skin:skin.skin,skinModel:args.model||skin.model});return snapshot();}
   if(cmd==='auth_refresh_profile'){const a=snapshot().account;if(!window.__savedSkins.some(s=>s.skin===a.skin&&s.model===a.skinModel))window.__savedSkins.push({id:'original-'+a.accountId,name:a.name+' · Original',model:a.skinModel,created:0,skin:a.skin});return snapshot();}
   if(cmd==='save_state'){window.__saved=args.state;return;}
   if(cmd==='auth_cancel')return;
   return [];
  }};
 });
 await p.goto('http://127.0.0.1:1420');await p.locator('.account strong').waitFor();
}
test('restored accounts switch immediately and removal clears only the chosen identity',async()=>{
 const p=await browser.newPage();await accountFixture(p);await p.locator('.account').click();
 await p.getByRole('button',{name:'Use SteveFixture · Unbox',exact:true}).click();await p.locator('.account').getByText('SteveFixture').waitFor();
 assert.equal(await p.evaluate(()=>window.__saved.account.accountId),'b');
 await p.locator('.account').click();await p.getByRole('button',{name:'Remove AlexFixture · Unbox'}).click();
 await p.getByRole('button',{name:'Remove AlexFixture · Unbox'}).waitFor({state:'hidden'});assert.equal(await p.locator('.account strong').textContent(),'SteveFixture');
 await p.getByRole('button',{name:'Remove SteveFixture · Unbox'}).click();await p.locator('.account').getByText('Sign in').waitFor();
 assert.equal(await p.evaluate(()=>window.__saved.account),null);assert.equal(await p.getByLabel('Saved accounts').count(),0);await p.close();
});
test('legacy accounts start fresh Unbox authorization without exposing application setup',async()=>{
 const p=await browser.newPage();await accountFixture(p);
 await p.evaluate(()=>{window.__accounts[0].needsSignIn=true;window.__accounts[0].provider='Previous application'});
 await p.locator('.account').click();await p.getByText('Sign in again',{exact:true}).waitFor();
 assert.equal(await p.getByRole('button',{name:'Application setup'}).count(),0);
 assert.equal(await p.getByText('Use DevLogin for development',{exact:true}).count(),0);
 await p.getByRole('button',{name:'Use AlexFixture · Previous application',exact:true}).click();
 await p.getByText('NEW-UNBOX',{exact:true}).waitFor();
 const calls=await p.evaluate(()=>window.__calls.map(c=>c.cmd));assert.ok(calls.includes('auth_begin'));assert.ok(calls.includes('auth_open'));assert.ok(!calls.includes('auth_select'));
 await p.getByRole('button',{name:'Cancel sign-in'}).click();assert.equal(await p.evaluate(()=>window.__accounts.length),2);assert.equal(await p.evaluate(()=>window.__selected),'a');await p.close();
});
test('skin preview stays local until Apply; failures preserve saved skin; upload and refresh use selected identity',async()=>{
 const p=await browser.newPage({viewport:{width:1280,height:820}});await accountFixture(p);await p.getByRole('button',{name:'Skins',exact:true}).click();
 const original=await p.evaluate(()=>window.__accounts[0].skin);
 const png=await p.evaluate(()=>{const c=document.createElement('canvas');c.width=c.height=64;const g=c.getContext('2d');g.fillStyle='#b56e55';g.fillRect(0,0,64,64);g.clearRect(32,0,32,16);return c.toDataURL().split(',')[1]});
 await p.getByLabel('Choose PNG skin').setInputFiles({name:'new-skin.png',mimeType:'image/png',buffer:Buffer.from(png,'base64')});await p.getByRole('group',{name:'Arm model'}).waitFor();
 assert.equal(await p.evaluate(()=>window.__calls.filter(c=>c.cmd==='skin_library_apply').length),0);assert.equal(await p.evaluate(()=>window.__accounts[0].skin),original);
 assert.equal(await p.getByRole('switch',{name:'Slim arms'}).getAttribute('aria-checked'),'false');await p.getByRole('button',{name:'Save skin',exact:true}).click();await p.getByRole('button',{name:'Preview new-skin.png'}).waitFor();await p.evaluate(()=>window.__uploadFail=true);await p.getByRole('button',{name:'Apply selected',exact:true}).click();await p.getByRole('alert').waitFor();
 assert.equal(await p.evaluate(()=>window.__accounts[0].skin),original);await p.evaluate(()=>window.__uploadFail=false);await p.getByRole('button',{name:'Apply selected',exact:true}).click();await p.getByText('Skin applied.',{exact:true}).waitFor();
 assert.equal(await p.evaluate(()=>window.__saved.account.skinModel),'classic');assert.equal(await p.evaluate(()=>window.__saved.account.skin),'data:image/png;base64,'+png);
 assert.equal(await p.evaluate(()=>window.__calls.find(c=>c.cmd==='skin_library_apply').args.accountId),'a');
 assert.equal(await p.getByRole('button',{name:'Apply selected',exact:true}).isDisabled(),false);
 await p.screenshot({path:'test-results/account-skins.png'});
 await p.getByLabel('Choose PNG skin').setInputFiles({name:'invalid.png',mimeType:'image/png',buffer:Buffer.from('not a skin')});await p.getByRole('alert').waitFor();await p.close();
});
test('skin library saves the current account skin and applies arm model only on confirmation',async()=>{
 const p=await browser.newPage({viewport:{width:1000,height:720}});await accountFixture(p);await p.getByRole('button',{name:'Skins',exact:true}).click();
 await p.getByRole('button',{name:'Preview AlexFixture · Original',exact:true}).waitFor();const original=await p.evaluate(()=>window.__accounts[0].skin);
 const arms=p.getByRole('switch',{name:'Slim arms'});await p.waitForFunction(()=>document.querySelector('[role="switch"][aria-label="Slim arms"]')?.getAttribute('aria-checked')==='true');
 await arms.click();assert.equal(await arms.getAttribute('aria-checked'),'false');assert.equal(await p.evaluate(()=>window.__accounts[0].skinModel),'slim');
 await p.getByRole('button',{name:'Apply selected',exact:true}).click();await p.getByText('Skin applied.',{exact:true}).waitFor();
 assert.equal(await p.evaluate(()=>window.__accounts[0].skinModel),'classic');assert.equal(await p.evaluate(()=>window.__accounts[0].skin),original);
 await p.getByRole('button',{name:'Preview AlexFixture · Original',exact:true}).click();assert.equal(await arms.getAttribute('aria-checked'),'true');
 await p.getByRole('button',{name:'Apply selected',exact:true}).click();await p.waitForFunction(()=>window.__accounts[0].skinModel==='slim');
 assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);await p.screenshot({path:'test-results/skin-arms-original-small.png'});await p.setViewportSize({width:1280,height:820});await p.screenshot({path:'test-results/skin-arms-original-desktop.png'});await p.close();
});
test('startup storage failure is visible and retry restores the app instead of leaving a spinner',async()=>{
 const p=await browser.newPage();await p.addInitScript(()=>{window.__attempts=0;window.__TAURI_INTERNALS__={invoke:async cmd=>{
  if(cmd==='bootstrap'){if(!window.__attempts++)throw Error('macOS Keychain is locked or access was denied.');return {state:{profiles:[],selected:null,settings:{memory:4096},account:null},dataDirectory:'/test'};}
  if(cmd==='status')return {busy:false,stage:'Idle'};return [];
 }}});
 await p.goto('http://127.0.0.1:1420');await p.getByRole('alert').waitFor();await p.getByRole('button',{name:'Retry',exact:true}).click();await p.locator('.hero').waitFor();await p.close();
});

test('file drops target the selected list, report rejects, and icon plus opens that folder',async()=>{
 const p=await browser.newPage({deviceScaleFactor:2});
 await p.addInitScript(()=>{
  window.__calls=[];window.__events={};window.__callbacks={};window.__files=[];let next=0;
  window.__TAURI_EVENT_PLUGIN_INTERNALS__={unregisterListener:event=>delete window.__events[event]};
  window.__TAURI_INTERNALS__={transformCallback:fn=>{window.__callbacks[++next]=fn;return next},invoke:async(cmd,args)=>{window.__calls.push({cmd,args});if(cmd==='plugin:event|listen'){window.__events[args.event]=args.handler;return args.handler}if(cmd==='bootstrap')return {state:{profiles:[{id:'00000000-0000-4000-8000-000000000001',name:'Test',version:'26.1',loader:'fabric',icon:'stone'}],selected:'00000000-0000-4000-8000-000000000001',settings:{memory:4096},account:{name:'Test'}}};if(cmd==='status')return {busy:false};if(cmd==='list_content')return window.__files;if(cmd==='import_content'){window.__files=[{name:'test.jar',title:'Test Mod',size:100}];return {imported:['test.jar'],rejected:[{name:'bad.jar',reason:'No mod metadata found'}]}}if(cmd==='social_status')return {connected:false,friends:[],invites:[]};return {}}};
 });
 await p.goto('http://127.0.0.1:1420');await p.getByRole('tab',{name:'Mods',exact:true}).click();await p.waitForFunction(()=>window.__events['tauri://drag-drop']);
 await p.getByRole('button',{name:'Open mods folder'}).click();assert.equal((await p.evaluate(()=>window.__calls.find(c=>c.cmd==='open_content_folder'))).args.kind,'Mods');
 const plus=p.getByRole('button',{name:'Open mods folder'});assert.equal(await plus.textContent(),'');assert.equal(await plus.evaluate(e=>getComputedStyle(e).borderWidth),'0px');
 await p.evaluate(()=>{const event='tauri://drag-drop',cb=window.__callbacks[window.__events[event]];cb({event,payload:{paths:['/fixture/test.jar'],position:{x:0,y:0}}})});assert.equal(await p.evaluate(()=>window.__calls.filter(c=>c.cmd==='import_content').length),0);
 await p.evaluate(()=>{const r=document.querySelector('.content-drop').getBoundingClientRect(),event='tauri://drag-drop';window.__callbacks[window.__events[event]]({event,payload:{paths:['/fixture/test.jar','/fixture/bad.jar'],position:{x:(r.left+20)*devicePixelRatio,y:(r.top+20)*devicePixelRatio}}})});
 await p.getByText('Test Mod',{exact:true}).waitFor();await p.getByText('bad.jar: No mod metadata found',{exact:true}).waitFor();assert.equal((await p.evaluate(()=>window.__calls.find(c=>c.cmd==='import_content'))).args.kind,'Mods');
 await p.getByRole('tab',{name:'Packs',exact:true}).click();await p.getByRole('button',{name:'Open packs folder'}).click();assert.equal((await p.evaluate(()=>window.__calls.filter(c=>c.cmd==='open_content_folder').at(-1))).args.kind,'Packs');await p.close();
});
test('friend list sends codes, accepts requests, disables offline invites and passes Join to game',async()=>{
 const p=await browser.newPage();await p.addInitScript(()=>{window.__calls=[];window.__view={connected:true,self:{id:'self',code:'AAAABBBBCCCC'},canInvite:true,friends:[{id:'pending',name:'Bob',kind:'microsoft',accepted:0,direction:'incoming',online:true},{id:'offline',name:'Carol',kind:'microsoft',accepted:1,online:false}],invites:[{id:'invite',receiver:'self',senderName:'David',expires:Date.now()+60000,room:{name:'World'}}]};window.__TAURI_INTERNALS__={invoke:async(cmd,args)=>{window.__calls.push({cmd,args});if(cmd==='bootstrap')return {state:{profiles:[],selected:null,settings:{memory:4096},account:{name:'Tester'}}};if(cmd==='status')return {busy:false};if(cmd==='social_status')return window.__view;if(cmd==='social_action'){if(args.op==='accept')window.__view.friends[0].accepted=1;return window.__view}return []}}});await p.goto('http://127.0.0.1:1420');await p.getByRole('article',{name:'World invitation',exact:true}).waitFor();
 await p.getByRole('button',{name:'Add friend',exact:true}).click();await p.getByLabel('Friend code',{exact:true}).fill('123456ABCDEF');await p.getByRole('button',{name:'Send friend request'}).click();await p.getByText('Friend request sent',{exact:true}).waitFor();await p.getByRole('button',{name:'Accept Bob'}).click();await p.locator('.friend-row').filter({hasText:'Bob'}).getByRole('button',{name:'Invite',exact:true}).click();assert.equal(await p.locator('.friend-row').filter({hasText:'Carol'}).getByRole('button',{name:'Invite',exact:true}).isDisabled(),true);await p.getByRole('button',{name:"Join David's world"}).click();assert.deepEqual(await p.evaluate(()=>window.__calls.filter(c=>c.cmd==='social_action').map(c=>c.args)),[{op:'request',id:'123456ABCDEF'},{op:'accept',id:'pending'},{op:'invite',id:'pending'},{op:'join',id:'invite'}]);await p.close();
});

test('email registration is removed; Microsoft and local identities remain available',async()=>{
 const p=await browser.newPage();await accountFixture(p);await p.locator('.account').click();assert.equal(await p.getByRole('button',{name:'Sign in with Unbox',exact:true}).count(),0);assert.equal(await p.getByRole('button',{name:'Create an Unbox account'}).count(),0);await p.getByRole('button',{name:'Sign in with Microsoft',exact:true}).waitFor();await p.getByText('Local play',{exact:true}).click();await p.getByRole('button',{name:'Use local account'}).waitFor();await p.close();
});
test('Local rename updates the account card while retaining UUID and handles a running-game rejection',async()=>{
 const p=await browser.newPage();await p.addInitScript(()=>{let account={accountId:'unbox:rename',name:'BeforeName',type:'local',uuid:'00000000000040008000000000000001',provider:'Local'};window.__running=true;window.__renameCalls=[];window.__TAURI_INTERNALS__={invoke:async(cmd,args)=>{
  if(cmd==='bootstrap')return {state:{profiles:[],selected:null,settings:{memory:4096},account},dataDirectory:'/test'};if(cmd==='status')return {busy:false,stage:'Idle'};if(cmd==='list_worlds'||cmd==='skin_library_list')return [];if(cmd==='auth_info')return {configured:true};if(cmd==='auth_accounts')return {accounts:[account],account};if(cmd==='auth_local_rename'){window.__renameCalls.push(args);if(window.__running)throw Error('Close the game before changing your player name');account={...account,name:args.name};return {accounts:[account],account}};if(cmd==='save_state')window.__saved=args.state;
 }}});
 await p.goto('http://127.0.0.1:1420');await p.locator('.account').click();await p.getByRole('button',{name:'Rename BeforeName',exact:true}).click();const form=p.getByRole('form',{name:'Change player name'});await form.getByLabel('Player name',{exact:true}).fill('SameName');await form.getByRole('button',{name:'Save name'}).click();await p.getByRole('alert').getByText('Close the game before changing your player name').waitFor();assert.equal(await form.getByLabel('Player name',{exact:true}).inputValue(),'SameName');
 await p.evaluate(()=>{window.__running=false});await form.getByRole('button',{name:'Save name'}).click();await p.locator('.account-name').getByText('SameName',{exact:true}).waitFor();assert.equal((await p.evaluate(()=>window.__saved)).account.uuid,'00000000000040008000000000000001');assert.equal(await form.count(),0);await p.close();
});

test('updates are manual, recover from check errors, and cannot install while playing',async()=>{
 const p=await browser.newPage({viewport:{width:1000,height:720},deviceScaleFactor:2});
 await p.addInitScript(()=>{window.__gameBusy=true;window.__update={status:'idle',currentVersion:'0.1.0'};window.__checks=0;window.__installs=0;window.__TAURI_INTERNALS__={invoke:async cmd=>{
  if(cmd==='bootstrap')return {state:{profiles:[],selected:null,settings:{memory:4096},account:null},dataDirectory:'/test'};
  if(cmd==='status')return {busy:window.__gameBusy,stage:window.__gameBusy?'Playing':'Idle'};
  if(cmd==='update_status')return window.__update;
  if(cmd==='check_update'){window.__checks++;if(window.__checks===1)throw Error('Could not reach GitHub. Check your connection and retry.');window.__update=window.__checks===2?{status:'unpublished'}:{status:'available',version:'0.2.0',notes:'A test update'};return window.__update;}
  if(cmd==='install_update'){window.__installs++;throw Error('Download or signature verification failed. No update was installed.');}
  return [];
 }}});
 await p.goto('http://127.0.0.1:1420');await p.locator('.logo').waitFor();
 assert.equal(await p.locator('.logo').evaluate(e=>e.getBoundingClientRect().width),44);
 assert.equal(await p.locator('svg.logo path').count(),6);assert.equal(await p.locator('svg.logo mask').count(),0);
 await p.locator('.topbar').screenshot({path:'.cache/header-logo-fixed.png'});
 assert.equal(await p.getByRole('button',{name:'Update available',exact:true}).count(),0);await p.locator('.rail').getByRole('button',{name:'Check for updates',exact:true}).click();
 const check=p.getByRole('region',{name:'Client updates'}).getByRole('button',{name:'Check for updates',exact:true});
 await check.click();await p.getByRole('status').getByText('No releases published yet.').waitFor();assert.equal(await p.evaluate(()=>window.__installs),0);
 await check.click();const install=p.getByRole('button',{name:'Install and restart',exact:true});await install.waitFor();assert.equal(await install.isDisabled(),true);
 await p.evaluate(()=>{window.__gameBusy=false});await p.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent==='Install and restart'&&!b.disabled));assert.equal(await p.evaluate(()=>window.__installs),0);
 await install.click();await p.getByRole('alert').getByText('Download or signature verification failed.',{exact:false}).waitFor();assert.equal(await p.evaluate(()=>window.__installs),1);assert.equal(await install.isDisabled(),false);
 assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);await p.screenshot({path:'.cache/client-updates.png'});await p.close();
});
test('cached offline friends render while reconnecting silently',async()=>{
 const p=await browser.newPage();await p.addInitScript(()=>{window.__TAURI_INTERNALS__={invoke:async cmd=>cmd==='bootstrap'?{state:{profiles:[],selected:null,settings:{memory:4096,minimize:false},account:null},dataDirectory:'/test'}:cmd==='status'?{busy:false,stage:'Idle'}:cmd==='social_status'?{connected:false,self:{id:'me',code:'ABCD12345678'},friends:[{id:'friend',name:'OfflineFriend',code:'12345678ABCD',accepted:1,online:false,state:'Offline',kind:'microsoft'}],invites:[],error:'Connecting…'}:[]}});
 await p.goto('http://127.0.0.1:1420');await p.getByText('OfflineFriend',{exact:true}).waitFor();assert.equal(await p.getByText('Offline',{exact:true}).count(),1);assert.equal(await p.getByText('Connecting…',{exact:true}).count(),0);assert.equal(await p.getByRole('button',{name:'Invite',exact:true}).isDisabled(),true);await p.close();
});
test('launcher removes personal mod settings and selects an existing isolated profile',async()=>{
 const p=await browser.newPage();await p.addInitScript(()=>{window.__saved=null;const profiles=[{id:'fabric-261',name:'Fabric 26.1',version:'26.1',loader:'fabric',icon:'stone'},{id:'fabric-2612',name:'Fabric 26.1.2',version:'26.1.2',loader:'fabric',icon:'grass'}];window.__TAURI_INTERNALS__={invoke:async(cmd,args)=>{if(cmd==='bootstrap')return {state:{profiles,selected:'fabric-261',settings:{memory:4096,minimize:false},modules:{},account:null},dataDirectory:'/fixture'};if(cmd==='status')return {busy:false,stage:'Idle'};if(cmd==='read_modules')return {};if(cmd==='list_worlds')return [];if(cmd==='save_state'){window.__saved=args.state;return}if(cmd==='social_status')return {connected:false,friends:[],invites:[]};return []}}});
 await p.goto('http://127.0.0.1:1420');assert.equal(await p.getByRole('button',{name:'Client Mods',exact:true}).count(),0);assert.equal(await p.getByLabel('Minecraft version').count(),0);await p.getByRole('button',{name:'Fabric 26.1.2 · fabric 26.1.2'}).click();assert.equal(await p.evaluate(()=>window.__saved.selected),'fabric-2612');await p.close();
});

test('home keeps the original tabs while content origins remain interactive',async()=>{
 const p=await browser.newPage();await p.addInitScript(()=>{window.__calls=[];window.__TAURI_INTERNALS__={transformCallback:()=>0,invoke:async(cmd,args)=>{window.__calls.push({cmd,args});if(cmd==='bootstrap')return {state:{profiles:[{id:'fixture',name:'Fabric',version:'26.1.2',loader:'fabric',icon:'stone'}],selected:'fixture',settings:{memory:4096},account:null},dataDirectory:'/fixture'};if(cmd==='status')return {busy:false,stage:'Idle'};if(cmd==='read_modules')return {};if(cmd==='social_status')return {connected:false,friends:[],invites:[]};if(cmd==='list_content')return [{name:'host.jar',title:'Host Mod',size:1048576,origin:'host',source:'Room manifest'},{name:'mine.jar',title:'My Mod',size:2097152,origin:'yours',source:'Your library'}];if(cmd==='plugin:event|listen')return 1;return []}}});
 await p.goto('http://127.0.0.1:1420');assert.deepEqual(await p.getByRole('tab').allTextContents(),['News','Mods','Packs','Shaders']);assert.equal(await p.evaluate(()=>window.__calls.some(c=>c.cmd==='list_worlds'||c.cmd==='preflight_world')),false);await p.getByRole('tab',{name:'Mods',exact:true}).click();await p.getByText('From host',{exact:true}).waitFor();await p.getByText('Yours',{exact:true}).waitFor();assert.equal(await p.getByText('Required',{exact:true}).count(),1);assert.equal(await p.getByRole('button',{name:'Remove host.jar'}).count(),0);assert.equal(await p.getByRole('button',{name:'Remove mine.jar'}).count(),1);await p.close();
});

test('home skin strip applies a saved skin and reports completion',async()=>{
 const p=await browser.newPage();await p.addInitScript(()=>{const c=document.createElement('canvas');c.width=c.height=64;const g=c.getContext('2d');g.fillStyle='#4f86d9';g.fillRect(0,0,64,64);g.fillStyle='#e4ad82';g.fillRect(8,8,8,8);g.fillStyle='#5f3b28';g.fillRect(8,8,8,3);g.fillStyle='#26313f';g.fillRect(10,12,1,1);g.fillRect(13,12,1,1);g.fillStyle='#a95f55';g.fillRect(11,14,3,1);g.clearRect(40,8,8,8);g.fillStyle='#55a868';g.fillRect(20,20,16,12);g.fillStyle='#f2d45c';g.fillRect(40,20,8,12);const skin=c.toDataURL();window.__skinApply=[];window.__skinLists=0;window.__TAURI_INTERNALS__={invoke:async(cmd,args)=>{const account={accountId:'skin-account',name:'Skin User',type:'unbox',uuid:'00000000000040008000000000000001',skin,skinModel:'classic'};if(cmd==='bootstrap')return {state:{profiles:[{id:'fixture',name:'Fabric',version:'26.1.2',loader:'fabric',icon:'stone'}],selected:'fixture',settings:{memory:4096},account},dataDirectory:'/fixture'};if(cmd==='status')return {busy:false};if(cmd==='read_modules')return {};if(cmd==='social_status')return {connected:false,friends:[],invites:[]};if(cmd==='list_worlds')return [];if(cmd==='asset_action')return {items:[],features:[],equipment:[]};if(cmd==='skin_library_list'){window.__skinLists++;return Array.from({length:6},(_,i)=>({id:'saved-'+i,name:'Fixture skin '+i,model:'slim',created:i,skin}))}if(cmd==='skin_library_apply'){window.__skinApply.push(args);return {accounts:[account],account:{...account,skinModel:'slim'}}}return []}}});
 await p.goto('http://127.0.0.1:1420');await p.getByLabel("Skin User's Minecraft skin. Drag to rotate.").waitFor();for(const [width,height,name]of [[1000,720,'small'],[1280,800,'regular'],[1920,1080,'large']]){await p.setViewportSize({width,height});await p.screenshot({path:`test-results/home-skins-${name}.png`,fullPage:true});assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false)}assert.equal(await p.getByRole('button',{name:/^Apply Fixture skin /}).count(),6);await p.getByRole('button',{name:'Open skin library',exact:true}).click();await p.getByRole('button',{name:'Home',exact:true}).click();await p.getByRole('button',{name:'Apply Fixture skin 5',exact:true}).waitFor();assert.ok(await p.evaluate(()=>window.__skinLists>=3));await p.getByRole('button',{name:'Apply Fixture skin 5',exact:true}).click();await p.getByText('Fixture skin 5 applied. Rejoin your world to see the change.',{exact:true}).waitFor();assert.deepEqual(await p.evaluate(()=>window.__skinApply),[{accountId:'skin-account',id:'saved-5'}]);await p.close();
});

test('file removal and profile deletion expose undo and folder actions',async()=>{
 const p=await browser.newPage();await p.addInitScript(()=>{
  let profile={id:'fixture',name:'My survival',loader:'fabric',version:'26.1',icon:'stone'},state={profiles:[profile],selected:'fixture',settings:{memory:4096},account:null};let files=[{name:'test.jar',size:2048,title:'Test mod'}];window.__actions=[];
  window.__TAURI_INTERNALS__={transformCallback:()=>0,invoke:async(cmd,args)=>{window.__actions.push({cmd,args});if(cmd==='bootstrap')return {state,dataDirectory:'/fixture'};if(cmd==='status')return {busy:false};if(cmd==='social_status')return {connected:false,friends:[],invites:[]};if(cmd==='read_modules')return {};if(cmd==='list_content')return files;if(cmd==='remove_content'){files=[];return {token:'file'}};if(cmd==='remove_profile'){state={...state,profiles:[],selected:null};return {token:'profile'}};if(cmd==='restore_removed'){if(args.token==='profile')state={...state,profiles:[profile],selected:'fixture'};else files=[{name:'test.jar',size:2048}];return}if(cmd==='plugin:event|listen')return 1;return []}};
 });await p.goto('http://127.0.0.1:1420');await p.getByRole('tab',{name:'Mods',exact:true}).click();await p.getByRole('button',{name:'Open mods folder',exact:true}).click();await p.getByRole('button',{name:'Remove test.jar',exact:true}).click();await p.getByText('test.jar removed').waitFor();await p.getByRole('button',{name:'Undo',exact:true}).click();await p.getByRole('button',{name:'Remove test.jar',exact:true}).waitFor();
 for(const tab of ['Packs','Shaders']){await p.getByRole('tab',{name:tab,exact:true}).click();await p.getByRole('button',{name:`Open ${tab.toLowerCase()} folder`,exact:true}).click();}
 await p.getByRole('button',{name:'Delete profile',exact:true}).click();await p.getByRole('dialog').getByText('worlds, mods and settings',{exact:false}).waitFor();await p.getByRole('dialog').getByRole('button',{name:'Delete profile',exact:true}).click();await p.getByRole('button',{name:'CREATE PROFILE',exact:true}).waitFor();await p.getByRole('button',{name:'Undo',exact:true}).click();await p.getByRole('button',{name:'LAUNCH 26.1',exact:true}).waitFor();assert.equal((await p.evaluate(()=>window.__actions.filter(a=>a.cmd==='open_content_folder'))).length,3);await p.close();
});
test('invitation popups persist across pages, keep pending invites in Friends and join without a running profile',async()=>{
 const p=await browser.newPage({viewport:{width:1280,height:800}});await p.addInitScript(()=>{window.__actions=[];window.__view={connected:true,self:{id:'me',code:'123456ABCDEF'},friends:[{id:'a',name:'Alex',accepted:0,direction:'incoming',online:true,kind:'unbox'}],invites:[{id:'world',receiver:'me',senderName:'Sam',expires:Date.now()+300000,room:{name:'Survival'}}]};window.__TAURI_INTERNALS__={invoke:async(cmd,args)=>{if(cmd==='bootstrap')return {state:{profiles:[],selected:null,settings:{memory:4096},account:null},dataDirectory:'/fixture'};if(cmd==='status')return {busy:false};if(cmd==='social_status')return window.__view;if(cmd==='social_action'){window.__actions.push(args);if(args.op==='accept')window.__view.friends[0].accepted=1;return window.__view}return []}}});
 await p.goto('http://127.0.0.1:1420');const friend=p.getByRole('article',{name:'Friend request',exact:true}),world=p.getByRole('article',{name:'World invitation',exact:true});await friend.waitFor();await world.waitFor();await p.waitForTimeout(650);assert.ok(await friend.evaluate(e=>e.getBoundingClientRect().width*e.getBoundingClientRect().height)<=9800);await p.screenshot({path:'.cache/invitation-popups.png'});await p.getByRole('button',{name:'Settings',exact:true}).click();await friend.getByRole('button',{name:'Accept',exact:true}).click();await friend.waitFor({state:'detached'});await p.getByRole('button',{name:'Home',exact:true}).click();await p.getByRole('button',{name:"Join Sam's world"}).waitFor();await world.getByRole('button',{name:'Join',exact:true}).click();assert.deepEqual(await p.evaluate(()=>window.__actions),[{op:'accept',id:'a'},{op:'join',id:'world'}]);await p.waitForTimeout(2200);assert.equal(await p.locator('.invitation-popup').count(),0);await p.close();
});

test('local skin rail scrolls, renames skins and keeps player identity stable',async()=>{
 const p=await browser.newPage({viewport:{width:1280,height:820}});await p.addInitScript(()=>{const c=document.createElement('canvas');c.width=c.height=64;c.getContext('2d').fillRect(0,0,64,64);const skin=c.toDataURL();let account={accountId:'local:stable',name:'LocalPlayer',type:'local',uuid:'11111111111141118111111111111111',skin},skins=Array.from({length:8},(_,i)=>({id:'s'+i,name:'Skin '+i,model:'classic',skin}));window.__changes=[];window.__TAURI_INTERNALS__={invoke:async(cmd,args)=>{if(cmd==='bootstrap')return {state:{profiles:[],settings:{memory:4096},account},dataDirectory:'/test'};if(cmd==='status')return {busy:false};if(cmd==='asset_action')return {items:[],features:[],equipment:[]};if(cmd==='skin_library_list')return skins;if(cmd==='skin_library_rename'){skins=skins.map(s=>s.id===args.id?{...s,name:args.name}:s);window.__changes.push(cmd);return skins}if(cmd==='auth_local_rename'){account={...account,name:args.name};window.__changes.push(args);return {account,accounts:[account]}}if(cmd==='social_status')return {friends:[],invites:[]};return []}}});
 await p.goto('http://127.0.0.1:1420');await p.getByRole('button',{name:'Skins',exact:true}).click();await p.getByLabel('Local player',{exact:true}).waitFor();assert.equal(await p.getByRole('button',{name:/^Preview Skin /}).count(),8);assert.ok(await p.locator('.skin-scroll').evaluate(e=>e.scrollHeight>e.clientHeight));
 await p.getByRole('button',{name:'Preview Skin 0',exact:true}).hover();await p.getByRole('button',{name:'Rename Skin 0',exact:true}).click();await p.getByLabel('Skin name',{exact:true}).fill('Forest');await p.getByRole('button',{name:'Save',exact:true}).click();await p.getByRole('button',{name:'Preview Forest'}).waitFor();await p.getByRole('button',{name:'Change player name'}).click();await p.getByLabel('Player name',{exact:true}).fill('NewLocal');await p.getByRole('button',{name:'Save',exact:true}).click();await p.locator('.skin-identity strong').filter({hasText:'NewLocal'}).waitFor();assert.deepEqual(await p.evaluate(()=>window.__changes),['skin_library_rename',{accountId:'local:stable',name:'NewLocal'}]);await p.screenshot({path:'test-results/local-skin-library.png'});await p.close();
});
test('server news opens safely and the update icon requires an available release',async()=>{
 const p=await browser.newPage();await p.addInitScript(()=>{window.__TAURI_INTERNALS__={invoke:async cmd=>{if(cmd==='bootstrap')return {state:{profiles:[],settings:{memory:4096},account:null},dataDirectory:'/test'};if(cmd==='status')return {busy:false};if(cmd==='social_status')return {friends:[],invites:[]};if(cmd==='news_feed')return {posts:[{id:'remote',title:'Server news',summary:'Fresh content',body:'<script>not executable</script>',published:Date.now()}]};if(cmd==='check_update'||cmd==='update_status')return {status:'available',version:'0.3.0',notes:'Account migration and skins'};return []}}});await p.goto('http://127.0.0.1:1420');await p.getByRole('button',{name:/Server news/}).click();await p.getByRole('dialog',{name:'Server news'}).getByText('<script>not executable</script>').waitFor();await p.getByRole('button',{name:'Close article'}).click();await p.getByRole('button',{name:'Update available',exact:true}).click();await p.getByText('Account migration and skins',{exact:true}).waitFor();assert.equal(await p.getByRole('button',{name:'Install and restart'}).isEnabled(),true);await p.close();
});
test('skin library restricts redemption to Microsoft and refreshes owned logos after redemption',async()=>{
 for(const type of ['local','microsoft']){
 const p=await browser.newPage();await p.addInitScript(type=>{
 const account={accountId:'fixture',uuid:'1'.repeat(32),type,name:'AssetTester'};let assets={items:[],features:[],equipment:[]};window.__assetCalls=[];
 window.__TAURI_INTERNALS__={invoke:async(cmd,args)=>{
 if(cmd==='bootstrap')return {state:{profiles:[],selected:null,settings:{memory:4096,minimize:false},account},dataDirectory:'/test'};
 if(cmd==='auth_accounts')return {accounts:[account],account};if(cmd==='status')return {busy:false};if(cmd==='social_status')return {connected:true,friends:[],invites:[],assets};
 if(cmd==='asset_action'){window.__assetCalls.push(args);if(args.op==='redeem'){if(args.value==='INVALID')throw Error('Invalid redemption code');assets={items:[{id:'unbox-fluttering-cash',kind:'logo',name:'Cash',resource:null}],features:['supporter'],equipment:[]}}if(args.op==='equip-logo')assets.equipment=args.value?[{slot:'logo',item:args.value}]:[];return assets}if(cmd==='skin_library_list')return [];return null;
 }};
 },type);await p.goto('http://127.0.0.1:1420');await p.getByRole('button',{name:'Skins',exact:true}).click();await p.getByLabel('Logo library').waitFor();const redeem=p.getByRole('button',{name:'Redeem',exact:true});
 if(type==='local'){assert.equal(await redeem.isDisabled(),true);assert.equal((await p.evaluate(()=>window.__assetCalls)).length,0)}else{
 await redeem.click();await p.getByLabel('Redemption code',{exact:true}).fill('INVALID');await p.getByRole('button',{name:'Redeem code',exact:true}).click();await p.getByRole('alert').getByText('Invalid redemption code').waitFor();await p.getByLabel('Redemption code',{exact:true}).fill('TEST-VALID');await p.getByRole('button',{name:'Redeem code',exact:true}).click();await p.getByRole('status').getByText('Redeemed. Your assets and permissions are ready.').waitFor();await p.getByRole('button',{name:'Use Cash logo'}).click();await p.waitForFunction(()=>document.querySelector('[aria-label="Use Cash logo"]')?.getAttribute('aria-pressed')==='true');assert.equal(await p.locator('.logo-grid button').count(),2);
 for(const [width,height]of [[1000,720],[1280,820],[1920,1080]]){await p.setViewportSize({width,height});assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);const box=await redeem.boundingBox();assert.ok(box.x+box.width<=width);await p.screenshot({path:`test-results/redeem-${width}.png`})}
 await redeem.click();await p.keyboard.press('Escape');assert.equal(await p.getByRole('dialog').count(),0);
 }await p.close();
 }
});
test('a friend displays their equipped crown, switches to cash, then returns to the basic logo',async()=>{
 const p=await browser.newPage();await p.addInitScript(()=>{window.__friendLogo={id:'unbox-creator-crown',name:'Creator crown',resource:null};window.__TAURI_INTERNALS__={invoke:async cmd=>{if(cmd==='bootstrap')return {state:{profiles:[],settings:{memory:4096},account:null},dataDirectory:'/fixture'};if(cmd==='status')return {busy:false};if(cmd==='social_status')return {connected:true,friends:[{id:'friend',name:'CreatorFriend',kind:'microsoft',accepted:1,online:true,state:'Playing',logo:window.__friendLogo}],invites:[]};return []}}});await p.goto('http://127.0.0.1:1420');await p.locator('.friend-badge[src$="unbox-creator-crown.svg"]').waitFor();await p.evaluate(()=>{window.__friendLogo={id:'unbox-fluttering-cash',name:'Cash',resource:null}});await p.locator('.friend-badge[src$="unbox-fluttering-cash.svg"]').waitFor();await p.evaluate(()=>{window.__friendLogo=null});await p.locator('svg.friend-badge').waitFor();assert.equal(await p.locator('img.friend-badge').count(),0);await p.close();
});

async function marketFixture(p){
 await p.route('https://cdn.modrinth.com/**',route=>route.fulfill({status:200,contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="640" height="300"><defs><linearGradient id="a"><stop stop-color="#637c89"/><stop offset="1" stop-color="#283e36"/></linearGradient></defs><rect width="640" height="300" fill="url(#a)"/><path d="M0 230 130 140 220 200 360 80 640 230V300H0" fill="#638271"/><path d="M0 260 180 210 270 230 440 155 640 260V300H0" fill="#354d47"/></svg>'}));
 await p.addInitScript(()=>{
  window.__marketCalls=[];window.__marketInstalled=false;window.__marketFail=false;window.__searchFail=false;window.__cancel=false;
  window.__profiles=[{id:'00000000-0000-4000-8000-000000000001',name:'Survival',version:'26.3',loader:'neoforge',icon:'stone'},{id:'00000000-0000-4000-8000-000000000002',name:'Creative',version:'26.1',loader:'fabric',icon:'bricks'},{id:'00000000-0000-4000-8000-000000000003',name:'Last played · Sam',version:'26.3',loader:'fabric',icon:'grass_block',sharedHost:'friend'}];
  window.__TAURI_INTERNALS__={invoke:async(cmd,args)=>{window.__marketCalls.push({cmd,args});if(cmd==='bootstrap')return {state:{profiles:window.__profiles,selected:window.__profiles[0].id,settings:{memory:4096},account:null}};if(cmd==='save_state')return args.state;if(cmd==='status')return {busy:false};if(cmd==='social_status')return {friends:[],invites:[]};if(cmd==='list_content')return window.__marketInstalled?[{name:'fixture.jar',title:'Forest Details',icon:'https://cdn.modrinth.com/data/demo/icon.png',source:'Modrinth',size:1000}]:[];if(cmd==='catalog_categories')return [{name:'adventure'},{name:'decoration'}];if(cmd==='catalog_search'){if(window.__searchFail)throw Error('Could not reach Modrinth. Check your connection and retry.');return {total_hits:42,hits:Array.from({length:6},(_,i)=>({project_id:'demo'+i,slug:'forest-details',title:['Forest Details','Soft Skies','Better Building','Quiet Interiors','Fresh Landscapes','Simple Light'][i],author:'Studio',description:'Thoughtful details for your next world. Explore, build and make it yours.',icon_url:window.__brokenIcon?'https://cdn.modrinth.com/data/demo/broken.png':'https://cdn.modrinth.com/data/demo/icon.png',featured_gallery:window.__noCover?null:'https://cdn.modrinth.com/data/demo/cover.png',downloads:123456,categories:['adventure','decoration']}))}}if(cmd==='catalog_project'){if(window.__detailFail)throw Error('Description unavailable');return {slug:'forest-details',title:'Forest Details',description:'Thoughtful details for your next world.',body:'# About this mod\n\nBuild forests with **seasonal trees**.\n\n| Feature | Supported |\n| --- | --- |\n| Trees | Yes |\n\n<details><summary>How to use</summary>Plant a sapling.</details>\n\n<img src="https://cdn.modrinth.com/data/demo/icon.png" alt="Example"/>\n<script>window.__unsafe=true</script><iframe src="https://example.com"></iframe><a href="javascript:alert(1)">Unsafe link</a><img src="x" onerror="window.__unsafe=true"/>',gallery:[],license:{name:'MIT'},loaders:['fabric','neoforge'],game_versions:['26.3']}}if(cmd==='catalog_plan'){if(window.__planFail)throw Error('No compatible file for this profile');return {token:'plan',profileId:args.id,profile:window.__profiles.find(p=>p.id===args.id),entries:[{title:'Forest Details',version:'1.2',kind:args.kind,icon:'https://cdn.modrinth.com/data/demo/icon.png',file:{filename:'fixture.jar',size:1048576}},{title:'Required Library',version:'1.0',kind:args.kind,file:{filename:'lib.jar',size:1024}}],provided:[{title:'Fabric API',managed:true}],optional:1,total:1049600}};if(cmd==='catalog_install'){if(window.__slowInstall)await new Promise((resolve,reject)=>{window.__cancelInstall=()=>reject(Error('Installation canceled. No profile files were changed.'))});if(window.__marketFail)throw Error('Download checksum failed. Nothing was installed.');window.__marketInstalled=true;return {installed:2}}if(cmd==='catalog_status')return {stage:'Downloading',downloaded:512,total:1024};if(cmd==='catalog_cancel'){window.__cancel=true;window.__cancelInstall?.();return}return []}};
 });await p.goto('http://127.0.0.1:1420');await p.getByRole('tab',{name:'Mods',exact:true}).click();await p.getByRole('button',{name:'Browse mods',exact:true}).click();await p.getByRole('button',{name:'View Forest Details'}).waitFor();
}
test('market locks environment, filters search and installs a dependency plan into the chosen profile',async()=>{
 const p=await browser.newPage({viewport:{width:1280,height:820}});await marketFixture(p);
 for(const [width,height]of [[1000,720],[1280,820],[1920,1080]]){await p.setViewportSize({width,height});await p.screenshot({path:`test-results/modrinth-${width}.png`});assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false)}
 await p.getByLabel('Search Modrinth').fill('forest');await p.getByRole('button',{name:'Filters',exact:true}).click();await p.getByRole('checkbox',{name:'decoration',exact:true}).check();await p.getByLabel('Sort results').selectOption('downloads');await p.waitForTimeout(350);const search=await p.evaluate(()=>window.__marketCalls.filter(c=>c.cmd==='catalog_search').at(-1).args);assert.equal(search.query,'forest');assert.equal(search.category,'decoration');assert.equal(search.sort,'downloads');assert.equal(search.id,'00000000-0000-4000-8000-000000000001');assert.equal(search.kind,'Mods');
 await p.getByLabel('Install to profile').selectOption('00000000-0000-4000-8000-000000000002');await p.getByRole('button',{name:'View Forest Details'}).waitFor();await p.getByRole('button',{name:'View Forest Details'}).click();await p.getByText('Required Library',{exact:true}).waitFor();assert.equal(await p.evaluate(()=>window.__marketCalls.filter(c=>c.cmd==='catalog_install').length),0);await p.screenshot({path:'test-results/modrinth-install-plan.png'});await p.getByRole('button',{name:'Install',exact:true}).click();await p.getByRole('button',{name:'View profile'}).click();await p.getByRole('button',{name:'Browse mods',exact:true}).waitFor();assert.equal(await p.locator('.file-card img').getAttribute('src'),'https://cdn.modrinth.com/data/demo/icon.png');assert.equal(await p.getByRole('button',{name:'Creative · fabric 26.1'}).getAttribute('class').then(s=>s.includes('active')),true);await p.close();
});
test('market retries failed downloads, shares pack/shader entry flow and blocks friend profile mutations',async()=>{
 const p=await browser.newPage();await marketFixture(p);await p.getByRole('button',{name:'View Forest Details'}).click();await p.getByRole('button',{name:'Install',exact:true}).waitFor();await p.evaluate(()=>window.__marketFail=true);await p.getByRole('button',{name:'Install',exact:true}).click();await p.getByRole('alert').getByText('Download checksum failed.',{exact:false}).waitFor();assert.equal(await p.evaluate(()=>window.__marketInstalled),false);await p.evaluate(()=>window.__marketFail=false);await p.getByRole('button',{name:'Check again'}).click();await p.getByRole('button',{name:'Back to results'}).click();
 await p.getByRole('button',{name:'Resource packs',exact:true}).click();await p.getByRole('button',{name:'View Forest Details'}).waitFor();assert.equal(await p.evaluate(()=>window.__marketCalls.filter(c=>c.cmd==='catalog_search').at(-1).args.kind),'Packs');await p.getByRole('button',{name:'Shaders',exact:true}).click();await p.getByRole('button',{name:'View Forest Details'}).waitFor();assert.equal(await p.evaluate(()=>window.__marketCalls.filter(c=>c.cmd==='catalog_search').at(-1).args.kind),'Shaders');
 await p.getByRole('button',{name:'Back to profile'}).click();await p.getByRole('button',{name:'Last played · Sam · fabric 26.3'}).click();assert.equal(await p.getByRole('button',{name:'Browse shaders'}).isDisabled(),true);await p.getByText('Managed by your friend · Read-only').waitFor();assert.equal(await p.locator('.file-remove').count(),0);await p.close();
});
test('market search errors recover and an in-flight download can be canceled without losing its profile',async()=>{
 const p=await browser.newPage();await marketFixture(p);await p.evaluate(()=>window.__searchFail=true);await p.getByLabel('Search Modrinth').fill('retry');await p.getByRole('alert').waitFor();await p.evaluate(()=>window.__searchFail=false);await p.getByRole('button',{name:'Retry',exact:true}).click();await p.getByRole('button',{name:'View Forest Details'}).click();await p.getByRole('button',{name:'Install',exact:true}).waitFor();await p.evaluate(()=>window.__slowInstall=true);await p.getByRole('button',{name:'Install',exact:true}).click();await p.getByRole('button',{name:'Cancel download'}).waitFor();assert.equal(await p.getByRole('button',{name:'Home',exact:true}).isDisabled(),true);assert.equal(await p.getByRole('button',{name:'Back to results'}).isDisabled(),true);await p.getByRole('button',{name:'Cancel download'}).click();await p.getByRole('alert').getByText('Installation canceled.',{exact:false}).waitFor();assert.equal(await p.evaluate(()=>window.__marketInstalled),false);await p.getByRole('button',{name:'Back to results'}).click();assert.equal(await p.getByRole('button',{name:'Home',exact:true}).isDisabled(),false);await p.close();
});

test('Discover sidebar works independently, combines categories and keeps friend profiles read-only',async()=>{
 const p=await browser.newPage({viewport:{width:1280,height:820}});await marketFixture(p);
 await p.getByRole('button',{name:'Home',exact:true}).click();await p.getByRole('button',{name:'Last played · Sam · fabric 26.3'}).click();await p.getByRole('button',{name:'Discover',exact:true}).click();await p.getByRole('button',{name:'View Forest Details'}).waitFor();
 assert.equal(await p.getByLabel('Install to profile').inputValue(),'');
 assert.equal(await p.getByLabel('Install to profile').locator('option').filter({hasText:'Read-only'}).evaluate(e=>e.disabled),true);
 await p.getByRole('button',{name:'Filters',exact:true}).click();await p.getByRole('checkbox',{name:'adventure',exact:true}).check();await p.getByRole('checkbox',{name:'decoration',exact:true}).check();await p.waitForTimeout(400);
 assert.equal(await p.evaluate(()=>window.__marketCalls.filter(c=>c.cmd==='catalog_search').at(-1).args.category),'adventure,decoration');
 await p.screenshot({path:'test-results/discover-filters.png'});
 await p.getByRole('button',{name:'Clear filters',exact:true}).click();await p.getByLabel('Install to profile').selectOption('');await p.getByRole('button',{name:'View Forest Details'}).waitFor();
 assert.equal(await p.evaluate(()=>window.__marketCalls.filter(c=>c.cmd==='catalog_search').at(-1).args.id),'');
 const plans=await p.evaluate(()=>window.__marketCalls.filter(c=>c.cmd==='catalog_plan').length);await p.getByRole('button',{name:'View Forest Details'}).click();await p.getByText('Choose a profile to check compatible files.',{exact:false}).waitFor();assert.equal(await p.evaluate(()=>window.__marketCalls.filter(c=>c.cmd==='catalog_plan').length),plans);await p.close();
});

test('project descriptions load without a profile, sanitize remote HTML and install from detail',async()=>{
 const p=await browser.newPage({viewport:{width:1280,height:820}});await marketFixture(p);
 await p.getByRole('button',{name:'Discover',exact:true}).click();await p.getByRole('button',{name:'View Forest Details'}).click();
 await p.getByRole('heading',{name:'About this mod'}).waitFor();assert.equal(await p.getByRole('button',{name:'Install',exact:true}).count(),0);
 assert.equal(await p.locator('.project-description table').count(),1);assert.equal(await p.locator('.project-description script,.project-description iframe,[onerror]').count(),0);assert.equal(await p.locator('.project-description a[href^="javascript:"]').count(),0);assert.equal(await p.evaluate(()=>window.__unsafe),undefined);
 await p.evaluate(()=>window.__planFail=true);await p.getByLabel('Install to profile').selectOption('00000000-0000-4000-8000-000000000002');await p.getByRole('alert').getByText('No compatible file',{exact:false}).waitFor();assert.equal(await p.getByRole('heading',{name:'About this mod'}).isVisible(),true);
 await p.evaluate(()=>window.__planFail=false);await p.getByRole('button',{name:'Check again'}).click();await p.getByRole('button',{name:'Install',exact:true}).waitFor();
 for(const [width,height]of [[1000,720],[1280,820],[1920,1080]]){await p.setViewportSize({width,height});assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);assert.equal(await p.getByRole('button',{name:'Install',exact:true}).isVisible(),true);await p.screenshot({path:`test-results/project-details-${width}.png`})}
 await p.getByRole('button',{name:'Install',exact:true}).click();await p.getByRole('button',{name:'View profile'}).click();await p.getByRole('button',{name:'Creative · fabric 26.1'}).waitFor();await p.close();
});
test('project description errors retry and missing cover or failed icon has a visible fallback',async()=>{
 const p=await browser.newPage();await marketFixture(p);await p.evaluate(()=>{window.__noCover=true;window.__detailFail=true});await p.getByLabel('Search Modrinth').fill('no cover');await p.locator('.market-cover-fallback img').first().waitFor();
 await p.route('https://cdn.modrinth.com/**/broken.png',r=>r.abort());await p.getByRole('button',{name:'View Forest Details'}).click();await p.getByRole('alert').getByText('Description unavailable').waitFor();
 await p.evaluate(()=>window.__detailFail=false);await p.getByRole('button',{name:'Retry description'}).click();await p.getByRole('heading',{name:'About this mod'}).waitFor();await p.getByRole('button',{name:'Back to results'}).click();await p.evaluate(()=>window.__brokenIcon=true);await p.getByLabel('Search Modrinth').fill('broken icon');await p.locator('.market-cover-fallback .market-initial').first().waitFor();assert.equal(await p.locator('.market-icon .market-initial').first().innerText(),'F');await p.close();
});
