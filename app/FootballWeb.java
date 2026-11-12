import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;

/** Local Java 17 web application. One JDBC connection per request. */
public final class FootballWeb {
    private static String url, user, password;
    private static final String[] reports = new String[15];
    private static int port;
    private static Path assets;
    private static FootballData football;
    private static AdminAuth auth;

    public static void main(String[] args) throws Exception {
        url = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/csdl1_mu_2025_26?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh");
        user = System.getenv().getOrDefault("DB_USER", "root");
        password = System.getenv("DB_PASSWORD");
        if (password == null) {
            char[] entered = System.console() == null ? null : System.console().readPassword("Mat khau MySQL: ");
            password = entered == null ? "" : new String(entered);
            if (entered != null) Arrays.fill(entered, '\0');
        }
        port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        Path script = Path.of(args.length > 0 ? args[0] : "../db/05_reports.sql").toAbsolutePath().normalize();
        assets = Path.of(args.length > 1 ? args[1] : "web").toAbsolutePath().normalize();
        auth=AdminAuth.open(assets.getParent().getParent());
        String sql = Files.readString(script);
        for (int i = 1; i <= 15; i++) {
            int start = sql.indexOf("-- Q%02d.".formatted(i));
            int end = i == 15 ? sql.length() : sql.indexOf("-- Q%02d.".formatted(i + 1));
            if (start < 0 || end <= start) throw new IllegalArgumentException("Thieu truy van Q" + i);
            reports[i - 1] = sql.substring(sql.indexOf('\n', start) + 1, end).trim();
        }
        try (Connection db = connect()) { scalar(db, "SELECT id FROM CauLacBo WHERE la_clb_quan_ly=TRUE"); }
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 32);
        try { football = new FootballData(assets.getParent().getParent(), FootballWeb::connect); }
        catch (Exception e) { server.stop(0); throw e; }
        server.createContext("/", FootballWeb::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { football.close(); server.stop(1); }));
        server.start();
        football.start();
        System.out.println("Giao dien: http://127.0.0.1:" + port + " | Ctrl+C de dung.");
    }

    private static Connection connect() throws SQLException {
        Connection db = DriverManager.getConnection(url, user, password);
        try { update(db, "SET time_zone='+07:00'"); return db; }
        catch (SQLException e) { db.close(); throw e; }
    }

    private static void handle(HttpExchange x) throws IOException {
        try {
            String host = x.getRequestHeaders().getFirst("Host");
            if (!("127.0.0.1:" + port).equals(host) && !("localhost:" + port).equals(host)) {
                send(x, 403, Map.of("error", "Địa chỉ truy cập không hợp lệ.")); return;
            }
            String path = x.getRequestURI().getPath(), method = x.getRequestMethod();
            if (path.startsWith("/api/")) {
                String token=AdminAuth.cookieToken(x.getRequestHeaders().getFirst("Cookie"));
                if(path.equals("/api/session")&&method.equals("GET")) {
                    boolean signedIn=auth.authenticated(token);send(x,200,signedIn?Map.of("authenticated",true,"username",auth.username()):Map.of("authenticated",false));return;
                }
                if(path.equals("/api/login")||path.equals("/api/logout")) {
                    if(!method.equals("POST")){send(x,405,Map.of("error","Phương thức không hợp lệ."));return;}
                    if(!sameOriginWrite(x,host)){send(x,403,Map.of("error","Yêu cầu phải đến từ giao diện ứng dụng."));return;}
                    if(path.equals("/api/logout")){auth.logout(token);x.getResponseHeaders().set("Set-Cookie","csdl_session=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");send(x,200,Map.of("authenticated",false));return;}
                    String type=x.getRequestHeaders().getFirst("Content-Type");
                    if(type==null||!type.startsWith("application/x-www-form-urlencoded")){send(x,415,Map.of("error","Định dạng biểu mẫu không hợp lệ."));return;}
                    byte[] body=x.getRequestBody().readNBytes(4097);if(body.length>4096){send(x,413,Map.of("error","Biểu mẫu quá lớn."));return;}
                    Map<String,String> form=params(new String(body,StandardCharsets.UTF_8));
                    String user=form.getOrDefault("username",""),password=form.getOrDefault("password","");
                    if(user.length()>40||password.isEmpty()||password.length()>256){send(x,401,Map.of("error","Tên đăng nhập hoặc mật khẩu không đúng."));return;}
                    AdminAuth.Login login;
                    try{login=auth.login(user,password.toCharArray());}catch(java.security.GeneralSecurityException e){send(x,503,Map.of("error","Chưa thể đăng nhập. Thử lại sau."));return;}
                    if(login.token()==null){send(x,login.throttled()?429:401,Map.of("error",login.throttled()?"Đã thử quá nhiều lần. Vui lòng chờ 15 phút.":"Tên đăng nhập hoặc mật khẩu không đúng."));return;}
                    x.getResponseHeaders().set("Set-Cookie","csdl_session="+login.token()+"; Path=/; HttpOnly; SameSite=Strict; Max-Age=28800");
                    send(x,200,Map.of("authenticated",true,"username",auth.username()));return;
                }
                if(!auth.authenticated(token)){send(x,401,Map.of("error","Vui lòng đăng nhập."));return;}
                if (path.equals("/api/data") && method.equals("GET")) {
                    try (Connection db = connect()) { send(x, 200, workspace(db, params(x.getRequestURI().getRawQuery()))); }
                } else if(path.equals("/api/standings")&&method.equals("GET")) {
                    Map<String,String> p=params(x.getRequestURI().getRawQuery());int year=number(p,"year",2026,java.time.Year.now().getValue()+1);
                    football.requestStandings(year);send(x,200,football.standings(year));
                } else if(path.equals("/api/match-detail")&&method.equals("GET")) {
                    Map<String,String> p=params(x.getRequestURI().getRawQuery());int year=number(p,"year",2026,java.time.Year.now().getValue()+1);long match=number(p,"match",1,Integer.MAX_VALUE);
                    football.requestMatchDetails(year,match);send(x,200,football.matchDetails(year,match));
                } else if (path.equals("/api/action") && method.equals("POST")) {
                    if (!sameOriginWrite(x,host)) {
                        send(x, 403, Map.of("error", "Yêu cầu ghi phải đến từ giao diện ứng dụng.")); return;
                    }
                    String type = x.getRequestHeaders().getFirst("Content-Type");
                    if (type == null || !type.startsWith("application/x-www-form-urlencoded")) {
                        send(x, 415, Map.of("error", "Định dạng biểu mẫu không hợp lệ.")); return;
                    }
                    byte[] body = x.getRequestBody().readNBytes(16385);
                    if (body.length > 16384) { send(x, 413, Map.of("error", "Biểu mẫu quá lớn.")); return; }
                    try (Connection db = connect()) {
                        Map<String,String> form=params(new String(body, StandardCharsets.UTF_8));
                        action(db, form);
                        send(x, 200, Map.of("message", "Đã lưu dữ liệu."));
                    }
                } else send(x, 405, Map.of("error", "Đường dẫn hoặc phương thức không hợp lệ."));
            } else if (method.equals("GET") && (Set.of("/", "/index.html", "/style.css", "/app.js", "/motion.js", "/standings.js", "/details.js", "/dashboard.js", "/player-detail.js", "/football-assets.js", "/player-photos.js", "/photo-credits.html", "/icons.svg", "/club-logo.png").contains(path) || path.matches("/(photos|crests|flags)/[a-z0-9-]+\\.webp"))) {
                String file = path.equals("/") ? "index.html" : path.substring(1);
                String type = file.endsWith(".css") ? "text/css" : file.endsWith(".js") ? "text/javascript" : file.endsWith(".svg") ? "image/svg+xml" : file.endsWith(".png") ? "image/png" : file.endsWith(".webp") ? "image/webp" : "text/html";
                byte[] body = Files.readAllBytes(assets.resolve(file));
                headers(x);
                x.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
                x.sendResponseHeaders(200, body.length); x.getResponseBody().write(body);
            } else send(x, 404, Map.of("error", "Không tìm thấy trang."));
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            send(x, 400, Map.of("error", "Dữ liệu không hợp lệ: " + e.getMessage()));
        } catch (SQLException e) {
            String message = "Không thể kết nối hoặc xử lý dữ liệu. Kiểm tra máy chủ MySQL.";
            int status = 503;
            if ("45000".equals(e.getSQLState())) { message = e.getMessage(); status = 409; }
            else if ("40001".equals(e.getSQLState()) || e.getErrorCode()==1205) {
                message = "Dữ liệu đang được cập nhật đồng thời. Tải lại rồi thử lại thao tác."; status = 409;
            }
            else if (e.getSQLState() != null && (e.getSQLState().startsWith("23") || e.getSQLState().startsWith("22") || e.getErrorCode() == 3819)) {
                message = "Dữ liệu vi phạm ràng buộc: kiểm tra ngày, giá trị, số áo hoặc hồ sơ đã có lịch sử."; status = 409;
            }
            System.err.println("SQL " + e.getSQLState() + ": " + e.getMessage());
            send(x, status, Map.of("error", message));
        } catch (Exception e) {
            System.err.println(e.getMessage());
            send(x, 500, Map.of("error", "Không thể xử lý yêu cầu. Xem terminal để kiểm tra."));
        } finally { x.close(); }
    }

    private static boolean sameOriginWrite(HttpExchange x,String host) {
        String origin=x.getRequestHeaders().getFirst("Origin");
        return "1".equals(x.getRequestHeaders().getFirst("X-CSDL1"))&&(origin==null||origin.equals("http://"+host));
    }

    private static Map<String,Object> workspace(Connection db, Map<String,String> p) throws SQLException {
        long club = scalar(db, "SELECT id FROM CauLacBo WHERE la_clb_quan_ly=TRUE");
        int year=FootballData.validateYear(p.getOrDefault("year","").isBlank()?2026:number(p,"year",2026,java.time.Year.now().getValue()+1));
        List<Map<String,Object>> choices=new ArrayList<>();
        for(int y=2026;y<=java.time.Year.now().getValue()+1;y++) choices.add(Map.of("year",y,"code",FootballData.seasonCode(y)));
        List<Map<String,Object>> seasons=rows(db,"SELECT * FROM MuaGiai WHERE ma_mua_giai=?",FootballData.seasonCode(year));
        if(seasons.isEmpty()) return Map.of("ready",false,"contextYear",year,"seasonChoices",choices,"footballApi",football.snapshot(year));
        if(seasons.size()!=1) throw new IllegalArgumentException("Mùa giải không duy nhất.");
        Map<String,Object> current=seasons.get(0);
        long season=((Number)current.get("id")).longValue();
        LocalDate start=LocalDate.parse(current.get("ngay_bat_dau").toString()),end=LocalDate.parse(current.get("ngay_ket_thuc").toString());
        LocalDate date=p.getOrDefault("date","").isBlank()?LocalDate.now():date(p,"date");
        if(date.isBefore(start)) date=start;
        if(date.isAfter(end)) date=end;
        // All dashboard panels share a consistent snapshot, even during another write request.
        db.setAutoCommit(false);
        try {
            update(db, "SET @club_id=?", club); update(db, "SET @season_id=?", season); update(db, "SET @as_of=?", date);
            Map<String,Object> out = new LinkedHashMap<>(), result = new LinkedHashMap<>();
            out.put("clubId", club);
            out.put("ready",true); out.put("contextSeason",String.valueOf(season)); out.put("contextDate",date.toString());
            out.put("contextYear",year);out.put("seasonChoices",choices);
            out.put("footballApi", football.snapshot(year));
            out.put("clubs", rows(db, "SELECT id,ten,san_nha,la_clb_quan_ly FROM CauLacBo ORDER BY id"));
            out.put("seasons", seasons);
            out.put("positions", rows(db, "SELECT * FROM ViTri ORDER BY id"));
            out.put("players", rows(db, """
                SELECT c.*,v.ma_vi_tri,v.ten_hien_thi,
                  EXISTS(SELECT 1 FROM HopDong h WHERE h.cau_thu_id=c.id AND h.cau_lac_bo_id=?) owned
                FROM CauThu c JOIN ViTri v ON v.id=c.vi_tri_chinh_id ORDER BY c.ho_ten
                """, club));
            out.put("injuries", rows(db, """
                SELECT i.*,c.ho_ten FROM ChanThuong i JOIN CauThu c ON c.id=i.cau_thu_id
                WHERE EXISTS(SELECT 1 FROM HopDong h WHERE h.cau_thu_id=c.id AND h.cau_lac_bo_id=?)
                ORDER BY i.ngay_bat_dau_nghi DESC,i.id DESC
                """, club));
            out.put("appearances", rows(db, """
                SELECT a.*,c.ho_ten FROM CauThu_TranDau a JOIN CauThu c ON c.id=a.cau_thu_id
                JOIN TranDau m ON m.id=a.tran_dau_id WHERE a.cau_lac_bo_id=? AND m.mua_giai_id=?
                ORDER BY a.tran_dau_id,c.ho_ten
                """, club, season));
            boolean assistsColumn=!rows(db,"SHOW COLUMNS FROM CauThu_TranDau LIKE 'kien_tao'").isEmpty();
            for (int i = 1; i <= 15; i++) {
                String query=reports[i-1];
                if(i==6 && !assistsColumn) query=query.replace("a.kien_tao","NULL");
                result.put(String.valueOf(i), rows(db, query));
            }
            out.put("playerStats",playerStats(db,club,season,date,assistsColumn));
            out.put("squad",squad(db,club,season,date,current,result,assistsColumn));
            out.put("reports", result); db.commit(); return out;
        } catch (SQLException | RuntimeException e) { db.rollback(); throw e; }
    }

    private static List<Map<String,Object>> playerStats(Connection db,long club,long season,LocalDate date,boolean assists) throws SQLException {
        return rows(db,"""
            SELECT a.cau_thu_id id,COUNT(*) so_tran,SUM(a.so_phut) so_phut,SUM(a.ban_thang) ban_thang,
              %s kien_tao,SUM(a.the_vang) the_vang,SUM(a.the_do) the_do,
              SUM(a.so_phut=90) du_90_phut,ROUND(AVG(a.so_phut),1) phut_moi_tran
            FROM CauThu_TranDau a JOIN TranDau m ON m.id=a.tran_dau_id
            WHERE a.cau_lac_bo_id=? AND m.mua_giai_id=? AND m.trang_thai='COMPLETED'
              AND DATE(m.thoi_gian_thi_dau)<=? GROUP BY a.cau_thu_id
            """.formatted(assists?"CASE WHEN COUNT(a.kien_tao)=COUNT(*) THEN SUM(a.kien_tao) ELSE NULL END":"NULL"),club,season,date);
    }

    private static Map<String,Object> squad(Connection db,long club,long season,LocalDate date,Map<String,Object> current,Map<String,Object> result,boolean assists) throws SQLException {
        Map<String,Object> squad=new LinkedHashMap<>();
        List<Map<String,Object>> registered=FootballData.objects(result.get("1"));
        List<Map<String,Object>> reference=rows(db,"SELECT * FROM MuaGiai WHERE ma_mua_giai='2025-26'");
        boolean fallback=registered.isEmpty()&&!reference.isEmpty();
        squad.put("registrations",registered);squad.put("eligible",result.get("12"));squad.put("positions",result.get("14"));
        squad.put("sourceSeason",current.get("ma_mua_giai"));squad.put("referenceDate",date.toString());
        if(!reference.isEmpty()) {
            Map<String,Object> source=reference.get(0);
            long sourceSeason=((Number)source.get("id")).longValue();
            LocalDate sourceDate=LocalDate.parse(source.get("ngay_ket_thuc").toString());
            try {
                update(db,"SET @season_id=?",sourceSeason);update(db,"SET @as_of=?",sourceDate);
                if(fallback) {
                    squad.put("registrations",rows(db,reports[0]));squad.put("eligible",rows(db,reports[11]));
                    squad.put("positions",rows(db,reports[13]));
                    squad.put("sourceSeason",source.get("ma_mua_giai"));squad.put("referenceDate",sourceDate.toString());
                }
                // Profile statistics have their own source, independent of the roster's registrations.
                squad.put("matches",rows(db,reports[1]));
                squad.put("stats",playerStats(db,club,sourceSeason,sourceDate,assists));
                squad.put("appearances",rows(db,"SELECT a.* FROM CauThu_TranDau a JOIN TranDau m ON m.id=a.tran_dau_id WHERE a.cau_lac_bo_id=? AND m.mua_giai_id=? AND DATE(m.thoi_gian_thi_dau)<=?",club,sourceSeason,sourceDate));
                squad.put("statsSourceSeason",source.get("ma_mua_giai"));
            } finally {update(db,"SET @season_id=?",season);update(db,"SET @as_of=?",date);}
        }
        squad.put("reference",fallback);return squad;
    }

    private static void action(Connection db, Map<String,String> p) throws SQLException {
        if("season".equals(p.get("action"))) { football.request(number(p,"year",2026,java.time.Year.now().getValue()+1));return; }
        String action = required(p, "action");
        if(!Set.of("onboard","edit","contract","register","injury","recover","transfer","delete").contains(action))
            throw new IllegalArgumentException("Thao tác không được hỗ trợ.");
        DbWriteRules.requireInstalled(db);
        long club = scalar(db, "SELECT id FROM CauLacBo WHERE la_clb_quan_ly=TRUE");
        long season = number(p, "season", 1, Integer.MAX_VALUE);
        scalar(db, "SELECT id FROM MuaGiai WHERE id=? AND CAST(LEFT(ma_mua_giai,4) AS UNSIGNED)>=?", season,2026);
        // The transfer procedure owns its transaction. Other actions lock and write together.
        boolean transaction=!action.equals("transfer");
        if(transaction) db.setAutoCommit(false);
        try {
            if(transaction) {
                scalar(db,"SELECT id FROM MuaGiai WHERE id=? FOR UPDATE",season);
                if(Set.of("edit","contract","register","injury","delete").contains(action))
                    scalar(db,"SELECT id FROM CauThu WHERE id=? FOR UPDATE",number(p,"player",1,Integer.MAX_VALUE));
            }
            switch (action) {
                case "onboard" -> {
                    LocalDate birth = optionalDate(p,"birth"), start = date(p,"start"), end = date(p,"end");
                    if (birth!=null && (birth.isAfter(start) || birth.isAfter(LocalDate.now()))) throw new IllegalArgumentException("Ngày sinh phải trước ngày tiếp nhận và không ở tương lai.");
                    update(db,"INSERT INTO CauThu(ho_ten,ngay_sinh,quoc_tich,vi_tri_chinh_id) VALUES(?,?,?,?)", text(p,"name",150,false), birth, text(p,"nationality",80,true), number(p,"position",1,4));
                    long player = scalar(db,"SELECT LAST_INSERT_ID()");
                    update(db,"INSERT INTO HopDong(cau_thu_id,cau_lac_bo_id,ngay_bat_dau,ngay_ket_thuc) VALUES(?,?,?,?)",player,club,start,end);
                    register(db,player,club,season,start,end,optionalNumber(p,"shirt",1,99));
                }
                case "edit" -> {
                    long player = owned(db,p,club); LocalDate birth = optionalDate(p,"birth");
                    if (birth!=null && birth.isAfter(LocalDate.now())) throw new IllegalArgumentException("Ngày sinh không được ở tương lai.");
                    update(db,"UPDATE CauThu SET ho_ten=?,quoc_tich=?,ngay_sinh=?,vi_tri_chinh_id=? WHERE id=?",text(p,"name",150,false),text(p,"nationality",80,true),birth,number(p,"position",1,4),player);
                }
                case "contract" -> update(db,"INSERT INTO HopDong(cau_thu_id,cau_lac_bo_id,ngay_bat_dau,ngay_ket_thuc) VALUES(?,?,?,?)",owned(db,p,club),club,optionalDate(p,"start"),optionalDate(p,"end"));
                case "register" -> {
                    long player=owned(db,p,club); LocalDate start=date(p,"start"),end=date(p,"end");
                    register(db,player,club,season,start,end,optionalNumber(p,"shirt",1,99));
                }
                case "injury" -> update(db,"INSERT INTO ChanThuong(cau_thu_id,mo_ta,thoi_diem_xay_ra,ngay_bat_dau_nghi,ngay_du_kien_tro_lai,cam_thi_dau) VALUES(?,?,?,?,?,?)",owned(db,p,club),text(p,"description",255,false),LocalDateTime.parse(required(p,"occurred")),date(p,"start"),optionalDate(p,"expected"),p.containsKey("blocked")?number(p,"blocked",0,1):1);
                case "recover" -> {
                    long injury=number(p,"injury",1,Integer.MAX_VALUE);
                    long player=scalar(db,"SELECT cau_thu_id FROM ChanThuong WHERE id=?",injury);
                    scalar(db,"SELECT id FROM CauThu WHERE id=? FOR UPDATE",player);
                    scalar(db,"SELECT i.id FROM ChanThuong i WHERE i.id=? AND EXISTS(SELECT 1 FROM HopDong h WHERE h.cau_thu_id=i.cau_thu_id AND h.cau_lac_bo_id=?) FOR UPDATE",injury,club);
                    LocalDate recovery=date(p,"recovery");
                    if(recovery.isAfter(LocalDate.now()))throw new IllegalArgumentException("Ngày trở lại thực tế không được ở tương lai.");
                    update(db,"UPDATE ChanThuong SET ngay_thuc_te_tro_lai=? WHERE id=?",recovery,injury);
                }
                case "transfer" -> {
                    long player=number(p,"player",1,Integer.MAX_VALUE),from=number(p,"from",0,Integer.MAX_VALUE),to=number(p,"to",1,Integer.MAX_VALUE);
                    if (from!=club && to!=club) throw new IllegalArgumentException("Chuyển nhượng phải liên quan CLB quản lý.");
                    BigDecimal fee=p.getOrDefault("fee","").isBlank()?null:new BigDecimal(required(p,"fee"));
                    if(fee!=null && (fee.signum()<0 || fee.scale()>2 || fee.compareTo(new BigDecimal("9999999999999.99"))>0)) throw new IllegalArgumentException("Phí không âm, tối đa 13 chữ số nguyên và 2 chữ số thập phân.");
                    // The procedure owns commit/rollback; keep this connection in auto-commit mode.
                    try(CallableStatement call=db.prepareCall("{CALL sp_hoan_tat_chuyen_nhuong(?,?,?,?,?,?,?,?)}")) {
                        Object[] v={player,from==0?null:from,to,season,date(p,"start"),date(p,"end"),optionalNumber(p,"shirt",1,99),fee};
                        for(int i=0;i<v.length;i++) call.setObject(i+1,v[i]); call.execute();
                    }
                }
                case "delete" -> {
                    long player=number(p,"player",1,Integer.MAX_VALUE);
                    scalar(db,"SELECT id FROM CauThu WHERE id=?",player);
                    update(db,"DELETE FROM CauThu WHERE id=?",player);
                }
                default -> throw new IllegalArgumentException("Thao tác không được hỗ trợ.");
            }
            if(transaction)db.commit();
        } catch(SQLException|RuntimeException e) { if(transaction)db.rollback();throw e; }
        finally {if(transaction)db.setAutoCommit(true);}
    }

    private static void register(Connection db,long player,long club,long season,LocalDate start,LocalDate end,Integer shirt) throws SQLException {
        LocalDate seasonEnd=LocalDate.parse(rows(db,"SELECT DATE_ADD(ngay_ket_thuc,INTERVAL 1 DAY) end_date FROM MuaGiai WHERE id=?",season).get(0).get("end_date").toString());
        LocalDate clipped=end.isBefore(seasonEnd)?end:seasonEnd;
        scalar(db,"SELECT id FROM HopDong WHERE cau_thu_id=? AND cau_lac_bo_id=? AND COALESCE(ngay_bat_dau,'1000-01-01')<=? AND COALESCE(ngay_ket_thuc,'9999-12-31')>=? ORDER BY id LIMIT 1 FOR UPDATE",player,club,start,clipped);
        update(db,"INSERT INTO CauThu_MuaGiai(cau_thu_id,mua_giai_id,cau_lac_bo_id,ngay_bat_dau,ngay_ket_thuc,so_ao) VALUES(?,?,?,?,?,?)",player,season,club,start,clipped,shirt);
    }
    private static long owned(Connection db,Map<String,String> p,long club) throws SQLException {
        return scalar(db,"SELECT c.id FROM CauThu c WHERE c.id=? AND EXISTS(SELECT 1 FROM HopDong h WHERE h.cau_thu_id=c.id AND h.cau_lac_bo_id=?)",number(p,"player",1,Integer.MAX_VALUE),club);
    }
    private static PreparedStatement statement(Connection db,String sql,Object... values) throws SQLException {
        PreparedStatement s=db.prepareStatement(sql);
        for(int i=0;i<values.length;i++) s.setObject(i+1,values[i]); return s;
    }
    private static int update(Connection db,String sql,Object... values) throws SQLException {
        try(PreparedStatement s=statement(db,sql,values)) { return s.executeUpdate(); }
    }
    private static long scalar(Connection db,String sql,Object... values) throws SQLException {
        try(PreparedStatement s=statement(db,sql,values);ResultSet r=s.executeQuery()) {
            if(!r.next()) throw new IllegalArgumentException("Không tìm thấy bản ghi phù hợp với ngày, mùa hoặc CLB đã chọn.");
            return r.getLong(1);
        }
    }
    private static List<Map<String,Object>> rows(Connection db,String sql,Object... values) throws SQLException {
        try(PreparedStatement s=statement(db,sql,values);ResultSet r=s.executeQuery()) {
            List<Map<String,Object>> result=new ArrayList<>(); ResultSetMetaData m=r.getMetaData();
            while(r.next()) {
                Map<String,Object> row=new LinkedHashMap<>();
                for(int i=1;i<=m.getColumnCount();i++) row.put(m.getColumnLabel(i),r.getObject(i));
                result.add(row);
            } return result;
        }
    }
    private static String required(Map<String,String> p,String key) {
        String v=p.getOrDefault(key,"").trim();
        if(v.isEmpty()) throw new IllegalArgumentException("Thiếu trường " + key);
        if(v.length()>500) throw new IllegalArgumentException("Trường " + key + " quá dài."); return v;
    }
    private static int number(Map<String,String> p,String key,int min,int max) {
        int v=Integer.parseInt(required(p,key));
        if(v<min || v>max) throw new IllegalArgumentException(key + " phải trong " + min + "–" + max); return v;
    }
    private static LocalDate date(Map<String,String> p,String key) { return LocalDate.parse(required(p,key)); }
    private static LocalDate optionalDate(Map<String,String> p,String key) {return p.getOrDefault(key,"").isBlank()?null:date(p,key);}
    private static Integer optionalNumber(Map<String,String> p,String key,int min,int max) {return p.getOrDefault(key,"").isBlank()?null:number(p,key,min,max);}
    private static String text(Map<String,String> p,String key,int max,boolean optional) {
        String v=p.getOrDefault(key,"").trim();if(optional&&v.isEmpty())return null;
        if(v.isEmpty()||v.codePointCount(0,v.length())>max)throw new IllegalArgumentException(key+" phải có 1–"+max+" ký tự.");return v;
    }
    private static Map<String,String> params(String body) {
        Map<String,String> p=new HashMap<>(); if(body==null || body.isEmpty()) return p;
        for(String pair:body.split("&")) {
            String[] kv=pair.split("=",2);
            String key=URLDecoder.decode(kv[0],StandardCharsets.UTF_8),value=kv.length==2?URLDecoder.decode(kv[1],StandardCharsets.UTF_8):"";
            if(p.putIfAbsent(key,value)!=null) throw new IllegalArgumentException("Trường trùng lặp.");
        } return p;
    }
    private static void headers(HttpExchange x) {
        x.getResponseHeaders().set("Cache-Control","no-store");
        x.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        x.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
    }
    private static void send(HttpExchange x,int status,Object value) throws IOException {
        headers(x); x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        byte[] bytes=json(value).getBytes(StandardCharsets.UTF_8);
        x.sendResponseHeaders(status,bytes.length); x.getResponseBody().write(bytes);
    }
    private static String json(Object v) {
        if(v==null) return "null";
        if(v instanceof Number || v instanceof Boolean) return v.toString();
        if(v instanceof Map<?,?> m) {
            StringJoiner j=new StringJoiner(",","{","}"); m.forEach((k,x)->j.add(json(k.toString())+":"+json(x))); return j.toString();
        }
        if(v instanceof Iterable<?> list) { StringJoiner j=new StringJoiner(",","[","]"); for(Object x:list) j.add(json(x)); return j.toString(); }
        StringBuilder b=new StringBuilder("\"");
        for(char c:v.toString().toCharArray()) {
            switch(c) {
                case '"' -> b.append("\\\""); case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n"); case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t");
                default -> { if(c<32) b.append("\\u%04x".formatted((int)c)); else b.append(c); }
            }
        } return b.append('"').toString();
    }
}
