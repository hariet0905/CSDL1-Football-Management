USE csdl1_mu_2025_26;
SET NAMES utf8mb4;
SET time_zone = '+07:00';

DELIMITER $$
CREATE TRIGGER trg_cauthu_bi BEFORE INSERT ON CauThu FOR EACH ROW BEGIN
 IF NEW.ngay_sinh>CURRENT_DATE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Ngay sinh khong duoc o tuong lai'; END IF;
END$$
CREATE TRIGGER trg_cauthu_bu BEFORE UPDATE ON CauThu FOR EACH ROW BEGIN
 IF NEW.ngay_sinh>CURRENT_DATE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Ngay sinh khong duoc o tuong lai'; END IF;
 IF NEW.ngay_sinh IS NOT NULL AND NOT(NEW.ngay_sinh<=>OLD.ngay_sinh) AND (
  EXISTS(SELECT 1 FROM HopDong h WHERE h.cau_thu_id=OLD.id AND (h.ngay_bat_dau<NEW.ngay_sinh OR h.ngay_ket_thuc<=NEW.ngay_sinh)) OR
  EXISTS(SELECT 1 FROM CauThu_MuaGiai r WHERE r.cau_thu_id=OLD.id AND r.ngay_bat_dau<NEW.ngay_sinh) OR
  EXISTS(SELECT 1 FROM CauThu_TranDau a JOIN TranDau m ON m.id=a.tran_dau_id WHERE a.cau_thu_id=OLD.id AND DATE(m.thoi_gian_thi_dau)<NEW.ngay_sinh) OR
  EXISTS(SELECT 1 FROM ChanThuong i WHERE i.cau_thu_id=OLD.id AND (DATE(i.thoi_diem_xay_ra)<NEW.ngay_sinh OR i.ngay_bat_dau_nghi<NEW.ngay_sinh)) OR
  EXISTS(SELECT 1 FROM ChuyenNhuong t WHERE t.cau_thu_id=OLD.id AND t.ngay_hieu_luc<NEW.ngay_sinh)
 ) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Ngay sinh mau thuan voi lich su cau thu'; END IF;
END$$
CREATE TRIGGER trg_hopdong_bi BEFORE INSERT ON HopDong FOR EACH ROW BEGIN
 DECLARE v_lock BIGINT UNSIGNED; DECLARE v_birth DATE;
 SELECT id,ngay_sinh INTO v_lock,v_birth FROM CauThu WHERE id=NEW.cau_thu_id FOR UPDATE;
 IF v_birth IS NOT NULL AND (NEW.ngay_bat_dau<v_birth OR NEW.ngay_ket_thuc<=v_birth) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Hop dong khong duoc truoc ngay sinh'; END IF;
 IF EXISTS(SELECT 1 FROM HopDong h WHERE h.cau_thu_id=NEW.cau_thu_id AND COALESCE(NEW.ngay_bat_dau,'1000-01-01')<COALESCE(h.ngay_ket_thuc,'9999-12-31') AND COALESCE(h.ngay_bat_dau,'1000-01-01')<COALESCE(NEW.ngay_ket_thuc,'9999-12-31')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Hop dong cua cau thu bi chong lap'; END IF;
END$$
CREATE TRIGGER trg_hopdong_bu BEFORE UPDATE ON HopDong FOR EACH ROW BEGIN
 DECLARE v_lock BIGINT UNSIGNED; DECLARE v_birth DATE;
 SELECT id,ngay_sinh INTO v_lock,v_birth FROM CauThu WHERE id=NEW.cau_thu_id FOR UPDATE;
 IF v_birth IS NOT NULL AND (NEW.ngay_bat_dau<v_birth OR NEW.ngay_ket_thuc<=v_birth) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Hop dong khong duoc truoc ngay sinh'; END IF;
 IF EXISTS(SELECT 1 FROM HopDong h WHERE h.id<>OLD.id AND h.cau_thu_id=NEW.cau_thu_id AND COALESCE(NEW.ngay_bat_dau,'1000-01-01')<COALESCE(h.ngay_ket_thuc,'9999-12-31') AND COALESCE(h.ngay_bat_dau,'1000-01-01')<COALESCE(NEW.ngay_ket_thuc,'9999-12-31')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Hop dong cua cau thu bi chong lap'; END IF;
 IF EXISTS(SELECT 1 FROM CauThu_TranDau a JOIN TranDau m ON m.id=a.tran_dau_id WHERE a.cau_thu_id=OLD.cau_thu_id AND a.cau_lac_bo_id=OLD.cau_lac_bo_id AND COALESCE(OLD.ngay_bat_dau,'1000-01-01')<=DATE(m.thoi_gian_thi_dau) AND DATE(m.thoi_gian_thi_dau)<COALESCE(OLD.ngay_ket_thuc,'9999-12-31') AND NOT(NEW.cau_thu_id=OLD.cau_thu_id AND NEW.cau_lac_bo_id=OLD.cau_lac_bo_id AND COALESCE(NEW.ngay_bat_dau,'1000-01-01')<=DATE(m.thoi_gian_thi_dau) AND DATE(m.thoi_gian_thi_dau)<COALESCE(NEW.ngay_ket_thuc,'9999-12-31'))) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cap nhat hop dong lam mat tinh hop le cua lan ra san'; END IF;
 IF EXISTS(SELECT 1 FROM CauThu_MuaGiai r WHERE r.cau_thu_id=OLD.cau_thu_id AND r.cau_lac_bo_id=OLD.cau_lac_bo_id AND COALESCE(OLD.ngay_bat_dau,'1000-01-01')<=r.ngay_bat_dau AND r.ngay_ket_thuc<=COALESCE(OLD.ngay_ket_thuc,'9999-12-31') AND NOT(NEW.cau_thu_id=OLD.cau_thu_id AND NEW.cau_lac_bo_id=OLD.cau_lac_bo_id AND COALESCE(NEW.ngay_bat_dau,'1000-01-01')<=r.ngay_bat_dau AND r.ngay_ket_thuc<=COALESCE(NEW.ngay_ket_thuc,'9999-12-31')) AND NOT EXISTS(SELECT 1 FROM HopDong h WHERE h.id<>OLD.id AND h.cau_thu_id=r.cau_thu_id AND h.cau_lac_bo_id=r.cau_lac_bo_id AND COALESCE(h.ngay_bat_dau,'1000-01-01')<=r.ngay_bat_dau AND r.ngay_ket_thuc<=COALESCE(h.ngay_ket_thuc,'9999-12-31'))) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cap nhat hop dong lam mat hop le cua dang ky'; END IF;
END$$
CREATE TRIGGER trg_hopdong_bd BEFORE DELETE ON HopDong FOR EACH ROW BEGIN
 DECLARE v_lock BIGINT UNSIGNED;
 SELECT id INTO v_lock FROM CauThu WHERE id=OLD.cau_thu_id FOR UPDATE;
 IF EXISTS(SELECT 1 FROM CauThu_MuaGiai r WHERE r.cau_thu_id=OLD.cau_thu_id AND r.cau_lac_bo_id=OLD.cau_lac_bo_id AND COALESCE(OLD.ngay_bat_dau,'1000-01-01')<=r.ngay_bat_dau AND r.ngay_ket_thuc<=COALESCE(OLD.ngay_ket_thuc,'9999-12-31') AND NOT EXISTS(SELECT 1 FROM HopDong h WHERE h.id<>OLD.id AND h.cau_thu_id=r.cau_thu_id AND h.cau_lac_bo_id=r.cau_lac_bo_id AND COALESCE(h.ngay_bat_dau,'1000-01-01')<=r.ngay_bat_dau AND r.ngay_ket_thuc<=COALESCE(h.ngay_ket_thuc,'9999-12-31'))) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Khong xoa hop dong dang bao dam dang ky'; END IF;
 IF EXISTS(SELECT 1 FROM CauThu_TranDau a JOIN TranDau m ON m.id=a.tran_dau_id WHERE a.cau_thu_id=OLD.cau_thu_id AND a.cau_lac_bo_id=OLD.cau_lac_bo_id AND COALESCE(OLD.ngay_bat_dau,'1000-01-01')<=DATE(m.thoi_gian_thi_dau) AND DATE(m.thoi_gian_thi_dau)<COALESCE(OLD.ngay_ket_thuc,'9999-12-31')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Khong duoc xoa hop dong dang chung minh lan ra san lich su'; END IF;
END$$

CREATE TRIGGER trg_ctmg_bi BEFORE INSERT ON CauThu_MuaGiai FOR EACH ROW BEGIN
 DECLARE v_start DATE; DECLARE v_end DATE; DECLARE v_lock BIGINT UNSIGNED; DECLARE v_contract BIGINT UNSIGNED DEFAULT NULL; DECLARE v_birth DATE;
 SELECT id INTO v_lock FROM MuaGiai WHERE id=NEW.mua_giai_id FOR UPDATE;
 SELECT id,ngay_sinh INTO v_lock,v_birth FROM CauThu WHERE id=NEW.cau_thu_id FOR UPDATE;
 IF v_birth IS NOT NULL AND NEW.ngay_bat_dau<v_birth THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Dang ky khong duoc truoc ngay sinh'; END IF;
 SELECT id INTO v_contract FROM HopDong h WHERE h.cau_thu_id=NEW.cau_thu_id AND h.cau_lac_bo_id=NEW.cau_lac_bo_id AND COALESCE(h.ngay_bat_dau,'1000-01-01')<=NEW.ngay_bat_dau AND NEW.ngay_ket_thuc<=COALESCE(h.ngay_ket_thuc,'9999-12-31') ORDER BY h.id LIMIT 1 FOR UPDATE;
 IF v_contract IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Dang ky can hop dong bao phu toan khoang'; END IF;
 SELECT ngay_bat_dau,DATE_ADD(ngay_ket_thuc,INTERVAL 1 DAY) INTO v_start,v_end FROM MuaGiai WHERE id=NEW.mua_giai_id;
 IF NEW.ngay_bat_dau<v_start OR NEW.ngay_ket_thuc>v_end THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Khoang dang ky phai nam trong mua giai'; END IF;
 IF EXISTS(SELECT 1 FROM CauThu_MuaGiai r WHERE r.cau_thu_id=NEW.cau_thu_id AND r.mua_giai_id=NEW.mua_giai_id AND NEW.ngay_bat_dau<r.ngay_ket_thuc AND r.ngay_bat_dau<NEW.ngay_ket_thuc) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Dang ky mua giai cua cau thu bi chong lap'; END IF;
 IF NEW.so_ao IS NOT NULL AND EXISTS(SELECT 1 FROM CauThu_MuaGiai r WHERE r.mua_giai_id=NEW.mua_giai_id AND r.cau_lac_bo_id=NEW.cau_lac_bo_id AND r.so_ao=NEW.so_ao AND NEW.ngay_bat_dau<r.ngay_ket_thuc AND r.ngay_bat_dau<NEW.ngay_ket_thuc) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='So ao bi trung trong cung CLB va khoang thoi gian'; END IF;
END$$
CREATE TRIGGER trg_ctmg_bu BEFORE UPDATE ON CauThu_MuaGiai FOR EACH ROW BEGIN
 DECLARE v_start DATE; DECLARE v_end DATE; DECLARE v_lock BIGINT UNSIGNED; DECLARE v_contract BIGINT UNSIGNED DEFAULT NULL; DECLARE v_birth DATE;
 SELECT id INTO v_lock FROM MuaGiai WHERE id=NEW.mua_giai_id FOR UPDATE;
 SELECT id,ngay_sinh INTO v_lock,v_birth FROM CauThu WHERE id=NEW.cau_thu_id FOR UPDATE;
 IF v_birth IS NOT NULL AND NEW.ngay_bat_dau<v_birth THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Dang ky khong duoc truoc ngay sinh'; END IF;
 SELECT id INTO v_contract FROM HopDong h WHERE h.cau_thu_id=NEW.cau_thu_id AND h.cau_lac_bo_id=NEW.cau_lac_bo_id AND COALESCE(h.ngay_bat_dau,'1000-01-01')<=NEW.ngay_bat_dau AND NEW.ngay_ket_thuc<=COALESCE(h.ngay_ket_thuc,'9999-12-31') ORDER BY h.id LIMIT 1 FOR UPDATE;
 IF v_contract IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Dang ky can hop dong bao phu toan khoang'; END IF;
 SELECT ngay_bat_dau,DATE_ADD(ngay_ket_thuc,INTERVAL 1 DAY) INTO v_start,v_end FROM MuaGiai WHERE id=NEW.mua_giai_id;
 IF NEW.ngay_bat_dau<v_start OR NEW.ngay_ket_thuc>v_end THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Khoang dang ky phai nam trong mua giai'; END IF;
 IF EXISTS(SELECT 1 FROM CauThu_MuaGiai r WHERE r.id<>OLD.id AND r.cau_thu_id=NEW.cau_thu_id AND r.mua_giai_id=NEW.mua_giai_id AND NEW.ngay_bat_dau<r.ngay_ket_thuc AND r.ngay_bat_dau<NEW.ngay_ket_thuc) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Dang ky mua giai cua cau thu bi chong lap'; END IF;
 IF NEW.so_ao IS NOT NULL AND EXISTS(SELECT 1 FROM CauThu_MuaGiai r WHERE r.id<>OLD.id AND r.mua_giai_id=NEW.mua_giai_id AND r.cau_lac_bo_id=NEW.cau_lac_bo_id AND r.so_ao=NEW.so_ao AND NEW.ngay_bat_dau<r.ngay_ket_thuc AND r.ngay_bat_dau<NEW.ngay_ket_thuc) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='So ao bi trung trong cung CLB va khoang thoi gian'; END IF;
 IF EXISTS(SELECT 1 FROM CauThu_TranDau a JOIN TranDau m ON m.id=a.tran_dau_id WHERE a.cau_thu_id=OLD.cau_thu_id AND a.cau_lac_bo_id=OLD.cau_lac_bo_id AND m.mua_giai_id=OLD.mua_giai_id AND OLD.ngay_bat_dau<=DATE(m.thoi_gian_thi_dau) AND DATE(m.thoi_gian_thi_dau)<OLD.ngay_ket_thuc AND NOT(NEW.cau_thu_id=OLD.cau_thu_id AND NEW.cau_lac_bo_id=OLD.cau_lac_bo_id AND NEW.mua_giai_id=OLD.mua_giai_id AND NEW.ngay_bat_dau<=DATE(m.thoi_gian_thi_dau) AND DATE(m.thoi_gian_thi_dau)<NEW.ngay_ket_thuc)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cap nhat dang ky lam mat tinh hop le cua lan ra san'; END IF;
END$$
CREATE TRIGGER trg_ctmg_bd BEFORE DELETE ON CauThu_MuaGiai FOR EACH ROW BEGIN
 IF EXISTS(SELECT 1 FROM CauThu_TranDau a JOIN TranDau m ON m.id=a.tran_dau_id WHERE a.cau_thu_id=OLD.cau_thu_id AND a.cau_lac_bo_id=OLD.cau_lac_bo_id AND m.mua_giai_id=OLD.mua_giai_id AND OLD.ngay_bat_dau<=DATE(m.thoi_gian_thi_dau) AND DATE(m.thoi_gian_thi_dau)<OLD.ngay_ket_thuc) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Khong duoc xoa dang ky dang chung minh lan ra san lich su'; END IF;
END$$

CREATE TRIGGER trg_trandau_bi BEFORE INSERT ON TranDau FOR EACH ROW BEGIN
 IF NOT EXISTS(SELECT 1 FROM MuaGiai s WHERE s.id=NEW.mua_giai_id AND DATE(NEW.thoi_gian_thi_dau) BETWEEN s.ngay_bat_dau AND s.ngay_ket_thuc) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Ngay tran dau phai nam trong mua giai'; END IF;
END$$
CREATE TRIGGER trg_trandau_bu BEFORE UPDATE ON TranDau FOR EACH ROW BEGIN
 IF NEW.trang_thai<>'COMPLETED' AND EXISTS(SELECT 1 FROM CauThu_TranDau WHERE tran_dau_id=OLD.id) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Tran co thong ke phai giu trang thai COMPLETED'; END IF;
 IF NOT EXISTS(SELECT 1 FROM MuaGiai s WHERE s.id=NEW.mua_giai_id AND DATE(NEW.thoi_gian_thi_dau) BETWEEN s.ngay_bat_dau AND s.ngay_ket_thuc) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Ngay tran dau phai nam trong mua giai'; END IF;
 IF EXISTS(SELECT 1 FROM CauThu_TranDau a WHERE a.tran_dau_id=OLD.id AND (a.cau_lac_bo_id NOT IN(NEW.clb_chu_nha_id,NEW.clb_khach_id) OR NOT EXISTS(SELECT 1 FROM CauThu_MuaGiai r WHERE r.cau_thu_id=a.cau_thu_id AND r.cau_lac_bo_id=a.cau_lac_bo_id AND r.mua_giai_id=NEW.mua_giai_id AND r.ngay_bat_dau<=DATE(NEW.thoi_gian_thi_dau) AND DATE(NEW.thoi_gian_thi_dau)<r.ngay_ket_thuc) OR NOT EXISTS(SELECT 1 FROM HopDong h WHERE h.cau_thu_id=a.cau_thu_id AND h.cau_lac_bo_id=a.cau_lac_bo_id AND COALESCE(h.ngay_bat_dau,'1000-01-01')<=DATE(NEW.thoi_gian_thi_dau) AND DATE(NEW.thoi_gian_thi_dau)<COALESCE(h.ngay_ket_thuc,'9999-12-31')) OR EXISTS(SELECT 1 FROM ChanThuong i WHERE i.cau_thu_id=a.cau_thu_id AND i.cam_thi_dau=TRUE AND i.ngay_bat_dau_nghi<=DATE(NEW.thoi_gian_thi_dau) AND(i.ngay_thuc_te_tro_lai IS NULL OR DATE(NEW.thoi_gian_thi_dau)<i.ngay_thuc_te_tro_lai)))) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cap nhat tran dau lam mat tinh hop le cua lan ra san'; END IF;
END$$
CREATE TRIGGER trg_trandau_bd BEFORE DELETE ON TranDau FOR EACH ROW BEGIN
 IF OLD.trang_thai='COMPLETED' OR EXISTS(SELECT 1 FROM CauThu_TranDau a WHERE a.tran_dau_id=OLD.id) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Khong duoc xoa tran dau da hoan tat hoac da co thong ke cau thu'; END IF;
END$$
CREATE TRIGGER trg_muagiai_bu BEFORE UPDATE ON MuaGiai FOR EACH ROW BEGIN
 IF EXISTS(SELECT 1 FROM TranDau m WHERE m.mua_giai_id=OLD.id AND DATE(m.thoi_gian_thi_dau) NOT BETWEEN NEW.ngay_bat_dau AND NEW.ngay_ket_thuc) OR EXISTS(SELECT 1 FROM CauThu_MuaGiai r WHERE r.mua_giai_id=OLD.id AND(r.ngay_bat_dau<NEW.ngay_bat_dau OR r.ngay_ket_thuc>DATE_ADD(NEW.ngay_ket_thuc,INTERVAL 1 DAY))) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cap nhat mua giai lam du lieu lich su nam ngoai khoang'; END IF;
END$$

CREATE TRIGGER trg_cttd_bi BEFORE INSERT ON CauThu_TranDau FOR EACH ROW BEGIN
 DECLARE v_lock BIGINT UNSIGNED; DECLARE v_birth DATE; DECLARE v_season BIGINT UNSIGNED; DECLARE v_date DATE; DECLARE v_home BIGINT UNSIGNED; DECLARE v_away BIGINT UNSIGNED;
 SELECT mua_giai_id,DATE(thoi_gian_thi_dau),clb_chu_nha_id,clb_khach_id INTO v_season,v_date,v_home,v_away FROM TranDau WHERE id=NEW.tran_dau_id;
 SELECT id,ngay_sinh INTO v_lock,v_birth FROM CauThu WHERE id=NEW.cau_thu_id FOR UPDATE;
 IF v_birth IS NOT NULL AND v_date<v_birth THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Lan ra san khong duoc truoc ngay sinh'; END IF;
 IF NOT EXISTS(SELECT 1 FROM TranDau WHERE id=NEW.tran_dau_id AND trang_thai='COMPLETED') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Chi ghi lan ra san cho tran COMPLETED'; END IF;
 IF NEW.cau_lac_bo_id NOT IN(v_home,v_away) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CLB cua cau thu khong tham gia tran dau'; END IF;
 IF NOT EXISTS(SELECT 1 FROM CauThu_MuaGiai r WHERE r.cau_thu_id=NEW.cau_thu_id AND r.cau_lac_bo_id=NEW.cau_lac_bo_id AND r.mua_giai_id=v_season AND r.ngay_bat_dau<=v_date AND v_date<r.ngay_ket_thuc) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cau thu khong co dang ky hop le cho CLB tai ngay thi dau'; END IF;
 IF NOT EXISTS(SELECT 1 FROM HopDong h WHERE h.cau_thu_id=NEW.cau_thu_id AND h.cau_lac_bo_id=NEW.cau_lac_bo_id AND COALESCE(h.ngay_bat_dau,'1000-01-01')<=v_date AND v_date<COALESCE(h.ngay_ket_thuc,'9999-12-31')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cau thu khong co hop dong hop le cho CLB tai ngay thi dau'; END IF;
 IF EXISTS(SELECT 1 FROM ChanThuong i WHERE i.cau_thu_id=NEW.cau_thu_id AND i.cam_thi_dau=TRUE AND i.ngay_bat_dau_nghi<=v_date AND(i.ngay_thuc_te_tro_lai IS NULL OR v_date<i.ngay_thuc_te_tro_lai)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cau thu dang trong khoang nghi chan thuong'; END IF;
END$$
CREATE TRIGGER trg_cttd_bu BEFORE UPDATE ON CauThu_TranDau FOR EACH ROW BEGIN
 DECLARE v_lock BIGINT UNSIGNED; DECLARE v_birth DATE; DECLARE v_season BIGINT UNSIGNED; DECLARE v_date DATE; DECLARE v_home BIGINT UNSIGNED; DECLARE v_away BIGINT UNSIGNED;
 IF NEW.tran_dau_id<>OLD.tran_dau_id OR NEW.cau_thu_id<>OLD.cau_thu_id THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Khong doi dinh danh lan ra san lich su'; END IF;
 SELECT mua_giai_id,DATE(thoi_gian_thi_dau),clb_chu_nha_id,clb_khach_id INTO v_season,v_date,v_home,v_away FROM TranDau WHERE id=NEW.tran_dau_id;
 SELECT id,ngay_sinh INTO v_lock,v_birth FROM CauThu WHERE id=NEW.cau_thu_id FOR UPDATE;
 IF v_birth IS NOT NULL AND v_date<v_birth THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Lan ra san khong duoc truoc ngay sinh'; END IF;
 IF NOT EXISTS(SELECT 1 FROM TranDau WHERE id=NEW.tran_dau_id AND trang_thai='COMPLETED') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Chi ghi lan ra san cho tran COMPLETED'; END IF;
 IF NEW.cau_lac_bo_id NOT IN(v_home,v_away) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CLB cua cau thu khong tham gia tran dau'; END IF;
 IF NOT EXISTS(SELECT 1 FROM CauThu_MuaGiai r WHERE r.cau_thu_id=NEW.cau_thu_id AND r.cau_lac_bo_id=NEW.cau_lac_bo_id AND r.mua_giai_id=v_season AND r.ngay_bat_dau<=v_date AND v_date<r.ngay_ket_thuc) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cau thu khong co dang ky hop le cho CLB tai ngay thi dau'; END IF;
 IF NOT EXISTS(SELECT 1 FROM HopDong h WHERE h.cau_thu_id=NEW.cau_thu_id AND h.cau_lac_bo_id=NEW.cau_lac_bo_id AND COALESCE(h.ngay_bat_dau,'1000-01-01')<=v_date AND v_date<COALESCE(h.ngay_ket_thuc,'9999-12-31')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cau thu khong co hop dong hop le cho CLB tai ngay thi dau'; END IF;
 IF EXISTS(SELECT 1 FROM ChanThuong i WHERE i.cau_thu_id=NEW.cau_thu_id AND i.cam_thi_dau=TRUE AND i.ngay_bat_dau_nghi<=v_date AND(i.ngay_thuc_te_tro_lai IS NULL OR v_date<i.ngay_thuc_te_tro_lai)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Cau thu dang trong khoang nghi chan thuong'; END IF;
END$$
CREATE TRIGGER trg_cttd_bd BEFORE DELETE ON CauThu_TranDau FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Khong xoa thong ke ra san lich su; chi duoc hieu chinh gia tri'; END$$

CREATE TRIGGER trg_chanthuong_bi BEFORE INSERT ON ChanThuong FOR EACH ROW BEGIN
 DECLARE v_lock BIGINT UNSIGNED; DECLARE v_birth DATE;
 SELECT id,ngay_sinh INTO v_lock,v_birth FROM CauThu WHERE id=NEW.cau_thu_id FOR UPDATE;
 IF v_birth IS NOT NULL AND (DATE(NEW.thoi_diem_xay_ra)<v_birth OR NEW.ngay_bat_dau_nghi<v_birth) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Chan thuong khong duoc truoc ngay sinh'; END IF;
 IF NEW.ngay_thuc_te_tro_lai>CURRENT_DATE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Ngay tro lai thuc te khong duoc o tuong lai'; END IF;
 IF EXISTS(SELECT 1 FROM ChanThuong i WHERE i.cau_thu_id=NEW.cau_thu_id AND i.mo_ta=NEW.mo_ta AND i.thoi_diem_xay_ra=NEW.thoi_diem_xay_ra AND i.ngay_bat_dau_nghi=NEW.ngay_bat_dau_nghi) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Dot chan thuong da ton tai'; END IF;

 IF NEW.cam_thi_dau=TRUE AND EXISTS(SELECT 1 FROM CauThu_TranDau a JOIN TranDau m ON m.id=a.tran_dau_id WHERE a.cau_thu_id=NEW.cau_thu_id AND NEW.ngay_bat_dau_nghi<=DATE(m.thoi_gian_thi_dau) AND(NEW.ngay_thuc_te_tro_lai IS NULL OR DATE(m.thoi_gian_thi_dau)<NEW.ngay_thuc_te_tro_lai)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Chan thuong mau thuan voi lan ra san trong khoang nghi'; END IF;
END$$
CREATE TRIGGER trg_chanthuong_bu BEFORE UPDATE ON ChanThuong FOR EACH ROW BEGIN
 DECLARE v_lock BIGINT UNSIGNED; DECLARE v_birth DATE;
 SELECT id,ngay_sinh INTO v_lock,v_birth FROM CauThu WHERE id=NEW.cau_thu_id FOR UPDATE;
 IF v_birth IS NOT NULL AND (DATE(NEW.thoi_diem_xay_ra)<v_birth OR NEW.ngay_bat_dau_nghi<v_birth) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Chan thuong khong duoc truoc ngay sinh'; END IF;
 IF NEW.ngay_thuc_te_tro_lai>CURRENT_DATE THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Ngay tro lai thuc te khong duoc o tuong lai'; END IF;
 IF NOT(NEW.cau_thu_id<=>OLD.cau_thu_id AND NEW.mo_ta<=>OLD.mo_ta AND NEW.thoi_diem_xay_ra<=>OLD.thoi_diem_xay_ra AND NEW.ngay_bat_dau_nghi<=>OLD.ngay_bat_dau_nghi) AND EXISTS(SELECT 1 FROM ChanThuong i WHERE i.id<>OLD.id AND i.cau_thu_id=NEW.cau_thu_id AND i.mo_ta=NEW.mo_ta AND i.thoi_diem_xay_ra=NEW.thoi_diem_xay_ra AND i.ngay_bat_dau_nghi=NEW.ngay_bat_dau_nghi) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Dot chan thuong da ton tai'; END IF;

 IF NEW.cam_thi_dau=TRUE AND EXISTS(SELECT 1 FROM CauThu_TranDau a JOIN TranDau m ON m.id=a.tran_dau_id WHERE a.cau_thu_id=NEW.cau_thu_id AND NEW.ngay_bat_dau_nghi<=DATE(m.thoi_gian_thi_dau) AND(NEW.ngay_thuc_te_tro_lai IS NULL OR DATE(m.thoi_gian_thi_dau)<NEW.ngay_thuc_te_tro_lai)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Chan thuong mau thuan voi lan ra san trong khoang nghi'; END IF;
END$$
CREATE TRIGGER trg_chanthuong_bd BEFORE DELETE ON ChanThuong FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Khong xoa lich su chan thuong; chi duoc hieu chinh thong tin'; END$$
CREATE TRIGGER trg_cn_bi BEFORE INSERT ON ChuyenNhuong FOR EACH ROW BEGIN
 DECLARE v_lock BIGINT UNSIGNED; DECLARE v_birth DATE;
 SELECT id,ngay_sinh INTO v_lock,v_birth FROM CauThu WHERE id=NEW.cau_thu_id FOR UPDATE;
 IF v_birth IS NOT NULL AND NEW.ngay_hieu_luc<v_birth THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Chuyen nhuong khong duoc truoc ngay sinh'; END IF;
END$$
CREATE TRIGGER trg_cn_bu BEFORE UPDATE ON ChuyenNhuong FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Khong sua giao dich chuyen nhuong da hoan tat'; END$$
CREATE TRIGGER trg_cn_bd BEFORE DELETE ON ChuyenNhuong FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Khong xoa giao dich chuyen nhuong da hoan tat'; END$$

DELIMITER ;
