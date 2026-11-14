# Multi-room Chat System (Java)

Bài tập lớn môn Lập trình mạng: hệ thống chat nhiều phòng theo mô hình Client–Server, dùng TCP socket, đa luồng và WebSocket. Chỉ dùng JDK 11 trở lên, không cần thư viện ngoài.

## Mở bằng NetBeans

1. **File → Open Project...** → chọn thư mục `ChatSystem` (biểu tượng Maven) → Open.
2. Lần đầu NetBeans sẽ tải các plugin Maven, cần có Internet.
3. Chạy bản web: bấm **Run Project (F6)**, rồi mở `http://localhost:8080`.
4. Chạy từng lớp riêng: nhấp phải vào file `.java` → **Run File** (Shift+F6).

> Nếu NetBeans hỏi JDK: dùng JDK 11 trở lên (**Tools → Java Platforms**).

## Cấu trúc project

```
ChatSystem/
├── pom.xml
└── src/main/
    ├── java/chatapp/
    │   ├── server/
    │   │   ├── Server.java          Nhận kết nối TCP, quản lý người dùng, phòng, broadcast
    │   │   └── ClientHandler.java   Một luồng cho mỗi client: đọc lệnh, xử lý, phản hồi
    │   ├── common/
    │   │   └── Protocol.java        Chuyển lệnh /join, /pm... thành lệnh gửi lên server
    │   ├── client/
    │   │   ├── CliClient.java       Client dòng lệnh
    │   │   └── GuiClient.java       Client Swing
    │   └── web/
    │       └── WebServer.java       HTTP + cầu nối WebSocket -> chat server (bản web)
    └── resources/web/index.html     Giao diện web (HTML/CSS/JS thuần)
```

## Các cách chạy

| Mục đích | Lớp chạy |
|---|---|
| **Bản web** (khởi động luôn chat server TCP cổng 5000, web cổng 8080) | `chatapp.web.WebServer` |
| Chỉ chat server TCP (cổng 5000) | `chatapp.server.Server` |
| Client giao diện Swing | `chatapp.client.GuiClient` |
| Client dòng lệnh | `chatapp.client.CliClient` |

Ví dụ thử đầy đủ: chạy `WebServer`, mở trình duyệt `http://localhost:8080`, rồi chạy thêm `GuiClient` (host `localhost`, cổng `5000`). Client web và client Swing chat chung được với nhau.

Chạy bằng dòng lệnh (không cần NetBeans):
```bash
mvn package
java -jar target/ChatSystem-1.0.jar        # = WebServer
java -cp target/ChatSystem-1.0.jar chatapp.client.GuiClient
```

Muốn chat qua nhiều máy: người khác mở `http://<IP-máy-bạn>:8080` (hoặc client Swing nhập IP đó, cổng 5000); nhớ mở cổng trên firewall.

## Lệnh phía người dùng

| Lệnh | Ý nghĩa |
|---|---|
| `/join <phòng>` | Vào phòng, tự tạo nếu chưa có |
| `/leave` | Về phòng `lobby` |
| `/rooms` | Danh sách phòng và số người |
| `/users` | Người dùng trong phòng hiện tại |
| `/pm <tên> <nội dung>` | Nhắn riêng |
| `/quit` | Thoát |
| (chữ thường) | Chat trong phòng hiện tại |

Trên GUI/web: nhấp vào phòng để vào, nhấp vào tên người dùng để nhắn riêng.

## Giao thức (text, mỗi dòng một thông điệp, UTF-8)

Client → Server: `NICK <tên>`, `JOIN <phòng>`, `LEAVE`, `MSG <nd>`, `PM <tên> <nd>`, `ROOMS`, `USERS`, `WATCH`, `QUIT`

Server → Client (các trường cách nhau bằng TAB):
`INFO`, `ERROR`, `NICKOK`, `JOINED`, `SYSTEM`, `CHAT`, `PM`, `ROOMS`, `USERS`

`WATCH` bật chế độ server tự đẩy danh sách phòng/người dùng mỗi khi có thay đổi (GUI và web dùng).

## Thiết kế

- **Đa luồng**: `ExecutorService` (cached thread pool) cấp một luồng `ClientHandler` cho mỗi kết nối.
- **Đồng bộ**: trạng thái dùng chung (`users`, `rooms`) được bảo vệ bằng `synchronized` trong `Server`; mỗi client có `send()` đồng bộ riêng để tránh ghi chồng.
- **Phòng**: mỗi client ở đúng một phòng tại một thời điểm; phòng trống (trừ `lobby`) tự bị xoá.
- **Nickname**: duy nhất, không phân biệt hoa/thường, 2–20 ký tự.
- **Ngắt kết nối đột ngột**: phát hiện qua `IOException`/EOF, server dọn dẹp và thông báo cho phòng.
- **Bản web**: `WebServer` tự cài đặt WebSocket (RFC 6455: bắt tay SHA-1/Base64, đọc frame có mask, gửi frame text/ping/pong/close). Mỗi kết nối WebSocket mở một kết nối TCP riêng tới chat server nên trình duyệt hoạt động như một client bình thường.

## Hướng mở rộng

Lưu lịch sử chat vào file/DB, mật khẩu cho phòng, gửi file, mã hoá TLS (`SSLServerSocket` / `wss://`), chuyển sang NIO.
