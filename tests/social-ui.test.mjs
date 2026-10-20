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
