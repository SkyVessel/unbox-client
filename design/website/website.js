const icons={map:'<path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3Z"/><path d="M9 3v15M15 6v15"/>',monitor:'<rect x="2" y="3" width="20" height="14" rx="2"/><path d="M12 17v4M8 21h8"/>',crosshair:'<circle cx="12" cy="12" r="8"/><path d="M12 2v5M12 17v5M2 12h5M17 12h5"/>',shirt:'<path d="m7 3-5 3 3 5 2-1v11h10V10l2 1 3-5-5-3c0 4-10 4-10 0Z"/>',mouse:'<rect x="5" y="2" width="14" height="20" rx="7"/><path d="M12 2v7"/>',keyboard:'<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M6 9h.01M10 9h.01M14 9h.01M18 9h.01M6 12h.01M10 12h.01M14 12h.01M18 12h.01M7 15h10"/>',pickaxe:'<path d="m3 21 12-12m-3-6 9 9M8 3c7-2 12 3 13 10M3 8c1-3 3-5 5-5"/>',sliders:'<path d="M4 3v18M12 3v18M20 3v18M1 8h6M9 16h6M17 7h6"/>'};
icons.hand='<path d="M18 11V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2"/><path d="M14 10V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v2"/><path d="M10 10.5V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2v8"/><path d="M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15"/>';
document.querySelectorAll('[data-icon]').forEach(el=>el.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true">'+icons[el.dataset.icon]+'</svg>');
document.querySelector('#theme').onclick=()=>{const theme=document.documentElement.dataset.theme==='dark'?'light':'dark';document.documentElement.dataset.theme=theme;history.replaceState(null,'','?theme='+theme);};
async function contours(){
 const source=await fetch('contours.glsl').then(r=>r.text());
 document.querySelectorAll('.contours').forEach(canvas=>{
  const gl=canvas.getContext('webgl',{alpha:true,premultipliedAlpha:false});if(!gl)return;
  const make=(kind,src)=>{const s=gl.createShader(kind);gl.shaderSource(s,src);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))return null;return s;};
  const v=make(gl.VERTEX_SHADER,'attribute vec2 p; void main(){gl_Position=vec4(p,0.,1.);}'),f=make(gl.FRAGMENT_SHADER,source);if(!v||!f)return;
  const p=gl.createProgram();gl.attachShader(p,v);gl.attachShader(p,f);gl.linkProgram(p);gl.useProgram(p);
  const b=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,b);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,-1,1,1,-1,1,1]),gl.STATIC_DRAW);const a=gl.getAttribLocation(p,'p');gl.enableVertexAttribArray(a);gl.vertexAttribPointer(a,2,gl.FLOAT,false,0,0);
  const reduced=matchMedia('(prefers-reduced-motion: reduce)');let visible=true,last=0;
  new IntersectionObserver(entries=>{visible=entries[0].isIntersecting;}).observe(canvas);
  function draw(time){requestAnimationFrame(draw);if(document.hidden||!visible||time-last<80)return;last=time;const w=Math.round(canvas.clientWidth),h=Math.round(canvas.clientHeight);if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}gl.viewport(0,0,w,h);gl.uniform2f(gl.getUniformLocation(p,'u_resolution'),w,h);gl.uniform1f(gl.getUniformLocation(p,'u_time'),reduced.matches?0:time/1000);gl.uniform1f(gl.getUniformLocation(p,'u_speed'),.018);const light=document.documentElement.dataset.theme==='light';gl.uniform3f(gl.getUniformLocation(p,'u_color'),...(light?[.29,.43,.67]:[.62,.75,1.]));gl.drawArrays(gl.TRIANGLES,0,6);}
  requestAnimationFrame(draw);
 });
}if(!document.documentElement.classList.contains('pen-canvas'))contours().catch(()=>{});
// Slow scene changes; pause on request, offscreen, hidden, or reduced motion.
const sceneImages=[...document.querySelectorAll('.hero-scenery img')];
const scenePause=document.querySelector('#scenery-pause');
if(sceneImages.length&&scenePause){
 const reduced=matchMedia('(prefers-reduced-motion: reduce)');let index=0,paused=false,inView=true,timer;
 const update=()=>{clearInterval(timer);const stopped=paused||reduced.matches;scenePause.setAttribute('aria-pressed',String(stopped));scenePause.textContent=stopped?'Background paused':'Pause background';scenePause.setAttribute('aria-label',stopped?'Resume background slideshow':'Pause background slideshow');scenePause.disabled=reduced.matches;
  if(stopped||document.hidden||!inView||document.documentElement.classList.contains('pen-canvas'))return;
  timer=setInterval(()=>{sceneImages[index].classList.remove('active');index=(index+1)%sceneImages.length;sceneImages[index].classList.add('active');},12000);
 };
 scenePause.onclick=()=>{paused=!paused;update();};
 document.addEventListener('visibilitychange',update);reduced.addEventListener('change',update);
 new IntersectionObserver(entries=>{inView=entries[0].isIntersecting;update();}).observe(document.querySelector('.hero'));update();
}
// Reveal each group once. Content stays visible without JavaScript or in Pen.
function setupReveals(){
 if(document.documentElement.classList.contains('pen-canvas'))return;
 const reduced=matchMedia('(prefers-reduced-motion: reduce)');
 if(reduced.matches)return;
 const groups=[
  [...document.querySelectorAll('.hero-copy > *')],
  ...[...document.querySelectorAll('.section-copy')].map(el=>[...el.children]),
  [...document.querySelectorAll('.world-visual,.mods-visual')],
  [...document.querySelectorAll('.section-heading > *,.discover-visual')],
  [...document.querySelectorAll('.discover-notes > div')],
  [...document.querySelectorAll('.extras > article')],
  [...document.querySelectorAll('.download > div:first-of-type > *,.download-card')]
 ];
 const observer=new IntersectionObserver(entries=>{
  for(const entry of entries)if(entry.isIntersecting){entry.target.classList.remove('reveal-pending');observer.unobserve(entry.target);}
 },{threshold:.08,rootMargin:'0px 0px -24px 0px'});
 const elements=[];
 for(const group of groups)group.forEach((el,index)=>{
  el.classList.add('reveal-item','reveal-pending');
  if(el.matches('.world-visual,.mods-visual,.discover-visual,.download-card,.extras > article'))el.classList.add('reveal-image');
  el.style.setProperty('--reveal-delay',Math.min(index*70,210)+'ms');
  elements.push(el);observer.observe(el);
 });
 const finish=()=>{observer.disconnect();elements.forEach(el=>el.classList.remove('reveal-pending'));};
 reduced.addEventListener('change',event=>{if(event.matches)finish();});
 document.addEventListener('focusin',event=>{const target=event.target.closest('.reveal-pending');if(target){target.classList.remove('reveal-pending');observer.unobserve(target);}});
}
setupReveals();
