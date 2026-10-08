import React,{useEffect,useRef,useState} from 'react';
import type {Account} from './model';
export function SkinFace({skin}:{skin?:string}){
 const ref=useRef<HTMLCanvasElement>(null);
 useEffect(()=>{if(!skin||!ref.current)return;const c=ref.current,g=c.getContext('2d')!,image=new Image();let canceled=false;image.onload=()=>{if(canceled)return;g.clearRect(0,0,8,8);g.imageSmoothingEnabled=false;const s=image.width/64;g.drawImage(image,8*s,8*s,8*s,8*s,0,0,8,8);g.drawImage(image,40*s,8*s,8*s,8*s,0,0,8,8)};image.src=skin;return()=>{canceled=true}},[skin]);
 return <canvas className="skin-face" ref={ref} width={8} height={8} aria-label="Player skin face"/>;
}
export function SkinPreview({account}:{account:Account}){
 const canvas=useRef<HTMLCanvasElement>(null),container=useRef<HTMLDivElement>(null);const [error,setError]=useState('');
 useEffect(()=>{setError('');if(!account.skin)return;let disposed=false,cleanup=()=>{};
 import('skinview3d').then(async({SkinViewer})=>{if(disposed||!canvas.current||!container.current)return;const v=new SkinViewer({canvas:canvas.current,width:220,height:280,renderPaused:true});v.pixelRatio=Math.min(window.devicePixelRatio,2);v.zoom=.86;v.controls.enableZoom=false;v.controls.enablePan=false;v.controls.enableDamping=false;v.playerObject.rotation.y=-.3;
 const redraw=()=>v.render();v.controls.addEventListener('change',redraw);const observer=new ResizeObserver(entries=>{const {width,height}=entries[0].contentRect;if(width>0&&height>0){v.setSize(width,height);v.render()}});observer.observe(container.current);cleanup=()=>{observer.disconnect();v.dispose()};
 try{await v.loadSkin(account.skin!,{model:account.skinModel==='slim'?'slim':'default'});if(!disposed)v.render()}catch{if(!disposed)setError('Skin could not be displayed')}
 }).catch(()=>{if(!disposed)setError('Skin renderer unavailable')});return()=>{disposed=true;cleanup()}},[account.skin,account.skinModel]);
 return <div className="live-skin" ref={container}>{account.skin&&!error?<canvas ref={canvas} aria-label={`${account.name}'s Minecraft skin. Drag to rotate.`}/>:<span>{error||'Skin unavailable'}</span>}</div>
}
