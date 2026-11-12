import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

/** Non-destructive routine migration; --check never executes DDL or DML. */
public final class UpdateDatabase {
    static final Map<String,String> CHECKS=new LinkedHashMap<>();
    static {
        CHECKS.put("Dang ky thieu hop dong bao phu", "SELECT r.id FROM CauThu_MuaGiai r WHERE NOT EXISTS(SELECT 1 FROM HopDong h WHERE h.cau_thu_id=r.cau_thu_id AND h.cau_lac_bo_id=r.cau_lac_bo_id AND COALESCE(h.ngay_bat_dau,'1000-01-01')<=r.ngay_bat_dau AND r.ngay_ket_thuc<=COALESCE(h.ngay_ket_thuc,'9999-12-31'))");
        CHECKS.put("Ngay sinh mau thuan lich su", "SELECT c.id FROM CauThu c WHERE c.ngay_sinh>CURRENT_DATE OR EXISTS(SELECT 1 FROM HopDong h WHERE h.cau_thu_id=c.id AND (h.ngay_bat_dau<c.ngay_sinh OR h.ngay_ket_thuc<=c.ngay_sinh)) OR EXISTS(SELECT 1 FROM CauThu_MuaGiai r WHERE r.cau_thu_id=c.id AND r.ngay_bat_dau<c.ngay_sinh) OR EXISTS(SELECT 1 FROM CauThu_TranDau a JOIN TranDau m ON m.id=a.tran_dau_id WHERE a.cau_thu_id=c.id AND DATE(m.thoi_gian_thi_dau)<c.ngay_sinh) OR EXISTS(SELECT 1 FROM ChanThuong i WHERE i.cau_thu_id=c.id AND (DATE(i.thoi_diem_xay_ra)<c.ngay_sinh OR i.ngay_bat_dau_nghi<c.ngay_sinh)) OR EXISTS(SELECT 1 FROM ChuyenNhuong t WHERE t.cau_thu_id=c.id AND t.ngay_hieu_luc<c.ngay_sinh)");
        CHECKS.put("Ngay tro lai thuc te o tuong lai", "SELECT id FROM ChanThuong WHERE ngay_thuc_te_tro_lai>CURRENT_DATE");
        CHECKS.put("Hop dong chong lap", "SELECT a.id FROM HopDong a JOIN HopDong b ON a.id<b.id AND a.cau_thu_id=b.cau_thu_id AND COALESCE(a.ngay_bat_dau,'1000-01-01')<COALESCE(b.ngay_ket_thuc,'9999-12-31') AND COALESCE(b.ngay_bat_dau,'1000-01-01')<COALESCE(a.ngay_ket_thuc,'9999-12-31')");
        CHECKS.put("Dang ky chong lap / trung so ao", "SELECT a.id FROM CauThu_MuaGiai a JOIN CauThu_MuaGiai b ON a.id<b.id AND a.mua_giai_id=b.mua_giai_id AND a.ngay_bat_dau<b.ngay_ket_thuc AND b.ngay_bat_dau<a.ngay_ket_thuc AND (a.cau_thu_id=b.cau_thu_id OR (a.cau_lac_bo_id=b.cau_lac_bo_id AND a.so_ao=b.so_ao))");
        CHECKS.put("Chan thuong cam thi dau mau thuan lan ra san", "SELECT i.id FROM ChanThuong i JOIN CauThu_TranDau a ON a.cau_thu_id=i.cau_thu_id JOIN TranDau m ON m.id=a.tran_dau_id WHERE i.cam_thi_dau=TRUE AND i.ngay_bat_dau_nghi<=DATE(m.thoi_gian_thi_dau) AND (i.ngay_thuc_te_tro_lai IS NULL OR DATE(m.thoi_gian_thi_dau)<i.ngay_thuc_te_tro_lai)");
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1||!Set.of("--check","--apply").contains(args[0]))throw new IllegalArgumentException("Dung --check hoac --apply");
        String url=System.getenv().getOrDefault("DB_URL","jdbc:mysql://localhost:3306/csdl1_mu_2025_26?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh");
        String user=System.getenv().getOrDefault("DB_USER","root"),password=System.getenv("DB_PASSWORD");
        if(password==null){
            Console console=System.console();if(console==null)throw new IllegalStateException("Mo update-db.bat trong Terminal de nhap mat khau MySQL.");
            char[] entered=console.readPassword("Mat khau MySQL: ");if(entered==null)return;password=new String(entered);Arrays.fill(entered,'\0');
        }
        try(Connection db=DriverManager.getConnection(url,user,password)){
            migrateCurrent(db,Path.of("..","db","migrations"),Path.of("build"),args[0].equals("--apply"));
        }
    }
    static void migrateCurrent(Connection db,Path directory,Path backups,boolean apply)throws Exception {
        boolean baseline=true;
        try{DbWriteRules.requireBaseline(db);}catch(SQLException e){if(!"45000".equals(e.getSQLState()))throw e;baseline=false;}
        migrate(db,directory.resolve(baseline?"003_appearance_identity.sql":"002_write_guards.sql"),backups,apply);
    }
    static void migrate(Connection db,Path migration,Path backups,boolean apply)throws Exception {
        execute(db,"SET time_zone='+07:00'");
        String catalog=db.getCatalog();if(catalog==null||!catalog.matches("[a-zA-Z0-9_]+"))throw new IllegalArgumentException("Chua chon schema hop le");
        System.out.println("Schema: "+catalog+" | "+(apply?"Cap nhat trigger/procedure":"Chi kiem tra"));
        System.out.println("Migration: "+migration.getFileName());
        List<String> failures=preflight(db);
        if(!failures.isEmpty())throw new IllegalStateException("Dung cap nhat; giu nguyen du lieu va trigger. Can doi chieu:\n"+String.join("\n",failures));
        System.out.println("Du lieu hien co: dat kiem tra.");
        if(!apply)return;
        List<String> commands=statements(Files.readString(migration));
        Pattern object=Pattern.compile("CREATE (TRIGGER|PROCEDURE) (\\w+)",Pattern.CASE_INSENSITIVE);
        List<String> restore=new ArrayList<>(),drops=new ArrayList<>();
        for(String command:commands){
            Matcher m=object.matcher(command);if(!m.find())continue;
            String kind=m.group(1).toUpperCase(Locale.ROOT),name=m.group(2);
            drops.add("DROP "+kind+" IF EXISTS "+name);
            try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SHOW CREATE "+kind+" "+name)){
                if(r.next()){
                    restore.add("SET SESSION sql_mode='"+r.getString(2).replace("'","''")+"'");
                    restore.add(r.getString(3));
                }
            }catch(SQLException e){if(e.getErrorCode()!=1360&&e.getErrorCode()!=1305)throw e;}
        }
        if(drops.size()==1&&drops.get(0).equals("DROP TRIGGER IF EXISTS trg_cttd_bu"))DbWriteRules.requireBaseline(db);
        else if(drops.size()<20)throw new IllegalStateException("Migration thieu trigger/procedure; chua thay doi DB");
        String oldMode;
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT @@SESSION.sql_mode")){r.next();oldMode=r.getString(1);}
        restore.add("SET SESSION sql_mode='"+oldMode.replace("'","''")+"'");
        Files.createDirectories(backups);
        Path backup=backups.resolve("db-routines-"+catalog+"-"+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))+"-"+UUID.randomUUID()+".sql").toAbsolutePath();
        List<String> rollback=new ArrayList<>(drops);rollback.addAll(restore);
        StringBuilder saved=new StringBuilder("-- Backup routines only; business data unchanged.\nUSE `"+catalog+"`;\nDELIMITER $$\n");
        for(String sql:rollback)saved.append(sql).append("$$\n");saved.append("DELIMITER ;\n");
        Files.writeString(backup,saved,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        System.out.println("Ban sao trigger/procedure: "+backup);
        try {
            for(String sql:commands)if(!sql.stripLeading().toUpperCase(Locale.ROOT).startsWith("USE "))execute(db,sql);
            DbWriteRules.requireInstalled(db);
            System.out.println("Da cap nhat. Khong thay bang, cot hoac du lieu. Co the mo lai run.bat.");
        }catch(Exception error){
            // MySQL routine DDL commits implicitly; restore definitions explicitly on failure.
            boolean restored=true;
            for(String sql:rollback)try{execute(db,sql);}catch(SQLException e){error.addSuppressed(e);restored=false;}
            throw new IllegalStateException((restored?"Cap nhat loi; da khoi phuc trigger/procedure cu. ":"Cap nhat loi; can khoi phuc tu ban sao. ")+backup,error);
        }
    }
    static List<String> preflight(Connection db)throws SQLException {
        List<String> failures=new ArrayList<>();
        for(var check:CHECKS.entrySet())try(Statement s=db.createStatement();ResultSet r=s.executeQuery(check.getValue()+" LIMIT 21")){
            List<Long> ids=new ArrayList<>();while(r.next())ids.add(r.getLong(1));
            if(!ids.isEmpty())failures.add(check.getKey()+"; ID: "+ids+(ids.size()==21?" (con co the nhieu hon)":""));
        }
        return failures;
    }
    static List<String> statements(String script){
        List<String> result=new ArrayList<>();String delimiter=";";StringBuilder pending=new StringBuilder();
        for(String line:script.split("\\R")){
            String trimmed=line.strip();if(trimmed.isEmpty()||trimmed.startsWith("--"))continue;
            if(trimmed.startsWith("DELIMITER ")){if(!pending.toString().isBlank())throw new IllegalArgumentException("SQL chua ket thuc");delimiter=trimmed.substring(10).strip();continue;}
            pending.append(line).append('\n');
            if(trimmed.endsWith(delimiter)){
                String sql=pending.toString().strip();result.add(sql.substring(0,sql.length()-delimiter.length()).strip());pending.setLength(0);
            }
        }
        if(!pending.toString().isBlank())throw new IllegalArgumentException("SQL chua ket thuc");return result;
    }
    static void execute(Connection db,String sql)throws SQLException{try(Statement s=db.createStatement()){s.execute(sql);}}
}
