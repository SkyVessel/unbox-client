import {useEffect,useRef,useState} from 'react';
import {ArrowLeft,Download,Check,LoaderCircle,ExternalLink} from 'lucide-react';
import {call} from './bridge';
import type {Profile} from './model';
import {ProjectDescription,projectLink} from './ProjectDescription';
import {ProjectCover,ProjectIcon,projectImage} from './ProjectMedia';
export type Hit={project_id:string;slug:string;title:string;author:string;description:string;icon_url?:string;featured_gallery?:string;downloads:number;categories:string[]};
type Project={slug:string;title:string;description:string;body:string;icon_url?:string;gallery:{url:string;title?:string;description?:string}[];license?:{name:string};source_url?:string;issues_url?:string;wiki_url?:string;game_versions?:string[];loaders?:string[]};
type Plan={token:string;profileId:string;entries:{title:string;version:string;releaseType?:string;kind:string;icon?:string;serverSide?:string;file:{filename:string;size:number}}[];provided:{title:string;managed:boolean}[];optional:number;total:number};
type Progress={stage:string;file?:string;downloaded?:number;total?:number};
const mb=(n:number)=>`${(n/1048576).toFixed(1)} MB`;
export function ProjectDetails({hit,profiles,initialId,kind,gameBusy,onBack,onBusy,onInstalled}:{hit:Hit;profiles:Profile[];initialId:string;kind:string;gameBusy:boolean;onBack:()=>void;onBusy:(busy:boolean)=>void;onInstalled:(id:string,kind:string)=>void}){
 const[id,setId]=useState(initialId),[project,setProject]=useState<Project|null>(null),[error,setError]=useState(''),[detailRetry,setDetailRetry]=useState(0);
 const[plan,setPlan]=useState<Plan|null>(null),[planBusy,setPlanBusy]=useState(false),[planError,setPlanError]=useState(''),[retry,setRetry]=useState(0),[installing,setInstalling]=useState(false),[installed,setInstalled]=useState(false),[progress,setProgress]=useState<Progress>({stage:'Preparing'}),[cancelling,setCancelling]=useState(false);
 const busyRef=useRef(false),heading=useRef<HTMLHeadingElement>(null),profile=profiles.find(p=>p.id===id);
 useEffect(()=>{heading.current?.focus()},[]);
 useEffect(()=>{let live=true;setError('');setProject(null);call<Project>('catalog_project',{project:hit.project_id}).then(v=>{if(live)setProject(v)}).catch(e=>{if(live)setError(String(e))});return()=>{live=false}},[hit.project_id,detailRetry]);
 useEffect(()=>{let live=true;setPlan(null);setPlanError('');setInstalled(false);setPlanBusy(false);
  if(!profile)return;
  if(profile.sharedHost){setPlanError('Friend profiles are read-only. Choose your own profile to install.');return}
  setPlanBusy(true);call<Plan>('catalog_plan',{id,kind,project:hit.project_id}).then(v=>{if(live)setPlan(v)}).catch(e=>{if(live)setPlanError(String(e))}).finally(()=>{if(live)setPlanBusy(false)});return()=>{live=false};
 },[id,kind,hit.project_id,retry,profile?.version,profile?.loader,profile?.sharedHost]);
 useEffect(()=>{if(!installing||!plan)return;let live=true;const refresh=()=>call<Progress>('catalog_status',{token:plan.token}).then(v=>{if(live)setProgress(v)}).catch(()=>{});void refresh();const timer=setInterval(refresh,400);return()=>{live=false;clearInterval(timer)}},[installing,plan]);
 const install=async()=>{if(!plan||busyRef.current||gameBusy||profile?.sharedHost)return;busyRef.current=true;setInstalling(true);setCancelling(false);setPlanError('');setProgress({stage:'Preparing'});onBusy(true);try{await call('catalog_install',{token:plan.token});setInstalled(true)}catch(e){setPlanError(String(e))}finally{busyRef.current=false;setInstalling(false);onBusy(false)}};
 const cancel=async()=>{if(!plan)return;setCancelling(true);try{await call('catalog_cancel',{token:plan.token})}catch(e){setPlanError(String(e));setCancelling(false)}};
 return <section className="market project-page page-content" aria-label={`${hit.title} details`}>
  <header className="market-header"><button className="icon-btn" aria-label="Back to results" disabled={installing} onClick={onBack}><ArrowLeft size={22}/></button><ProjectIcon src={project?.icon_url||hit.icon_url} title={hit.title}/><div><h1 ref={heading} tabIndex={-1}>{project?.title||hit.title}</h1><small>by {hit.author} · {Intl.NumberFormat('en',{notation:'compact'}).format(hit.downloads)} downloads</small></div><a className="market-source" href={`https://modrinth.com/project/${encodeURIComponent(hit.slug)}`} target="_blank" rel="noreferrer">Modrinth <ExternalLink size={14}/></a></header>
  <div className="project-layout"><article className="project-content"><p className="project-summary">{project?.description||hit.description}</p>
   {!project&&!error&&<p className="market-progress" role="status"><LoaderCircle size={18} className="spin"/>Loading description…</p>}
   {error&&<div role="alert"><p className="error">{error}</p><button className="secondary" onClick={()=>setDetailRetry(v=>v+1)}>Retry description</button></div>}
   {project&&<><ProjectDescription body={project.body||'No description has been provided by the author.'} slug={project.slug}/>{!!project.gallery?.length&&<section className="project-gallery" aria-label="Project screenshots"><h2>Screenshots</h2>{project.gallery.map(g=><figure key={g.url}><a href={projectImage(g.url)} target="_blank" rel="noreferrer"><ProjectCover src={g.url} icon={project.icon_url} title={g.title||project.title}/></a>{(g.title||g.description)&&<figcaption>{g.title}{g.description&&<p>{g.description}</p>}</figcaption>}</figure>)}</section>}</>}
  </article><aside className="project-install" aria-label="Installation"><h2>Install</h2><label>Install to<select aria-label="Install to profile" value={id} disabled={installing} onChange={e=>setId(e.target.value)}><option value="">Choose a profile</option>{profiles.map(p=><option key={p.id} value={p.id} disabled={!!p.sharedHost}>{p.name}{p.sharedHost?' · Read-only':''}</option>)}</select></label>
   {profile?<p className="context-note">{profile.version} · {profile.loader}</p>:<p className="context-note">Choose a profile to check compatible files. You can keep reading without one.{!profiles.some(p=>!p.sharedHost)&&' Create your own profile from Home to install.'}</p>}
   {planBusy&&<p className="market-progress" role="status"><LoaderCircle className="spin" size={17}/>Checking compatibility…</p>}
   {plan&&<><div className="market-install-list">{plan.entries.map((e,i)=><div key={e.file.filename}><ProjectIcon src={e.icon} title={e.title}/><div><strong>{e.title}</strong><small>{e.version}{e.releaseType&&e.releaseType!=='release'?` (${e.releaseType})`:''} · {i===0?'Selected project':'Required dependency'}</small></div></div>)}{plan.provided.map((p,i)=><div key={i}><Check size={17}/><div><strong>{p.title}</strong><small>{p.managed?'Included with Unbox':'Already installed'}</small></div></div>)}</div>
   {plan.optional>0&&<p className="context-note">{plan.optional} optional dependencies are not included.</p>}
   {plan.entries.some(e=>e.kind==='Mods'&&e.serverSide==='required')&&<p className="context-note">The host or server may also need these mods.</p>}
   {plan.entries.some(e=>e.kind==='Packs')&&<p className="context-note">Available in your shared resource pack library. Enable in Minecraft.</p>}
   {plan.entries.some(e=>e.kind==='Shaders')&&<p className="context-note">Enable your shader in Minecraft’s shader settings.</p>}
   {!installed&&<><p className="context-note">{plan.entries.length?`${plan.entries.length} files · ${mb(plan.total)}`:'Ready in this profile'}</p><button className="primary" disabled={!plan.entries.length||installing||gameBusy||!!profile?.sharedHost} onClick={()=>void install()}>{installing?<LoaderCircle size={17} className="spin"/>:<Download size={17}/>}Install</button></>}
   </>}
   {planError&&<div role="alert"><p className="error">{planError}</p>{!installing&&!profile?.sharedHost&&<button className="secondary" onClick={()=>setRetry(v=>v+1)}>Check again</button>}</div>}
   {gameBusy&&!installed&&<p className="context-note">Close Minecraft before installing. You can keep browsing.</p>}
   {installing&&<div className="market-progress" role="status"><span>{progress.stage}{progress.file?` · ${progress.file}`:''}</span><progress aria-label="Installation progress" max={progress.total||1} value={progress.total?progress.downloaded:undefined}/><button className="text-button" disabled={cancelling||progress.stage==='Installing'} onClick={()=>void cancel()}>{cancelling?'Cancelling…':'Cancel download'}</button></div>}
   {installed&&<><p className="market-success" role="status"><Check size={17}/>Installed</p><button className="primary" onClick={()=>onInstalled(id,kind)}>View profile</button></>}
   {project&&<div className="project-meta">{project.license?.name&&<p>License · {project.license.name}</p>}{!!project.loaders?.length&&<p>{project.loaders.join(' · ')}</p>}{project.game_versions?.length?<details><summary>Supported Minecraft versions</summary><p>{project.game_versions.join(', ')}</p></details>:null}{[['Source',project.source_url],['Issues',project.issues_url],['Wiki',project.wiki_url]].map(([name,url])=>projectLink(url)&&<a key={name} href={projectLink(url)} target="_blank" rel="noreferrer">{name} <ExternalLink size={12}/></a>)}</div>}
  </aside></div>
 </section>;
}
