import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
export function projectLink(value?:string|null){try{const url=new URL(value||'');return ['https:','http:'].includes(url.protocol)&&!url.username&&!url.password?url.href:undefined}catch{return undefined}}
export function ProjectDescription({body,slug}:{body:string;slug:string}){
 return <div className="project-description"><Markdown remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeRaw,rehypeSanitize]} urlTransform={(url,key)=>{
  try{const resolved=new URL(url,`https://modrinth.com/mod/${encodeURIComponent(slug)}`).href;return key==='src'?(resolved.startsWith('https:')?projectLink(resolved)||'':''):projectLink(resolved)||''}catch{return ''}
 }} components={{a:({href,children})=><a href={href||undefined} target="_blank" rel="noopener noreferrer">{children}</a>,img:({src,alt,width,height})=>src?<img src={src} alt={alt||''} width={width} height={height} loading="lazy" referrerPolicy="no-referrer"/>:null}}>{body}</Markdown></div>;
}
