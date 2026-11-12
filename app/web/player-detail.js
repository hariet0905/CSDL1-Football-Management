'use strict';
const PlayerDetails=(()=>{
 const safe=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const number=v=>v==null?'—':Number(v).toLocaleString('vi-VN',{maximumFractionDigits:1});
 const date=v=>v?String(v).split('-').reverse().join('/'):'—';
 function statsFor(data,id){
  const selected=(data.playerStats||[]).find(p=>String(p.id)===String(id));
  if(selected)return {value:selected,cohort:data.playerStats,season:data.seasons?.[0]?.ma_mua_giai,appearances:data.appearances||[],matches:(data.reports||{})['2']||[]};
  const old=(data.squad?.stats||[]).find(p=>String(p.id)===String(id));
  return {value:old,cohort:data.squad?.stats||[],season:old?(data.squad.statsSourceSeason||data.squad.sourceSeason):data.seasons?.[0]?.ma_mua_giai,appearances:old?data.squad.appearances||[]:[],matches:old?data.squad.matches||[]:[]};
 }
 function radar(stats,cohort){
  const axes=[['so_tran','Ra sân'],['so_phut','Số phút'],['ban_thang','Bàn thắng'],['du_90_phut','Đủ 90 phút'],['phut_moi_tran','Phút / trận']];
  const point=(i,r)=>{const angle=(-90+i*360/axes.length)*Math.PI/180;return [200+Math.cos(angle)*r,160+Math.sin(angle)*r];};
  const points=r=>axes.map((_,i)=>point(i,r).map(n=>n.toFixed(2)).join(',')).join(' ');
  let svg='<svg viewBox="0 0 400 320" role="img" aria-label="Biểu đồ thống kê thi đấu" class="player-radar"><title>Thống kê thi đấu</title>';
  for(const scale of [.25,.5,.75,1])svg+=`<polygon points="${points(96*scale)}" class="radar-ring"/>`;
  axes.forEach(([key,label],i)=>{const [x,y]=point(i,96),[lx,ly]=point(i,125);svg+=`<line x1="200" y1="160" x2="${x}" y2="${y}" class="radar-spoke"/><text x="${lx}" y="${ly+4}" text-anchor="${lx>230?'start':lx<170?'end':'middle'}">${safe(label)}</text>`;});
  if(stats&&axes.every(([key])=>stats[key]!=null)){
   const values=axes.map(([key],i)=>{const max=Math.max(1,...cohort.map(p=>Number(p[key])||0));return point(i,Math.max(0,Math.min(1,Number(stats[key])/max))*96);});
   svg+=`<polygon points="${values.map(p=>p.map(n=>n.toFixed(2)).join(',')).join(' ')}" class="radar-value"/>`;
   values.forEach(([x,y])=>svg+=`<circle cx="${x}" cy="${y}" r="3.5" class="radar-dot"/>`);
  }
  return svg+'</svg>';
 }
 function age(birth){
  if(!birth)return '—';const now=new Date(),then=new Date(birth+'T00:00:00');
  return now.getFullYear()-then.getFullYear()-(now.getMonth()<then.getMonth()||(now.getMonth()===then.getMonth()&&now.getDate()<then.getDate())?1:0);
 }
 function render(data,player,registration){
  const source=statsFor(data,player.id),stats=source.value,season=Presentation.seasonName(source.season);
  const cards=[['so_tran','Ra sân'],['so_phut','Phút thi đấu'],['ban_thang','Bàn thắng'],['kien_tao','Kiến tạo'],['the_vang','Thẻ vàng'],['the_do','Thẻ đỏ']];
  const appearances=source.appearances.filter(a=>String(a.cau_thu_id)===String(player.id));
  const recent=appearances.map(a=>({...a,match:source.matches.find(m=>String(m.id)===String(a.tran_dau_id))})).filter(a=>a.match).sort((a,b)=>Presentation.kickoffTime(b.match)-Presentation.kickoffTime(a.match)).slice(0,5);
  const history=recent.length?`<div class="table-scroll"><table><thead><tr><th>Ngày</th><th>Trận đấu</th><th>Phút</th><th>Bàn thắng</th></tr></thead><tbody>${recent.map(a=>`<tr><td>${date(a.match.thoi_gian_thi_dau.slice(0,10))}</td><td><span class="detail-match">${Presentation.crest(a.match.chu_nha)}<span>${safe(a.match.chu_nha)} <b>${safe(a.match.ban_thang_chu_nha??'—')} : ${safe(a.match.ban_thang_khach??'—')}</b> ${safe(a.match.khach)}</span>${Presentation.crest(a.match.khach)}</span></td><td>${number(a.so_phut)}</td><td>${number(a.ban_thang)}</td></tr>`).join('')}</tbody></table></div>`:'<p class="detail-no-data">Chưa có trận đấu.</p>';
  return `<div class="player-profile"><div class="profile-top"><div class="profile-portrait">${Presentation.portrait(player)}</div><div class="profile-bio"><p class="profile-position">${safe(player.ten_hien_thi||player.ma_vi_tri)}</p><h3>${safe(player.ho_ten)}</h3>${Presentation.nation(player.quoc_tich)}<div class="profile-facts"><div><span>Ngày sinh</span><strong>${date(player.ngay_sinh)}</strong></div><div><span>Tuổi</span><strong>${age(player.ngay_sinh)}</strong></div><div><span>Vị trí</span><strong>${safe(player.ma_vi_tri)}</strong></div></div></div><div class="profile-shirt"><span>SỐ ÁO</span><strong>${safe(registration?.so_ao??'—')}</strong><small>${safe(Presentation.seasonName(data.squad?.sourceSeason||data.seasons?.[0]?.ma_mua_giai))}</small></div></div><div class="detail-section-title"><h3>Thống kê mùa giải</h3><span class="badge neutral">${safe(season)}</span></div><div class="player-stat-grid">${cards.map(([key,label])=>`<div class="player-stat"><strong>${number(stats?.[key])}</strong><span>${label}</span></div>`).join('')}</div><section class="radar-panel"><div><h3>Hoạt động thi đấu</h3><p>${stats?'So sánh trong đội':'Chưa có thống kê'}</p><span class="badge neutral">${safe(season)}</span></div>${radar(stats,source.cohort)}</section><div class="detail-section-title"><h3>Trận gần đây</h3><span>${safe(season)}</span></div>${history}</div>`;
 }
 return {render,radar,statsFor};
})();
