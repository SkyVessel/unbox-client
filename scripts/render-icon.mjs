import {chromium} from '@playwright/test';
import {readFileSync,mkdirSync} from 'node:fs';
const browser=await chromium.launch({headless:true});
const page=await browser.newPage({viewport:{width:1024,height:1024},deviceScaleFactor:1});
const logo=readFileSync('app/public/assets/logo.svg','utf8');
await page.setContent(`<style>html,body{margin:0;background:transparent}.icon{position:absolute;inset:52px;border-radius:210px;background:#101012;box-shadow:inset 0 5px 0 #ffffff18,0 10px 20px #0002;display:flex;align-items:center;justify-content:center}.icon svg{width:700px;height:700px}</style><div class="icon">${logo}</div>`);
await page.screenshot({path:'src-tauri/icons/icon.png',omitBackground:true});
await browser.close();
