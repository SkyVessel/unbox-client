import React,{useState} from 'react';
import {ArrowLeft,ArrowRight,Eye,EyeOff,LoaderCircle,Mail} from 'lucide-react';
import {call,native} from './bridge';
import type {AccountSnapshot} from './AccountControls';
export function UnboxAccount({onComplete,onEditing}:{onComplete:(snapshot:AccountSnapshot)=>Promise<void>;onEditing:(editing:boolean)=>void}){
 const[mode,setMode]=useState<'login'|'register'|null>(null),[email,setEmail]=useState(''),[password,setPassword]=useState(''),[visible,setVisible]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('');
 const submit=async(e:React.FormEvent)=>{e.preventDefault();if(busy)return;setBusy(true);setError('');try{const snapshot=await call<AccountSnapshot>('auth_unbox_password',{email:email.trim(),password,mode});setPassword('');await onComplete(snapshot)}catch(e){setError(String(e))}finally{setBusy(false)}};
 if(!mode)return <div className="unbox-entry"><button className="secondary" disabled={!native} onClick={()=>{setMode('login');onEditing(true)}}><Mail size={17}/>Sign in with Unbox</button><button className="text-button" disabled={!native} onClick={()=>{setMode('register');onEditing(true)}}>Create an Unbox account</button></div>;
 return <form className="unbox-signin" onSubmit={submit} aria-label={mode==='register'?'Create Unbox account':'Unbox sign-in'}>
  <header><button type="button" className="icon-btn" disabled={busy} aria-label="Back to account options" onClick={()=>{setMode(null);onEditing(false);setPassword('');setVisible(false);setError('')}}><ArrowLeft size={18}/></button><strong>{mode==='register'?'Create your account':'Sign in to Unbox'}</strong></header>
  <label>Email<input autoFocus type="email" autoComplete="username" placeholder="you@example.com" required maxLength={254} disabled={busy} value={email} onChange={e=>setEmail(e.target.value)}/></label>
  <label>Password<span className="unbox-password"><input type={visible?'text':'password'} autoComplete={mode==='register'?'new-password':'current-password'} placeholder="At least 12 characters" required minLength={12} maxLength={128} disabled={busy} value={password} onChange={e=>setPassword(e.target.value)}/><button type="button" className="icon-btn" aria-label={visible?'Hide password':'Show password'} aria-pressed={visible} onClick={()=>setVisible(!visible)}>{visible?<EyeOff size={18}/>:<Eye size={18}/>}</button></span></label>
  <small>Email verification is off during development. Password recovery is unavailable.</small>
  {error&&<p className="error" role="alert">{error}</p>}
  <button className="primary" type="submit" disabled={busy}>{busy?<LoaderCircle size={17} className="spin"/>:<ArrowRight size={17}/>} {mode==='register'?'Create account':'Sign in'}</button>
  <button type="button" className="text-button" disabled={busy} onClick={()=>{setMode(mode==='register'?'login':'register');setPassword('');setVisible(false);setError('')}}>{mode==='register'?'Already registered? Sign in':'Create an account'}</button>
  {mode==='register'&&<small>A player name is created for you. Unbox accounts work in private Unbox worlds and do not include Minecraft ownership.</small>}
 </form>
}
