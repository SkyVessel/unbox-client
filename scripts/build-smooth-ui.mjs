// Original Barlow glyph atlas and rounded masks; no competitor assets.
import {chromium} from '@playwright/test';
import {readFile,writeFile} from 'node:fs/promises';
const out='client-mod/src/main/resources/assets/unbox/textures/ui';
const browser=await chromium.launch();const page=await browser.newPage();
const font=(await readFile('app/public/assets/barlow-semibold.ttf')).toString('base64');
await page.setContent(`<style>@font-face{font-family:Unbox;src:url(data:font/ttf;base64,${font})}</style>`);
const data=await page.evaluate(async()=>{
 await document.fonts.load('88px Unbox');
 const chars=Array.from({length:95},(_,i)=>String.fromCharCode(i+32)).join('')+'·×–—→←…';
 const c=document.createElement('canvas');c.width=2048;c.height=1024;const g=c.getContext('2d');g.font='88px Unbox';g.fillStyle='#fff';const advances=[];
 [...chars].forEach((ch,i)=>{g.fillText(ch,(i%16)*128+8,Math.floor(i/16)*128+88);advances.push(Math.round(g.measureText(ch).width));});
 const r=document.createElement('canvas');r.width=r.height=256;const ctx=r.getContext('2d');ctx.fillStyle='#fff';ctx.beginPath();ctx.arc(128,128,128,0,Math.PI*2);ctx.fill();
 return {chars,advances,font:c.toDataURL().split(',')[1],circle:r.toDataURL().split(',')[1]};
});
for(const [n,key] of [['glyphs','font'],['rounded','circle']]){await writeFile(`${out}/${n}.png`,Buffer.from(data[key],'base64'));await writeFile(`${out}/${n}.png.mcmeta`,JSON.stringify({texture:{blur:true,clamp:true}}));}
await writeFile('client-mod/src/main/resources/assets/unbox/ui/metrics.json',JSON.stringify({chars:data.chars,advances:data.advances}));
await browser.close();
