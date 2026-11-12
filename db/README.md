# Các file SQL

| File | Mục đích |
| --- | --- |
| `01_schema.sql` | Tạo CSDL, bảng, khóa, CHECK và chỉ mục. |
| `02_triggers.sql` | Tạo trigger bảo vệ dữ liệu và lịch sử. |
| `03_procedures.sql` | Tạo thủ tục hoàn tất chuyển nhượng. |
| `04_seed.sql` | Nhập dữ liệu mẫu trong giao dịch. |
| `05_reports.sql` | Tham số minh họa và 15 truy vấn báo cáo Q01–Q15. |
| `../submission.sql` | Gọi năm file trên theo đúng thứ tự bằng mysql CLI. |

Việc tách file giữ nguyên cấu trúc, ràng buộc, dữ liệu và truy vấn của bản SQL
trước khi tách. Sau đó cột kiến tạo được bổ sung theo ER mới, có migration
riêng cho CSDL cũ. Mô hình và quy tắc dữ liệu được mô tả trong [báo cáo DB](../report-db.md).

## Mở lại giao diện khi CSDL đã có

Cập nhật kiểm tra thao tác ghi: dừng Java, chạy `.\app\update-db.bat`, nhập
mật khẩu MySQL tại Terminal rồi mở lại `run.bat` khi báo thành công. Công cụ
kiểm tra dữ liệu cũ, sao lưu trigger/thủ tục, không thay bảng/cột hoặc dữ liệu.
Không chạy lại schema/seed để cập nhật DB. Có thể dùng `--check` để chỉ kiểm tra.

Guard 003 giữ nguyên định danh `(tran_dau_id,cau_thu_id)` của lần ra sân;
chỉ hiệu chỉnh thống kê khi quan hệ vẫn hợp lệ. Công cụ tự chọn
`migrations/003_appearance_identity.sql` cho DB đã có guard nền 002,
hoặc cài `migrations/002_write_guards.sql` đồng bộ cho DB cũ hơn.
Không đổi ER, 11 bảng, cột, kiểu dữ liệu, PK/FK hoặc bản ghi hiện có.
DB mới khởi tạo bằng `02_triggers.sql` đã có guard này. DB thật chỉ được
coi đã cập nhật sau khi công cụ chạy thành công trên chính kết nối đó.

Không cần nhập lại SQL sau khi tách file. Giữ MySQL hoạt động, mở PowerShell
tại thư mục gốc project rồi chạy:

```powershell
.\app\run.bat
```

Nhập mật khẩu MySQL `root` khi được hỏi. Khi terminal báo đã khởi động,
mở <http://127.0.0.1:8080>. Giữ terminal hoạt động; dùng Ctrl+C để dừng.
Nếu tiến trình Java cũ còn chạy, dừng nó bằng Ctrl+C trước khi chạy bản mới.
Trên macOS/Linux, dùng `sh app/run.sh`.

Ứng dụng đọc truy vấn từ `db/05_reports.sql`; tài nguyên giao diện nằm tại
`app/web`. Các script chạy tự chuyển về thư mục `app` trước khi chạy Java.
Cấu hình kết nối dùng `DB_URL`, `DB_USER`, `DB_PASSWORD`; mặc định là MySQL
tại `localhost:3306`, CSDL `csdl1_mu_2025_26`, tài khoản `root`.
Các script cần JDK 17 trở lên (có cả `javac`), biên dịch các file Java vào
`app/build` trước khi chạy.

## Đăng nhập quản trị

Một quản trị viên, giữ nguyên ER và 11 bảng nghiệp vụ. Tạo tài khoản tại
terminal máy này, không gửi mật khẩu vào hội thoại:

```powershell
.\app\setup-admin.bat
.\app\run.bat
```

Nhập tên đăng nhập (3–40 ký tự chữ/số/dấu chấm/gạch ngang/gạch dưới), mật
khẩu ít nhất 12 ký tự và nhập lại. Mật khẩu gõ trong console không hiện.
Nếu chưa tạo, `run.bat` cũng hỏi khi khởi động lần đầu. Không có tài khoản
hoặc mật khẩu mặc định. Trên macOS/Linux lần đầu tạo qua console của `run.sh`.

`app/.admin.properties` chỉ chứa tên, salt ngẫu nhiên và băm PBKDF2-HMAC-SHA256
600.000 vòng, bị Git bỏ qua. Phiên ngẫu nhiên ở bộ nhớ server, hết hạn 8 giờ;
đăng nhập mới thu hồi phiên cũ, đăng xuất thu hồi cookie và phiên server.
Cookie HttpOnly, SameSite=Strict; ứng dụng HTTP chỉ lắng nghe `127.0.0.1`.
API dữ liệu, thao tác ghi và BXH đều kiểm tra phiên ở backend. Thử sai 5 lần
bị giới hạn 15 phút. Chưa có đăng ký tài khoản/phân quyền nhiều người dùng.

Quên mật khẩu: dừng Java, đổi tên file `.admin.properties` thành bản sao
cục bộ rồi chạy lại `setup-admin.bat`, khởi động Java. Không sửa hoặc nạp
lại DB; không commit file cấu hình hay bản sao chứa hash.

## Bảng xếp hạng EPL và hiệu ứng

Tab **Bảng xếp hạng** đọc đủ 20 đội từ
[football-data.org Standings](https://docs.football-data.org/general/v4/competition.html#_standings).
Cache riêng theo mùa, không ghi MySQL, không suy ra bảng toàn giải từ lịch MU.
Nguồn được tải tối đa 2 phút/lần khi mở tab; giao diện kiểm tra cache 5 giây/lần.
Độ trễ theo gói API, không bảo đảm cập nhật ngay khi có bàn thắng. Khi nguồn
lỗi, giữ bảng gần nhất kèm thông báo; mùa chưa có dữ liệu không hiện bảng mùa khác.
Mùa hiện tại ưu tiên endpoint không bộ lọc để giữ trừ điểm chính thức; mùa
khác dùng `season`, theo tài liệu nguồn bộ lọc có thể không bao gồm trừ điểm.
Hiển thị nguyên điểm/thứ hạng nguồn, không tự tính lại. BXH theo thời điểm
nguồn cập nhật, độc lập ngày báo cáo của các bảng nghiệp vụ.

Khi `form` trong bảng nguồn bị thiếu, backend lấy kết quả FINISHED toàn EPL
đúng mùa để tạo chuỗi 5 trận (trên BXH: cũ đến mới). Không thay điểm hoặc thứ
hạng nguồn; lỗi giữ phong độ gần nhất và thông báo. Bấm CLB mở tóm tắt thứ
hạng, điểm, thành tích, sân nhà có trong DB và 5 kết quả gần nhất.

Bấm trận lịch sử hoặc nút **Thông số trận / Cầu thủ** để mở chi tiết. Trận
API chưa có bản ghi DB vẫn mở bằng provider ID. `/api/match-detail` yêu cầu
đăng nhập, kiểm tra đúng EPL/mùa/ID, cache bộ nhớ; không ghi MySQL. Cầu thủ
trong DB được lọc theo local match ID; không mượn thống kê 2025–26. Chi tiết
nguồn thiếu giữ trống, không gán 0. Gói football-data.org hiện tại không trả
lineup, sự kiện hoặc chỉ số trận. API-FOOTBALL Free cũng được kiểm tra từ chối
mùa 2026; `API_FOOTBALL_KEY` không được ứng dụng sử dụng.

Theo lựa chọn của người dùng, `EspnFootball.java` bổ sung thống kê từ endpoint
công khai ESPN, không cần token. Khi mở trận đã kết thúc, backend lấy
`https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/scoreboard?dates=YYYYMMDD`,
chọn đúng một trận khớp EPL/mùa, chủ nhà/khách, giờ đấu UTC (sai lệch tối đa
15 phút) và tỷ số; sau đó kiểm tra lại toàn bộ định danh ở `summary?event=ID`.
Không dùng tên gần giống, không đảo sân, không lấy trận khác mùa để bù.
ID ESPN chỉ dùng trong cache; không thay ID football-data.org hoặc ID DB.

Chi tiết có chỉ số đội, cầu thủ thực sự ra sân, số áo, bàn/kiến tạo/thẻ,
cú sút và sút trúng đích khi nguồn có. Cầu thủ dự bị không ra sân bị loại.
Phút thi đấu ưu tiên số phút nguồn trả; nếu thiếu, chỉ tính từ cờ đá chính,
vào/ra sân, thẻ đỏ và mốc sự kiện rõ ràng của trận đã kết thúc. Phần bù giờ
không cộng vào miền 0–90; thiếu mốc thay người/thẻ đỏ thì để trống.
Không ghép cầu thủ ESPN vào `CauThu` bằng tên, không ghi lần ra sân vào DB.

Cache lịch ESPN theo ngày UTC trong 10 phút (tối đa 32 ngày), dùng chung
worker chi tiết; kết quả chi tiết hợp lệ cache 6 giờ. Lỗi thử lại khi mở sau
2 phút, giữ dữ liệu gần nhất nếu định danh/giờ/tỷ số không đổi. Nếu nguồn
sửa tỷ số, không tái sử dụng bộ thống kê cũ. Endpoint công khai có thể đổi
cấu trúc; lỗi không làm mất metadata chính hoặc phá dữ liệu nghiệp vụ.

Khung hộp thoại giữ bo góc và nút đóng cố định; chỉ `.modal-body` cuộn, có
khoảng cách với viền. Không đặt scrollbar trực tiếp trên shell đang phóng to.

Chuyển tab quét ngang theo hướng điều hướng; hồ sơ mở rộng từ hàng vừa bấm,
đóng trở lại hàng đó; biểu mẫu thu phóng có nhịp dừng ngắn. Khi lọc, danh sách
co giãn và các hàng di chuyển về vị trí mới. Radar vẽ nét trước rồi tô vùng;
BXH trượt hàng khi đổi hạng, nhấn điểm khi đổi số liệu. Thông báo trượt ngang,
lỗi rung nhẹ. Các thao tác không dùng chung hiệu ứng mờ dần. Poll không đổi
không chạy lại hiệu ứng, không dựng lại các ô tổng quan. Hỗ trợ
`prefers-reduced-motion` cho cả CSS và Web Animations API.

## API trận đấu và thống kê cầu thủ

Nguồn: [football-data.org v4](https://docs.football-data.org/general/v4/match.html).

Đặt `FOOTBALL_DATA_API_KEY=...` trong `.env` tại thư mục gốc hoặc biến môi
trường cùng tên; biến môi trường được ưu tiên. `.env` không được commit.
Ứng dụng chỉ đọc token ở backend; đổi token cần khởi động lại Java.

Với CSDL cũ, kiểm tra cột trước:

```sql
USE csdl1_mu_2025_26;
SHOW COLUMNS FROM CauThu_TranDau LIKE 'kien_tao';
```

Nếu chưa có cột, chạy một lần `db/migrations/001_add_assists.sql` trong
Workbench. Không chạy lại bộ khởi tạo. CSDL mới đã có cột từ `01_schema.sql`.
NULL giữ nguyên ý nghĩa chưa biết; các thống kê cũ không tự nhận kiến tạo 0.
Nếu chưa migration, giao diện vẫn mở và lịch/kết quả vẫn đồng bộ; thống kê
cầu thủ từ API sẽ bị chặn kèm thông báo.

Ứng dụng mặc định mùa **2026–2027** (mã DB `2026-27`, năm API `2026`),
cho chọn từ mùa này đến mùa kế tiếp của năm hiện tại. Đổi lựa chọn sẽ đồng bộ
đúng năm, không dùng cache trận mùa khác. Mùa chưa được nguồn cung cấp hiện chờ.
Chỉ lấy Premier League của Manchester United (provider team ID 66).
Java tự đồng bộ ngay khi khởi động, tiếp tục khoảng 2 phút/lần khi tiến
trình còn chạy; không cần bấm nút hay nhập năm. Một lượt dài không chạy
chồng lượt khác. Giao diện tự tải lại khoảng 5 giây/lần tại trang Tổng quan/Trận đấu,
khi đang nạp lần đầu hoặc đồng bộ đang chạy; đây không phải tần suất gọi
nguồn bóng đá. Tốc độ gọi nguồn tối đa khoảng 10 lần/phút.

Chưa có mùa 2026–2027 thì giao diện chờ nguồn tạo mùa hợp lệ, tự thử lại;
không dùng trận/kết quả 2025–26 thay thế. Ngày báo cáo mặc định là hôm nay, giới hạn
trong khoảng mùa lấy từ API. Không đổi ID hoặc xóa dữ liệu các mùa cũ.

Đã bỏ nhập/sửa trận và số liệu ra sân thủ công ở cả frontend lẫn backend.
Các chức năng nhân sự, hợp đồng, đăng ký, y tế và chuyển nhượng vẫn giữ.
Đăng ký/hợp đồng không được tự tạo từ một lần ra sân trong API: nếu cầu thủ
chưa có hồ sơ hoặc điều kiện đăng ký phù hợp, nhập thống kê sẽ bị chặn.

Trang Trận đấu chỉ có lịch sắp tới, lịch sử và danh sách đang diễn ra khi có.
Dữ liệu nguồn được ghép vào trận theo ID hoặc cặp chủ/khách trong mùa,
không hiển thị bảng API riêng hoặc bảng tiến độ đồng bộ. Lỗi nguồn hiện
bằng thông báo ngắn; trạng thái chi tiết vẫn có trong phản hồi backend.
Phút live phải do nguồn cung cấp, thiếu thì hiện chưa có; dữ liệu có thể trễ
theo gói. Trận live/tạm dừng hoặc trạng thái chưa có trong enum chỉ hiển thị
từ cache. Không đổi trận đã hoàn tất thành chưa thi đấu để nhận dữ liệu nguồn.
Trận đã có giữ sân trong CSDL khi API không trả sân cụ thể. Trận mới dùng
sân của CLB chủ nhà từ API khi thiếu sân trận; độ chính xác phụ thuộc nguồn.

Thống kê chỉ được nhập khi API trả đủ đội hình, bàn thắng, thẻ, thay người
và trận đã kết thúc trong thời gian thi đấu thông thường. Phút quy đổi từ
đội hình/thay người/thẻ đỏ, 0–90 không cộng bù giờ; vào sân phút 90 vẫn có
lần ra sân 0 phút. Không tự cắt dữ liệu hiệp phụ về 90 phút. Trường kiến tạo
thiếu giữ NULL. Tổng bàn trong danh sách sự kiện phải khớp tỷ số theo nguồn,
có xét phản lưới. Không xóa lịch sử ra sân khi nguồn khác dữ liệu đã lưu.

Khớp CLB theo ID provider và bảng tên đối chiếu rõ ràng. Trận ban đầu khớp
theo mùa và cặp chủ/khách duy nhất; sau đó giữ ID ánh xạ. Cầu thủ được xác
minh bằng ID, tên đầy đủ chuẩn hóa và ngày sinh; trường hợp không duy nhất
bị chặn. File `app/.football-data-bindings.properties` lưu ánh xạ cục bộ,
không chứa token, không được commit. Nếu chuyển sang CSDL khác, cần đối
chiếu lại file ánh xạ, không dùng các ID cũ một cách mặc định.

Nếu gói không có chi tiết cầu thủ, backend ghi nhận thiếu và giữ số liệu cũ.
Không có dữ liệu không có nghĩa là 0. Khi nguồn lỗi, giữ bản gần nhất và
hiển thị lỗi. Đồng bộ chạy nền, không có nghĩa mọi trận đã lưu.
Mỗi trận được kiểm tra và commit riêng; trận lỗi rollback, các trận hợp lệ
khác có thể thành công. Chưa có bộ sửa tay để vượt các ràng buộc này.

## Tổng quan và ảnh hồ sơ

Tổng quan có kết quả trận hoàn tất gần nhất và đếm ngược trận tiếp theo của
mùa đang chọn theo thời gian hiện tại. Đồng hồ chạy mỗi giây, dùng giờ Việt
Nam cho lịch lưu trong DB và UTC từ API; không cộng sai múi giờ theo máy xem.
Trận hoãn/hủy không có đếm ngược. Trận chưa chốt giờ có thông báo riêng.
Đếm về 0 chỉ hiện chờ cập nhật, không tự suy ra trận đang thi đấu.
Thống kê và phân bố đội hình vẫn theo ngày báo cáo được chọn.

Phân bố hiển thị đủ Thủ môn, Hậu vệ, Tiền vệ, Tiền đạo với tên vị trí,
số lượng và thanh tỷ lệ. Theo yêu cầu, khi mùa mới chưa có đăng ký, danh sách
và phân bố dùng đăng ký cuối mùa 2025–2026 để tham chiếu, có nhãn mùa nguồn.
Seed hiện có 33 đăng ký tham chiếu trong 34 hồ sơ. Không tạo hợp đồng hoặc
đăng ký giả cho mùa mới; Q01–Q15 và thành tích CLB giữ đúng mùa đang chọn.
Các ô thống kê không dựng lại khi dữ liệu không đổi trong lượt cập nhật nền.

34 ảnh chân dung được lưu trong `app/web/photos`, lấy từ trang chính thức
Manchester United và tài nguyên ảnh Premier League. `player-photos.js`
đối chiếu tên/ngày sinh, `photo-credits.html` ghi từng nguồn. Không cần đổi
schema hoặc truy cập mạng để tải ảnh khi mở ứng dụng. Hồ sơ mới chưa có
ảnh hoặc ảnh lỗi dùng chữ viết tắt. Trong Cầu thủ, chọn **Tất cả cầu thủ** để
xem cả hồ sơ chưa đăng ký. Bấm hàng cầu thủ để mở hồ sơ: số áo thuộc đăng ký,
cờ quốc tịch, thống kê và radar. Thống kê ưu tiên mùa đang chọn; chưa có thì
đọc riêng mùa tham chiếu và ghi rõ mùa nguồn. Radar so sánh số trận, phút,
bàn thắng, trận đủ 90 phút và phút/trận với mức cao nhất trong cùng đội/mùa;
không phải điểm kỹ năng. Giá trị chưa biết hiển thị dấu gạch, kiến tạo giữ NULL.

26 logo CLB và 17 cờ quốc tịch là WebP cục bộ tại `app/web/crests`,
`app/web/flags`. `football-assets.js` chứa ánh xạ và nguồn ESPN/FlagCDN.
Chạy `python app/tools/fetch_badges.py` khi cần cập nhật tài nguyên; không ghi DB.

Chỉ khi cần cập nhật tài nguyên ảnh: `python app/tools/fetch_player_photos.py`
(cần `beautifulsoup4`, `Pillow`). Script lấy danh sách đối chiếu từ seed,
không kết nối hoặc ghi MySQL, không tạo hợp đồng/đăng ký từ ảnh.

## Khởi tạo lần đầu bằng mysql CLI

Chỉ thực hiện khi các bảng chưa tồn tại. Không chạy lại bộ khởi tạo trên CSDL
đang có dữ liệu. Không dùng `--force` để bỏ qua lỗi khởi tạo.

Từ PowerShell tại thư mục gốc project, chạy lệnh sau trên máy Windows này:

```powershell
& "C:\Program Files\MySQL\MySQL Server 9.7\bin\mysql.exe" --default-character-set=utf8mb4 --abort-source-on-error -u root -p --execute="SOURCE submission.sql;"
```

Nhập mật khẩu ở lời nhắc, không ghi mật khẩu vào file. Nếu máy cài MySQL ở
đường dẫn khác, thay đường dẫn `mysql.exe`. Các lệnh SOURCE dùng đường dẫn
tương đối với thư mục hiện hành nên phải chạy từ thư mục gốc project.

## Khởi tạo lần đầu bằng Workbench

Mở kết nối, lần lượt mở và chạy toàn bộ từng file `01_schema.sql` đến
`05_reports.sql` theo thứ tự. Chỉ tiếp tục file sau khi file trước thành công.
Mỗi file tự chọn CSDL và thiết lập bộ ký tự, múi giờ.

`submission.sql` dùng lệnh SOURCE của mysql CLI; không chạy file điều phối
này như SQL thông thường trong Workbench.

## Chạy riêng báo cáo

Sau khi đã có CSDL, có thể chạy riêng `05_reports.sql` trong Workbench để xem
kết quả. Các tham số minh họa được đặt ở đầu file. Khi ứng dụng web chạy,
Java thiết lập CLB, mùa, ngày theo ngữ cảnh người dùng và chỉ thực thi các
truy vấn Q01–Q15, không thực thi phần khởi tạo hoặc dữ liệu mẫu.
