import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {chromium} from '@playwright/test';
import {spawn} from 'node:child_process';
let server,browser;
const url='http://127.0.0.1:1437';
before(async()=>{server=spawn('node',['node_modules/vite/bin/vite.js','--host','127.0.0.1','--port','1437','--strictPort'],{stdio:'ignore'});for(let i=0;i<60;i++){try{await fetch(url);break}catch{await new Promise(r=>setTimeout(r,100))}}browser=await chromium.launch()});
after(async()=>{await browser?.close();server?.kill()});
test('invitation timeout and Hide preserve the world; launcher and game share read state',async()=>{
 const p=await browser.newPage();await p.clock.install();await p.addInitScript(()=>{
  window.__view={connected:true,self:{id:'guest',code:'123456789ABC'},friends:[],seenInvites:[],invites:[{id:'one',receiver:'guest',senderName:'Host',sender:'host',expires:Date.now()+300000,room:{name:'Survival together'}}]};window.__socialActions=[];
  window.__TAURI_INTERNALS__={invoke:async(cmd,args)=>{
   if(cmd==='bootstrap')return {state:{profiles:[],selected:null,settings:{memory:4096,minimize:false},account:{type:'local',accountId:'guest',name:'Guest',uuid:'a'.repeat(32)}},native:true,dataDirectory:'/test'};
   if(cmd==='social_status')return structuredClone(window.__view);
   if(cmd==='social_action'){window.__socialActions.push(args.op);window.__view.seenInvites.push(args.id);return structuredClone(window.__view)}
   if(cmd==='status')return {busy:false,stage:'Idle'};if(cmd==='read_modules')return {};return [];
  }};
 });await p.goto(url);await p.locator('.invitation-popup').waitFor();assert.equal(await p.locator('.world-invite').count(),1);
 await p.clock.fastForward(16000);await p.waitForFunction(()=>document.querySelectorAll('.invitation-popup').length===0);assert.equal(await p.locator('.world-invite').count(),1);assert.deepEqual(await p.evaluate(()=>window.__socialActions),[]);
 await p.evaluate(()=>{window.__view.invites[0].id='two'});await p.clock.fastForward(1100);await p.locator('.invitation-popup').waitFor();await p.locator('.invitation-popup').getByRole('button',{name:'Hide',exact:true}).click();assert.equal(await p.locator('.world-invite').count(),1);assert.deepEqual(await p.evaluate(()=>window.__socialActions),['hide']);
 await p.evaluate(()=>{window.__view.invites[0].id='three';window.__view.seenInvites.push('three')});await p.clock.fastForward(1100);assert.equal(await p.locator('.invitation-popup').count(),0);assert.equal(await p.locator('.world-invite').count(),1);
 await p.evaluate(()=>{window.__view.invites[0].id='four'});await p.clock.fastForward(1100);await p.locator('.invitation-popup').getByRole('button',{name:'Join',exact:true}).click();assert.deepEqual(await p.evaluate(()=>window.__socialActions),['hide','join']);assert.equal(await p.locator('.invitation-popup').count(),0);assert.equal(await p.locator('.world-invite').count(),1);await p.close();
});
test('recent friend profile shows an avatar and refreshes an existing slot after joining',async()=>{
 const p=await browser.newPage({viewport:{width:1280,height:820}});await p.addInitScript(()=>{
  const face='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==';
  window.__state={profiles:[{id:'personal',name:'My world',version:'26.1',loader:'fabric',icon:'stone'},{id:'friend-profile',name:'Last played · Builder',version:'26.1',loader:'fabric',icon:'grass_block',sharedHost:'host',sharedHostName:'Builder',sharedHostFace:face}],selected:'personal',settings:{memory:4096,minimize:false},account:null};window.__job={busy:false,stage:'Idle'};
  window.__TAURI_INTERNALS__={invoke:async(cmd,args)=>{
   if(cmd==='bootstrap')return {state:structuredClone(window.__state),dataDirectory:'/test'};
   if(cmd==='status')return structuredClone(window.__job);
   if(cmd==='social_status')return {connected:true,friends:[],invites:[]};
   if(cmd==='read_modules')return {};
   if(cmd==='save_state'){window.__state={...args.state,profiles:window.__state.profiles};return structuredClone(window.__state)}
   if(cmd==='news_list')return [];return [];
  }};
 });await p.goto(url);
 await p.locator('.profile-friend-avatar img').waitFor();assert.equal(await p.locator('.profile-rail .inventory-slot').count(),2);
 const badge=p.locator('.profile-friend-avatar');const button=p.locator('.friend-profile');const b=await badge.boundingBox(),r=await button.boundingBox();assert.ok(b.x>r.x+r.width/2&&b.y<r.y+r.height/2);
 await button.click();await p.waitForFunction(()=>window.__state.selected==='friend-profile');
 await p.evaluate(()=>{window.__state.profiles[1]={...window.__state.profiles[1],name:'Last played · Builder',version:'26.3',loader:'neoforge'};window.__job={busy:true,stage:'Playing',profileId:'friend-profile'}});
 await p.getByRole('button',{name:'Last played · Builder · neoforge 26.3',exact:true}).waitFor();assert.equal(await p.locator('.profile-rail .inventory-slot').count(),2);
 await p.screenshot({path:'.cache/friend-profile-desktop.png'});await p.setViewportSize({width:1000,height:720});await p.screenshot({path:'.cache/friend-profile-small.png'});
 const small=await badge.boundingBox();assert.ok(small.x>=0&&small.x+small.width<=1000&&small.y>=0&&small.y+small.height<=720);
 await p.close();
});
