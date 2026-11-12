-- Chi chay mot lan tren CSDL da tao truoc khi co kien_tao trong ER moi.
-- CSDL tao moi bang 01_schema.sql da co cot nay, khong chay migration nay.
USE csdl1_mu_2025_26;
ALTER TABLE CauThu_TranDau
 ADD COLUMN kien_tao TINYINT UNSIGNED NULL DEFAULT NULL AFTER ban_thang;
-- NULL: chua co du lieu kien tao. Khong gan 0 cho lich su chua xac minh.
