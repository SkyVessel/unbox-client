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
 assert.equal(await p.getByRole('button',{name:'Forge Integration pending',exact:true}).isDisabled(),true);
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
 await p.getByRole('button',{name:'Connection status',exact:true}).first().click();await p.getByRole('heading',{name:'Cloudflare is not connected'}).waitFor();assert.equal(await p.getByText('No paid services enabled').count(),1);await p.close();
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
   if(cmd==='auth_info')return {configured:true};
   if(cmd==='auth_begin')return {id:'test',code:'TEST-CODE',interval:1,expiresIn:900,url:'https://www.microsoft.com/link'};
   if(cmd==='auth_poll')return ++window.__polls<2?{status:'pending'}:{status:'complete',account:{name:'SkinFixture',type:'microsoft',uuid:'00000000000040008000000000000001',skin:c.toDataURL(),skinModel:'slim'}};
   if(cmd==='save_state'){window.__saved=args.state;return;}
  }};
 });
 await p.goto('http://127.0.0.1:1420');await p.getByRole('button',{name:'Sign in',exact:true}).click();await p.getByRole('button',{name:'Sign in with Microsoft',exact:true}).click();
 await p.getByText('TEST-CODE',{exact:true}).waitFor();await p.getByRole('button',{name:'Open Microsoft',exact:false}).click();
 await p.locator('.account').getByText('SkinFixture',{exact:true}).waitFor();await p.getByLabel("SkinFixture's Minecraft skin. Drag to rotate.").waitFor();
 assert.equal(await p.locator('.avatar .skin-face').count(),1);
 const result=await p.evaluate(()=>({saved:window.__saved,calls:window.__authCalls}));assert.equal(result.saved.account.type,'microsoft');assert.equal('access_token' in result.saved.account,false);assert.equal('refresh_token' in result.saved.account,false);assert.ok(result.calls.includes('auth_open'));
 await p.getByRole('button',{name:'Skins',exact:true}).click();await p.getByText('Slim · Account skin').waitFor();await p.close();
});
test('canceling Microsoft authorization leaves the selected account unchanged',async()=>{
 const p=await browser.newPage();await p.addInitScript(()=>{window.__cancelled=0;window.__TAURI_INTERNALS__={invoke:async cmd=>{
  if(cmd==='bootstrap')return {state:{profiles:[],selected:null,settings:{memory:4096},account:{name:'LocalUser',type:'local'}},dataDirectory:'/test'};
  if(cmd==='status')return {busy:false,stage:'Idle'};if(cmd==='auth_info')return {configured:true};if(cmd==='auth_begin')return {id:'test',code:'CANCEL',interval:5,expiresIn:900};if(cmd==='auth_poll')return {status:'pending'};if(cmd==='auth_cancel')window.__cancelled++;
 }}});
 await p.goto('http://127.0.0.1:1420');await p.locator('.account').click();await p.getByRole('button',{name:'Sign in with Microsoft',exact:true}).click();await p.getByRole('button',{name:'Cancel sign-in'}).click();
 assert.equal(await p.locator('.account strong').textContent(),'LocalUser');assert.equal(await p.evaluate(()=>window.__cancelled),1);await p.close();
});
