# ATM Transaction Simulation API

## Yêu cầu
- Java 17+
- Maven 3.8+ (hoặc dùng `mvnw` wrapper đi kèm)
- MySQL 8.0+
- Redis 6+

---

## Cài đặt & Chạy

tạo database
```bash
# Linux/Mac
mysql -u root -p < src/main/resources/db/schema.sql

# Windows (dùng cmd.exe, không phải PowerShell)
mysql -u root -p < src\main\resources\db\schema.sql
```



sửa `src/main/resources/application.properties`:
```properties
db.url=jdbc:mysql://localhost:3306/atm_db?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true
db.username=root
db.password=YOUR_PASSWORD

redis.host=localhost
redis.port=6379
redis.password=
```

Build & Run
```bash
# Linux/Mac
./mvnw clean package
java -jar target/atm-api.jar

# Windows
mvnw.cmd clean package
java -jar target/atm-api.jar
```

Mở trình duyệt
```
http://localhost:8080
```

---

## Chạy bằng Docker
```bash
# Copy file cấu hình
copy application.properties.example src\main\resources\application.properties
# Sửa db.password trong file vừa copy

# Chạy toàn bộ stack
docker compose up --build
```

## API

| Method | Path | Mô tả |
|--------|------|-|
| POST | `/api/auth/login` | Đăng nhập, nhận session token |
| POST | `/api/auth/logout` | Đăng xuất |
| GET | `/api/account/info`  | Số dư và thông tin tài khoản |
| POST | `/api/account/deposit` | Nạp tiền |
| POST | `/api/account/withdraw` | Rút tiền |
| PUT | `/api/account/change-pin` | Đổi PIN |
| GET | `/api/account/history` | Lịch sử giao dịch |
| GET | `/api/report/monthly`  | Báo cáo theo tháng |

---

## kỹ thuật sử dụng trong dự án

Phần rút tiền dùng 3 lớp bảo vệ race condition: Redis distributed lock (`SET NX EX`) để chặn concurrent request trên cùng tài khoản, `SELECT FOR UPDATE` để lock DB row, và JDBC transaction để đảm bảo balance update và transaction log là atomic.

PIN được hash bằng jBCrypt (cost factor 10). Session lưu Redis TTL 30 phút. Brute-force bị chặn sau 3 lần sai liên tiếp, tài khoản tạm khóa 5 phút.

Bảng `transactions` partition theo tháng (RANGE partition) — MySQL chỉ scan partition cần thiết khi query. `report_monthly` là summary table được scheduled job upsert mỗi đêm 2AM. Connection pool dùng HikariCP, size 10.
