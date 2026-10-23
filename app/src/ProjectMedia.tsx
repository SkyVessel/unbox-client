import {useState} from 'react';
export const projectImage=(url?:string|null)=>{try{const u=new URL(url||'');return u.protocol==='https:'&&u.hostname==='cdn.modrinth.com'&&!u.username&&!u.password?url||undefined:undefined}catch{return undefined}};
function Image({src,title,fallback}:{src?:string;title:string;fallback?:React.ReactNode}){
 const[failed,setFailed]=useState(false);
 return src&&!failed?<img src={src} alt="" loading="lazy" onError={()=>setFailed(true)}/>:<>{fallback||<span className="market-initial" aria-label={`${title} image unavailable`}>{title.slice(0,1).toUpperCase()}</span>}</>;
}
export function ProjectIcon({src,title}:{src?:string|null;title:string}){return <div className="market-icon"><Image key={src} src={projectImage(src)} title={title}/></div>}
export function ProjectCover({src,icon,title}:{src?:string|null;icon?:string|null;title:string}){
 return <div className="market-cover"><Image key={src} src={projectImage(src)} title={title} fallback={<div className="market-cover-fallback"><Image key={icon} src={projectImage(icon)} title={title}/></div>}/></div>;
}
