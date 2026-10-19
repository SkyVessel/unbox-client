import {useEffect,useState} from 'react';
import {Download,RefreshCw,LoaderCircle} from 'lucide-react';
import {call,native} from './bridge';
type Status={status:string;testingBuild?:boolean;currentVersion?:string;version?:string;notes?:string;error?:string;downloaded?:number;total?:number};
export function Updates({gameBusy}:{gameBusy:boolean}){
 const[view,setView]=useState<Status>({status:'idle'}),[busy,setBusy]=useState(false),[error,setError]=useState('');
 useEffect(()=>{let live=true;const refresh=()=>{if(native)void call<Status>('update_status').then(v=>{if(live&&v)setView(v)}).catch(()=>{})};refresh();const t=setInterval(refresh,700);return()=>{live=false;clearInterval(t)}},[]);
 const run=async(install=false)=>{if(busy)return;setBusy(true);setError('');try{if(install)await call('install_update');else setView(await call<Status>('check_update'))}catch(e){setError(String(e))}finally{setBusy(false)}};
 const installing=['downloading','installing','restarting'].includes(view.status),message=view.status==='unpublished'?'No releases published yet.':view.status==='current'?'You’re up to date.':view.status==='available'?`Version ${view.version} is available.`:view.status==='downloading'?'Downloading and verifying update…':view.status==='installing'?'Installing update…':view.status==='restarting'?'Restarting Unbox…':'Updates from GitHub Releases.';
 return <section className="updates" aria-label="Client updates"><div className="setting"><div><strong>Unbox Client {view.currentVersion||'0.2.15'}</strong><small role="status">{message}</small></div><button className="secondary" disabled={!native||busy||installing} onClick={()=>void run()}>{busy&&!installing?<LoaderCircle size={17} className="spin"/>:<RefreshCw size={17}/>}Check for updates</button></div>
 {view.testingBuild&&<p className="context-note">Testing build</p>}
 {view.status==='available'&&<div className="release-notes"><h3>What’s new</h3><p>{view.notes||'Maintenance updates and fixes.'}</p></div>}
 {view.status==='available'&&<button className="primary" disabled={busy||gameBusy} onClick={()=>void run(true)}><Download size={17}/>Install and restart</button>}
 {installing&&<progress aria-label="Update download progress" max={view.total||undefined} value={view.total?view.downloaded:undefined}/>}
 {gameBusy&&<p className="context-note">You can check while playing. Close Minecraft before installing.</p>}
 {(error||view.error)&&<p className="error" role="alert">{error||view.error}</p>}
 {!native&&<p className="context-note">Open the desktop app to check for updates.</p>}
 <p className="context-note">Updates install only when you choose. Profiles and worlds are kept.</p></section>
}

export function UpdateIndicator({onOpen}:{onOpen:()=>void}){
 const[available,setAvailable]=useState(false);
 useEffect(()=>{if(!native)return;let live=true;const check=()=>call<Status>('check_update').then(v=>{if(live)setAvailable(v.status==='available')}).catch(()=>{});void check();const refresh=setInterval(()=>void call<Status>('update_status').then(v=>{if(live)setAvailable(['available','downloading','installing','restarting'].includes(v.status))}).catch(()=>{}),2000);const periodic=setInterval(check,3600000);return()=>{live=false;clearInterval(refresh);clearInterval(periodic)}},[]);
 return available?<button className="nav-btn update-available" aria-label="Update available" title="Update available" onClick={onOpen}><Download size={22}/></button>:null;
}
