import {invoke} from '@tauri-apps/api/core';
import {empty,type State,type Config} from './model';
export const native='__TAURI_INTERNALS__' in window;
export async function call<T>(cmd:string,args:Record<string,unknown>={}):Promise<T>{
  if(native)return invoke<T>(cmd,args);
  if(cmd==='bootstrap')return {state:JSON.parse(localStorage.getItem('unbox-preview')||'null')||structuredClone(empty),native:false,dataDirectory:'Browser preview'} as T;
  if(cmd==='save_state'){localStorage.setItem('unbox-preview',JSON.stringify(args.state));return undefined as T;}
  if(cmd==='save_modules')return undefined as T;
  if(cmd==='status')return {busy:false,stage:'Idle'} as T;
  if(cmd==='list_content')return [] as T;
  if(cmd==='create_profile')return {...args,id:crypto.randomUUID()} as T;
  throw new Error('Open the desktop app to use this feature.');
}
export const persist=(state:State)=>call<void>('save_state',{state});
export const saveModules=(id:string,config:Config)=>call<void>('save_modules',{id,config});
