'use strict';
const $ = s => document.querySelector(s);
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const pages = {overview:['Tổng quan','Tổng quan câu lạc bộ'],players:['Cầu thủ','Đội hình & hồ sơ'],matches:['Trận đấu','Lịch đấu & kết quả'],standings:['Bảng xếp hạng','Bảng xếp hạng'],contracts:['Hợp đồng','Hợp đồng cầu thủ'],medical:['Y tế','Chấn thương & hồi phục'],transfers:['Chuyển nhượng','Chuyển nhượng'],reports:['Báo cáo','Báo cáo câu lạc bộ']};
const reportNames=['Đội hình đăng ký','Lịch thi đấu','Thành tích CLB','Bàn thắng','Top 10 số phút','Ra sân & thẻ phạt','Chưa ra sân','Đang chấn thương','Hợp đồng sắp hết hạn','Ban huấn luyện','Lịch sử hợp đồng','Đăng ký, chưa ghi nhận nghỉ','Lịch sử đối đầu','Phân bố vị trí','Chuyển nhượng'];
const labels={id:'ID',cau_thu_id:'Mã cầu thủ',ho_ten:'Họ tên',ten_hien_thi:'Vị trí',so_ao:'Số áo',ngay_bat_dau:'Bắt đầu',ngay_ket_thuc:'Kết thúc',chu_nha:'Chủ nhà',khach:'Đội khách',thoi_gian_thi_dau:'Giờ thi đấu',trang_thai:'Trạng thái',ban_thang_chu_nha:'Bàn chủ nhà',ban_thang_khach:'Bàn khách',so_tran:'Số trận',thang:'Thắng',hoa:'Hòa',thua:'Thua',ban_thang:'Bàn thắng',ban_thua:'Bàn thua',hieu_so:'Hiệu số',diem:'Điểm',so_phut:'Số phút',kien_tao:'Kiến tạo',phut_api:'Phút',the_vang:'Thẻ vàng',the_do:'Thẻ đỏ',mo_ta:'Chấn thương',ngay_bat_dau_nghi:'Bắt đầu nghỉ',ngay_du_kien_tro_lai:'Dự kiến trở lại',ngay_thuc_te_tro_lai:'Trở lại thực tế',so_ngay_con_lai:'Ngày còn lại',vai_tro:'Vai trò',ghi_chu:'Ghi chú',ten:'Đối thủ',so_lan:'Số lần',ma_vi_tri:'Vị trí',so_cau_thu:'Số cầu thủ',clb_di:'CLB đi',clb_den:'CLB đến',ngay_hieu_luc:'Ngày hiệu lực',phi_chuyen_nhuong:'Phí (GBP)'};
const statusNames={COMPLETED:'Đã thi đấu',SCHEDULED:'Đã lên lịch',POSTPONED:'Hoãn',CANCELLED:'Đã hủy',IN_PLAY:'Đang thi đấu',PAUSED:'Nghỉ giữa hiệp',SUSPENDED:'Tạm dừng',AWARDED:'Xử kết quả'};
let data=null, route='overview', report=1, search='', position='', scope='current', activeAction='', activeId=null, requestId=0, toastTimer, selectedYear=2026, lastViewSignature='', loading=false;
let detailState=null,detailTimer,detailEpoch=0;
let signedIn=false,standingData=null,standingRequest=0,standingBusy=false,lastRoute='',closingModal=false;
let editorEpoch=0,sessionEpoch=0;
const R=n=>data?.reports[String(n)]||[];
const S=n=>data?.squad?.[({1:'registrations',12:'eligible',14:'positions'})[n]]||R(n);
const seasonLabel=()=>Presentation.seasonName(data?.seasons?.find(s=>String(s.id)===String(data.contextSeason))?.ma_mua_giai||`${selectedYear}-${String(selectedYear+1).slice(-2)}`);
const icon=name=>`<svg class="icon" aria-hidden="true"><use href="/icons.svg#${name}"></use></svg>`;
const navIcons={overview:'grid-outline',players:'people-outline',matches:'football-outline',contracts:'document-text-outline',medical:'medkit-outline',transfers:'swap-horizontal-outline',reports:'bar-chart-outline'};
navIcons.standings='trophy-outline';
const actionIcons={onboard:'add-outline',edit:'create-outline',delete:'trash-outline',register:'add-outline',contract:'add-outline',injury:'medkit-outline',recover:'checkmark-outline','show-appearances':'people-outline',transfer:'swap-horizontal-outline',retry:'arrow-forward-outline'};
const button=(name,text,id='',cls='text-button')=>`<button type="button" class="${cls}" data-action="${name}" data-id="${esc(id)}">${icon(actionIcons[name]||'arrow-forward-outline')}${text.replace(/^\+ /,'')}</button>`;
const badge=(text,kind='')=>`<span class="badge ${kind}">${esc(text)}</span>`;
function table(rows,columns=null,action=null){
 if(!rows.length)return '<div class="empty">Chưa có dữ liệu phù hợp.</div>';
 columns=columns||Object.keys(rows[0]).filter(k=>k!=='ghi_chu');
 return `<div class="table-scroll"><table><thead><tr>${columns.map(k=>`<th>${esc(labels[k]||k)}</th>`).join('')}${action?'<th>Thao tác</th>':''}</tr></thead><tbody>${rows.map(r=>`<tr>${columns.map(k=>`<td>${(['chu_nha','khach'].includes(k)?`<span class="club-inline">${Presentation.crest(r[k])}<span>${esc(r[k])}</span></span>`:esc(r[k]==null?'—':statusNames[r[k]]||r[k]))}</td>`).join('')}${action?`<td>${action(r)}</td>`:''}</tr>`).join('')}</tbody></table></div>`;
}
const panel=(title,body,link='')=>`<section class="panel"><div class="panel-heading"><div><h2>${title}</h2></div>${link}</div>${body}</section>`;
function navigate(){route=location.hash.slice(1);if(!pages[route])route='overview';render();if(route==='standings'&&signedIn)loadStandings();}
function animateRoute(){if(lastRoute!==route){const order=Object.keys(pages);Motion.route($('#page'),order.indexOf(route)-order.indexOf(lastRoute));}lastRoute=route;}
function render(){
 $('#navigation').innerHTML=Object.entries(pages).map(([key,v],i)=>`<a href="#${key}" ${key===route?'aria-current="page"':''}>${icon(navIcons[key])}${v[0]}</a>`).join('');
 $('#page-title').textContent=pages[route][1];
 const season=seasonLabel();
 $('#eyebrow').textContent='MANCHESTER UNITED / '+season;
 $('.demo-tag').textContent='MÙA '+season;
 $('#as-of').closest?.('label')?.toggleAttribute('hidden',route==='standings');
 $('#context button[type="submit"]')?.toggleAttribute?.('hidden',route==='standings');
 $('.page-heading > button')?.toggleAttribute?.('hidden',route==='standings');
 if(route==='standings'){
  $('#page').innerHTML=LeagueTable.render(standingData,selectedYear);
  animateRoute();return;
 }
 if(!data){$('#page').innerHTML='<div class="empty">Chưa có dữ liệu mùa '+esc(seasonLabel())+'.</div>';return;}
 const renderers={overview:overview,players:players,matches:matches,contracts:contracts,medical:medical,transfers:transfers,reports:reports};
 const html=renderers[route]();
 const page=$('#page');
 if(route==='overview'&&page.querySelector&&document.createElement){
  const template=document.createElement('template');template.innerHTML=html;
  for(const selector of ['.metrics','.hero-summary']){const previous=page.querySelector(selector),next=template.content.querySelector(selector);if(previous&&next&&previous.innerHTML===next.innerHTML)next.replaceWith(previous);}
  page.replaceChildren(template.content);
 }else page.innerHTML=html;
 Presentation.tick();
 animateRoute();
}
function overview(){
 return Presentation.overview({data,report:R,rows:matchRows(),icon,panel,badge,table});
}
function rosterRows(){
 const current=new Map(S(1).map(p=>[Number(p.id),p]));
 return data.players.filter(p=>(scope==='all'||current.has(Number(p.id)))&&(!position||String(p.vi_tri_chinh_id)===position)&&p.ho_ten.toLocaleLowerCase('vi').includes(search.toLocaleLowerCase('vi'))).map(p=>({...p,registration:current.get(Number(p.id))}));
}
function playerTable(){
 const eligible=new Set(S(12).map(p=>Number(p.id))),injured=new Set(R(8).map(p=>Number(p.id))),rows=rosterRows();
 if(!rows.length)return '<div class="empty">Chưa có cầu thủ.</div>';
 return `<div class="table-scroll"><table class="player-table"><thead><tr><th>Số áo</th><th>Cầu thủ</th><th>Vị trí</th><th>Quốc tịch</th><th>Tình trạng</th><th></th></tr></thead><tbody>${rows.map(p=>`<tr data-player-id="${p.id}" tabindex="0" aria-label="${esc(p.ho_ten)}"><td><span class="shirt">${esc(p.registration?.so_ao??'—')}</span></td><td><span class="player-identity">${Presentation.portrait(p)}<span><button class="player-open" type="button" data-action="player-detail" data-id="${p.id}">${esc(p.ho_ten)}</button><span class="player-sub">${esc(p.ten_hien_thi||p.ma_vi_tri)}</span></span></span></td><td>${badge(p.ma_vi_tri,'neutral')}</td><td>${Presentation.nation(p.quoc_tich)}</td><td>${data.squad?.reference?badge(Presentation.seasonName(data.squad.sourceSeason),'neutral'):injured.has(Number(p.id))?badge('Chấn thương','red'):eligible.has(Number(p.id))?badge('Đã đăng ký'):badge('Chưa đăng ký','neutral')}</td><td><div class="actions">${p.owned?button('edit','Sửa',p.id):''}${button('delete','Xóa',p.id)}</div></td></tr>`).join('')}</tbody></table></div>`;
}
function players(){return `<div class="toolbar"><input id="search" aria-label="Tìm cầu thủ" placeholder="Tìm cầu thủ…" value="${esc(search)}"><select id="position-filter" aria-label="Lọc vị trí"><option value="">Tất cả vị trí</option>${data.positions.map(p=>`<option value="${p.id}" ${position===String(p.id)?'selected':''}>${esc(p.ten_hien_thi)}</option>`).join('')}</select><select id="scope-filter" aria-label="Phạm vi cầu thủ"><option value="current" ${scope==='current'?'selected':''}>Đội hình</option><option value="all" ${scope==='all'?'selected':''}>Tất cả cầu thủ</option></select><span class="spacer"></span>${button('register','+ Đăng ký thi đấu','','secondary')}</div><section class="panel"><div class="panel-heading"><h2>Manchester United</h2><span class="badge neutral">${esc(Presentation.seasonName(data.squad?.sourceSeason||data.seasons?.[0]?.ma_mua_giai))}</span></div><div id="roster">${playerTable()}</div></section><p class="photo-credit-link"><a href="/photo-credits.html" target="_blank" rel="noopener">Nguồn ảnh</a></p>`;}

function matchRows(){
 const rows=R(2).map(m=>({...m})), api=data.footballApi||{};
 if(api.year===(data.contextYear||2026))for(const m of api.fixtures||[]){
  const existing=rows.find(r=>(m.localId!=null&&String(r.id)===String(m.localId))||(r.chu_nha===m.home&&r.khach===m.away));
  const row=existing||{id:m.localId??null};
  const time=new Date(m.utcDate);
  Object.assign(row,{providerId:m.id,kickoffUtc:m.utcDate,kickoffConfirmed:m.status!=='SCHEDULED',chu_nha:m.home,khach:m.away,thoi_gian_thi_dau:time.toLocaleString('sv-SE',{timeZone:'Asia/Ho_Chi_Minh'}),trang_thai:({FINISHED:'COMPLETED',TIMED:'SCHEDULED'})[m.status]||m.status,ban_thang_chu_nha:m.score?.home??null,ban_thang_khach:m.score?.away??null,phut_api:['IN_PLAY','PAUSED','SUSPENDED'].includes(m.status)?(m.minute==null?'Chưa có':String(m.minute)+(m.injuryTime?'+'+m.injuryTime:'')):null});
  if(!existing)rows.push(row);
 }
 return rows.sort((a,b)=>a.thoi_gian_thi_dau.localeCompare(b.thoi_gian_thi_dau));
}
function matches(){
 const api=data.footballApi||{}, rows=matchRows();
 const finished=rows.filter(m=>m.trang_thai==='COMPLETED').reverse(), live=rows.filter(m=>['IN_PLAY','PAUSED','SUSPENDED'].includes(m.trang_thai));
 const upcoming=rows.filter(m=>m.trang_thai!=='COMPLETED'&&!live.includes(m));
 const columns=['thoi_gian_thi_dau','chu_nha','khach','trang_thai','ban_thang_chu_nha','ban_thang_khach'];
 return '<div class="toolbar"><span class="muted">Mùa '+esc(seasonLabel())+' · Giờ Việt Nam</span></div>'+(api.error?'<p class="form-error" role="status">'+esc(api.error)+'</p>':'')+(live.length?panel('Đang diễn ra',table(live,[...columns,'phut_api'])):'')+panel('Lịch sắp tới',table(upcoming,columns))+panel('Lịch sử thi đấu',historyTable(finished));
}
const matchKey=m=>m.providerId!=null?'api:'+m.providerId:'db:'+m.id;
function historyTable(rows){
 if(!rows.length)return '<div class="empty">Chưa có trận đấu.</div>';
 return `<div class="table-scroll"><table class="history-table"><thead><tr><th>Ngày</th><th>Trận đấu</th><th>Tỷ số</th><th></th></tr></thead><tbody>${rows.map(m=>`<tr data-match-key="${esc(matchKey(m))}" tabindex="0" aria-label="${esc(m.chu_nha+' gặp '+m.khach)}"><td>${esc(m.thoi_gian_thi_dau)}</td><td><div class="history-clubs"><span class="club-inline">${Presentation.crest(m.chu_nha)}${esc(m.chu_nha)}</span><span class="club-inline">${Presentation.crest(m.khach)}${esc(m.khach)}</span></div></td><td class="history-score">${esc(m.ban_thang_chu_nha??'—')} : ${esc(m.ban_thang_khach??'—')}</td><td>${button('match-detail','Thông số trận',matchKey(m))}${button('show-appearances','Cầu thủ',matchKey(m))}</td></tr>`).join('')}</tbody></table></div>`;
}
function clearDetail(){clearTimeout(detailTimer);detailEpoch++;detailState=null;}
function showReadOnly(title,html,source,team=false){
 editorEpoch++;
 activeAction=team?'team-detail':'match-detail';activeId=null;
 $('#modal').classList?.remove('player-modal');$('#modal').classList?.add('detail-modal');$('#modal').classList?.toggle('team-modal',team);
 $('#modal-title').textContent=title;$('#form-note').hidden=true;$('#form-error').hidden=true;$('#fields').innerHTML=html;
 $('#save').hidden=true;$('#save').disabled=false;$('#save').removeAttribute?.('aria-busy');$('#cancel').textContent='Đóng';$('.modal-body')?.scrollTo?.(0,0);
 if(!$('#modal').open){$('#modal').showModal();Motion.open($('#modal'),true,source);}
}
function openDetail(action,key){
 if(closingModal)return;
 clearDetail();
 if(action==='team-detail'){
  const row=(standingData?.table||[]).find(r=>String(r.teamId)===String(key));if(!row)return;
  showReadOnly(row.name,FootballDetails.club(row,data?.clubs,selectedYear),document.querySelector(`[data-team-id="${Number(key)}"]`),true);return;
 }
 let row=data?matchRows().find(m=>matchKey(m)===String(key)||(String(key).startsWith('db:')&&String(m.id)===String(key).slice(3))):null;
 if(!row&&String(key).startsWith('api:')){
  const recent=(standingData?.table||[]).flatMap(t=>t.recentMatches||[]).find(m=>'api:'+m.id===key);
  if(recent)row={id:null,providerId:recent.id,chu_nha:recent.home,khach:recent.away,ban_thang_chu_nha:recent.homeGoals,ban_thang_khach:recent.awayGoals,thoi_gian_thi_dau:recent.utcDate,trang_thai:'COMPLETED'};
 }
 if(!row||row.trang_thai!=='COMPLETED')return;
 detailState={row,year:selectedYear,tab:action==='show-appearances'?'players':'match',payload:{running:row.providerId!=null}};
 showReadOnly('Chi tiết trận đấu',FootballDetails.match(row,detailState.payload,data,detailState.tab),document.querySelector(`[data-match-key="${matchKey(row)}"]`));
 if(row.providerId!=null)fetchMatchDetail(detailEpoch);
}
function renderMatchDetail(){if(detailState)$('#fields').innerHTML=FootballDetails.match(detailState.row,detailState.payload,data,detailState.tab);}
async function fetchMatchDetail(epoch,attempt=0){
 if(epoch!==detailEpoch||!detailState)return;const current=detailState;
 try{
  const response=await fetch('/api/match-detail?'+new URLSearchParams({year:current.year,match:current.row.providerId}));const result=await response.json();
  if(epoch!==detailEpoch||detailState!==current)return;if(response.status===401){showLogin();return;}if(!response.ok)throw Error(result.error||'Chưa thể tải chi tiết trận.');
  if(Number(result.id)!==Number(current.row.providerId)||Number(result.year)!==current.year)throw Error('Chi tiết trận không khớp mùa đã chọn.');
  current.payload=result;renderMatchDetail();
  if(result.running&&attempt<40)detailTimer=setTimeout(()=>fetchMatchDetail(epoch,attempt+1),1500);
  else if(result.running){current.payload={...result,running:false,error:'Chi tiết đang chờ nguồn. Vui lòng mở lại sau.'};renderMatchDetail();}
 }catch(e){if(epoch!==detailEpoch)return;current.payload={running:false,error:e.message};renderMatchDetail();}
}
function contracts(){return `<div class="toolbar">${button('contract','+ Thêm hợp đồng','','primary')}${button('register','Đăng ký thi đấu','','secondary')}</div>${panel('Hết hạn trong 90 ngày',table(R(9),['ho_ten','ngay_ket_thuc','so_ngay_con_lai']))}${panel('Lịch sử hợp đồng tại CLB',table(R(11),['ho_ten','ngay_bat_dau','ngay_ket_thuc']))}`;}
function medical(){return `<div class="toolbar">${button('injury','+ Ghi nhận chấn thương','','primary')}</div>${panel('Đang nghỉ tại ngày báo cáo',table(R(8),['ho_ten','mo_ta','ngay_bat_dau_nghi','ngay_du_kien_tro_lai']))}${panel('Hồ sơ y tế',table(data.injuries,['ho_ten','mo_ta','ngay_bat_dau_nghi','ngay_du_kien_tro_lai','ngay_thuc_te_tro_lai'],i=>button('recover','Cập nhật hồi phục',i.id)))}`;}
function transfers(){return `<div class="toolbar">${button('transfer','+ Hoàn tất chuyển nhượng','','primary')}</div>${panel('Giao dịch đến ngày báo cáo',table(R(15)))}`;}
function reports(){return `<div class="report-layout"><div class="report-menu">${reportNames.map((n,i)=>`<button data-report="${i+1}" class="${report===i+1?'active':''}"><small>Q${String(i+1).padStart(2,'0')}</small>${n}</button>`).join('')}</div>${panel(reportNames[report-1],table(R(report)))}</div>`;}
function viewSignature(result){
 return JSON.stringify({year:result.contextYear,season:result.contextSeason,date:result.contextDate,players:result.players,squad:result.squad,stats:result.playerStats,reports:result.reports,appearances:result.appearances,injuries:result.injuries,clubs:result.clubs,positions:result.positions,fixtures:result.footballApi?.fixtures,error:result.footballApi?.error});
}
async function load(silent=false){
 if(silent&&loading)return;
 const requestedDate=silent&&data?data.contextDate:$('#as-of').value, id=++requestId;loading=true;
 if(!silent)$('#connection').textContent='Đang tải…';
 try{
  const response=await fetch('/api/data?'+new URLSearchParams({year:selectedYear,date:requestedDate}));const result=await response.json();if(id!==requestId)return;if(response.status===401){showLogin();return;}if(!response.ok)throw Error(result.error);
  const choices=result.seasonChoices||[{year:result.contextYear||selectedYear,code:`${selectedYear}-${String(selectedYear+1).slice(-2)}`}];
  const options=choices.map(s=>`<option value="${s.year}" ${Number(s.year)===selectedYear?'selected':''}>${esc(Presentation.seasonName(s.code))}</option>`).join('');
  if($('#season').innerHTML!==options)$('#season').innerHTML=options;
  if(result.ready===false){data=null;render();$('#connection').textContent='';if(route!=='standings')$('#page').innerHTML='<div class="empty">'+esc(result.footballApi?.error||(result.footballApi?.running?'Đang tải mùa '+seasonLabel()+'…':'Chưa có dữ liệu mùa '+seasonLabel()+'.'))+'</div>';return;}
  const signature=viewSignature(result),changed=signature!==lastViewSignature;data=result;lastViewSignature=signature;
  if(!silent)$('#as-of').value=data.contextDate;
  $('#as-of').min=data.seasons[0].ngay_bat_dau;$('#as-of').max=data.seasons[0].ngay_ket_thuc;
  $('#connection').textContent='';if(changed)render();
 }catch(e){if(id!==requestId)return;$('#connection').textContent='Chưa cập nhật';if(!data&&route!=='standings')$('#page').innerHTML=`<div class="error-panel"><strong>Không thể tải dữ liệu</strong><p>${esc(e.message)}</p>${button('retry','Thử lại','','secondary')}</div>`;}
 finally{if(id===requestId)loading=false;}
}

const opt=(rows,label='ho_ten')=>rows.map(p=>[p.id,p[label]]);
function input(name,label,type='text',value='',extra='',required=true){return `<label>${label}<input name="${name}" type="${type}" value="${esc(value)}" ${required?'required':''} ${extra}></label>`;}
function select(name,label,options,value=''){return `<label>${label}<select name="${name}" required>${options.map(([v,t])=>`<option value="${esc(v)}" ${String(value)===String(v)?'selected':''}>${esc(t)}</option>`).join('')}</select></label>`;}
function openForm(action,id){
 if(closingModal)return;
 if(action==='retry'){load();return;}if(['team-detail','match-detail','show-appearances'].includes(action)){openDetail(action,id);return;}if(!data)return;clearDetail();
 if(action==='view-profiles'){scope='all';location.hash='players';navigate();return;}
 const source=action==='player-detail'?document.querySelector(`[data-player-id="${Number(id)}"]`):null;
 activeAction=action;activeId=id;$('#modal').classList?.remove('detail-modal','team-modal');$('#modal').classList?.toggle('player-modal',action==='player-detail');const date=data.contextDate;const own=data.players.filter(p=>p.owned);const player=()=>select('player','Cầu thủ',opt(own),id);
 const endDate=new Date(data.seasons[0].ngay_ket_thuc+'T00:00:00Z');endDate.setUTCDate(endDate.getUTCDate()+1);
 const now=new Date(),today=now.getFullYear()+'-'+String(now.getMonth()+1).padStart(2,'0')+'-'+String(now.getDate()).padStart(2,'0');
 const period=(optional=false)=>input('start','Ngày bắt đầu','date',optional?'':date,'',!optional)+input('end','Ngày kết thúc (không bao gồm)','date',optional?'':endDate.toISOString().slice(0,10),'',!optional);
 const shirt=()=>input('shirt','Số áo','number','','min="1" max="99"',false);
 let title='',fields='',note='';
 switch(action){
 case 'onboard':case 'edit':{
  const p=data.players.find(p=>String(p.id)===String(id))||{};title=action==='onboard'?'Tiếp nhận cầu thủ':'Sửa hồ sơ cầu thủ';
  fields=input('name','Họ tên','text',p.ho_ten||'','maxlength="150"')+input('nationality','Quốc tịch','text',p.quoc_tich??'','maxlength="80"',false)+input('birth','Ngày sinh','date',p.ngay_sinh??'',`max="${today}"`,false)+select('position','Vị trí chính',opt(data.positions,'ten_hien_thi'),p.vi_tri_chinh_id||2);
  if(action==='onboard'){fields+=period()+shirt();note='Số áo phải còn trống.';}break;}
 case 'player-detail':{const p=data.players.find(p=>String(p.id)===String(id));if(!p)return;title=p.ho_ten;fields='<div class="wide">'+PlayerDetails.render(data,p,S(1).find(r=>String(r.id)===String(id)))+'</div>';break;}
 case 'contract':title='Thêm hợp đồng';fields=player()+period(true);note='Hợp đồng mới không được chồng lấn hợp đồng hiện có.';break;
 case 'register':title='Đăng ký thi đấu';fields=player()+period()+shirt();note='Khoảng đăng ký phải có hợp đồng phù hợp. Ngày kết thúc được giới hạn theo mùa đang chọn.';break;
 case 'injury':title='Ghi nhận chấn thương';fields=player()+input('description','Mô tả','text','','maxlength="255"')+input('occurred','Thời điểm xảy ra','datetime-local',date+'T18:00')+input('start','Bắt đầu nghỉ','date',date)+input('expected','Dự kiến trở lại','date','','',false)+select('blocked','Cấm thi đấu',[[1,'Có'],[0,'Không']],1);break;
 case 'recover':{const i=data.injuries.find(i=>String(i.id)===String(id));title='Cập nhật hồi phục';fields=input('recovery','Ngày trở lại thực tế','date',i.ngay_thuc_te_tro_lai||today,`max="${today}"`);note=i.ho_ten+' · '+i.mo_ta;break;}
 case 'transfer':title='Hoàn tất chuyển nhượng';fields=select('player','Cầu thủ',opt(data.players))+select('from','CLB đi',[[0,'Cầu thủ tự do'],...opt(data.clubs,'ten')],data.clubId)+select('to','CLB đến',opt(data.clubs,'ten'),data.clubId)+period()+shirt()+input('fee','Phí chuyển nhượng (GBP)','number','','min="0" max="9999999999999.99" step="0.01"',false);note='Chọn Manchester United ở CLB đi hoặc CLB đến.';break;
 case 'delete':{const p=data.players.find(p=>String(p.id)===String(id));title='Xóa hồ sơ nhập nhầm';note=`Xóa hồ sơ ${p.ho_ten}? Chỉ xóa được hồ sơ chưa có hợp đồng, đăng ký hoặc lịch sử nghiệp vụ. Thao tác này không thể hoàn tác.`;break;}
 case 'show-appearances':{const m=R(2).find(m=>String(m.id)===String(id));title='Thống kê ra sân';note=m.chu_nha+' — '+m.khach;fields=`<div class="wide">${table(data.appearances.filter(a=>String(a.tran_dau_id)===String(id)),['ho_ten','so_phut','ban_thang','kien_tao','the_vang','the_do'])}</div>`;break;}
 default:return;
 }
 editorEpoch++;$('#save').removeAttribute?.('aria-busy');
 $('#modal-title').textContent=title;$('#form-note').textContent=note;$('#form-note').hidden=!note;$('#fields').innerHTML=fields;$('#form-error').hidden=true;$('#cancel').textContent=['show-appearances','player-detail'].includes(action)?'Đóng':'Hủy';$('#save').hidden=['show-appearances','player-detail'].includes(action);$('#save').disabled=false;$('#save').innerHTML=icon(action==='delete'?'trash-outline':'checkmark-outline')+(action==='delete'?'Xóa hồ sơ':'Lưu dữ liệu');$('.modal-body')?.scrollTo?.(0,0);$('#modal').showModal();Motion.open($('#modal'),action==='player-detail',source);if(action==='player-detail')Motion.radar($('#fields'));
}
function toast(message){clearTimeout(toastTimer);$('#toast').textContent=message;$('#toast').hidden=false;Motion.feedback($('#toast'));toastTimer=setTimeout(()=>$('#toast').hidden=true,4500);}
async function closeModal(){if(closingModal)return;editorEpoch++;clearDetail();closingModal=true;try{await Motion.close($('#modal'));}finally{closingModal=false;}}
$('#editor').addEventListener('submit',async e=>{
 e.preventDefault();if($('#save').disabled||['team-detail','match-detail','player-detail','show-appearances'].includes(activeAction))return;const body=new URLSearchParams(new FormData(e.target));body.set('action',activeAction);body.set('season',data.contextSeason);
 const editor=editorEpoch,session=sessionEpoch,currentEditor=()=>editor===editorEpoch&&$('#modal').open;
 if(['edit','delete'].includes(activeAction))body.set('player',activeId);if(activeAction==='recover')body.set('injury',activeId);
 $('#save').disabled=true;$('#save').setAttribute?.('aria-busy','true');$('#form-error').hidden=true;
 try{
  const res=await fetch('/api/action',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded','X-CSDL1':'1'},body});const result=await res.json();
  if(session!==sessionEpoch)return;if(res.status===401){showLogin();return;}if(!res.ok)throw Error(result.error);
  if(currentEditor()){await closeModal();if(session!==sessionEpoch)return;toast(result.message);}
  await load();
 }catch(e){if(session===sessionEpoch&&currentEditor()){$('#form-error').textContent=e.message;$('#form-error').hidden=false;Motion.feedback($('#form-error'),true);}}
 finally{if(session===sessionEpoch&&currentEditor()){$('#save').disabled=false;$('#save').removeAttribute?.('aria-busy');}}
});
$('#close-modal').onclick=$('#cancel').onclick=closeModal;
$('#modal').addEventListener('cancel',e=>{e.preventDefault();if(!$('#save').disabled)closeModal();});
$('#context').addEventListener('submit',e=>{e.preventDefault();load();});
function updateRoster(){const roster=$('#roster'),before=Motion.capture(roster);roster.innerHTML=playerTable();Motion.filter(roster,before);}
$('#page').addEventListener('input',e=>{if(e.target.id==='search'){search=e.target.value;updateRoster();}});
$('#page').addEventListener('change',e=>{if(e.target.id==='position-filter'){position=e.target.value;updateRoster();}if(e.target.id==='scope-filter'){scope=e.target.value;updateRoster();}});
 document.addEventListener('click',e=>{const tab=e.target.closest('[data-detail-tab]');if(tab&&detailState){detailState.tab=tab.dataset.detailTab;renderMatchDetail();return;}const a=e.target.closest('[data-action]');if(a){openForm(a.dataset.action,a.dataset.id);return;}const row=e.target.closest('[data-player-id]');if(row){openForm('player-detail',row.dataset.playerId);return;}const team=e.target.closest('tr[data-team-id]');if(team){openDetail('team-detail',team.dataset.teamId);return;}const match=e.target.closest('[data-match-key]');if(match){openDetail('match-detail',match.dataset.matchKey);return;}const q=e.target.closest('[data-report]');if(q){report=Number(q.dataset.report);render();}});
const today=new Date();$('#as-of').value=today.getFullYear()+'-'+String(today.getMonth()+1).padStart(2,'0')+'-'+String(today.getDate()).padStart(2,'0');
window.addEventListener('hashchange',navigate);navigate();bootstrap();

setInterval(()=>{if(!signedIn||document.hidden||$('#modal').open)return;if(route==='standings')loadStandings();else if(['overview','matches'].includes(route)||!data||data.footballApi?.running)load(true);},5000);

setInterval(()=>{if(route==='overview'&&!document.hidden)Presentation.tick();},1000);
document.addEventListener('error',e=>{if(e.target.matches?.('img[data-portrait]'))e.target.hidden=true;},true);

$('#season').addEventListener('change',async()=>{
 selectedYear=Number($('#season').value);requestId++;lastViewSignature='';data=null;
 standingRequest++;standingBusy=false;standingData=null;render();if(route!=='standings')$('#page').innerHTML='<div class="empty">Đang tải mùa '+esc(seasonLabel())+'…</div>';else loadStandings();
 $('#as-of').removeAttribute?.('min');$('#as-of').removeAttribute?.('max');
 try{const res=await fetch('/api/action',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded','X-CSDL1':'1'},body:new URLSearchParams({action:'season',year:selectedYear})});const result=await res.json();if(!res.ok)throw Error(result.error);}catch(e){toast(e.message);}
 await load();
});
document.addEventListener('keydown',e=>{if((e.key==='Enter'||e.key===' ')&&e.target.matches?.('tr[data-team-id],tr[data-match-key]')){e.preventDefault();openDetail(e.target.dataset.teamId?'team-detail':'match-detail',e.target.dataset.teamId||e.target.dataset.matchKey);return;}if((e.key==='Enter'||e.key===' ')&&e.target.matches?.('tr[data-player-id]')){e.preventDefault();openForm('player-detail',e.target.dataset.playerId);}});

function showLogin(message=''){
 sessionEpoch++;editorEpoch++;
 clearDetail();
 signedIn=false;requestId++;standingRequest++;loading=false;standingBusy=false;data=null;standingData=null;lastViewSignature='';lastRoute='';
 $('#modal').close();$('#page').innerHTML='';$('#toast').hidden=true;$('#login-password').value='';$('#login-password').type='password';$('#toggle-password').textContent='Hiện';$('#toggle-password').setAttribute?.('aria-pressed','false');
 document.body.classList.add('signed-out');$('#login-screen').hidden=false;$('#login-error').textContent=message;$('#login-error').hidden=!message;$('#login-username').focus();
}
async function enterWorkspace(session){
 sessionEpoch++;
 signedIn=true;document.body.classList.remove('signed-out');$('#login-screen').hidden=true;$('#admin-name').textContent=session.username;$('#login-password').value='';
 navigate();await load();$('#content').focus();
}
async function bootstrap(){
 try{const res=await fetch('/api/session');if(!res.ok)throw Error();const session=await res.json();if(session.authenticated)await enterWorkspace(session);else showLogin();}
 catch{showLogin('Không thể kết nối máy chủ. Vui lòng thử lại.');}
}
$('#login-form').addEventListener('submit',async e=>{
 e.preventDefault();const submit=$('#login-submit');submit.disabled=true;submit.setAttribute('aria-busy','true');$('#login-error').hidden=true;
 try{
  const body=new URLSearchParams(new FormData(e.target));const res=await fetch('/api/login',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded','X-CSDL1':'1'},body});const result=await res.json();
  if(!res.ok)throw Error(result.error||'Chưa thể đăng nhập.');await enterWorkspace(result);
 }catch(e){$('#login-error').textContent=e.message;$('#login-error').hidden=false;$('#login-password').value='';$('#login-password').focus();Motion.feedback($('#login-card')||$('#login-form'),true);}
 finally{submit.disabled=false;submit.removeAttribute('aria-busy');}
});
$('#toggle-password').addEventListener('click',()=>{const input=$('#login-password'),show=input.type==='password';input.type=show?'text':'password';$('#toggle-password').textContent=show?'Ẩn':'Hiện';$('#toggle-password').setAttribute('aria-label',show?'Ẩn mật khẩu':'Hiện mật khẩu');$('#toggle-password').setAttribute('aria-pressed',String(show));input.focus();});
$('#logout').addEventListener('click',async()=>{
 $('#logout').disabled=true;
 try{const res=await fetch('/api/logout',{method:'POST',headers:{'X-CSDL1':'1'}});if(!res.ok)throw Error('Chưa thể đăng xuất.');showLogin();}
 catch(e){toast(e.message);}finally{$('#logout').disabled=false;}
});
async function loadStandings(){
 if(standingBusy||!signedIn)return;const year=selectedYear,id=++standingRequest;standingBusy=true;
 try{
  const res=await fetch('/api/standings?'+new URLSearchParams({year}));const result=await res.json();if(id!==standingRequest||year!==selectedYear)return;
  if(res.status===401){showLogin();return;}if(!res.ok)throw Error(result.error||'Chưa thể tải bảng xếp hạng.');
  const tableChanged=JSON.stringify(standingData?.table)!==JSON.stringify(result.table);
  const changed=tableChanged||standingData?.error!==result.error||standingData?.formError!==result.formError||standingData?.running!==result.running;
  const changedRows=LeagueTable.changes(standingData,result);standingData=result;
  if(route==='standings'){
   if(changed){const before=tableChanged?Motion.capture($('#page'),'data-team-id'):null;render();if(tableChanged)Motion.rows($('#page'),changedRows,before);}
   else if(result.updatedAt){const node=$('.league-updated');if(node)node.textContent='Cập nhật '+new Date(result.updatedAt).toLocaleTimeString('vi-VN',{hour:'2-digit',minute:'2-digit',second:'2-digit'});}
  }
 }catch(e){if(id!==standingRequest)return;standingData={...standingData,year,error:e.message,running:false};if(route==='standings')render();}
 finally{if(id===standingRequest)standingBusy=false;}
}
