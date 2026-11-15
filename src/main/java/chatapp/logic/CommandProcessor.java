package chatapp.logic;

/**
 * Phân tích lệnh từ client và gọi RoomManager (chủ sở hữu: TV2).
 *
 * <p>Tầng mạng (TV1) chỉ gọi ba phương thức public: {@link #onConnect},
 * {@link #handle}, {@link #onDisconnect}.
 *
 * <p>Lệnh client -> server: NICK, JOIN, LEAVE, MSG, PM, ROOMS, USERS, WATCH, QUIT.
 * Chi tiết định dạng xem PROTOCOL.md.
 */
public class CommandProcessor {
    private final RoomManager rooms;

    public CommandProcessor(RoomManager rooms) {
        this.rooms = rooms;
    }

    /** Gọi ngay khi client vừa kết nối. */
    public void onConnect(ChatUser u) {
        u.send("INFO\tChào mừng! Hãy đặt nickname bằng lệnh: NICK <tên>");
    }

    /** Gọi khi kết nối kết thúc (dù bình thường hay đột ngột). */
    public void onDisconnect(ChatUser u) {
        rooms.disconnect(u);
    }

    /**
     * Xử lý một dòng lệnh.
     *
     * @return false nếu cần đóng kết nối (client gửi QUIT), true nếu tiếp tục
     */
    public boolean handle(ChatUser u, String line) {
        if (line.isEmpty()) return true;
        String[] p = line.split("\\s+", 2);
        String cmd = p[0].toUpperCase();
        String arg = p.length > 1 ? p[1].trim() : "";

        if (u.getName() == null && !cmd.equals("NICK") && !cmd.equals("QUIT")) {
            u.send("ERROR\tHãy đặt nickname trước: NICK <tên>");
            return true;
        }

        switch (cmd) {
            case "NICK":
                if (u.getName() != null) {
                    u.send("ERROR\tBạn đã có nickname là '" + u.getName() + "'");
                } else if (!valid(arg, 2, 20)) {
                    u.send("ERROR\tNickname 2-20 ký tự (chữ, số, _ hoặc -)");
                } else if (!rooms.register(u, arg)) {
                    u.send("ERROR\tNickname '" + arg + "' đã có người dùng");
                } else {
                    u.send("NICKOK\t" + u.getName());
                    rooms.join(u, RoomManager.LOBBY);
                }
                break;
            case "JOIN":
                if (!valid(arg, 1, 20)) u.send("ERROR\tTên phòng 1-20 ký tự (chữ, số, _ hoặc -)");
                else rooms.join(u, arg);
                break;
            case "LEAVE":
                rooms.join(u, RoomManager.LOBBY);
                break;
            case "MSG":
                if (!arg.isEmpty()) rooms.chat(u, arg.replace('\t', ' '));
                break;
            case "PM":
                String[] q = arg.split("\\s+", 2);
                if (q.length < 2 || q[1].isBlank()) u.send("ERROR\tCú pháp: PM <tên> <nội dung>");
                else rooms.privateMessage(u, q[0], q[1].replace('\t', ' '));
                break;
            case "ROOMS":
                u.send(rooms.roomsLine());
                break;
            case "USERS":
                u.send(rooms.usersLine(u.getRoom()));
                break;
            case "WATCH":
                u.setWatching(true);
                rooms.pushTo(u);
                break;
            case "QUIT":
                u.send("INFO\tTạm biệt!");
                return false;
            default:
                u.send("ERROR\tLệnh không hợp lệ: " + cmd);
        }
        return true;
    }

    private static boolean valid(String s, int min, int max) {
        return s.length() >= min && s.length() <= max && s.matches("[\\p{L}\\p{N}_-]+");
    }
}
