-- BAI NOP NHOM 26: QUAN LY MANCHESTER UNITED TAI PREMIER LEAGUE
-- MySQL 8.4+. MU Premier League 2025-26. Nguon va gioi han: ../report-db.md.
-- Khong xoa CSDL cu. Chay tren schema moi; lan chay lai se bao table already exists.
CREATE DATABASE IF NOT EXISTS csdl1_mu_2025_26 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE csdl1_mu_2025_26;

-- MySQL 8.4+. HopDong and CauThu_MuaGiai use [ngay_bat_dau, ngay_ket_thuc).
SET NAMES utf8mb4;
SET time_zone = '+07:00';

CREATE TABLE ViTri (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 ma_vi_tri VARCHAR(20) NOT NULL, ten_hien_thi VARCHAR(80) NOT NULL,
 CONSTRAINT uq_vitri_ma UNIQUE(ma_vi_tri),
 CONSTRAINT ck_vitri_ma CHECK(CHAR_LENGTH(TRIM(ma_vi_tri))>0),
 CONSTRAINT ck_vitri_ten CHECK(CHAR_LENGTH(TRIM(ten_hien_thi))>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE CauLacBo (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 ten VARCHAR(150) NOT NULL, thanh_pho VARCHAR(100), san_nha VARCHAR(150),
 thong_tin_lien_he VARCHAR(255), la_clb_quan_ly BOOLEAN NOT NULL DEFAULT FALSE,
 quan_ly_duy_nhat TINYINT GENERATED ALWAYS AS (CASE WHEN la_clb_quan_ly=TRUE THEN 1 ELSE NULL END) STORED,
 CONSTRAINT uq_clb_quan_ly UNIQUE(quan_ly_duy_nhat),
 CONSTRAINT uq_clb_ten UNIQUE(ten),
 CONSTRAINT ck_clb_ten CHECK(CHAR_LENGTH(TRIM(ten))>0),
 CONSTRAINT ck_clb_quanly CHECK(la_clb_quan_ly IN(0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE CauThu (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 ho_ten VARCHAR(150) NOT NULL, ngay_sinh DATE, quoc_tich VARCHAR(80),
 vi_tri_chinh_id BIGINT UNSIGNED NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 CONSTRAINT fk_cauthu_vitri FOREIGN KEY(vi_tri_chinh_id) REFERENCES ViTri(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT ck_cauthu_hoten CHECK(CHAR_LENGTH(TRIM(ho_ten))>0),
 INDEX idx_cauthu_vitri(vi_tri_chinh_id), INDEX idx_cauthu_hoten(ho_ten)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE HopDong (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 cau_thu_id BIGINT UNSIGNED NOT NULL, cau_lac_bo_id BIGINT UNSIGNED NOT NULL,
 ngay_bat_dau DATE, ngay_ket_thuc DATE, ghi_chu VARCHAR(500),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_hopdong_cauthu FOREIGN KEY(cau_thu_id) REFERENCES CauThu(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_hopdong_clb FOREIGN KEY(cau_lac_bo_id) REFERENCES CauLacBo(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT ck_hopdong_dates CHECK(ngay_ket_thuc>ngay_bat_dau),
 INDEX idx_hopdong_cauthu_dates(cau_thu_id,ngay_bat_dau,ngay_ket_thuc),
 INDEX idx_hopdong_clb_dates(cau_lac_bo_id,ngay_bat_dau,ngay_ket_thuc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE MuaGiai (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 ma_mua_giai VARCHAR(30) NOT NULL, ngay_bat_dau DATE NOT NULL, ngay_ket_thuc DATE NOT NULL,
 CONSTRAINT uq_muagiai_ma UNIQUE(ma_mua_giai),
 CONSTRAINT ck_muagiai_dates CHECK(ngay_ket_thuc>=ngay_bat_dau)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE CauThu_MuaGiai (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 cau_thu_id BIGINT UNSIGNED NOT NULL, mua_giai_id BIGINT UNSIGNED NOT NULL,
 cau_lac_bo_id BIGINT UNSIGNED NOT NULL, ngay_bat_dau DATE NOT NULL,
 ngay_ket_thuc DATE NOT NULL, so_ao TINYINT UNSIGNED,
 CONSTRAINT fk_ctmg_cauthu FOREIGN KEY(cau_thu_id) REFERENCES CauThu(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_ctmg_muagiai FOREIGN KEY(mua_giai_id) REFERENCES MuaGiai(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_ctmg_clb FOREIGN KEY(cau_lac_bo_id) REFERENCES CauLacBo(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT ck_ctmg_dates CHECK(ngay_ket_thuc>ngay_bat_dau),
 CONSTRAINT ck_ctmg_soao CHECK(so_ao IS NULL OR so_ao BETWEEN 1 AND 99),
 INDEX idx_ctmg_player_season_dates(cau_thu_id,mua_giai_id,ngay_bat_dau,ngay_ket_thuc),
 INDEX idx_ctmg_club_shirt(cau_lac_bo_id,mua_giai_id,so_ao,ngay_bat_dau,ngay_ket_thuc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ThanhVienBHL (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, cau_lac_bo_id BIGINT UNSIGNED NOT NULL,
 ho_ten VARCHAR(150) NOT NULL, vai_tro VARCHAR(100) NOT NULL, thong_tin_lien_he VARCHAR(255),
 ngay_bat_dau DATE NOT NULL, ngay_ket_thuc DATE,
 CONSTRAINT fk_bhl_clb FOREIGN KEY(cau_lac_bo_id) REFERENCES CauLacBo(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT ck_bhl_hoten CHECK(CHAR_LENGTH(TRIM(ho_ten))>0),
 CONSTRAINT ck_bhl_vaitro CHECK(CHAR_LENGTH(TRIM(vai_tro))>0),
 CONSTRAINT ck_bhl_dates CHECK(ngay_ket_thuc IS NULL OR ngay_ket_thuc>=ngay_bat_dau),
 INDEX idx_bhl_clb_active(cau_lac_bo_id,ngay_bat_dau,ngay_ket_thuc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE TranDau (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, mua_giai_id BIGINT UNSIGNED NOT NULL,
 clb_chu_nha_id BIGINT UNSIGNED NOT NULL, clb_khach_id BIGINT UNSIGNED NOT NULL,
 thoi_gian_thi_dau DATETIME NOT NULL, san_thi_dau VARCHAR(150) NOT NULL,
 trang_thai ENUM('SCHEDULED','COMPLETED','POSTPONED','CANCELLED') NOT NULL DEFAULT 'SCHEDULED',
 ban_thang_chu_nha TINYINT UNSIGNED, ban_thang_khach TINYINT UNSIGNED,
 CONSTRAINT fk_trandau_muagiai FOREIGN KEY(mua_giai_id) REFERENCES MuaGiai(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_trandau_chunha FOREIGN KEY(clb_chu_nha_id) REFERENCES CauLacBo(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_trandau_khach FOREIGN KEY(clb_khach_id) REFERENCES CauLacBo(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT ck_trandau_clubs CHECK(clb_chu_nha_id<>clb_khach_id),
 CONSTRAINT ck_trandau_san CHECK(CHAR_LENGTH(TRIM(san_thi_dau))>0),
 CONSTRAINT ck_trandau_score_status CHECK((trang_thai='COMPLETED' AND ban_thang_chu_nha IS NOT NULL AND ban_thang_khach IS NOT NULL) OR (trang_thai<>'COMPLETED' AND ban_thang_chu_nha IS NULL AND ban_thang_khach IS NULL)),
 INDEX idx_trandau_muagiai_time(mua_giai_id,thoi_gian_thi_dau),
 INDEX idx_trandau_chunha(clb_chu_nha_id), INDEX idx_trandau_khach(clb_khach_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE CauThu_TranDau (
 tran_dau_id BIGINT UNSIGNED NOT NULL, cau_thu_id BIGINT UNSIGNED NOT NULL,
 cau_lac_bo_id BIGINT UNSIGNED NOT NULL, so_phut TINYINT UNSIGNED NOT NULL DEFAULT 0,
 ban_thang TINYINT UNSIGNED NOT NULL DEFAULT 0,
 kien_tao TINYINT UNSIGNED NULL DEFAULT NULL,
 the_vang TINYINT UNSIGNED NOT NULL DEFAULT 0, the_do TINYINT UNSIGNED NOT NULL DEFAULT 0,
 PRIMARY KEY(tran_dau_id,cau_thu_id),
 CONSTRAINT fk_cttd_trandau FOREIGN KEY(tran_dau_id) REFERENCES TranDau(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_cttd_cauthu FOREIGN KEY(cau_thu_id) REFERENCES CauThu(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_cttd_clb FOREIGN KEY(cau_lac_bo_id) REFERENCES CauLacBo(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT ck_cttd_sophut CHECK(so_phut BETWEEN 0 AND 90),
 CONSTRAINT ck_cttd_the CHECK(the_vang<=2 AND the_do<=1),
 INDEX idx_cttd_cauthu(cau_thu_id), INDEX idx_cttd_clb(cau_lac_bo_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ChanThuong (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, cau_thu_id BIGINT UNSIGNED NOT NULL,
 mo_ta VARCHAR(255) NOT NULL, thoi_diem_xay_ra DATETIME NOT NULL,
 ngay_bat_dau_nghi DATE NOT NULL, ngay_du_kien_tro_lai DATE, ngay_thuc_te_tro_lai DATE,
 cam_thi_dau BOOLEAN NOT NULL DEFAULT TRUE,
 CONSTRAINT fk_chanthuong_cauthu FOREIGN KEY(cau_thu_id) REFERENCES CauThu(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT ck_chanthuong_mota CHECK(CHAR_LENGTH(TRIM(mo_ta))>0),
 CONSTRAINT ck_chanthuong_start CHECK(ngay_bat_dau_nghi>=DATE(thoi_diem_xay_ra)),
 CONSTRAINT ck_chanthuong_expected CHECK(ngay_du_kien_tro_lai IS NULL OR ngay_du_kien_tro_lai>=ngay_bat_dau_nghi),
 CONSTRAINT ck_chanthuong_actual CHECK(ngay_thuc_te_tro_lai IS NULL OR ngay_thuc_te_tro_lai>=ngay_bat_dau_nghi),
 CONSTRAINT ck_chanthuong_block CHECK(cam_thi_dau IN(0,1)),
 INDEX idx_chanthuong_cauthu_dates(cau_thu_id,ngay_bat_dau_nghi,ngay_thuc_te_tro_lai)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE ChuyenNhuong (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, cau_thu_id BIGINT UNSIGNED NOT NULL,
 clb_di_id BIGINT UNSIGNED, clb_den_id BIGINT UNSIGNED NOT NULL, ngay_hieu_luc DATE NOT NULL,
 phi_chuyen_nhuong DECIMAL(15,2), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_cn_cauthu FOREIGN KEY(cau_thu_id) REFERENCES CauThu(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_cn_clbdi FOREIGN KEY(clb_di_id) REFERENCES CauLacBo(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT fk_cn_clbden FOREIGN KEY(clb_den_id) REFERENCES CauLacBo(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
 CONSTRAINT uq_cn_player_date UNIQUE(cau_thu_id,ngay_hieu_luc),
 CONSTRAINT ck_cn_clubs CHECK(clb_di_id IS NULL OR clb_di_id<>clb_den_id),
 CONSTRAINT ck_cn_fee CHECK(phi_chuyen_nhuong>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

