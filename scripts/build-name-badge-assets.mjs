// Export the approved SVGs at a size that fits Minecraft's vanilla 256px font atlas.
// Keep the 512px UI textures separate; bitmap font height does not resize source pixels.
import {chromium} from '@playwright/test';
import {mkdir,readFile} from 'node:fs/promises';
const out='client-mod/src/main/resources/assets/unbox/textures/font';
await mkdir(out,{recursive:true});
const browser=await chromium.launch();
try{
 const page=await browser.newPage({viewport:{width:128,height:128},deviceScaleFactor:1});
 for(const id of ['unbox-creator-crown','unbox-fluttering-cash']){
  const svg=await readFile(`app/public/assets/logos/${id}.svg`,'utf8');
  await page.setContent(`<style>html,body{margin:0;background:transparent}svg{display:block;width:128px;height:128px}</style>${svg}`);
  await page.screenshot({path:`${out}/${id}.png`,omitBackground:true});
 }
}finally{await browser.close();}
