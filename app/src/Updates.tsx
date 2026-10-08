import React,{useEffect,useState} from 'react';
import {Download,RefreshCw,LoaderCircle} from 'lucide-react';
import {call,native} from './bridge';
type Status={status:string;currentVersion?:string;version?:string;notes?:string;error?:string;downloaded?:number;total?:number};
export function Updates({gameBusy}:{gameBusy:boolean}){
 const[view,setView]=useState<Status>({status:'idle'}),[busy,setBusy]=useState(false),[error,setError]=useState('');
 useEffect(()=>{let live=true;const refresh=()=>{if(native)void call<Status>('update_status').then(v=>{if(live&&v)setView(v)}).catch(()=>{})};refresh();const t=setInterval(refresh,700);return()=>{live=false;clearInterval(t)}},[]);
 const run=async(install=false)=>{if(busy)return;setBusy(true);setError('');try{if(install)await call('install_update');else setView(await call<Status>('check_update'))}catch(e){setError(String(e))}finally{setBusy(false)}};
 const installing=['downloading','installing','restarting'].includes(view.status),message=view.status==='unpublished'?'No releases published yet.':view.status==='current'?'You’re up to date.':view.status==='available'?`Version ${view.version} is available.`:view.status==='downloading'?'Downloading and verifying update…':view.status==='installing'?'Installing update…':view.status==='restarting'?'Restarting Unbox…':'Updates from GitHub Releases.';
 return <section className="updates" aria-label="Client updates"><div className="setting"><div><strong>Unbox Client {view.currentVersion||'0.2.1'}</strong><small role="status">{message}</small></div><button className="secondary" disabled={!native||busy||installing} onClick={()=>void run()}>{busy&&!installing?<LoaderCircle size={17} className="spin"/>:<RefreshCw size={17}/>}Check for updates</button></div>
 {view.notes&&<p className="release-notes">{view.notes}</p>}
 {view.status==='available'&&<button className="primary" disabled={busy||gameBusy} onClick={()=>void run(true)}><Download size={17}/>Install and restart</button>}
 {installing&&<progress aria-label="Update download progress" max={view.total||undefined} value={view.total?view.downloaded:undefined}/>}
 {gameBusy&&<p className="context-note">You can check while playing. Close Minecraft before installing.</p>}
 {(error||view.error)&&<p className="error" role="alert">{error||view.error}</p>}
 {!native&&<p className="context-note">Open the desktop app to check for updates.</p>}
 <p className="context-note">Updates install only when you choose. Profiles and worlds are kept.</p></section>
}
