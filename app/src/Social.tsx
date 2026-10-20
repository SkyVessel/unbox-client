import React,{createContext,useContext,useEffect,useRef,useState} from 'react';
import {Box} from 'lucide-react';
import type {PublicLogo} from './AccountLogo';
import {call,native} from './bridge';
export type Friend={logo?:PublicLogo|null;id:string;name:string;code:string;face?:string;online:boolean;state:string;accepted:number;direction:string;kind:string};
export type Invite={id:string;receiver:string;senderName:string;sender?:string;expires:number;room:{name:string}};
export type SocialView={seenInvites?:string[];connected:boolean;assets?:{items:{id:string;kind:string;name:string;resource:string|null}[];equipment:{slot:string;item:string}[]};self?:{id:string;code:string};friends:Friend[];invites:Invite[];error?:string;canInvite?:boolean;gameError?:string;joining?:boolean};
const empty:SocialView={connected:false,friends:[],invites:[]};
const Context=createContext<{view:SocialView;action:(op:string,id:string)=>Promise<SocialView>}>({view:empty,action:async()=>empty});
export const useSocial=()=>useContext(Context);
export function SocialProvider({children,accountKey}:{children:React.ReactNode;accountKey:string}){
 const [view,setView]=useState(empty),generation=useRef(0);
 useEffect(()=>{let live=true,inFlight=false;generation.current++;setView(empty);const refresh=async()=>{if(!native||inFlight)return;inFlight=true;try{const next=await call<SocialView>('social_status');if(live&&next)setView(next)}catch{/* Cached rows survive connection errors. */}finally{inFlight=false}};void refresh();const timer=setInterval(refresh,1000);return()=>{live=false;clearInterval(timer)}},[accountKey]);
 const action=async(op:string,id:string)=>{const g=generation.current;const next=await call<SocialView>('social_action',{op,id});if(g===generation.current)setView(next);return next};
 return <Context.Provider value={{view,action}}>{children}</Context.Provider>;
}
export function InvitationNotifications(){
 const {view,action}=useSocial();const [now,setNow]=useState(Date.now()),arrivals=useRef(new Map<string,number>());
 useEffect(()=>{const t=setInterval(()=>setNow(Date.now()),1000);return()=>clearInterval(t)},[]);
 const [hidden,setHidden]=useState<Set<string>>(new Set()),[busy,setBusy]=useState(''),[error,setError]=useState('');
 useEffect(()=>{setHidden(new Set());arrivals.current.clear();setError('');setBusy('')},[view.self?.id]);
 const rows=[...(view.friends||[]).filter(f=>!f.accepted&&f.direction==='incoming').map(f=>({key:'friend:'+f.id,id:f.id,name:f.name,face:f.face,detail:'Friend request',world:false})),...(view.invites||[]).filter(i=>i.receiver===view.self?.id&&i.expires>now).map(i=>({key:'world:'+i.id,id:i.id,name:i.senderName,face:view.friends.find(f=>f.id===i.sender)?.face,detail:i.room.name,world:true}))];
 for(const n of rows)if(!arrivals.current.has(n.key))arrivals.current.set(n.key,now);
 const liveKeys=new Set(rows.map(n=>n.key));for(const key of arrivals.current.keys())if(!liveKeys.has(key))arrivals.current.delete(key);
 const visible=rows.filter(n=>!hidden.has(n.key)&&(!n.world||!view.seenInvites?.includes(n.id)&&now-arrivals.current.get(n.key)!<15000));
 const hide=(key:string)=>setHidden(s=>new Set([...s,key]));
 const respond=async(n:typeof rows[number],accept:boolean)=>{setBusy(n.key);setError('');try{await action(n.world?(accept?'join':'hide'):(accept?'accept':'remove'),n.id);hide(n.key)}catch(e){setError(String(e))}finally{setBusy('')}};
 return <section className="invitation-stack" aria-label="Invitations" aria-live="polite">{visible.slice(0,2).map(n=><article className="invitation-popup" key={n.key} aria-label={n.world?'World invitation':'Friend request'}><span className="invitation-face">{n.face?<img src={n.face} alt=""/>:<Box size={26}/>}</span><div className="invitation-copy"><strong>{n.name}</strong><span>{n.detail}</span></div><div className="invitation-actions"><button className="primary" disabled={!!busy||!view.connected||(n.world&&view.joining)} onClick={()=>void respond(n,true)}>{busy===n.key?'…':n.world?'Join':'Accept'}</button><button className="secondary" disabled={!!busy||!view.connected} onClick={()=>void respond(n,false)}>{n.world?'Hide':'Decline'}</button></div>{error&&<p className="error" role="alert">{error}</p>}</article>)}</section>
}
