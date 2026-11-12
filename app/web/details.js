'use strict';
const FootballDetails=(()=>{
 const safe=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const value=v=>v==null?'—':safe(v);
 const date=v=>v?new Date(v).toLocaleDateString('vi-VN'):'—';
 const formNames={W:['Thắng','win','T'],D:['Hòa','draw','H'],L:['Thua','loss','B']};
 function club(row,clubs,year){
  const local=(clubs||[]).find(c=>c.ten===row.name),recent=(row.recentMatches||[]).slice().reverse();
  return `<div class="club-detail"><div class="club-detail-head">${Presentation.crest(row.name)}<div><p class="eyebrow">PREMIER LEAGUE</p><h3>${safe(row.name)}</h3><span class="muted">${year}–${year+1}</span></div></div><div class="detail-kpis">${[[row.position,'Thứ hạng'],[row.points,'Điểm'],[row.playedGames,'Trận']].map(([n,label])=>`<div><strong>${value(n)}</strong><span>${label}</span></div>`).join('')}</div><dl class="club-facts"><div><dt>Thắng / Hòa / Thua</dt><dd>${value(row.won)} / ${value(row.draw)} / ${value(row.lost)}</dd></div><div><dt>Bàn thắng / Bàn thua</dt><dd>${value(row.goalsFor)} / ${value(row.goalsAgainst)}</dd></div>${local?.san_nha?`<div><dt>Sân nhà</dt><dd>${safe(local.san_nha)}</dd></div>`:''}</dl><div class="detail-section-title"><h3>5 trận gần nhất</h3></div>${recent.length?`<div class="club-recent">${recent.map(m=>{const form=formNames[m.outcome];return `<div><span class="result-pill ${form[1]}" title="${form[0]}">${form[2]}</span><time>${date(m.utcDate)}</time><span class="club-recent-teams">${safe(m.home)} <b>${value(m.homeGoals)} : ${value(m.awayGoals)}</b> ${safe(m.away)}</span></div>`;}).join('')}</div>`:'<p class="detail-no-data">Chưa có kết quả gần đây.</p>'}</div>`;
 }
 function localSide(data,match,name){
  const club=(data?.clubs||[]).find(c=>c.ten===name),id=club?.id??(name==='Manchester United'?data?.clubId:null);
  return (data?.appearances||[]).filter(a=>match.id!=null&&String(a.tran_dau_id)===String(match.id)&&id!=null&&String(a.cau_lac_bo_id)===String(id)).map(a=>({name:a.ho_ten,shirt:a.so_ao,minutes:a.so_phut,goals:a.ban_thang,assists:a.kien_tao,yellow:a.the_vang,red:a.the_do}));
 }
 function players(side,local){
  const rows=side?.playerProvider==='ESPN'&&side.players?.length?side.players:local.length?local:side?.players||[];
  if(!rows.length)return '<p class="detail-no-data">Chưa có thống kê cầu thủ cho trận này.</p>';
  const shots=rows.some(p=>p.shots!=null||p.shotsOnTarget!=null);
  return `<div class="table-scroll"><table class="match-players"><thead><tr><th>#</th><th>Cầu thủ</th><th>Phút</th><th>Bàn</th><th title="Kiến tạo">KT</th><th title="Thẻ vàng">TV</th><th title="Thẻ đỏ">TĐ</th>${shots?'<th>Sút</th><th title="Sút trúng đích">Trúng đích</th>':''}</tr></thead><tbody>${rows.map(p=>`<tr><td>${value(p.shirt)}</td><th scope="row">${safe(p.name)}</th><td>${value(p.minutes)}</td><td>${value(p.goals)}</td><td>${value(p.assists)}</td><td>${value(p.yellow)}</td><td>${value(p.red)}</td>${shots?`<td>${value(p.shots)}</td><td>${value(p.shotsOnTarget)}</td>`:''}</tr>`).join('')}</tbody></table></div>`;
 }
 function match(row,payload,data,tab='match'){
  const home=payload?.home,away=payload?.away;
  const homeName=home?.name||row.chu_nha,awayName=away?.name||row.khach;
  const score=payload?.score||{home:row.ban_thang_chu_nha,away:row.ban_thang_khach};
  const localHome=localSide(data,row,homeName),localAway=localSide(data,row,awayName);
  const fields={ball_possession:'Kiểm soát bóng (%)',shots:'Cú sút',shots_on_goal:'Sút trúng đích',shots_off_goal:'Sút ngoài khung',corner_kicks:'Phạt góc',fouls:'Phạm lỗi',offsides:'Việt vị',saves:'Cứu thua',yellow_cards:'Thẻ vàng',red_cards:'Thẻ đỏ'};
  const hs=home?.statistics||{},as=away?.statistics||{},keys=Object.keys(fields).filter(k=>hs[k]!=null||as[k]!=null);
  const compare=keys.length?`<dl class="match-comparison">${keys.map(k=>`<div><dd>${value(hs[k])}</dd><dt>${fields[k]}</dt><dd>${value(as[k])}</dd></div>`).join('')}</dl>`:'<p class="detail-no-data">Nguồn hiện tại chưa có chỉ số trận đấu.</p>';
  const facts=[[payload?.venue,'Sân'],[payload?.matchday,'Vòng'],[payload?.halfTime?.home!=null&&payload?.halfTime?.away!=null?payload.halfTime.home+' : '+payload.halfTime.away:null,'Hiệp 1'],[(payload?.referees||[]).join(', '),'Trọng tài']].filter(([v])=>v!=null&&v!=='');
  return `<div class="match-detail"><div class="detail-scoreboard"><div>${Presentation.crest(homeName)}<strong>${safe(homeName)}</strong></div><b>${value(score.home)} : ${value(score.away)}</b><div>${Presentation.crest(awayName)}<strong>${safe(awayName)}</strong></div></div><p class="detail-match-date">${payload?.utcDate?new Date(payload.utcDate).toLocaleString('vi-VN'):safe(row.thoi_gian_thi_dau)}</p><div class="detail-tabs" role="group" aria-label="Chi tiết trận"><button type="button" data-detail-tab="match" aria-pressed="${tab==='match'}">Thông số trận</button><button type="button" data-detail-tab="players" aria-pressed="${tab==='players'}">Cầu thủ</button></div>${payload?.running?'<p class="detail-status" role="status">Đang tải chi tiết…</p>':''}${payload?.error?`<p class="detail-status" role="status">${safe(payload.error)}</p>`:''}${tab==='players'?`<section class="detail-team-section"><h3>${safe(homeName)}</h3>${players(home,localHome)}</section><section class="detail-team-section"><h3>${safe(awayName)}</h3>${players(away,localAway)}</section>`:`${facts.length?`<dl class="club-facts">${facts.map(([v,label])=>`<div><dt>${label}</dt><dd>${safe(v)}</dd></div>`).join('')}</dl>`:''}${compare}`}</div>`;
 }
 return {club,match,localSide};
})();
