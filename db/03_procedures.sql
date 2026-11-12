USE csdl1_mu_2025_26;
SET NAMES utf8mb4;
SET time_zone = '+07:00';

DELIMITER $$
CREATE PROCEDURE sp_hoan_tat_chuyen_nhuong(
 IN p_cau_thu_id BIGINT UNSIGNED, IN p_clb_di_id BIGINT UNSIGNED, IN p_clb_den_id BIGINT UNSIGNED,
 IN p_mua_giai_id BIGINT UNSIGNED, IN p_ngay_hieu_luc DATE, IN p_ngay_ket_thuc_hop_dong DATE,
 IN p_so_ao TINYINT UNSIGNED, IN p_phi DECIMAL(15,2))
BEGIN
 DECLARE v_lock BIGINT UNSIGNED; DECLARE v_contract_id BIGINT UNSIGNED DEFAULT NULL; DECLARE v_registration_id BIGINT UNSIGNED DEFAULT NULL; DECLARE v_season_end DATE DEFAULT NULL;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 IF p_phi<0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Phi chuyen nhuong khong duoc am'; END IF;
 IF p_cau_thu_id IS NULL OR p_clb_den_id IS NULL OR p_mua_giai_id IS NULL OR p_ngay_hieu_luc IS NULL OR p_ngay_ket_thuc_hop_dong IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Thieu tham so chuyen nhuong'; END IF;
 IF p_clb_di_id IS NOT NULL AND p_clb_di_id=p_clb_den_id THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='CLB di va CLB den phai khac nhau'; END IF;
 IF p_ngay_ket_thuc_hop_dong<=p_ngay_hieu_luc THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Ngay ket thuc hop dong moi phai sau ngay chuyen nhuong'; END IF;
 SELECT DATE_ADD(ngay_ket_thuc,INTERVAL 1 DAY) INTO v_season_end FROM MuaGiai WHERE id=p_mua_giai_id AND p_ngay_hieu_luc BETWEEN ngay_bat_dau AND ngay_ket_thuc FOR UPDATE;
 IF v_season_end IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Ngay chuyen nhuong khong nam trong mua giai'; END IF;
 SELECT id INTO v_lock FROM CauThu WHERE id=p_cau_thu_id FOR UPDATE;
 IF p_clb_di_id IS NOT NULL THEN
  SELECT MAX(id) INTO v_contract_id FROM HopDong WHERE cau_thu_id=p_cau_thu_id AND cau_lac_bo_id=p_clb_di_id AND COALESCE(ngay_bat_dau,'1000-01-01')<p_ngay_hieu_luc AND p_ngay_hieu_luc<COALESCE(ngay_ket_thuc,'9999-12-31') FOR UPDATE;
  SELECT MAX(id) INTO v_registration_id FROM CauThu_MuaGiai WHERE cau_thu_id=p_cau_thu_id AND cau_lac_bo_id=p_clb_di_id AND mua_giai_id=p_mua_giai_id AND ngay_bat_dau<p_ngay_hieu_luc AND p_ngay_hieu_luc<ngay_ket_thuc FOR UPDATE;
  IF v_contract_id IS NULL OR v_registration_id IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Khong tim thay hop dong va dang ky nguon dang hieu luc'; END IF;
  IF EXISTS(SELECT 1 FROM CauThu_MuaGiai r JOIN HopDong h ON h.id=v_contract_id WHERE r.id<>v_registration_id AND r.cau_thu_id=p_cau_thu_id AND r.cau_lac_bo_id=p_clb_di_id AND r.ngay_ket_thuc>p_ngay_hieu_luc AND COALESCE(h.ngay_bat_dau,'1000-01-01')<=r.ngay_bat_dau AND r.ngay_ket_thuc<=COALESCE(h.ngay_ket_thuc,'9999-12-31')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Con dang ky khac phu thuoc hop dong nguon; can doi chieu truoc khi chuyen nhuong'; END IF;
  UPDATE CauThu_MuaGiai SET ngay_ket_thuc=p_ngay_hieu_luc WHERE id=v_registration_id;
  UPDATE HopDong SET ngay_ket_thuc=p_ngay_hieu_luc WHERE id=v_contract_id;
 END IF;
 INSERT INTO HopDong(cau_thu_id,cau_lac_bo_id,ngay_bat_dau,ngay_ket_thuc,ghi_chu) VALUES(p_cau_thu_id,p_clb_den_id,p_ngay_hieu_luc,p_ngay_ket_thuc_hop_dong,'Tao boi giao dich chuyen nhuong da hoan tat');
 INSERT INTO CauThu_MuaGiai(cau_thu_id,mua_giai_id,cau_lac_bo_id,ngay_bat_dau,ngay_ket_thuc,so_ao) VALUES(p_cau_thu_id,p_mua_giai_id,p_clb_den_id,p_ngay_hieu_luc,LEAST(v_season_end,p_ngay_ket_thuc_hop_dong),p_so_ao);
 INSERT INTO ChuyenNhuong(cau_thu_id,clb_di_id,clb_den_id,ngay_hieu_luc,phi_chuyen_nhuong) VALUES(p_cau_thu_id,p_clb_di_id,p_clb_den_id,p_ngay_hieu_luc,p_phi);
 COMMIT;
END$$
DELIMITER ;

