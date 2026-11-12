-- DB da co guard 002: chi thay trigger UPDATE lan ra san; khong doi bang/du lieu.
-- Dung app/update-db.bat de kiem tra, sao luu va khoi phuc khi loi.
USE csdl1_mu_2025_26;
SET NAMES utf8mb4;
SET time_zone = '+07:00';
DELIMITER $$
DROP TRIGGER IF EXISTS trg_cttd_bu$$
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
DELIMITER ;
