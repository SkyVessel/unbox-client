// Rebuild original launcher logo + ISC-licensed Lucide icons for Minecraft's texture renderer.
import { chromium } from '@playwright/test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { Puzzle, Move, Keyboard, Monitor, Mouse, Compass, Shirt, Crosshair, Sparkles, Footprints, ZoomIn, MessageSquare, Eye, RectangleVertical, Cpu, Star, Settings, X } from 'lucide-react';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
const out='client-mod/src/main/resources/assets/unbox/textures/ui';
await mkdir(out,{recursive:true});
const browser=await chromium.launch();const page=await browser.newPage({viewport:{width:96,height:96},deviceScaleFactor:1});
const Cape=()=>React.createElement("svg",{width:96,height:96,viewBox:"0 0 24 24",fill:"none",stroke:"white",strokeWidth:1.7,strokeLinecap:"round",strokeLinejoin:"round"},React.createElement("path",{d:"M8 3c0 3 8 3 8 0l4 17c-5-2-11 3-16 0L8 3Z M9 8 7 17 M15 8l2 8"}));
const icons={mods:Puzzle,move:Move,keystrokes:Keyboard,fps:Monitor,cps:Mouse,coordinates:Compass,armor:Shirt,crosshair:Crosshair,particles:Sparkles,sprint:Footprints,zoom:ZoomIn,chat:MessageSquare,freelook:Eye,cape:Cape,performance:Cpu,favorite:Star,settings:Settings,close:X};
for(const [name,icon] of Object.entries(icons)){
 const svg=renderToStaticMarkup(React.createElement(icon,{size:96,color:'#ffffff',strokeWidth:1.7}));
 await page.setContent(`<style>html,body{margin:0;background:transparent}</style>${svg}`);
 await page.screenshot({path:`${out}/${name}.png`,omitBackground:true});
}
let svg=await readFile('app/public/assets/logo.svg','utf8');
await page.setViewportSize({width:768,height:768});
svg=svg.replace('viewBox=', 'width="768" height="768" viewBox=');
await page.setContent(`<style>html,body{margin:0;background:transparent}</style>${svg}`);
await page.screenshot({path:`${out}/logo.png`,omitBackground:true});
// Compact name badge: original paths without the large menu logo's padding.
await page.setViewportSize({width:96,height:96});
const badge=(await readFile('app/public/assets/badge.svg','utf8')).replace('viewBox=', 'width="96" height="96" viewBox=');
await page.setContent(`<style>html,body{margin:0;background:transparent}</style>${badge}`);
await page.screenshot({path:`${out}/badge.png`,omitBackground:true});
await writeFile(`${out}/badge.png.mcmeta`,JSON.stringify({texture:{blur:true,clamp:true}}));
await writeFile('client-mod/src/main/resources/lucide-license.txt',await readFile('node_modules/lucide-react/LICENSE','utf8'));
await browser.close();
