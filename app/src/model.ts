import catalogue from './mod-options.json';
export type Account={accountId?:string;provider?:string;needsSignIn?:boolean;skinStatus?:"ready"|"cached"|"unavailable";name:string;type:"local"|"microsoft"|"unbox";uuid?:string;skin?:string;skinModel?:"classic"|"slim"};
export type Profile = { id:string; name:string; version:'26.1'; loader:'fabric'|'vanilla'|'neoforge'; icon:string };
export type Config = Record<string,boolean|number|string>;
export type State = {profiles:Profile[];selected:string|null;settings:{memory:number;reducedMotion:boolean;reducedTransparency:boolean;minimize:boolean};account:Account|null;modules?:Record<string,Config>};
export const defaults:Config = {fps:true,cps:false,coordinates:true,armor:true,crosshair:false,particles:false,chat:false,sprint:false,zoom:true,minimap:false,ping:false};
for(const tabs of Object.values(catalogue.modules))for(const options of Object.values(tabs))if(Array.isArray(options))for(const o of options)defaults[o.key]=o.kind==='toggle'?o.fallback==='true':o.kind==='slider'?Number(o.fallback):o.fallback;

export const empty:State = {profiles:[],selected:null,settings:{memory:4096,reducedMotion:false,reducedTransparency:false,minimize:true},account:null,modules:{}};
export const blocks = ['grass_block','stone','dirt','oak_planks','bricks','tnt','gold_block','iron_block'];
export const blockLabel=(s:string)=>s.replaceAll('_',' ').replace(/\b\w/g,c=>c.toUpperCase());
export const validName=(s:string)=>s.trim().length>0 && s.trim().length<=48;
