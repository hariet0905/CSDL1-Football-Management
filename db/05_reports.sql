USE csdl1_mu_2025_26;
SET NAMES utf8mb4;
SET time_zone = '+07:00';

-- BAO CAO: ngay demo co dinh de ket qua tai lap duoc.
SET @season_id=(SELECT id FROM MuaGiai WHERE ma_mua_giai='2025-26');
SET @club_id=(SELECT id FROM CauLacBo WHERE la_clb_quan_ly=TRUE);
SET @as_of=DATE('2026-05-24');

-- Q01. Danh sach dang ky con hieu luc cua CLB.
SELECT c.id,c.ho_ten,v.ten_hien_thi,r.so_ao,r.ngay_bat_dau,r.ngay_ket_thuc
FROM CauThu_MuaGiai r JOIN CauThu c ON c.id=r.cau_thu_id JOIN ViTri v ON v.id=c.vi_tri_chinh_id
WHERE r.mua_giai_id=@season_id AND r.cau_lac_bo_id=@club_id
 AND r.ngay_bat_dau<=@as_of AND @as_of<r.ngay_ket_thuc ORDER BY r.so_ao;

-- Q02. Lich va ket qua cua CLB trong mua.
SELECT m.id,h.ten chu_nha,a.ten khach,m.thoi_gian_thi_dau,m.trang_thai,m.ban_thang_chu_nha,m.ban_thang_khach
FROM TranDau m JOIN CauLacBo h ON h.id=m.clb_chu_nha_id JOIN CauLacBo a ON a.id=m.clb_khach_id
WHERE m.mua_giai_id=@season_id AND @club_id IN(m.clb_chu_nha_id,m.clb_khach_id) ORDER BY m.thoi_gian_thi_dau;

-- Q03. Thanh tich CLB tu cac tran da ghi nhan, khong phai bang xep hang toan giai.
WITH k AS (
 SELECT IF(clb_chu_nha_id=@club_id,ban_thang_chu_nha,ban_thang_khach) gf,
 IF(clb_chu_nha_id=@club_id,ban_thang_khach,ban_thang_chu_nha) ga
 FROM TranDau WHERE mua_giai_id=@season_id AND trang_thai='COMPLETED'
 AND @club_id IN(clb_chu_nha_id,clb_khach_id) AND DATE(thoi_gian_thi_dau)<=@as_of)
SELECT COUNT(*) so_tran,COALESCE(SUM(gf>ga),0) thang,COALESCE(SUM(gf=ga),0) hoa,COALESCE(SUM(gf<ga),0) thua,
 COALESCE(SUM(gf),0) ban_thang,COALESCE(SUM(ga),0) ban_thua,
 COALESCE(SUM(CAST(gf AS SIGNED)-CAST(ga AS SIGNED)),0) hieu_so,
 COALESCE(SUM(CASE WHEN gf>ga THEN 3 WHEN gf=ga THEN 1 ELSE 0 END),0) diem FROM k;

-- Q04. Cau thu ghi ban cho CLB trong mua, tinh den ngay bao cao.
SELECT c.id,c.ho_ten,SUM(a.ban_thang) ban_thang FROM CauThu_TranDau a
JOIN CauThu c ON c.id=a.cau_thu_id JOIN TranDau m ON m.id=a.tran_dau_id
WHERE a.cau_lac_bo_id=@club_id AND m.mua_giai_id=@season_id AND m.trang_thai='COMPLETED' AND DATE(m.thoi_gian_thi_dau)<=@as_of
GROUP BY c.id,c.ho_ten HAVING SUM(a.ban_thang)>0 ORDER BY ban_thang DESC,c.id;

-- Q05. Top 10 cau thu co tong so phut thi dau cao nhat.
SELECT c.id,c.ho_ten,COUNT(*) so_tran,SUM(a.so_phut) so_phut
FROM CauThu_TranDau a JOIN CauThu c ON c.id=a.cau_thu_id JOIN TranDau m ON m.id=a.tran_dau_id
WHERE a.cau_lac_bo_id=@club_id AND m.mua_giai_id=@season_id AND m.trang_thai='COMPLETED' AND DATE(m.thoi_gian_thi_dau)<=@as_of
GROUP BY c.id,c.ho_ten ORDER BY so_phut DESC,so_tran DESC,c.id LIMIT 10;

-- Q06. So tran, phut va the phat cua cau thu cho CLB.
SELECT c.id,c.ho_ten,COUNT(*) so_tran,SUM(a.so_phut) so_phut,
 CASE WHEN COUNT(a.kien_tao)=COUNT(*) THEN SUM(a.kien_tao) ELSE NULL END kien_tao,
 SUM(a.the_vang) the_vang,SUM(a.the_do) the_do
FROM CauThu_TranDau a JOIN CauThu c ON c.id=a.cau_thu_id JOIN TranDau m ON m.id=a.tran_dau_id
WHERE a.cau_lac_bo_id=@club_id AND m.mua_giai_id=@season_id AND m.trang_thai='COMPLETED' AND DATE(m.thoi_gian_thi_dau)<=@as_of
GROUP BY c.id,c.ho_ten ORDER BY so_tran DESC,so_phut DESC,c.id;

-- Q07. Dang ky hieu luc nhung chua ra san cho CLB trong mua den ngay bao cao.
SELECT c.id,c.ho_ten FROM CauThu c
WHERE EXISTS(SELECT 1 FROM CauThu_MuaGiai r WHERE r.cau_thu_id=c.id AND r.cau_lac_bo_id=@club_id
 AND r.mua_giai_id=@season_id AND r.ngay_bat_dau<=@as_of AND @as_of<r.ngay_ket_thuc)
AND NOT EXISTS(SELECT 1 FROM CauThu_TranDau a JOIN TranDau m ON m.id=a.tran_dau_id
 WHERE a.cau_thu_id=c.id AND a.cau_lac_bo_id=@club_id AND m.mua_giai_id=@season_id
 AND m.trang_thai='COMPLETED' AND DATE(m.thoi_gian_thi_dau)<=@as_of) ORDER BY c.id;

-- Q08. Cau thu cua CLB dang nghi chan thuong.
SELECT c.id,c.ho_ten,i.mo_ta,i.ngay_bat_dau_nghi,i.ngay_du_kien_tro_lai,i.ngay_thuc_te_tro_lai
FROM ChanThuong i JOIN CauThu c ON c.id=i.cau_thu_id
WHERE i.cam_thi_dau=TRUE AND i.ngay_bat_dau_nghi<=@as_of AND(i.ngay_thuc_te_tro_lai IS NULL OR @as_of<i.ngay_thuc_te_tro_lai)
AND EXISTS(SELECT 1 FROM HopDong h WHERE h.cau_thu_id=c.id AND h.cau_lac_bo_id=@club_id AND (h.ngay_bat_dau IS NULL OR h.ngay_bat_dau<=@as_of) AND (h.ngay_ket_thuc IS NULL OR @as_of<h.ngay_ket_thuc));

-- Q09. Hop dong CLB sap het han trong 90 ngay; ngay ket thuc la bien loai tru.
SELECT c.id,c.ho_ten,h.ngay_ket_thuc,DATEDIFF(h.ngay_ket_thuc,@as_of) so_ngay_con_lai
FROM HopDong h JOIN CauThu c ON c.id=h.cau_thu_id WHERE h.cau_lac_bo_id=@club_id
AND h.ngay_bat_dau<=@as_of AND h.ngay_ket_thuc>@as_of AND h.ngay_ket_thuc<=DATE_ADD(@as_of,INTERVAL 90 DAY) ORDER BY h.ngay_ket_thuc,c.id;

-- Q10. Ban huan luyen con lam viec tai CLB.
SELECT ho_ten,vai_tro,ngay_bat_dau,ngay_ket_thuc FROM ThanhVienBHL WHERE cau_lac_bo_id=@club_id
AND ngay_bat_dau<=@as_of AND(ngay_ket_thuc IS NULL OR @as_of<=ngay_ket_thuc) ORDER BY id;

-- Q11. Lich su hop dong tai CLB.
SELECT c.id,c.ho_ten,h.ngay_bat_dau,h.ngay_ket_thuc,h.ghi_chu FROM HopDong h JOIN CauThu c ON c.id=h.cau_thu_id
WHERE h.cau_lac_bo_id=@club_id ORDER BY c.id,h.ngay_bat_dau;

-- Q12. Co dang ky, chua ghi nhan nghi; khong phai ket luan y te/du dieu kien EPL.
SELECT c.id,c.ho_ten FROM CauThu c
WHERE EXISTS(SELECT 1 FROM CauThu_MuaGiai r WHERE r.cau_thu_id=c.id AND r.cau_lac_bo_id=@club_id AND r.mua_giai_id=@season_id AND r.ngay_bat_dau<=@as_of AND @as_of<r.ngay_ket_thuc)
AND EXISTS(SELECT 1 FROM HopDong h WHERE h.cau_thu_id=c.id AND h.cau_lac_bo_id=@club_id AND (h.ngay_bat_dau IS NULL OR h.ngay_bat_dau<=@as_of) AND (h.ngay_ket_thuc IS NULL OR @as_of<h.ngay_ket_thuc))
AND NOT EXISTS(SELECT 1 FROM ChanThuong i WHERE i.cau_thu_id=c.id AND i.cam_thi_dau=TRUE AND i.ngay_bat_dau_nghi<=@as_of AND(i.ngay_thuc_te_tro_lai IS NULL OR @as_of<i.ngay_thuc_te_tro_lai)) ORDER BY c.id;

-- Q13. So lan DA doi dau tung doi thu, qua cac mua, den ngay bao cao.
SELECT o.ten,COUNT(*) so_lan FROM TranDau m JOIN CauLacBo o ON o.id=IF(m.clb_chu_nha_id=@club_id,m.clb_khach_id,m.clb_chu_nha_id)
WHERE @club_id IN(m.clb_chu_nha_id,m.clb_khach_id) AND m.trang_thai='COMPLETED' AND DATE(m.thoi_gian_thi_dau)<=@as_of
GROUP BY o.id,o.ten ORDER BY so_lan DESC,o.id;

-- Q14. Phan bo vi tri cua doi hinh dang ky tai ngay bao cao; moi cau thu tinh mot lan.
SELECT v.ma_vi_tri,COUNT(*) so_cau_thu FROM CauThu c JOIN ViTri v ON v.id=c.vi_tri_chinh_id
WHERE EXISTS(SELECT 1 FROM CauThu_MuaGiai r WHERE r.cau_thu_id=c.id AND r.cau_lac_bo_id=@club_id
 AND r.mua_giai_id=@season_id AND r.ngay_bat_dau<=@as_of AND @as_of<r.ngay_ket_thuc)
GROUP BY v.id,v.ma_vi_tri ORDER BY v.id;

-- Q15. Chuyen nhuong lien quan CLB den ngay bao cao; phi tinh bang GBP.
SELECT c.ho_ten,src.ten clb_di,dst.ten clb_den,t.ngay_hieu_luc,t.phi_chuyen_nhuong
FROM ChuyenNhuong t JOIN CauThu c ON c.id=t.cau_thu_id LEFT JOIN CauLacBo src ON src.id=t.clb_di_id JOIN CauLacBo dst ON dst.id=t.clb_den_id
WHERE @club_id IN(t.clb_di_id,t.clb_den_id) AND t.ngay_hieu_luc<=@as_of ORDER BY t.ngay_hieu_luc,t.id;
