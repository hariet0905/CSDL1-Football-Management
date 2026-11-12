import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** football-data.org adapter. Live data stays in memory; only final stats enter the ER model. */
final class FootballData implements AutoCloseable {
    static final int SEASON_YEAR=2026;
    static final String SEASON_CODE="2026-27";
    interface Connections { Connection open() throws SQLException; }
    interface Source { Map<String,Object> get(String path) throws Exception; }
    private final Connections connections;
    private final Source source;
    private final EspnFootball espn;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean busy=new AtomicBoolean();
    private final AtomicBoolean started=new AtomicBoolean();
    private final Properties bindings=new Properties();
    private final Map<Long,Map<String,Object>> people=new HashMap<>();
    private final Path bindingFile;
    private volatile Map<String,Object> state=Map.of("message","Chưa đồng bộ API.","running",false);
    private volatile int activeYear=SEASON_YEAR,jobYear=SEASON_YEAR;
    private final boolean configured;
    private long nextRequest;
    private final java.util.concurrent.locks.ReentrantLock requestLock=new java.util.concurrent.locks.ReentrantLock(true);
    private final ExecutorService standingsWorker=Executors.newSingleThreadExecutor();
    private final Map<Integer,Map<String,Object>> tables=new ConcurrentHashMap<>();
    private final Map<Integer,Long> tableAttempts=new HashMap<>();
    private final ExecutorService detailWorker=Executors.newSingleThreadExecutor();
    private final Map<String,Map<String,Object>> details=new ConcurrentHashMap<>();
    private final Map<String,Long> detailAttempts=new HashMap<>();

    FootballData(Path root, Connections connections) throws IOException {
        this(root,connections,null,null);
    }

    FootballData(Path root, Connections connections, Source testSource) throws IOException {
        this(root,connections,testSource,null);
    }

    FootballData(Path root, Connections connections, Source testSource,Source testEspnSource) throws IOException {
        this.connections=connections;
        espn=testSource==null||testEspnSource!=null?new EspnFootball(testEspnSource):null;
        String token=token(root);
        configured=testSource!=null || !token.isBlank();
        bindingFile=root.resolve("app/.football-data-bindings.properties");
        if(Files.exists(bindingFile)) try(Reader r=Files.newBufferedReader(bindingFile)) { bindings.load(r); }
        HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
        Source httpSource=path -> {
            requestLock.lockInterruptibly();
            try {
            long delay=nextRequest-System.currentTimeMillis();
            if(delay>0) Thread.sleep(delay);
            nextRequest=System.currentTimeMillis()+6500; // At most 10 calls/minute.
            HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.football-data.org/v4/"+path))
                .timeout(Duration.ofSeconds(25)).header("X-Auth-Token",token)
                .header("X-Unfold-Lineups","true").header("X-Unfold-Goals","true")
                .header("X-Unfold-Bookings","true").header("X-Unfold-Subs","true").GET().build();
            HttpResponse<InputStream> response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(InputStream in=response.body()) {
                if(response.statusCode()!=200) throw new IOException(switch(response.statusCode()) {
                    case 401 -> "API từ chối token. Kiểm tra .env.";
                    case 403 -> "Gói API chưa cho phép dữ liệu hoặc mùa đã chọn.";
                    case 429 -> "Đã chạm giới hạn API; hãy chờ trước khi đồng bộ lại.";
                    default -> "Nguồn API chưa phản hồi thành công (HTTP "+response.statusCode()+").";
                });
                byte[] bytes=in.readNBytes(4*1024*1024+1);
                if(bytes.length>4*1024*1024) throw new IOException("Phản hồi API quá lớn.");
                return obj(Json.parse(new String(bytes,StandardCharsets.UTF_8)));
            }
            } finally {requestLock.unlock();}
        };
        source=testSource==null?httpSource:testSource;
    }

    void start() {
        if(!started.compareAndSet(false,true)) return;
        if(configured) {
            request(SEASON_YEAR);
            worker.scheduleWithFixedDelay(()->request(activeYear),120,120,TimeUnit.SECONDS);
        }
        else state=Map.of("error","Chưa cấu hình FOOTBALL_DATA_API_KEY trong .env.","year",SEASON_YEAR);
    }

    static String token(Path root) throws IOException {
        String token=System.getenv("FOOTBALL_DATA_API_KEY");
        if(token!=null && !token.isBlank()) return token.trim();
        Path file=root.resolve(".env");
        if(!Files.exists(file)) return "";
        for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)) {
            String[] kv=line.replace("\uFEFF","").split("=",2);
            if(kv.length==2 && kv[0].trim().equals("FOOTBALL_DATA_API_KEY")) {
                String value=kv[1].trim();
                if(value.length()>=2 && ((value.startsWith("\"")&&value.endsWith("\"")) || (value.startsWith("'")&&value.endsWith("'")))) value=value.substring(1,value.length()-1);
                if(value.contains("\r") || value.contains("\n")) throw new IOException("Token sai định dạng.");
                return value;
            }
        }
        return "";
    }

    Map<String,Object> snapshot() {
        return snapshot(activeYear);
    }
    Map<String,Object> snapshot(int year) {
        Map<String,Object> out=Objects.equals(state.get("year"),year)?new LinkedHashMap<>(state):new LinkedHashMap<>();
        out.put("year",year);out.put("configured",configured); out.put("running",busy.get() && (jobYear==year || activeYear==year));
        out.put("provider","football-data.org"); out.put("pollSeconds",120);
        return out;
    }
    void request(int year) {
        validateYear(year);
        if(!configured) throw new IllegalArgumentException("Chưa cấu hình FOOTBALL_DATA_API_KEY trong .env.");
        activeYear=year;
        if(!busy.compareAndSet(false,true)) return;
        jobYear=year;
        worker.execute(()-> {
            try { sync(year); }
            catch(Exception e) {
                // Never return provider bodies, request headers or authentication errors verbatim.
                Map<String,Object> out=new LinkedHashMap<>(state);
                out.put("error",e instanceof SQLException?"Đồng bộ bị chặn bởi ràng buộc CSDL; dữ liệu của trận lỗi đã được hoàn tác.":
                    e instanceof IOException?e.getMessage():"Dữ liệu API chưa đủ hoặc không khớp mô hình; giữ dữ liệu đã có.");
                out.put("running",false); state=out;
            } finally { busy.set(false); if(activeYear!=year && !worker.isShutdown()) request(activeYear); }
        });
    }
    static int validateYear(int year) {
        if(year<SEASON_YEAR || year>Year.now().getValue()+1) throw new IllegalArgumentException("Mùa giải phải từ 2026–2027 đến mùa kế tiếp.");
        return year;
    }

    Map<String,Object> standings(int year) {
        validateYear(year);
        return tables.getOrDefault(year,Map.of("year",year,"table",List.of(),"running",false,"error",configured?"Đang chờ bảng xếp hạng.":"Chưa cấu hình nguồn bóng đá."));
    }
    synchronized void requestStandings(int year) {
        validateYear(year);
        if(!configured||standingsWorker.isShutdown())return;
        long now=System.currentTimeMillis();
        if(now-tableAttempts.getOrDefault(year,0L)<120_000)return;
        tableAttempts.put(year,now);
        Map<String,Object> waiting=new LinkedHashMap<>(standings(year));waiting.remove("error");waiting.put("running",true);tables.put(year,waiting);
        standingsWorker.execute(()->{
            try {
                // The unfiltered current table retains official points deductions.
                Map<String,Object> payload=source.get("competitions/PL/standings");
                if(!Objects.equals(obj(payload.get("season")).get("startDate"),null)&&LocalDate.parse(text(obj(payload.get("season")),"startDate")).getYear()!=year)
                    payload=source.get("competitions/PL/standings?season="+year);
                Map<String,Object> table=normalizeStandings(payload,year);
                try{table=FootballViews.form(table,source.get("competitions/PL/matches?season="+year+"&status=FINISHED"),year);}
                catch(Exception e){
                    var old=standings(year);for(var row:objects(table.get("table")))for(var previous:objects(old.get("table")))if(Objects.equals(row.get("teamId"),previous.get("teamId"))&&previous.containsKey("recentMatches")){row.put("recentMatches",previous.get("recentMatches"));row.put("form",previous.get("form"));}
                    if(old.containsKey("formUpdatedAt"))table.put("formUpdatedAt",old.get("formUpdatedAt"));table.put("formError","Chưa cập nhật được phong độ 5 trận.");
                }
                tables.put(year,table);
            } catch(Exception e) {
                Map<String,Object> old=new LinkedHashMap<>(standings(year));old.put("running",false);
                old.put("error",e instanceof IOException?e.getMessage():"Chưa có bảng xếp hạng hợp lệ cho mùa đã chọn.");tables.put(year,old);
            }
        });
    }
    static Map<String,Object> normalizeStandings(Map<String,Object> payload,int year) {
        if(!"PL".equals(obj(payload.get("competition")).get("code")))throw new IllegalArgumentException("Sai giải đấu.");
        Map<String,Object> season=obj(payload.get("season"));
        if(LocalDate.parse(text(season,"startDate")).getYear()!=year)throw new IllegalArgumentException("Sai mùa bảng xếp hạng.");
        List<Map<String,Object>> totals=objects(payload.get("standings")).stream().filter(s->"TOTAL".equals(s.get("type"))).toList();
        if(totals.size()!=1)throw new IllegalArgumentException("Bảng tổng không duy nhất.");
        Map<String,Object> total=totals.get(0);
        List<Map<String,Object>> source=objects(total.get("table")),table=new ArrayList<>();Set<Long> teamIds=new HashSet<>();Set<Integer> positions=new HashSet<>();
        if(source.size()!=20)throw new IllegalArgumentException("Bảng xếp hạng chưa đủ 20 đội.");
        for(Map<String,Object> row:source) {
            Map<String,Object> team=obj(row.get("team"));long id=id(team);int position=integer(row,"position",1,20);
            if(!teamIds.add(id)||!positions.add(position))throw new IllegalArgumentException("Đội hoặc thứ hạng trùng.");
            Map<String,Object> out=new LinkedHashMap<>();out.put("position",position);out.put("teamId",id);out.put("name",TEAM_NAMES.getOrDefault(id,text(team,"name")));
            for(String field:List.of("playedGames","won","draw","lost","goalsFor","goalsAgainst","goalDifference","points"))out.put(field,integer(row,field,field.equals("points")||field.equals("goalDifference")?-1000:0,1000));
            if((int)out.get("playedGames")!=(int)out.get("won")+(int)out.get("draw")+(int)out.get("lost"))throw new IllegalArgumentException("Số trận không khớp.");
            if((int)out.get("goalDifference")!=(int)out.get("goalsFor")-(int)out.get("goalsAgainst"))throw new IllegalArgumentException("Hiệu số không khớp.");
            Object form=row.get("form");out.put("form",form==null?List.of():Arrays.stream(form.toString().split(",")).map(String::trim).filter(v->Set.of("W","D","L").contains(v)).limit(5).toList());table.add(out);
        }
        table.sort(Comparator.comparingInt(r->(int)r.get("position")));
        Map<String,Object> result=new LinkedHashMap<>();result.put("year",year);result.put("table",table);result.put("matchday",season.get("currentMatchday"));result.put("updatedAt",Instant.now().toString());result.put("running",false);return result;
    }

    static String teamName(Map<String,Object> team){return TEAM_NAMES.getOrDefault(id(team),text(team,"name"));}
    Map<String,Object> matchDetails(int year,long id){return details.getOrDefault(year+"."+id,Map.of("id",id,"year",year,"running",false));}
    synchronized void requestMatchDetails(int year,long id){
        validateYear(year);if(id<=0)throw new IllegalArgumentException("Mã trận không hợp lệ.");
        String key=year+"."+id;long now=System.currentTimeMillis();
        if(!configured){details.put(key,Map.of("id",id,"year",year,"running",false,"error","Chưa cấu hình nguồn bóng đá."));return;}
        var previous=matchDetails(year,id);long interval="FINISHED".equals(previous.get("status"))&&!previous.containsKey("error")?21_600_000:120_000;
        if(now-detailAttempts.getOrDefault(key,0L)<interval||detailWorker.isShutdown())return;
        if(details.size()>=200){String oldest=detailAttempts.entrySet().stream().filter(e->!Boolean.TRUE.equals(details.getOrDefault(e.getKey(),Map.of()).get("running"))).min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);if(oldest==null)return;details.remove(oldest);detailAttempts.remove(oldest);}
        detailAttempts.put(key,now);Map<String,Object> waiting=new LinkedHashMap<>(previous);waiting.put("running",true);waiting.remove("error");details.put(key,waiting);
        detailWorker.execute(()->{
            try{
                Map<String,Object> detail=FootballViews.match(source.get("matches/"+id),id,year);
                detail=EspnFootball.retain(detail,previous);
                if(espn!=null&&"FINISHED".equals(detail.get("status")))try{detail=espn.enrich(detail,year);}
                catch(Exception e){detail.put("error","Chưa tải được thống kê ESPN khớp trận này. Sẽ thử lại sau.");}
                details.put(key,detail);
            }
            catch(Exception e){Map<String,Object> failed=new LinkedHashMap<>(previous);failed.put("running",false);failed.put("error",e instanceof IOException?e.getMessage():"Chưa có chi tiết hợp lệ cho trận đã chọn.");details.put(key,failed);}
        });
    }

    private void sync(int year) throws Exception {
        progress(year,"Đang lấy lịch thi đấu…",null,null);
        Map<String,Object> payload=source.get("teams/66/matches?competitions=PL&season="+year);
        List<Map<String,Object>> matches=objects(payload.get("matches"));
        if(matches.isEmpty()) throw new IOException("API chưa có trận Premier League cho mùa này.");
        // Reject the whole response before exposing any fixtures or opening the database.
        Set<Long> matchIds=new HashSet<>();
        for(Map<String,Object> match:matches) {
            validateScheduleMatch(match,year);
            if(!matchIds.add(id(match))) throw new IllegalArgumentException("Trùng ID trận trong lịch API.");
        }
        for(int i=0;i<matches.size();i++) {
            Map<String,Object> m=matches.get(i);
            if(Set.of("IN_PLAY","PAUSED","SUSPENDED").contains(m.get("status"))) {
                Map<String,Object> detail=source.get("matches/"+id(m));
                validateMatchIdentity(detail,m,year);
                // The list remains immutable; merge timing into the cached display below.
                Map<String,Object> enriched=new LinkedHashMap<>(m);
                for(String key:List.of("minute","injuryTime","score","status")) if(detail.containsKey(key)) enriched.put(key,detail.get(key));
                List<Map<String,Object>> copy=new ArrayList<>(matches); copy.set(i,enriched); matches=copy;
            }
        }
        // Expose provider timing exactly as received, never infer match minutes from wall time.
        List<Map<String,Object>> live=new ArrayList<>();
        for(Map<String,Object> m:matches) {
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("id",m.get("id")); row.put("localId",bindings.getProperty("match."+id(m)));
            Map<String,Object> home=obj(m.get("homeTeam")),away=obj(m.get("awayTeam"));
            row.put("home",TEAM_NAMES.getOrDefault(id(home),text(home,"name")));
            row.put("away",TEAM_NAMES.getOrDefault(id(away),text(away,"name"))); row.put("utcDate",m.get("utcDate"));
            row.put("status",m.get("status")); row.put("minute",m.get("minute")); row.put("injuryTime",m.get("injuryTime"));
            row.put("score",obj(obj(m.get("score")).get("fullTime"))); live.add(row);
        }
        progress(year,"Đang đối chiếu CSDL…",live,null);
        Map<String,Object> teamsPayload=source.get("competitions/PL/teams?season="+year);
        Map<Long,Map<String,Object>> teams=new HashMap<>();
        for(Map<String,Object> team:objects(teamsPayload.get("teams"))) teams.put(id(team),team);
        List<String> notes=new ArrayList<>();
        boolean depth=true;
        long season;
        Map<Long,Long> clubIds=new HashMap<>();
        try(Connection db=connections.open()) {
            long managed=one(db,"SELECT id FROM CauLacBo WHERE la_clb_quan_ly=TRUE");
            if(managed!=team(db,teams.get(66L))) throw new IllegalArgumentException("CLB quản lý không phải Manchester United.");
            for(Map<String,Object> team:teams.values()) clubIds.put(id(team),team(db,team));
            List<Map<String,Object>> seasons=rows(db,"SELECT * FROM MuaGiai WHERE ma_mua_giai=?",seasonCode(year));
            if(seasons.size()>1) throw new IllegalArgumentException("Mùa không duy nhất.");
            if(seasons.isEmpty()) {
                Map<String,Object> remoteSeason=obj(matches.get(0).get("season"));
                LocalDate start=LocalDate.parse(text(remoteSeason,"startDate"));
                LocalDate end=LocalDate.parse(text(remoteSeason,"endDate"));
                if(start.getYear()!=year || end.isBefore(start)) throw new IllegalArgumentException("Sai mùa API.");
                exec(db,"INSERT INTO MuaGiai(ma_mua_giai,ngay_bat_dau,ngay_ket_thuc) VALUES(?,?,?)",seasonCode(year),start,end);
                season=one(db,"SELECT LAST_INSERT_ID()");
            } else season=((Number)seasons.get(0).get("id")).longValue();
            if(rows(db,"SHOW COLUMNS FROM CauThu_TranDau LIKE 'kien_tao'").isEmpty()) {
                depth=false;
                notes.add("Chưa có cột kiến tạo theo ER mới: áp dụng db/migrations/001_add_assists.sql trước khi nhập thống kê. Lịch và kết quả vẫn được đồng bộ.");
            }
        }
        int saved=0, stats=0;
        for(Map<String,Object> match:matches) {
            try {
                if(!Set.of("FINISHED","SCHEDULED","TIMED","POSTPONED","CANCELLED").contains(match.get("status"))) continue;
                Map<String,Object> detail=null;
                String stamp=Objects.toString(match.get("lastUpdated"),"");
                boolean alreadyImported=!stamp.isBlank() && stamp.equals(bindings.getProperty("stats."+id(match)+".updated"));
                if("FINISHED".equals(match.get("status")) && depth && !alreadyImported) {
                    Map<String,Object> candidate=source.get("matches/"+id(match));
                    validateMatchIdentity(candidate,match,year);
                    boolean home=id(obj(candidate.get("homeTeam")))==66;
                    if(!hasDetails(candidate,home)) {
                        depth=false;
                        notes.add("Gói API chưa trả đủ đội hình, bàn thắng, thẻ hoặc thay người. Thống kê cũ được giữ nguyên.");
                    } else {
                        detail=candidate;
                        for(Appearance a:appearances(detail,home)) if(!people.containsKey(a.providerId())) people.put(a.providerId(),source.get("persons/"+a.providerId()));
                    }
                }
                int imported=saveMatch(detail==null?match:detail,season,clubIds,teams,detail);
                stats+=imported;
                if(imported>0 && !stamp.isBlank()) bindings.setProperty("stats."+id(match)+".updated",stamp);
                saved++;
            } catch(SQLException | IllegalArgumentException e) {
                if(notes.size()<12) notes.add("Trận API "+id(match)+": dữ liệu không khớp đăng ký, hợp đồng, khóa hoặc ràng buộc; không ghi phần lỗi.");
            }
            progress(year,"Đã đồng bộ "+saved+"/"+matches.size()+" trận…",live,notes);
        }
        saveBindings();
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("year",year); out.put("localSeasonId",season); out.put("fixtures",live);
        out.put("message","Đã đồng bộ "+saved+" trận, "+stats+" lượt ra sân từ API.");
        out.put("notes",notes); out.put("updatedAt",Instant.now().toString());
        out.put("playerDetailsAvailable",depth); state=out;
    }
    private static void validateScheduleMatch(Map<String,Object> match,int year) {
        FootballViews.validate(match,year);
        id(match);
        long home=id(obj(match.get("homeTeam"))),away=id(obj(match.get("awayTeam")));
        if(home==away || home!=66 && away!=66) throw new IllegalArgumentException("Sai CLB trong lịch Manchester United.");
        Instant.parse(text(match,"utcDate"));
        text(match,"status");
    }
    private static void validateMatchIdentity(Map<String,Object> detail,Map<String,Object> match,int year) {
        validateScheduleMatch(detail,year);
        if(id(detail)!=id(match) || id(obj(detail.get("homeTeam")))!=id(obj(match.get("homeTeam")))
            || id(obj(detail.get("awayTeam")))!=id(obj(match.get("awayTeam"))))
            throw new IllegalArgumentException("Sai định danh chi tiết trận.");
    }
    private void progress(int year,String message,List<Map<String,Object>> fixtures,List<String> notes) {
        Map<String,Object> out=new LinkedHashMap<>();
        if(Objects.equals(state.get("year"),year)) {
            if(state.containsKey("fixtures")) out.put("fixtures",state.get("fixtures"));
            if(state.containsKey("updatedAt")) out.put("updatedAt",state.get("updatedAt"));
        }
        out.put("year",year); out.put("message",message);
        if(fixtures!=null) out.put("fixtures",fixtures);
        if(notes!=null) out.put("notes",List.copyOf(notes));
        out.put("fetchedAt",Instant.now().toString()); state=out;
    }
    static String seasonCode(int year) { return year+"-"+String.format("%02d",(year+1)%100); }
    private long team(Connection db,Map<String,Object> remote) throws SQLException {
        if(remote==null) throw new IllegalArgumentException("Thiếu CLB API.");
        long external=id(remote);
        String key="team."+external;
        String bound=bindings.getProperty(key);
        String alias=TEAM_NAMES.getOrDefault(external,text(remote,"name"));
        if(bound!=null) return one(db,"SELECT id FROM CauLacBo WHERE id=? AND ten=?",Long.parseLong(bound),alias);
        List<Map<String,Object>> found=rows(db,"SELECT id FROM CauLacBo WHERE ten=?",alias);
        if(found.isEmpty()) {
            exec(db,"INSERT INTO CauLacBo(ten,san_nha) VALUES(?,?)",alias,remote.get("venue"));
            found=rows(db,"SELECT id FROM CauLacBo WHERE id=LAST_INSERT_ID()");
        }
        if(found.size()!=1) throw new IllegalArgumentException("CLB không duy nhất.");
        long local=((Number)found.get(0).get("id")).longValue(); bindings.setProperty(key,""+local); return local;
    }
    private static final Map<Long,String> TEAM_NAMES=Map.ofEntries(
        Map.entry(57L,"Arsenal"),Map.entry(58L,"Aston Villa"),Map.entry(61L,"Chelsea"),Map.entry(62L,"Everton"),
        Map.entry(63L,"Fulham"),Map.entry(64L,"Liverpool"),Map.entry(65L,"Manchester City"),Map.entry(66L,"Manchester United"),
        Map.entry(67L,"Newcastle United"),Map.entry(71L,"Sunderland"),Map.entry(73L,"Tottenham Hotspur"),
        Map.entry(76L,"Wolverhampton Wanderers"),Map.entry(328L,"Burnley"),Map.entry(341L,"Leeds United"),
        Map.entry(351L,"Nottingham Forest"),Map.entry(354L,"Crystal Palace"),Map.entry(397L,"Brighton & Hove Albion"),
        Map.entry(402L,"Brentford"),Map.entry(563L,"West Ham United"),Map.entry(1044L,"AFC Bournemouth"));

    private int saveMatch(Map<String,Object> remote,long season,Map<Long,Long> clubs,Map<Long,Map<String,Object>> teams,Map<String,Object> detail) throws Exception {
        long homeExternal=id(obj(remote.get("homeTeam"))),awayExternal=id(obj(remote.get("awayTeam")));
        if(homeExternal!=66 && awayExternal!=66) throw new IllegalArgumentException("Sai CLB.");
        Long home=clubs.get(homeExternal),away=clubs.get(awayExternal);
        if(home==null || away==null) throw new IllegalArgumentException("CLB chưa ánh xạ.");
        LocalDateTime kickoff=LocalDateTime.ofInstant(Instant.parse(text(remote,"utcDate")),ZoneId.of("Asia/Ho_Chi_Minh"));
        String status=switch(text(remote,"status")) {
            case "FINISHED" -> "COMPLETED";
            case "SCHEDULED","TIMED" -> "SCHEDULED";
            case "POSTPONED" -> "POSTPONED"; case "CANCELLED" -> "CANCELLED";
            default -> null;
        };
        // Live/suspended/awarded states have no equivalent in the approved persisted enum.
        if(status==null) throw new IllegalArgumentException("Trạng thái chỉ hiển thị từ cache.");
        Map<String,Object> score=obj(obj(remote.get("score")).get("fullTime"));
        Object hg=status.equals("COMPLETED")?integer(score,"home",0,255):null;
        Object ag=status.equals("COMPLETED")?integer(score,"away",0,255):null;
        long local; int stats=0;
        try(Connection db=connections.open()) {
            db.setAutoCommit(false);
            try {
                // Lock the season to serialize natural-key matching without changing the ER schema.
                one(db,"SELECT id FROM MuaGiai WHERE id=? FOR UPDATE",season);
                String binding=bindings.getProperty("match."+id(remote));
                List<Map<String,Object>> found=binding==null?
                    rows(db,"SELECT * FROM TranDau WHERE mua_giai_id=? AND clb_chu_nha_id=? AND clb_khach_id=?",season,home,away):
                    rows(db,"SELECT * FROM TranDau WHERE id=? AND mua_giai_id=? AND clb_chu_nha_id=? AND clb_khach_id=?",Long.parseLong(binding),season,home,away);
                if(found.size()>1 || (binding!=null && found.isEmpty())) throw new IllegalArgumentException("Trận không ánh xạ duy nhất.");
                if(found.isEmpty()) {
                    Object venue=remote.get("venue");
                    if(venue==null) venue=teams.get(homeExternal).get("venue");
                    if(!(venue instanceof String s) || s.isBlank()) throw new IllegalArgumentException("Thiếu sân thi đấu.");
                    exec(db,"INSERT INTO TranDau(mua_giai_id,clb_chu_nha_id,clb_khach_id,thoi_gian_thi_dau,san_thi_dau,trang_thai,ban_thang_chu_nha,ban_thang_khach) VALUES(?,?,?,?,?,?,?,?)",season,home,away,kickoff,venue,status,hg,ag);
                    local=one(db,"SELECT LAST_INSERT_ID()");
                } else {
                    local=((Number)found.get(0).get("id")).longValue();
                    if("COMPLETED".equals(found.get(0).get("trang_thai")) && !"COMPLETED".equals(status)) throw new IllegalArgumentException("Không hạ trạng thái trận đã hoàn tất.");
                    exec(db,"UPDATE TranDau SET thoi_gian_thi_dau=?,trang_thai=?,ban_thang_chu_nha=?,ban_thang_khach=? WHERE id=?",kickoff,status,hg,ag,local);
                    if(remote.get("venue") instanceof String s && !s.isBlank()) exec(db,"UPDATE TranDau SET san_thi_dau=? WHERE id=?",s,local);
                }
                if(detail!=null) stats=saveAppearances(db,detail,local,clubs.get(66L),homeExternal==66);
                db.commit();
            } catch(SQLException | RuntimeException e) { db.rollback(); throw e; }
        }
        bindings.setProperty("match."+id(remote),""+local); return stats;
    }

    static boolean hasDetails(Map<String,Object> match,boolean home) {
        return "FINISHED".equals(match.get("status")) && "REGULAR".equals(obj(match.get("score")).get("duration"))
            && objects(obj(match.get(home?"homeTeam":"awayTeam")).get("lineup")).size()==11
            && match.get("goals") instanceof List<?> && match.get("bookings") instanceof List<?>
            && match.get("substitutions") instanceof List<?>;
    }
    record Appearance(long providerId,String name,int minutes,int goals,Integer assists,int yellow,int red) {}
    static List<Appearance> appearances(Map<String,Object> match,boolean home) {
        if(!hasDetails(match,home)) throw new IllegalArgumentException("Thiếu chi tiết thống kê.");
        long team=id(obj(match.get(home?"homeTeam":"awayTeam")));
        Map<Long,int[]> counts=new LinkedHashMap<>(); Map<Long,String> names=new HashMap<>();
        for(Map<String,Object> player:objects(obj(match.get(home?"homeTeam":"awayTeam")).get("lineup"))) {
            long id=id(player); if(counts.containsKey(id)) throw new IllegalArgumentException("Trùng đội hình.");
            counts.put(id,new int[]{0,90,0,0,0,0}); names.put(id,text(player,"name"));
        }
        List<Map<String,Object>> subs=new ArrayList<>(objects(match.get("substitutions")));
        subs.sort(Comparator.comparingInt(s->integer(s,"minute",0,90)));
        Set<Long> left=new HashSet<>();
        for(Map<String,Object> sub:subs) {
            if(id(obj(sub.get("team")))!=team) continue;
            int minute=integer(sub,"minute",0,90);
            long out=id(obj(sub.get("playerOut"))),in=id(obj(sub.get("playerIn")));
            if(!counts.containsKey(out) || counts.containsKey(in) || !left.add(out) || counts.get(out)[0]>minute) throw new IllegalArgumentException("Sai thay người.");
            counts.get(out)[1]=minute; counts.put(in,new int[]{minute,90,0,0,0,0}); names.put(in,text(obj(sub.get("playerIn")),"name"));
        }
        boolean assistsKnown=true;
        for(Map<String,Object> goal:objects(match.get("goals"))) {
            if(id(obj(goal.get("team")))!=team) continue;
            String type=text(goal,"type");
            if(type.equals("OWN")) continue;
            long scorer=id(obj(goal.get("scorer")));
            if(!counts.containsKey(scorer)) throw new IllegalArgumentException("Người ghi bàn không có lần ra sân.");
            counts.get(scorer)[2]++;
            if(!goal.containsKey("assist")) assistsKnown=false;
            if(goal.get("assist") instanceof Map<?,?>) {
                long assist=id(obj(goal.get("assist")));
                if(!counts.containsKey(assist)) throw new IllegalArgumentException("Người kiến tạo không ra sân.");
                counts.get(assist)[3]++;
            }
        }
        int recordedGoals=counts.values().stream().mapToInt(v->v[2]).sum();
        int ownGoals=0;
        for(Map<String,Object> goal:objects(match.get("goals"))) if(id(obj(goal.get("team")))==team && "OWN".equals(goal.get("type"))) ownGoals++;
        int finalGoals=integer(obj(obj(match.get("score")).get("fullTime")),home?"home":"away",0,255);
        if(recordedGoals+ownGoals!=finalGoals) throw new IllegalArgumentException("Danh sách bàn thắng chưa đầy đủ.");
        for(Map<String,Object> booking:objects(match.get("bookings"))) {
            if(id(obj(booking.get("team")))!=team) continue;
            long player=id(obj(booking.get("player")));
            if(!counts.containsKey(player)) continue; // Cards to unused substitutes do not create appearances.
            int[] v=counts.get(player); String card=text(booking,"card");
            if(card.equals("YELLOW")) v[4]++;
            else if(card.equals("YELLOW_RED") || card.equals("RED")) {
                if(card.equals("YELLOW_RED")) v[4]++;
                v[5]++; v[1]=Math.min(v[1],integer(booking,"minute",0,90));
            } else throw new IllegalArgumentException("Loại thẻ chưa hỗ trợ.");
        }
        List<Appearance> out=new ArrayList<>();
        for(var entry:counts.entrySet()) {
            int[] v=entry.getValue();
            if(v[1]<v[0] || v[4]>2 || v[5]>1) throw new IllegalArgumentException("Thống kê ngoài miền ER.");
            out.add(new Appearance(entry.getKey(),names.get(entry.getKey()),v[1]-v[0],v[2],assistsKnown?v[3]:null,v[4],v[5]));
        }
        return out;
    }
    private int saveAppearances(Connection db,Map<String,Object> detail,long match,long club,boolean home) throws SQLException {
        List<Appearance> appearances=appearances(detail,home);
        Map<Long,Long> ids=new HashMap<>();
        for(Appearance a:appearances) {
            Map<String,Object> person=people.get(a.providerId());
            if(person==null) throw new IllegalArgumentException("Chưa xác minh cầu thủ API.");
            if(id(person)!=a.providerId() || person.get("dateOfBirth")==null) throw new IllegalArgumentException("Thiếu định danh cầu thủ.");
            {
                List<Map<String,Object>> candidates=rows(db,"SELECT id,ho_ten FROM CauThu WHERE ngay_sinh=?",LocalDate.parse(text(person,"dateOfBirth")));
                String bound=bindings.getProperty("player."+a.providerId());
                List<Map<String,Object>> exact=candidates.stream().filter(p->bound!=null?bound.equals(p.get("id").toString()):normalize(p.get("ho_ten").toString()).equals(normalize(text(person,"name")))).toList();
                if(exact.size()!=1) throw new IllegalArgumentException("Cầu thủ cần đối chiếu ID, tên và ngày sinh.");
                ids.put(a.providerId(),((Number)exact.get(0).get("id")).longValue());
            }
        }
        if(new HashSet<>(ids.values()).size()!=ids.size()) throw new IllegalArgumentException("Hai ID API trỏ cùng cầu thủ.");
        {
            boolean assistsColumn=rows(db,"SHOW COLUMNS FROM CauThu_TranDau LIKE 'kien_tao'").size()==1;
            if(!assistsColumn) throw new IllegalArgumentException("Chưa áp dụng migration kiến tạo.");
                for(Map<String,Object> old:rows(db,"SELECT cau_thu_id FROM CauThu_TranDau WHERE tran_dau_id=? AND cau_lac_bo_id=?",match,club)) {
                    if(!ids.containsValue(((Number)old.get("cau_thu_id")).longValue())) throw new IllegalArgumentException("API mâu thuẫn lịch sử ra sân; cần đối chiếu.");
                }
                for(Appearance a:appearances) exec(db,"""
                    INSERT INTO CauThu_TranDau(tran_dau_id,cau_thu_id,cau_lac_bo_id,so_phut,ban_thang,kien_tao,the_vang,the_do)
                    VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE so_phut=?,ban_thang=?,kien_tao=COALESCE(?,kien_tao),the_vang=?,the_do=?
                    """,match,ids.get(a.providerId()),club,a.minutes(),a.goals(),a.assists(),a.yellow(),a.red(),a.minutes(),a.goals(),a.assists(),a.yellow(),a.red());
        }
        ids.forEach((external,local)->bindings.setProperty("player."+external,""+local));
        return appearances.size();
    }
    static String normalize(String value) { return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]",""); }
    private void saveBindings() throws IOException {
        Path tmp=bindingFile.resolveSibling(bindingFile.getFileName()+".tmp");
        try(Writer w=Files.newBufferedWriter(tmp)) { bindings.store(w,"Provider ID bindings, no credentials. Never edit statistics here."); }
        Files.move(tmp,bindingFile,StandardCopyOption.REPLACE_EXISTING);
    }
    static Map<String,Object> obj(Object value) {
        if(value==null) return Map.of();
        if(!(value instanceof Map<?,?>)) throw new IllegalArgumentException("Thiếu object API.");
        @SuppressWarnings("unchecked") Map<String,Object> out=(Map<String,Object>)value; return out;
    }
    static List<Map<String,Object>> objects(Object value) {
        if(!(value instanceof List<?> list)) return List.of();
        return list.stream().map(FootballData::obj).toList();
    }
    static String text(Map<String,Object> value,String key) {
        if(!(value.get(key) instanceof String s) || s.isBlank()) throw new IllegalArgumentException("Thiếu trường API: "+key);
        return s;
    }
    static int integer(Map<String,Object> value,String key,int min,int max) {
        if(!(value.get(key) instanceof Number number)) throw new IllegalArgumentException("Thiếu số API.");
        int n=new java.math.BigDecimal(number.toString()).intValueExact();
        if(n<min || n>max) throw new IllegalArgumentException("Số API ngoài miền."); return n;
    }
    static long id(Map<String,Object> value) {
        if(!(value.get("id") instanceof Number number)) throw new IllegalArgumentException("Thiếu ID API.");
        long n=new java.math.BigDecimal(number.toString()).longValueExact();
        if(n<=0) throw new IllegalArgumentException("ID API không hợp lệ."); return n;
    }
    private static List<Map<String,Object>> rows(Connection db,String sql,Object... args) throws SQLException {
        try(PreparedStatement s=db.prepareStatement(sql)) {
            for(int i=0;i<args.length;i++) s.setObject(i+1,args[i]);
            try(ResultSet r=s.executeQuery()) {
                List<Map<String,Object>> out=new ArrayList<>(); ResultSetMetaData meta=r.getMetaData();
                while(r.next()) { Map<String,Object> row=new LinkedHashMap<>(); for(int i=1;i<=meta.getColumnCount();i++) row.put(meta.getColumnLabel(i),r.getObject(i)); out.add(row); } return out;
            }
        }
    }
    private static void exec(Connection db,String sql,Object... args) throws SQLException {
        try(PreparedStatement s=db.prepareStatement(sql)) { for(int i=0;i<args.length;i++) s.setObject(i+1,args[i]); s.executeUpdate(); }
    }
    private static long one(Connection db,String sql,Object... args) throws SQLException {
        var rows=rows(db,sql,args); if(rows.size()!=1) throw new IllegalArgumentException("Ánh xạ không duy nhất."); return ((Number)rows.get(0).values().iterator().next()).longValue();
    }
    public void close() { worker.shutdownNow();standingsWorker.shutdownNow();detailWorker.shutdownNow(); }
}
