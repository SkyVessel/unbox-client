import {useEffect,useRef,useState} from 'react';
import {Gift,LoaderCircle} from 'lucide-react';
import {call,native} from './bridge';
import {BrandMark} from './BrandMark';
import type {Account} from './model';
type Item={id:string;kind:string;name:string;resource:string|null};
type Assets={items:Item[];features:string[];equipment:{slot:string;item:string}[];duplicate?:boolean};
const empty:Assets={items:[],features:[],equipment:[]};
const bundled=new Set(['unbox-creator-crown','unbox-fluttering-cash']);
export function LogoLibrary({account}:{account:Account}){
 const [assets,setAssets]=useState(empty),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState(''),[code,setCode]=useState('');
 const dialog=useRef<HTMLDialogElement>(null),generation=useRef(0);
 const eligible=account.type==='microsoft'&&!account.needsSignIn&&!!account.accountId;
 const perform=async(op:string,value='')=>{const v=await call<Assets>('asset_action',{accountId:account.accountId,op,value});if(!v||!Array.isArray(v.items)||!Array.isArray(v.features)||!Array.isArray(v.equipment))throw Error('Could not load your assets. Please retry.');return v};
 useEffect(()=>{const g=++generation.current;setAssets(empty);setBusy(false);setError('');setNotice('');setCode('');dialog.current?.close();if(native&&eligible){setBusy(true);perform('list').then(v=>{if(g===generation.current)setAssets(v)}).catch(e=>{if(g===generation.current)setError(String(e))}).finally(()=>{if(g===generation.current)setBusy(false)})}return()=>{generation.current++}},[account.accountId,eligible]);
 const run=async(op:string,value='')=>{if(busy||!eligible)return;const g=generation.current;setBusy(true);setError('');setNotice('');try{const result=await perform(op,value);if(g!==generation.current)return;setAssets(result);if(op==='redeem'){setCode('');dialog.current?.close();setNotice(result.duplicate?'Already redeemed by this account.':'Redeemed. Your assets and permissions are ready.')}}catch(e){if(g===generation.current)setError(String(e))}finally{if(g===generation.current)setBusy(false)}};
 const logos=assets.items.filter(i=>i.kind==='logo'),equipped=assets.equipment.find(e=>e.slot==='logo')?.item;
 return <aside className="logo-library" aria-label="Logo library"><header><h2>Logos <span>{logos.length+1}</span></h2><button className="secondary" disabled={!native||!eligible} title={eligible?'Redeem assets and permissions':'Requires a verified Minecraft account'} onClick={()=>{setError('');setCode('');dialog.current?.showModal()}}><Gift size={16}/>Redeem</button></header>
 {!eligible&&<p className="logo-hint">Sign in with a verified Minecraft account to redeem codes.</p>}
 <div className="logo-grid"><button className={!equipped?'selected':''} aria-label="Use Unbox logo" aria-pressed={!equipped} disabled={busy||!eligible||!native} onClick={()=>void run('equip-logo')}><BrandMark decorative/><span>Unbox</span></button>{logos.map(i=>{const source=bundled.has(i.id)?`/assets/logos/${i.id}.svg`:i.resource?.startsWith('https://')?i.resource:null;return <button key={i.id} className={equipped===i.id?'selected':''} aria-label={`Use ${i.name} logo`} aria-pressed={equipped===i.id} disabled={busy||!eligible} onClick={()=>void run('equip-logo',i.id)}>{source?<img src={source} alt=""/>:<BrandMark decorative/>}<span>{i.name}</span></button>})}</div>
 {busy&&<p role="status"><LoaderCircle size={15} className="spin"/> Updating assets…</p>}{notice&&<p role="status">{notice}</p>}{error&&!dialog.current?.open&&<p className="error" role="alert">{error}<button className="text-button" disabled={busy} onClick={()=>void run('list')}>Retry</button></p>}
 <dialog ref={dialog} className="redeem-dialog" aria-labelledby="redeem-title" onCancel={()=>{setCode('');setError('')}}><form onSubmit={e=>{e.preventDefault();void run('redeem',code)}}><h2 id="redeem-title">Redeem code</h2><p>Redeem for <strong>{account.name}</strong>. Each code can be used once.</p><label>Redemption code<input autoFocus required autoComplete="off" spellCheck={false} maxLength={100} value={code} onChange={e=>setCode(e.target.value)} disabled={busy}/></label>{error&&<p className="error" role="alert">{error}</p>}<footer><button type="button" className="secondary" disabled={busy} onClick={()=>{setCode('');setError('');dialog.current?.close()}}>Cancel</button><button className="primary" disabled={busy||!code.trim()||!eligible}>{busy?'Redeeming…':'Redeem code'}</button></footer></form></dialog></aside>
}
