import React,{useEffect,useRef,useState} from 'react';
import {Upload,RefreshCw,LoaderCircle,Check,Trash2} from 'lucide-react';
import {call,native} from './bridge';
import type {Account} from './model';
import type {AccountSnapshot} from './AccountControls';
import {SkinFace,SkinPreview} from './SkinPreview';
type Draft={skin:string;name:string;height:number};
type SavedSkin={id:string;name:string;model:'classic'|'slim';created:number;skin:string};
export function SkinManager({account,onAccount,onSignIn}:{account:Account;onAccount:(a:Account|null)=>Promise<void>;onSignIn:()=>void}){
 const [draft,setDraft]=useState<Draft|null>(null),[model,setModel]=useState<'classic'|'slim'>(account.skinModel||'classic'),[saved,setSaved]=useState<SavedSkin[]>([]),[selected,setSelected]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const alive=useRef(true),request=useRef(0);useEffect(()=>{alive.current=true;return()=>{alive.current=false;request.current++}},[]);
 useEffect(()=>{setDraft(null);setModel(account.skinModel||'classic')},[account.accountId,account.skin,account.skinModel]);
 useEffect(()=>{if(!native)return;call<SavedSkin[]>('skin_library_list').then(v=>{if(alive.current)setSaved(v)}).catch(e=>{if(alive.current)setError(String(e))})},[]);
 const choose=async(file?:File)=>{if(!file)return;const seq=++request.current;setError('');setNotice('');try{
  if(file.size>1024*1024||!file.name.toLowerCase().endsWith('.png'))throw Error('Choose a PNG skin under 1 MB.');
  const skin=await new Promise<string>((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(String(r.result));r.onerror=()=>reject(Error('Could not read this file.'));r.readAsDataURL(file)});
  const img=new Image();img.src=skin;await img.decode();if(img.width!==64||![32,64].includes(img.height))throw Error('Choose a 64 × 64 or 64 × 32 skin.');if(account.type!=='microsoft'&&img.height===32)throw Error('Use a 64 × 64 skin for local Unbox identities.');
  if(!alive.current||seq!==request.current)return;setDraft({skin,name:file.name,height:img.height});if(img.height===32)setModel('classic');
 }catch(e){if(alive.current&&seq===request.current)setError(String(e))}};
 const saveDraft=async()=>{if(!draft||busy)return;setBusy(true);setError('');try{const next=await call<SavedSkin[]>('skin_library_import',{name:draft.name,pngBase64:draft.skin.split(',')[1],model});if(!alive.current)return;setSaved(next);setSelected(next.find(s=>s.skin===draft.skin)?.id||'');setDraft(null);setNotice('Saved to your local skin library.')}catch(e){if(alive.current)setError(String(e))}finally{if(alive.current)setBusy(false)}};
 const remove=async(id:string)=>{if(busy)return;setBusy(true);setError('');try{const next=await call<SavedSkin[]>('skin_library_delete',{id});if(!alive.current)return;setSaved(next);if(selected===id)setSelected('')}catch(e){if(alive.current)setError(String(e))}finally{if(alive.current)setBusy(false)}};
 const run=async(upload:boolean)=>{if(busy)return;setBusy(true);setError('');setNotice('');try{
  const result=await call<AccountSnapshot>(upload?'skin_library_apply':'auth_refresh_profile',upload?{accountId:account.accountId||`${account.type}:${account.uuid||account.name}`,id:selected}:{accountId:account.accountId});
  if(!alive.current)return;await onAccount(result.account);setDraft(null);setNotice(upload?'Skin updated. Your Unbox preview refreshes automatically.':result.account?.skinStatus==='cached'?'Skin download is unavailable. Showing your cached skin.':'Profile refreshed.');
 }catch(e){if(alive.current)setError(String(e))}finally{if(alive.current)setBusy(false)}};
 const chosen=saved.find(s=>s.id===selected),preview=draft?{skin:draft.skin,model}:chosen?{skin:chosen.skin,model:chosen.model}:{skin:account.skin,model:account.skinModel||'classic'};
 return <div className="skin-workspace"><div className="skin-stage"><SkinPreview account={{...account,skin:preview.skin,skinModel:preview.model}}/><span>{draft?'Import preview':chosen?'Saved skin preview':'Account skin'}</span></div><div className="skin-tools"><header><h2>{account.name}</h2><p>{account.provider||(account.type==='microsoft'?'Microsoft':account.type==='unbox'?'Unbox':'Local')} · Java Edition</p></header>
  {account.needsSignIn?<div className="quiet-note">Sign in again with the configured application.<button className="secondary" onClick={onSignIn}>Sign in</button></div>:null}
  <div className="skin-actions"><label className={`secondary skin-file ${busy?'disabled':''}`}><Upload size={17}/>Choose PNG<input aria-label="Choose PNG skin" type="file" accept="image/png,.png" disabled={busy||!native||account.needsSignIn} onChange={e=>{void choose(e.target.files?.[0]);e.target.value=''}}/></label><button className="secondary" disabled={busy||!native||account.needsSignIn||account.type!=='microsoft'} onClick={()=>run(false)}><RefreshCw size={16}/>Refresh</button></div>
  <small>{draft?.name||(account.type==='microsoft'?'64 × 64 or 64 × 32 PNG · Up to 1 MB':'64 × 64 PNG · Up to 1 MB')}</small>
  {draft&&<><fieldset disabled={busy} className="skin-model"><legend>Arm model</legend>{(['classic','slim'] as const).map(m=><button key={m} type="button" disabled={m==='slim'&&draft.height===32} aria-pressed={model===m} className={model===m?'active':''} onClick={()=>setModel(m)}>{m==='classic'?'Classic':'Slim'}<small>{m==='classic'?'Wide arms':'Narrow arms'}</small></button>)}</fieldset><button className="secondary" disabled={busy} onClick={saveDraft}>Save to library</button></>}
  <div className="saved-skins" aria-label="Saved skins">{saved.map(s=><div className={`saved-skin ${selected===s.id?'selected':''}`} key={s.id}><button onClick={()=>setSelected(s.id)}><span className="avatar"><SkinFace skin={s.skin}/></span><span><strong>{s.name}</strong><small>{s.model==='slim'?'Slim arms':'Classic arms'}</small></span>{selected===s.id&&<Check size={16}/>}</button><button className="icon-btn" aria-label={`Delete ${s.name}`} disabled={busy} onClick={()=>remove(s.id)}><Trash2 size={15}/></button></div>)}</div>
  <p className="skin-scope">{account.type==='microsoft'?'Apply updates your Minecraft account skin everywhere. Previewing a file does not upload it.':'Apply changes this identity inside Unbox. Uploading a skin to Minecraft services requires a Microsoft Java account.'}</p>
  <div className="skin-actions"><button className="primary" disabled={busy||!native||account.needsSignIn||!selected} onClick={()=>run(true)}>{busy?<LoaderCircle className="spin" size={17}/>:<Check size={17}/>}Apply selected</button>{draft&&<button className="secondary" disabled={busy} onClick={()=>{request.current++;setDraft(null);setModel(account.skinModel||'classic');setError('')}}>Discard import</button>}</div>
  {error&&<p role="alert" className="error">{error}</p>}{notice&&<p role="status" className="skin-notice">{notice}</p>}
 </div></div>
}
