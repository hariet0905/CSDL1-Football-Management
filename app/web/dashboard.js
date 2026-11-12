'use strict';
const Presentation=(()=>{
 const safe=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const slug=v=>String(v).normalize('NFD').replace(/\p{M}/gu,'').replace(/ı/g,'i').toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'');
 const initials=name=>String(name).trim().split(/\s+/).map(s=>Array.from(s)[0]).filter(Boolean).slice(0,2).join('').toUpperCase();
 function portrait(player){
  const photo=(window.PLAYER_PHOTOS||{})[slug(player.ho_ten)+'|'+player.ngay_sinh];
  const image=photo&&/^\/photos\/[a-z0-9-]+\.webp$/.test(photo.src)?`<img src="${safe(photo.src)}" alt="" width="48" height="56" loading="lazy" decoding="async" data-portrait>`:'';
  return `<span class="player-portrait"><span aria-hidden="true">${safe(initials(player.ho_ten))}</span>${image}</span>`;
 }
 function kickoffTime(match){
  if(match.kickoffUtc)return Date.parse(match.kickoffUtc);
  const value=String(match.thoi_gian_thi_dau||'').replace(' ','T');
  return Date.parse(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(value)?value+'+07:00':value);
 }
 function countdownParts(kickoff,now=Date.now()){
  let seconds=Math.max(0,Math.floor((kickoff-now)/1000));
  const days=Math.floor(seconds/86400);seconds%=86400;
  const hours=Math.floor(seconds/3600);seconds%=3600;
  return {days,hours,minutes:Math.floor(seconds/60),seconds:seconds%60};
 }
 function clock(match,now){
  if(match.kickoffConfirmed===false)return '<p class="countdown-note">Chưa chốt giờ thi đấu.</p>';
  const target=kickoffTime(match),parts=countdownParts(target,now);
  return `<div class="countdown" role="timer" aria-label="Thời gian đến trận tiếp theo" aria-live="off" data-countdown data-kickoff="${target}">${Object.entries(parts).map(([unit,n],i)=>`<div><strong data-unit="${unit}">${String(n).padStart(2,'0')}</strong><span>${['NGÀY','GIỜ','PHÚT','GIÂY'][i]}</span></div>`).join('')}</div><p class="countdown-note" data-countdown-note>Còn lại đến giờ bóng lăn</p>`;
 }
 function tick(){
  const timer=document.querySelector('[data-countdown]');
  if(!timer?.dataset?.kickoff)return;
  const target=Number(timer.dataset.kickoff),parts=countdownParts(target);
  for(const [unit,n] of Object.entries(parts)){
   const node=timer.querySelector(`[data-unit="${unit}"]`);if(node)node.textContent=String(n).padStart(2,'0');
  }
  const note=document.querySelector('[data-countdown-note]');
  if(note)note.textContent=target<=Date.now()?'Chờ cập nhật trạng thái trận đấu':'Còn lại đến giờ bóng lăn';
 }
 function crest(name){
  const src=(window.FOOTBALL_ASSETS?.clubs||{})[name];
  return src&&/^\/crests\/[a-z0-9-]+\.webp$/.test(src)?`<img src="${safe(src)}" alt="" width="42" height="42" loading="lazy">`:`<span class="team-monogram" aria-hidden="true">${safe(initials(name))}</span>`;
 }
 function nation(name){
  const flag=(window.FOOTBALL_ASSETS?.flags||{})[name];
  const image=flag&&/^\/flags\/[a-z0-9-]+\.webp$/.test(flag.src)?`<img src="${safe(flag.src)}" alt="" width="22" height="15" loading="lazy">`:'';
  return `<span class="nationality">${image}<span>${safe(flag?.name||name||'—')}</span></span>`;
 }
 function team(name){return `<div class="fixture-team">${crest(name)}<strong>${safe(name)}</strong></div>`;}
 function seasonName(code){return String(code||'2026-27').replace(/^(\d{4})-(\d{2})$/,'$1–20$2');}

 function fixture(match,next,now,pending=false){
  if(!match)return `<section class="fixture-card ${next?'next-fixture':''}"><p class="card-kicker">${next?'TRẬN TIẾP THEO':'TRẬN GẦN NHẤT'}</p><div class="fixture-empty"><strong>${next?'Chưa có lịch đấu tiếp theo':'Chưa có trận hoàn tất'}</strong><p>Lịch và kết quả sẽ xuất hiện khi nguồn cung cấp dữ liệu.</p></div></section>`;
  const time=kickoffTime(match),date=Number.isFinite(time)?new Date(time).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh',weekday:'short',day:'2-digit',month:'2-digit',year:'numeric',...(next&&match.kickoffConfirmed===false?{}:{hour:'2-digit',minute:'2-digit'})}):'Chưa có giờ thi đấu';
  const hg=match.ban_thang_chu_nha,ag=match.ban_thang_khach;
  const valid=hg!=null&&ag!=null,forUs=match.chu_nha==='Manchester United'?hg:ag,against=match.chu_nha==='Manchester United'?ag:hg;
  const result=!valid?'Đã kết thúc':forUs>against?'Chiến thắng':forUs<against?'Thất bại':'Hòa';
  return `<section class="fixture-card ${next?'next-fixture':''}"><div class="fixture-heading"><p class="card-kicker">${pending?'CHỜ TRẠNG THÁI NGUỒN':next?'TRẬN TIẾP THEO':'TRẬN GẦN NHẤT'}</p><span class="fixture-label">${next?'PREMIER LEAGUE':safe(result)}</span></div><p class="fixture-date">${safe(date)} · Giờ Việt Nam</p><div class="fixture-teams">${team(match.chu_nha)}<strong class="fixture-score ${next?'versus':''}">${next?'VS':safe(hg??'—')+' : '+safe(ag??'—')}</strong>${team(match.khach)}</div>${pending?'<p class="countdown-note" role="status">Chờ cập nhật trạng thái trận đấu</p>':next?clock(match,now):'<p class="fixture-footnote">PREMIER LEAGUE</p>'}</section>`;
 }
 function distribution(data,report){
  const fallback=[{ma_vi_tri:'GK',ten_hien_thi:'Thủ môn'},{ma_vi_tri:'DF',ten_hien_thi:'Hậu vệ'},{ma_vi_tri:'MF',ten_hien_thi:'Tiền vệ'},{ma_vi_tri:'FW',ten_hien_thi:'Tiền đạo'}];
  const positions=data.positions?.length?data.positions:fallback,total=report(1).length;
  const counts=new Map(report(14).map(row=>[row.ma_vi_tri,Number(row.so_cau_thu)]));
  return `<div class="panel-body squad-distribution"><p class="distribution-summary"><strong>${total}</strong> cầu thủ đăng ký <span>· ${safe(data.contextDate)}</span></p>${positions.map(p=>{const n=counts.get(p.ma_vi_tri)||0;return `<div class="squad-position"><div class="position-label"><strong>${safe(p.ten_hien_thi)}</strong><small>${safe(p.ma_vi_tri)}</small></div><progress max="${Math.max(1,total)}" value="${n}" aria-label="${safe(p.ten_hien_thi)}: ${n} cầu thủ"></progress><b>${n}</b></div>`;}).join('')}${!total?'<p class="distribution-empty">Chưa có đội hình.</p>':''}</div>`;
 }
 function overview({data,report,rows,now=Date.now(),icon,panel,badge,table}){
  const stats=report(3)[0]||{},squadReport=n=>({'1':'registrations','12':'eligible','14':'positions'}[n]&&data.squad?data.squad[{'1':'registrations','12':'eligible','14':'positions'}[n]]:report(n))||[],roster=squadReport(1),eligible=squadReport(12).length,season=seasonName(data.seasons?.find(s=>String(s.id)===String(data.contextSeason))?.ma_mua_giai),squadSeason=seasonName(data.squad?.sourceSeason||data.seasons?.[0]?.ma_mua_giai);
  const finished=rows.filter(m=>m.trang_thai==='COMPLETED'&&kickoffTime(m)<=now).sort((a,b)=>kickoffTime(b)-kickoffTime(a));
  const next=rows.filter(m=>m.trang_thai==='SCHEDULED'&&kickoffTime(m)>now).sort((a,b)=>kickoffTime(a)-kickoffTime(b))[0];
  // Keep elapsed confirmed fixtures separate from the next future match. A later
  // official result/live fixture supersedes older stale scheduled records.
  const latestKnown=Math.max(-Infinity,...rows.filter(m=>['COMPLETED','IN_PLAY','PAUSED','SUSPENDED'].includes(m.trang_thai)&&kickoffTime(m)<=now).map(kickoffTime));
  const pending=rows.filter(m=>m.trang_thai==='SCHEDULED'&&m.kickoffConfirmed===true&&kickoffTime(m)<=now&&kickoffTime(m)>latestKnown).sort((a,b)=>kickoffTime(b)-kickoffTime(a))[0];
  const alerts=[...report(8).map(p=>`<div class="notice">${badge('Đang nghỉ','red')}<strong>${safe(p.ho_ten)}</strong><p>${safe(p.mo_ta)} · Dự kiến ${safe(p.ngay_du_kien_tro_lai||'chưa rõ')}</p></div>`),...report(9).map(p=>`<div class="notice">${badge('Hợp đồng sắp hết','warn')}<strong>${safe(p.ho_ten)}</strong><p>Còn ${safe(p.so_ngay_con_lai)} ngày · Kết thúc ${safe(p.ngay_ket_thuc)}</p></div>`)].join('');
  return `<section class="hero overview-hero"><div class="hero-copy"><p class="eyebrow">${icon('trophy-outline')} PREMIER LEAGUE · ${safe(season)}</p><h2>Manchester United</h2></div><img class="hero-crest" src="/club-logo.png" alt="" aria-hidden="true"><div class="hero-summary"><div><strong>${stats.thang||0}</strong><span>THẮNG</span></div><div><strong>${stats.hoa||0}</strong><span>HÒA</span></div><div><strong>${stats.thua||0}</strong><span>THUA</span></div></div></section><div class="fixture-section-heading"><div><h2>Trận đấu</h2></div><a class="text-button" href="#matches">Toàn bộ lịch đấu ${icon('arrow-forward-outline')}</a></div><div class="fixture-grid">${fixture(finished[0],false,now)}${pending?fixture(pending,true,now,true):''}${fixture(next,true,now)}</div><div class="squad-season-tag">Đội hình <span>${safe(squadSeason)}</span></div><div class="metrics">${[[roster.length,'Cầu thủ đăng ký',squadSeason],[eligible,'Chưa ghi nhận nghỉ',squadSeason],[stats.so_tran||0,'Trận đã thi đấu',season],[stats.diem||0,'Điểm tích lũy',`${season} · ${stats.ban_thang||0} bàn ghi · ${stats.ban_thua||0} bàn thua`]].map(([n,label,note],i)=>`<div class="metric ${i===3?'accent':''}"><span class="metric-label">${label}${icon(['people-outline','shield-checkmark-outline','football-outline','trophy-outline'][i])}</span><strong class="metric-value">${n}</strong>${note?`<small>${note}</small>`:''}</div>`).join('')}</div><div class="grid"><div>${panel('Phân bố đội hình',distribution({...data,contextDate:data.squad?.referenceDate||data.contextDate},squadReport))}${panel('Ban huấn luyện',table(report(10),['ho_ten','vai_tro']))}</div><div>${panel('Cần theo dõi',alerts||'<div class="empty">Chưa có cảnh báo tại ngày này.</div>')}</div></div>`;
 }
 return {portrait,crest,nation,seasonName,kickoffTime,countdownParts,tick,distribution,overview};
})();
