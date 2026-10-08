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
 assert.deepEqual(await p.getByLabel('Minecraft version').locator('option').allTextContents(),['26.1 · Java Edition']);
 await p.getByRole('button',{name:'Back',exact:true}).click();
 assert.equal(await p.getByRole('textbox',{name:'Profile name'}).inputValue(),'  Spruce Valley  ');
 await p.getByRole('button',{name:'Confirm',exact:true}).click();await p.getByRole('button',{name:'Confirm',exact:true}).click();
 assert.equal(await p.getByRole('button',{name:'Forge Integration pending',exact:true}).isDisabled(),true);assert.equal(await p.getByRole('button',{name:'NeoForge Integration pending',exact:true}).isDisabled(),true);
 await p.getByRole('button',{name:'Confirm',exact:true}).click();await p.getByRole('button',{name:'Stone',exact:true}).click();
 assert.equal(await p.evaluate(()=>JSON.parse(localStorage.getItem('unbox-preview')||'{}').profiles?.length||0),0);
 await p.getByRole('button',{name:'Create profile',exact:true}).last().click();await p.getByRole('button',{name:'LAUNCH 26.1',exact:true}).waitFor();await p.reload();
 await p.getByRole('button',{name:'Spruce Valley · fabric 26.1'}).waitFor();
 assert.equal(await p.evaluate(()=>JSON.parse(localStorage.getItem('unbox-preview')).profiles[0].icon),'stone');
 await p.getByRole('button',{name:'Client Mods',exact:true}).click();await p.getByRole('switch',{name:'Enable CPS',exact:true}).click();
 await p.getByLabel('Search client mods').fill('CPS');assert.equal(await p.locator('.mod-card').count(),1);
 await p.getByRole('button',{name:'CPS settings',exact:true}).click();await p.keyboard.press('Escape');assert.equal(await p.locator('dialog[open]').count(),0);
 await p.reload();await p.getByRole('button',{name:'Client Mods',exact:true}).click();assert.equal(await p.getByRole('switch',{name:'Enable CPS',exact:true}).getAttribute('aria-checked'),'true');await p.close();
});
test('home panel adapts to compact, windowed and fullscreen dimensions without blank lower half',async()=>{
 const p=await browser.newPage();await p.goto('http://127.0.0.1:1420');await p.locator('.hero').waitFor();
 // Resize the same live page to catch stale sizing when entering and leaving fullscreen.
 for(const [width,height]of [[1000,720],[1280,800],[1920,1080],[3840,2160],[1280,800]]){
  await p.setViewportSize({width,height});
  const layout=await p.evaluate(()=>{const rect=s=>{const r=document.querySelector(s).getBoundingClientRect();return{width:r.width,height:r.height,right:r.right,bottom:r.bottom}};return{panel:rect('.panel'),hero:rect('.hero'),card:rect('.news-card'),bottom:rect('.home-bottom'),overflow:document.documentElement.scrollWidth>innerWidth,broken:[...document.images].filter(i=>i.complete&&!i.naturalWidth).map(i=>i.src)}});
  assert.equal(layout.overflow,false);assert.equal(layout.broken.length,0);assert.ok(layout.hero.height/layout.panel.height>.39&&layout.hero.height/layout.panel.height<.49);assert.ok(height-layout.panel.bottom>=10&&height-layout.panel.bottom<=32);assert.ok(layout.panel.bottom-layout.card.bottom<60);assert.ok(layout.card.height>=230);
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
   if(cmd==='auth_info')return {configured:true,provider:'Unbox'};if(cmd==='auth_accounts')return {accounts:[],account:null};
   if(cmd==='auth_begin')return {id:'test',code:'TEST-CODE',interval:1,expiresIn:900,url:'https://www.microsoft.com/link'};
   if(cmd==='auth_poll')return ++window.__polls<2?{status:'pending'}:{status:'complete',accounts:[],account:{accountId:'fixture',provider:'Unbox',name:'SkinFixture',type:'microsoft',uuid:'00000000000040008000000000000001',skin:c.toDataURL(),skinModel:'slim'}};
   if(cmd==='save_state'){window.__saved=args.state;return;}
  }};
 });
 await p.goto('http://127.0.0.1:1420');await p.getByRole('button',{name:'Sign in',exact:true}).click();await p.getByRole('button',{name:'Sign in with Microsoft',exact:true}).click();
 await p.getByText('TEST-CODE',{exact:true}).waitFor();await p.getByRole('button',{name:'Open Microsoft',exact:false}).click();
 await p.locator('.account').getByText('SkinFixture',{exact:true}).waitFor();await p.getByLabel("SkinFixture's Minecraft skin. Drag to rotate.").waitFor();
 assert.equal(await p.locator('.avatar .skin-face').count(),1);
 const result=await p.evaluate(()=>({saved:window.__saved,calls:window.__authCalls}));assert.equal(result.saved.account.type,'microsoft');assert.equal('access_token' in result.saved.account,false);assert.equal('refresh_token' in result.saved.account,false);assert.ok(result.calls.includes('auth_open'));
 await p.getByRole('button',{name:'Skins',exact:true}).click();assert.equal(await p.getByRole('button',{name:'Slim Narrow arms'}).getAttribute('aria-pressed'),'true');await p.close();
});
test('canceling Microsoft authorization leaves the selected account unchanged',async()=>{
 const p=await browser.newPage();await p.addInitScript(()=>{window.__cancelled=0;window.__TAURI_INTERNALS__={invoke:async cmd=>{
  if(cmd==='bootstrap')return {state:{profiles:[],selected:null,settings:{memory:4096},account:{name:'LocalUser',type:'local'}},dataDirectory:'/test'};
  if(cmd==='status')return {busy:false,stage:'Idle'};if(cmd==='auth_info')return {configured:true,provider:'Unbox'};if(cmd==='auth_accounts')return {accounts:[],account:{name:'LocalUser',type:'local'}};if(cmd==='auth_begin')return {id:'test',code:'CANCEL',interval:5,expiresIn:900};if(cmd==='auth_poll')return {status:'pending'};if(cmd==='auth_cancel')window.__cancelled++;
 }}});
 await p.goto('http://127.0.0.1:1420');await p.locator('.account').click();await p.getByRole('button',{name:'Sign in with Microsoft',exact:true}).click();await p.getByRole('button',{name:'Cancel sign-in'}).click();
 assert.equal(await p.locator('.account strong').textContent(),'LocalUser');assert.equal(await p.evaluate(()=>window.__cancelled),1);await p.close();
});

async function accountFixture(p){
 await p.addInitScript(()=>{
  const c=document.createElement('canvas');c.width=c.height=64;const g=c.getContext('2d');g.fillStyle='#467bac';g.fillRect(0,0,64,64);g.fillStyle='#d9a174';g.fillRect(8,8,8,8);g.clearRect(32,0,32,16);
  const a={accountId:'a',name:'AlexFixture',type:'microsoft',uuid:'00000000000040008000000000000001',skin:c.toDataURL(),skinModel:'slim',skinStatus:'ready',provider:'Unbox',needsSignIn:false};
  const b={...a,accountId:'b',name:'SteveFixture',uuid:'00000000000040008000000000000002',skinModel:'classic'};
  window.__accounts=[a,b];window.__selected='a';window.__calls=[];window.__uploadFail=false;window.__info={configured:true,provider:'Unbox',clientId:'fixture',developmentAvailable:true};
  const snapshot=()=>({accounts:window.__accounts,account:window.__accounts.find(a=>a.accountId===window.__selected)||null});
  window.__TAURI_INTERNALS__={invoke:async(cmd,args)=>{window.__calls.push({cmd,args});
   if(cmd==='bootstrap')return {state:{profiles:[],selected:null,settings:{memory:4096,minimize:false},account:snapshot().account},dataDirectory:'/test'};
   if(cmd==='status')return {busy:false,stage:'Idle'};
   if(cmd==='auth_info')return window.__info;
   if(cmd==='auth_accounts')return snapshot();
   if(cmd==='auth_select'){window.__selected=args.accountId;return snapshot();}
   if(cmd==='auth_sign_out'){window.__accounts=window.__accounts.filter(a=>a.accountId!==args.accountId);return snapshot();}
   if(cmd==='auth_development_setup'){window.__info={...window.__info,provider:'DevLogin · Development'};window.__accounts.forEach(a=>a.needsSignIn=true);return snapshot();}
   if(cmd==='auth_upload_skin'){if(window.__uploadFail)throw Error('Skin upload: connection failed');Object.assign(window.__accounts.find(a=>a.accountId===args.accountId),{skin:'data:image/png;base64,'+args.pngBase64,skinModel:args.model});return snapshot();}
   if(cmd==='auth_refresh_profile')return snapshot();
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
test('provider changes preserve accounts and explicitly require new authorization',async()=>{
 const p=await browser.newPage();await accountFixture(p);await p.locator('.account').click();await p.getByRole('button',{name:'Application setup'}).click();
 await p.getByRole('button',{name:'Use DevLogin for development'}).click();await p.getByText('Sign in again',{exact:true}).first().waitFor();
 assert.equal(await p.getByText('Sign in again',{exact:true}).count(),2);assert.equal(await p.evaluate(()=>window.__saved.account.needsSignIn),true);await p.close();
});
test('skin preview stays local until Apply; failures preserve saved skin; upload and refresh use selected identity',async()=>{
 const p=await browser.newPage({viewport:{width:1280,height:820}});await accountFixture(p);await p.getByRole('button',{name:'Skins',exact:true}).click();
 const original=await p.evaluate(()=>window.__accounts[0].skin);
 const png=await p.evaluate(()=>{const c=document.createElement('canvas');c.width=c.height=64;const g=c.getContext('2d');g.fillStyle='#b56e55';g.fillRect(0,0,64,64);g.clearRect(32,0,32,16);return c.toDataURL().split(',')[1]});
 await p.getByLabel('Choose PNG skin').setInputFiles({name:'new-skin.png',mimeType:'image/png',buffer:Buffer.from(png,'base64')});await p.getByText('Preview · not applied').waitFor();
 assert.equal(await p.evaluate(()=>window.__calls.filter(c=>c.cmd==='auth_upload_skin').length),0);assert.equal(await p.evaluate(()=>window.__accounts[0].skin),original);
 await p.getByRole('button',{name:'Classic Wide arms'}).click();await p.evaluate(()=>window.__uploadFail=true);await p.getByRole('button',{name:'Apply skin'}).click();await p.getByRole('alert').waitFor();
 assert.equal(await p.evaluate(()=>window.__accounts[0].skin),original);await p.evaluate(()=>window.__uploadFail=false);await p.getByRole('button',{name:'Apply skin'}).click();await p.getByText('Skin updated. Rejoin your world to see the change.').waitFor();
 assert.equal(await p.evaluate(()=>window.__saved.account.skinModel),'classic');assert.equal(await p.evaluate(()=>window.__saved.account.skin),'data:image/png;base64,'+png);
 await p.getByRole('button',{name:'Refresh',exact:true}).click();await p.getByText('Profile refreshed.').waitFor();assert.equal(await p.evaluate(()=>window.__calls.find(c=>c.cmd==='auth_refresh_profile').args.accountId),'a');
 assert.equal(await p.getByRole('button',{name:'Apply skin'}).isDisabled(),true);
 await p.screenshot({path:'test-results/account-skins.png'});
 await p.getByLabel('Choose PNG skin').setInputFiles({name:'invalid.png',mimeType:'image/png',buffer:Buffer.from('not a skin')});await p.getByRole('alert').waitFor();assert.equal(await p.getByRole('button',{name:'Apply skin'}).isDisabled(),true);await p.close();
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
 const p=await browser.newPage();await p.addInitScript(()=>{window.__calls=[];window.__view={connected:true,self:{id:'self',code:'AAAABBBBCCCC'},canInvite:true,friends:[{id:'pending',name:'Bob',kind:'microsoft',accepted:0,direction:'incoming',online:true},{id:'offline',name:'Carol',kind:'microsoft',accepted:1,online:false}],invites:[{id:'invite',receiver:'self',senderName:'David',expires:Date.now()+60000,room:{name:'World'}}]};window.__TAURI_INTERNALS__={invoke:async(cmd,args)=>{window.__calls.push({cmd,args});if(cmd==='bootstrap')return {state:{profiles:[],selected:null,settings:{memory:4096},account:{name:'Tester'}}};if(cmd==='status')return {busy:false};if(cmd==='social_status')return window.__view;if(cmd==='social_action'){if(args.op==='accept')window.__view.friends[0].accepted=1;return window.__view}return []}}});await p.goto('http://127.0.0.1:1420');await p.getByRole('button',{name:'Add friend',exact:true}).click();await p.getByLabel('Friend code',{exact:true}).fill('123456ABCDEF');await p.getByRole('button',{name:'Send friend request'}).click();await p.getByText('Friend request sent',{exact:true}).waitFor();await p.getByRole('button',{name:'Accept Bob'}).click();await p.locator('.friend-row').filter({hasText:'Bob'}).getByRole('button',{name:'Invite',exact:true}).click();assert.equal(await p.locator('.friend-row').filter({hasText:'Carol'}).getByRole('button',{name:'Invite',exact:true}).isDisabled(),true);await p.getByRole('button',{name:"Join David's world"}).click();assert.deepEqual(await p.evaluate(()=>window.__calls.filter(c=>c.cmd==='social_action').map(c=>c.args)),[{op:'request',id:'123456ABCDEF'},{op:'accept',id:'pending'},{op:'invite',id:'pending'},{op:'join',id:'invite'}]);await p.close();
});

test('Unbox password registration uses two inputs, signs in, handles wrong passwords, and never persists credentials',async()=>{
 const p=await browser.newPage({deviceScaleFactor:2});await p.addInitScript(()=>{window.__accountCalls=[];window.__saved=null;window.__registered=false;window.__TAURI_INTERNALS__={invoke:async(cmd,args)=>{
  if(cmd==='bootstrap')return {state:{profiles:[],selected:null,settings:{memory:4096},account:null},dataDirectory:'/test'};
  if(cmd==='status')return {busy:false,stage:'Idle'};if(cmd==='auth_info')return {configured:true,provider:'Unbox'};if(cmd==='auth_accounts')return {accounts:[],account:null};
  if(cmd==='auth_unbox_password'){window.__accountCalls.push({email:args.email,mode:args.mode});if(args.password!=='my-test-password')throw Error('Incorrect email or password');if(args.mode==='register')window.__registered=true;return {accounts:[],account:{accountId:'unbox:test',name:'Unbox_aabbccdd',type:'unbox',uuid:'00000000000040008000000000000001',provider:'Unbox',emailVerified:false}}}
  if(cmd==='save_state')window.__saved=args.state;
 }}});
 await p.goto('http://127.0.0.1:1420');await p.getByRole('button',{name:'Sign in',exact:true}).click();await p.getByRole('button',{name:'Create an Unbox account'}).click();
 const f=p.getByRole('form',{name:'Create Unbox account'});assert.equal(await f.locator('input').count(),2);await f.getByLabel('Email',{exact:true}).fill('tester@example.com');await f.getByLabel('Password',{exact:true}).fill('my-test-password');
 assert.equal(await f.getByLabel('Password',{exact:true}).getAttribute('type'),'password');await f.getByRole('button',{name:'Show password'}).click();assert.equal(await f.getByLabel('Password',{exact:true}).getAttribute('type'),'text');await f.getByRole('button',{name:'Hide password'}).click();
 await p.screenshot({path:'.cache/account-password-form.png'});await f.getByRole('button',{name:'Create account',exact:true}).click();await p.locator('.account-name').getByText('Unbox_aabbccdd').waitFor();assert.equal(await p.evaluate(()=>window.__registered),true);
 const saved=await p.evaluate(()=>window.__saved);for(const secret of ['email','token','password','password_hash'])assert.equal(secret in saved.account,false);
 await p.locator('.account').screenshot({path:'.cache/account-badge-fixed.png'});
 await p.reload();await p.getByRole('button',{name:'Sign in',exact:true}).click();await p.getByRole('button',{name:'Sign in with Unbox'}).click();const login=p.getByRole('form',{name:'Unbox sign-in'});await login.getByLabel('Email',{exact:true}).fill('tester@example.com');await login.getByLabel('Password',{exact:true}).fill('wrong-password');await login.getByRole('button',{name:'Sign in',exact:true}).click();await login.getByRole('alert').getByText('Incorrect email or password').waitFor();assert.equal(await p.locator('.account-name').count(),0);
 await login.getByLabel('Password',{exact:true}).fill('my-test-password');await login.getByRole('button',{name:'Sign in',exact:true}).click();await p.locator('.account-name').getByText('Unbox_aabbccdd').waitFor();await p.close();
});
test('Unbox rename updates the account card while retaining UUID and handles a running-game rejection',async()=>{
 const p=await browser.newPage();await p.addInitScript(()=>{let account={accountId:'unbox:rename',name:'BeforeName',type:'unbox',uuid:'00000000000040008000000000000001',provider:'Unbox'};window.__running=true;window.__renameCalls=[];window.__TAURI_INTERNALS__={invoke:async(cmd,args)=>{
  if(cmd==='bootstrap')return {state:{profiles:[],selected:null,settings:{memory:4096},account},dataDirectory:'/test'};if(cmd==='status')return {busy:false,stage:'Idle'};if(cmd==='auth_info')return {configured:true};if(cmd==='auth_accounts')return {accounts:[account],account};if(cmd==='auth_unbox_rename'){window.__renameCalls.push(args);if(window.__running)throw Error('Close the game before changing your player name');account={...account,name:args.name};return {accounts:[account],account}};if(cmd==='save_state')window.__saved=args.state;
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
 await p.getByRole('button',{name:'Client updates',exact:true}).click();
 const check=p.getByRole('button',{name:'Check for updates',exact:true});await check.click();await p.getByRole('alert').getByText('Could not reach GitHub.',{exact:false}).waitFor();
 await check.click();await p.getByRole('status').getByText('No releases published yet.').waitFor();assert.equal(await p.evaluate(()=>window.__installs),0);
 await check.click();const install=p.getByRole('button',{name:'Install and restart',exact:true});await install.waitFor();assert.equal(await install.isDisabled(),true);
 await p.evaluate(()=>{window.__gameBusy=false});await p.waitForFunction(()=>[...document.querySelectorAll('button')].some(b=>b.textContent==='Install and restart'&&!b.disabled));assert.equal(await p.evaluate(()=>window.__installs),0);
 await install.click();await p.getByRole('alert').getByText('Download or signature verification failed.',{exact:false}).waitFor();assert.equal(await p.evaluate(()=>window.__installs),1);assert.equal(await install.isDisabled(),false);
 assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);await p.screenshot({path:'.cache/client-updates.png'});await p.close();
});
