'use strict';
const Motion=(()=>{
 const active=new WeakMap(),dialogs=new WeakMap();
 const reduced=()=>typeof matchMedia==='function'&&matchMedia('(prefers-reduced-motion: reduce)').matches;
 function play(node,frames,options={}){
  if(!node?.animate||reduced())return Promise.resolve();
  active.get(node)?.cancel();
  const animation=node.animate(frames,{duration:280,easing:'cubic-bezier(.2,.75,.2,1)',...options});active.set(node,animation);
  return animation.finished.catch(()=>{}).finally(()=>{if(active.get(node)===animation)active.delete(node);});
 }
 function route(node,direction=1){
  const forward=direction>=0;
  return play(node,[{clipPath:forward?'inset(0 0 0 100%)':'inset(0 100% 0 0)',transform:`translateX(${forward?24:-24}px)`},{clipPath:'inset(0 0 0 0)',transform:'translateX(0)'}],{duration:340});
 }
 function capture(node,attribute='data-player-id'){
  const snapshot=new Map();snapshot.height=node?.getBoundingClientRect?.().height;
  node?.querySelectorAll?.(`[${attribute}]`).forEach(row=>snapshot.set(row.getAttribute(attribute),row.getBoundingClientRect()));
  return snapshot;
 }
 function filter(node,before=new Map()){
  if(reduced())return Promise.resolve();
  const jobs=[],height=node?.getBoundingClientRect?.().height;
  if(before.height&&height&&before.height!==height)jobs.push(play(node,[{height:before.height+'px',overflow:'clip'},{height:height+'px',overflow:'clip'}],{duration:300}));
  node?.querySelectorAll?.('[data-player-id]').forEach((row,index)=>{
   const previous=before.get(row.dataset.playerId),next=row.getBoundingClientRect();
   if(previous){const dy=previous.top-next.top;if(Math.abs(dy)>1)jobs.push(play(row,[{transform:`translateY(${dy}px)`},{transform:'translateY(0)'}],{duration:300}));}
   else jobs.push(play(row,[{clipPath:'inset(0 0 100% 0)'},{clipPath:'inset(0 0 0 0)'}],{duration:230,delay:Math.min(index,7)*24,fill:'backwards'}));
  });
  return Promise.all(jobs);
 }
 function sourceTransform(node,source,box=null){
  if(!source?.isConnected||!source.getBoundingClientRect||!node.getBoundingClientRect)return null;
  const from=source.getBoundingClientRect(),to=box||node.getBoundingClientRect();
  if(!from.width||!from.height||!to.width||!to.height)return null;
  const dx=from.left+from.width/2-to.left-to.width/2,dy=from.top+from.height/2-to.top-to.height/2;
  return `translate(${dx}px,${dy}px) scale(${Math.min(1,from.width/to.width)},${Math.min(.3,from.height/to.height)})`;
 }
 function open(node,profile,source=null){
  dialogs.set(node,{profile,source,box:node?.getBoundingClientRect?.()});
  const from=profile?sourceTransform(node,source):null;
  if(profile)return play(node,[{transform:from||'scale(.88,.35)',borderRadius:'10px',clipPath:'inset(0 round 10px)'},{transform:'translate(0,0) scale(1,1)',borderRadius:'18px',clipPath:'inset(0 round 18px)'}],{duration:420,easing:'cubic-bezier(.18,.8,.2,1)'});
  return play(node,[{transform:'scale(.88)',offset:0},{transform:'scale(1.015)',offset:.75},{transform:'scale(1)',offset:1}],{duration:300});
 }
 async function close(node){
  if(!node?.open)return;
  const state=dialogs.get(node),target=state?.profile?sourceTransform(node,state.source,active.has(node)?state.box:null):null;
  const current=typeof getComputedStyle==='function'?getComputedStyle(node).transform:'none';
  await play(node,[{transform:current},{transform:target||(state?.profile?'scale(.88,.35)':'scale(.9)')}],{duration:state?.profile?240:160,easing:'cubic-bezier(.4,0,.8,.3)'});
  node.close();dialogs.delete(node);
 }
 function feedback(node,error=false){
  return play(node,error?[{transform:'translateX(0)'},{transform:'translateX(-7px)'},{transform:'translateX(6px)'},{transform:'translateX(-3px)'},{transform:'translateX(0)'}]:[{clipPath:'inset(0 0 0 100%)',transform:'translateX(24px)'},{clipPath:'inset(0 0 0 0)',transform:'translateX(0)'}],{duration:error?320:300});
 }
 function radar(node){
  const polygon=node?.querySelector?.('.radar-value');if(!polygon||reduced())return Promise.resolve();
  const length=polygon.getTotalLength?.();if(!length)return Promise.resolve();
  const dash=`${length} ${length}`;
  const jobs=[play(polygon,[{strokeDasharray:dash,strokeDashoffset:length,fillOpacity:0,offset:0},{strokeDasharray:dash,strokeDashoffset:0,fillOpacity:0,offset:.8},{strokeDasharray:dash,strokeDashoffset:0,fillOpacity:1,offset:1}],{duration:760,delay:240,fill:'backwards',easing:'ease-in-out'})];
  node.querySelectorAll('.radar-dot').forEach((dot,index)=>{
   const origin=`${dot.getAttribute('cx')}px ${dot.getAttribute('cy')}px`;
   jobs.push(play(dot,[{transform:'scale(0)',transformOrigin:origin},{transform:'scale(1)',transformOrigin:origin}],{duration:180,delay:300+index*90,fill:'backwards'}));
  });
  return Promise.all(jobs);
 }
 function rows(node,ids,before=new Map()){
  const jobs=[];
  node?.querySelectorAll?.('[data-team-id]').forEach(row=>{
   const previous=before.get(row.dataset.teamId),next=row.getBoundingClientRect(),dy=previous?previous.top-next.top:0;
   if(Math.abs(dy)>1)jobs.push(play(row,[{transform:`translateY(${dy}px)`},{transform:'translateY(0)'}],{duration:520}));
   if(ids.has(row.dataset.teamId))jobs.push(play(row.querySelector('.league-points'),[{transform:'scale(1)',color:'#d71920'},{transform:'scale(1.18)',color:'#d71920',offset:.35},{transform:'scale(1)',color:'inherit'}],{duration:480}));
  });
  return Promise.all(jobs);
 }
 return {route,capture,filter,open,close,feedback,radar,rows};
})();
