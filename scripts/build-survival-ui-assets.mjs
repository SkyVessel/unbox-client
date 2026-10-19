// Render vector sources at 384px, with the same rounded 1.7-unit strokes as the original UI.
import { chromium } from '@playwright/test';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { IdCard, ListOrdered, Camera, Backpack, Lamp, Landmark, Pickaxe, TreePine, Mouse, Box, Timer, Library, Download, PanelsTopLeft, Cloud, Blocks, Shapes } from 'lucide-react';
import { writeFile } from 'node:fs/promises';
const fallingApplePath='M7 2v4 M12 1v4 M17 2v5 M12 12c-2.5-2.4-7-1.5-7 2.6 0 3.3 2.3 7.4 4.6 7.4 1.1 0 1.6-.7 2.4-.7s1.3.7 2.4.7c2.3 0 4.6-4.1 4.6-7.4 0-4.1-4.5-5-7-2.6Z M12 12c0-1.9 1-3 2.5-3.5 M12 11c-1.5 0-2.4-.7-2.8-1.8';
const FallingApple=props=>React.createElement('svg',{...props,width:384,height:384,viewBox:'0 0 24 24',fill:'none',stroke:'currentColor',strokeLinecap:'round',strokeLinejoin:'round'},React.createElement('path',{d:fallingApplePath}));
const out='client-mod/src/main/resources/assets/unbox/textures/ui';
const browser=await chromium.launch();
const page=await browser.newPage({viewport:{width:384,height:384},deviceScaleFactor:1});
const icons={nickname:IdCard,scoreboard:ListOrdered,freecam:Camera,inventory:Backpack,items:FallingApple,dynamiclight:Lamp,gravestones:Landmark,veinminer:Pickaxe,fallingtree:TreePine,mousetweaks:Mouse,shulker:Box,effectbars:Timer,bookshelf:Library,resources:Download,spatial:PanelsTopLeft,ghast:Cloud,building:Blocks,variants:Shapes};
for(const [name,icon] of Object.entries(icons)){
 if(process.argv[2]&&!process.argv.slice(2).includes(name))continue;
 const svg=renderToStaticMarkup(React.createElement(icon,{size:384,color:'#ffffff',strokeWidth:1.7}));
 await page.setContent(`<style>html,body{margin:0;background:transparent}</style>${svg}`);
 await page.screenshot({path:`${out}/${name}.png`,omitBackground:true});
 await writeFile(`${out}/${name}.png.mcmeta`,JSON.stringify({texture:{blur:true,clamp:true}}));
}
await browser.close();
