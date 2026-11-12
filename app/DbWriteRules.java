import java.sql.*;
import java.util.*;

/** Refuse manual writes against an old or partially installed set of DB guards. */
final class DbWriteRules {
    static final String APPEARANCE_IDENTITY="Khong doi dinh danh lan ra san lich su";
    static final Map<String,String> TRIGGERS=Map.ofEntries(
        Map.entry("trg_cauthu_bi","Ngay sinh khong duoc o tuong lai"),
        Map.entry("trg_cauthu_bu","Ngay sinh mau thuan voi lich su cau thu"),
        Map.entry("trg_hopdong_bi","Hop dong khong duoc truoc ngay sinh"),
        Map.entry("trg_hopdong_bu","Cap nhat hop dong lam mat hop le cua dang ky"),
        Map.entry("trg_hopdong_bd","Khong xoa hop dong dang bao dam dang ky"),
        Map.entry("trg_ctmg_bi","Dang ky can hop dong bao phu toan khoang"),
        Map.entry("trg_ctmg_bu","Dang ky can hop dong bao phu toan khoang"),
        Map.entry("trg_cttd_bi","Lan ra san khong duoc truoc ngay sinh"),
        Map.entry("trg_cttd_bu","Lan ra san khong duoc truoc ngay sinh"),
        Map.entry("trg_chanthuong_bi","Dot chan thuong da ton tai"),
        Map.entry("trg_chanthuong_bu","Dot chan thuong da ton tai"),
        Map.entry("trg_cn_bi","Chuyen nhuong khong duoc truoc ngay sinh"));
    static void requireInstalled(Connection db)throws SQLException {
        requireInstalled(db,true);
    }
    static void requireBaseline(Connection db)throws SQLException {
        requireInstalled(db,false);
    }
    private static void requireInstalled(Connection db,boolean identity)throws SQLException {
        Set<String> found=new HashSet<>();
        try(PreparedStatement s=db.prepareStatement("SELECT TRIGGER_NAME,ACTION_STATEMENT FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE()");ResultSet r=s.executeQuery()){
            while(r.next()){
                String name=r.getString(1),body=r.getString(2),marker=TRIGGERS.get(name);
                if(marker!=null&&body!=null&&body.contains(marker)
                        &&(!identity||!name.equals("trg_cttd_bu")||body.contains(APPEARANCE_IDENTITY)))found.add(name);
            }
        }
        boolean procedure=false;
        try(PreparedStatement s=db.prepareStatement("SELECT ROUTINE_DEFINITION FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA=DATABASE() AND ROUTINE_NAME='sp_hoan_tat_chuyen_nhuong' AND ROUTINE_TYPE='PROCEDURE'");ResultSet r=s.executeQuery()){
            if(r.next()){String body=r.getString(1);procedure=body!=null&&body.contains("Con dang ky khac phu thuoc hop dong nguon");}
        }
        if(found.size()!=TRIGGERS.size()||!procedure)
            throw new SQLException("DB chưa cập nhật kiểm tra dữ liệu. Dừng ứng dụng, chạy app\\update-db.bat rồi mở lại.","45000");
    }
}
