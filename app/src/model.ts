export type Account={name:string;type:"local"|"microsoft";uuid?:string;skin?:string;skinModel?:"classic"|"slim"};
export type Profile = { id:string; name:string; version:'26.1'; loader:'fabric'|'vanilla'; icon:string };
export type Config = Record<string,boolean|number|string>;
export type State = {profiles:Profile[];selected:string|null;settings:{memory:number;reducedMotion:boolean;reducedTransparency:boolean;minimize:boolean};account:Account|null;modules?:Record<string,Config>};
export const defaults:Config = {fps:true,cps:false,coordinates:true,armor:true,crosshair:false,'crosshair.size':5,'crosshair.gap':2,'crosshair.color':'#FFFFFF',particles:false,'particles.density':100,chat:false,'chat.opacity':50,'chat.scale':100,'chat.lines':10,sprint:false,zoom:true,'zoom.factor':3,'hud.x':12,'hud.y':12};
export const empty:State = {profiles:[],selected:null,settings:{memory:4096,reducedMotion:false,reducedTransparency:false,minimize:true},account:null,modules:{}};
export const blocks = ['grass_block','stone','dirt','oak_planks','bricks','tnt','gold_block','iron_block'];
export const blockLabel=(s:string)=>s.replaceAll('_',' ').replace(/\b\w/g,c=>c.toUpperCase());
export const validName=(s:string)=>s.trim().length>0 && s.trim().length<=48;
