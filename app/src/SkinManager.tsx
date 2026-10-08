import React,{useEffect,useRef,useState} from 'react';
import {Upload,RefreshCw,LoaderCircle,Check} from 'lucide-react';
import {call,native} from './bridge';
import type {Account} from './model';
import type {AccountSnapshot} from './AccountControls';
import {SkinPreview} from './SkinPreview';
type Draft={skin:string;name:string;height:number};
export function SkinManager({account,onAccount,onSignIn}:{account:Account;onAccount:(a:Account|null)=>Promise<void>;onSignIn:()=>void}){
 const [draft,setDraft]=useState<Draft|null>(null),[model,setModel]=useState<'classic'|'slim'>(account.skinModel||'classic'),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const alive=useRef(true),request=useRef(0);useEffect(()=>{alive.current=true;return()=>{alive.current=false;request.current++}},[]);
 useEffect(()=>{setDraft(null);setModel(account.skinModel||'classic')},[account.accountId,account.skin,account.skinModel]);
 const choose=async(file?:File)=>{if(!file)return;const seq=++request.current;setError('');setNotice('');try{
  if(file.size>1024*1024||!file.name.toLowerCase().endsWith('.png'))throw Error('Choose a PNG skin under 1 MB.');
  const skin=await new Promise<string>((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(String(r.result));r.onerror=()=>reject(Error('Could not read this file.'));r.readAsDataURL(file)});
  const img=new Image();img.src=skin;await img.decode();if(img.width!==64||![32,64].includes(img.height))throw Error('Choose a 64 × 64 or 64 × 32 skin.');
  if(!alive.current||seq!==request.current)return;setDraft({skin,name:file.name,height:img.height});if(img.height===32)setModel('classic');
 }catch(e){if(alive.current&&seq===request.current)setError(String(e))}};
 const run=async(upload:boolean)=>{if(busy)return;setBusy(true);setError('');setNotice('');try{
  const result=await call<AccountSnapshot>(upload?'auth_upload_skin':'auth_refresh_profile',upload?{accountId:account.accountId,pngBase64:(draft?.skin||account.skin||'').split(',')[1],model}:{accountId:account.accountId});
  if(!alive.current)return;await onAccount(result.account);setDraft(null);setNotice(upload?'Skin updated. Rejoin your world to see the change.':result.account?.skinStatus==='cached'?'Skin download is unavailable. Showing your cached skin.':'Profile refreshed.');
 }catch(e){if(alive.current)setError(String(e))}finally{if(alive.current)setBusy(false)}};
 const changed=!!draft||model!==(account.skinModel||'classic');
 return <div className="skin-workspace"><div className="skin-stage"><SkinPreview account={{...account,skin:draft?.skin||account.skin,skinModel:model}}/><span>{draft?'Preview · not applied':'Account skin'}</span></div><div className="skin-tools"><header><h2>{account.name}</h2><p>{account.provider||'Microsoft'} · Java Edition</p></header>
  {account.needsSignIn?<div className="quiet-note">Sign in again with the configured application.<button className="secondary" onClick={onSignIn}>Sign in</button></div>:null}
  <div className="skin-actions"><label className={`secondary skin-file ${busy?'disabled':''}`}><Upload size={17}/>Choose PNG<input aria-label="Choose PNG skin" type="file" accept="image/png,.png" disabled={busy||!native||account.needsSignIn} onChange={e=>{void choose(e.target.files?.[0]);e.target.value=''}}/></label><button className="secondary" disabled={busy||!native||account.needsSignIn} onClick={()=>run(false)}><RefreshCw size={16}/>Refresh</button></div>
  <small>{draft?.name||'64 × 64 or 64 × 32 PNG · Up to 1 MB'}</small>
  <fieldset disabled={busy} className="skin-model"><legend>Arm model</legend>{(['classic','slim'] as const).map(m=><button key={m} type="button" disabled={m==='slim'&&draft?.height===32} aria-pressed={model===m} className={model===m?'active':''} onClick={()=>setModel(m)}>{m==='classic'?'Classic':'Slim'}<small>{m==='classic'?'Wide arms':'Narrow arms'}</small></button>)}</fieldset>
  <p className="skin-scope">Apply updates your Minecraft account skin everywhere. Previewing a file does not upload it.</p>
  <div className="skin-actions"><button className="primary" disabled={busy||!native||account.needsSignIn||!changed||!(draft?.skin||account.skin)} onClick={()=>run(true)}>{busy?<LoaderCircle className="spin" size={17}/>:<Check size={17}/>}Apply skin</button>{changed&&<button className="secondary" disabled={busy} onClick={()=>{request.current++;setDraft(null);setModel(account.skinModel||'classic');setError('')}}>Discard</button>}</div>
  {error&&<p role="alert" className="error">{error}</p>}{notice&&<p role="status" className="skin-notice">{notice}</p>}
 </div></div>
}
