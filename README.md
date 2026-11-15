# Multi-room Chat System (Java)

Bài tập lớn môn Lập trình mạng: hệ thống chat nhiều phòng theo mô hình Client–Server, dùng TCP socket, đa luồng và WebSocket. Chỉ dùng JDK 11 trở lên, không cần thư viện ngoài.

## Mở bằng NetBeans

1. **File → Open Project...** → chọn thư mục `ChatSystem` (biểu tượng Maven) → Open.
2. Lần đầu NetBeans sẽ tải các plugin Maven, cần có Internet.
3. Chạy bản web: bấm **Run Project (F6)**, rồi mở `http://localhost:8080`.
4. Chạy từng lớp riêng: nhấp phải vào file `.java` → **Run File** (Shift+F6).

> Nếu NetBeans hỏi JDK: dùng JDK 11 trở lên (**Tools → Java Platforms**).

## Cấu trúc project và phân công

Mỗi file chỉ có **một chủ sở hữu**, mỗi người chỉ sửa file của mình để không xung đột khi gộp code.

```
ChatSystem/
├── pom.xml                              TV1
├── .gitignore                           TV1
├── PROTOCOL.md                          TV2  (đặc tả giao thức)
├── README.md                            TV3
└── src/main/
    ├── java/chatapp/
    │   ├── server/                      TV1 - Server & Connection
    │   │   ├── Server.java                  Nhận kết nối TCP, thread pool, ghi log
    │   │   └── ClientHandler.java           Một luồng/client: đọc, ghi socket, dọn dẹp khi ngắt kết nối
    │   ├── web/                         TV1
    │   │   └── WebServer.java               HTTP + cầu nối WebSocket -> chat server
    │   ├── logic/                       TV2 - Room Management & Messaging
    │   │   ├── ChatUser.java                Hợp đồng giữa tầng mạng và tầng logic
    │   │   ├── RoomManager.java             Người dùng, phòng, broadcast, tin nhắn riêng
    │   │   └── CommandProcessor.java        Phân tích lệnh NICK/JOIN/MSG/PM...
    │   └── client/                      TV3 - Client
    │       ├── CliClient.java               Client dòng lệnh
    │       ├── GuiClient.java               Client Swing
    │       └── Protocol.java                Dịch lệnh /join, /pm... thành lệnh gửi server
    └── resources/web/                   TV3
        ├── index.html, style.css, app.js    Giao diện web
```

Quan hệ phụ thuộc: `client` độc lập hoàn toàn (nói chuyện với server qua socket); `server` dùng `logic` thông qua hợp đồng `ChatUser` + `CommandProcessor`; `web` dùng `server`.

### Quy tắc làm việc nhóm

- Mỗi người một nhánh (`tv1-network`, `tv2-logic`, `tv3-client`), chỉ sửa file của mình, xong tạo Pull Request vào `main`.
- `git pull` trước khi bắt đầu làm và trước khi push.
- Muốn đổi hợp đồng (`ChatUser`, chữ ký `CommandProcessor`, `PROTOCOL.md`) thì báo cả nhóm trước.
- Cần sửa file của người khác thì nhờ chủ file hoặc tạo issue, đừng sửa trực tiếp.

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

Giao thức chi tiết xem [PROTOCOL.md](PROTOCOL.md).

## Thiết kế

- **Đa luồng**: `ExecutorService` (cached thread pool) cấp một luồng `ClientHandler` cho mỗi kết nối.
- **Tách tầng**: tầng mạng (`server`) chỉ đọc/ghi dòng; tầng logic (`logic`) không biết gì về socket, chỉ làm việc với interface `ChatUser`.
- **Đồng bộ**: trạng thái dùng chung (`users`, `rooms`) được bảo vệ bằng `synchronized` trong `RoomManager`; mỗi client có `send()` đồng bộ riêng để tránh ghi chồng.
- **Phòng**: mỗi client ở đúng một phòng tại một thời điểm; phòng đã tạo được giữ lại kể cả khi không còn ai (hiển thị 0 người), và lưu 100 tin gần nhất để phát lại khi vào phòng.
- **Nickname**: duy nhất, không phân biệt hoa/thường, 2–20 ký tự.
- **Ngắt kết nối đột ngột**: phát hiện qua `IOException`/EOF, server dọn dẹp và thông báo cho phòng.
- **Bản web**: `WebServer` tự cài đặt WebSocket (RFC 6455: bắt tay SHA-1/Base64, đọc frame có mask, gửi frame text/ping/pong/close). Mỗi kết nối WebSocket mở một kết nối TCP riêng tới chat server nên trình duyệt hoạt động như một client bình thường.

## Hướng mở rộng

Lưu lịch sử chat vào file/DB, mật khẩu cho phòng, gửi file/ảnh, mã hoá TLS (`SSLServerSocket` / `wss://`), chuyển sang NIO.
