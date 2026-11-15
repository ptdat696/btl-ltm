# Đặc tả giao thức (chủ sở hữu: TV2)

Giao thức dạng text, mỗi thông điệp một dòng, mã hoá UTF-8, kết thúc bằng `\n`.
Cả TV1 (mạng), TV2 (logic) và TV3 (client) đều dựa vào tài liệu này. **Muốn đổi phải báo cả nhóm.**

## Client → Server

| Lệnh | Ý nghĩa | Ghi chú |
|---|---|---|
| `NICK <tên>` | Đặt nickname, vào phòng `lobby` | 2–20 ký tự (chữ, số, `_`, `-`), không phân biệt hoa/thường, không trùng |
| `JOIN <phòng>` | Vào phòng (tự tạo nếu chưa có) | 1–20 ký tự (chữ, số, `_`, `-`), lưu dạng chữ thường |
| `LEAVE` | Về phòng `lobby` | |
| `MSG <nội dung>` | Gửi tin cho cả phòng hiện tại | |
| `PM <tên> <nội dung>` | Nhắn riêng | Người gửi cũng nhận lại bản sao |
| `ROOMS` | Xin danh sách phòng | |
| `USERS` | Xin danh sách người trong phòng hiện tại | |
| `WATCH` | Bật chế độ server tự đẩy `ROOMS`/`USERS` mỗi khi có thay đổi | GUI và web dùng |
| `QUIT` | Thoát | Server đóng kết nối |

Chưa có nickname thì mọi lệnh (trừ `NICK`, `QUIT`) đều bị từ chối bằng `ERROR`.

## Server → Client (các trường cách nhau bằng ký tự TAB)

| Thông điệp | Định dạng | Ý nghĩa |
|---|---|---|
| `INFO` | `INFO<TAB>nội dung` | Thông tin chung |
| `ERROR` | `ERROR<TAB>nội dung` | Lỗi |
| `NICKOK` | `NICKOK<TAB>nick` | Đặt nickname thành công |
| `JOINED` | `JOINED<TAB>phòng` | Bạn vừa vào phòng này |
| `SYSTEM` | `SYSTEM<TAB>phòng<TAB>nội dung` | Thông báo hệ thống (ai đó vào/rời phòng) |
| `CHAT` | `CHAT<TAB>phòng<TAB>người gửi<TAB>nội dung` | Tin nhắn phòng |
| `HISTORY` | `HISTORY<TAB>phòng<TAB>HH:mm<TAB>người gửi<TAB>nội dung` | Tin cũ của phòng, gửi ngay sau `JOINED` (tối đa 100 tin gần nhất của phòng) |
| `PM` | `PM<TAB>người gửi<TAB>người nhận<TAB>nội dung` | Tin nhắn riêng |
| `ROOMS` | `ROOMS<TAB>tên:số_người,tên:số_người,...` | Danh sách phòng |
| `USERS` | `USERS<TAB>phòng<TAB>nick1,nick2,...` | Người dùng trong phòng |

Ký tự TAB trong nội dung tin nhắn được server đổi thành dấu cách.

## Ví dụ

```
C: NICK alice
S: NICKOK<TAB>alice
S: JOINED<TAB>lobby
C: JOIN java
S: JOINED<TAB>java
C: MSG xin chào
S: CHAT<TAB>java<TAB>alice<TAB>xin chào
```

## Hợp đồng giữa tầng mạng và tầng logic

Chỉ có ba điểm tiếp xúc giữa TV1 và TV2 (xem `ChatUser.java`, `CommandProcessor.java`):

1. `ChatUser`: interface do `ClientHandler` (TV1) cài đặt.
2. `CommandProcessor.onConnect(ChatUser)`, `handle(ChatUser, String) : boolean`, `onDisconnect(ChatUser)`: TV1 gọi sang TV2.
3. `new RoomManager(Consumer<String> logger)`: TV1 tạo và truyền hàm ghi log.
