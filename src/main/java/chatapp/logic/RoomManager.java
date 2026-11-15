package chatapp.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Quản lý người dùng, phòng và phát tin (chủ sở hữu: TV2).
 *
 * <p>Toàn bộ trạng thái dùng chung được bảo vệ bằng {@code synchronized} trên đối tượng này.
 * Mọi thay đổi người dùng/phòng PHẢI đi qua lớp này. Các phương thức để package-private
 * vì chỉ {@link CommandProcessor} gọi; tầng mạng (TV1) chỉ cần dùng constructor.
 */
public class RoomManager {
    public static final String LOBBY = "lobby";

    private final Map<String, ChatUser> users = new HashMap<>();             // nick (chữ thường) -> user
    private final Map<String, Set<ChatUser>> rooms = new TreeMap<>();        // tên phòng (chữ thường) -> thành viên
    private final Consumer<String> logger;

    /** Số tin gần nhất được lưu cho mỗi phòng. */
    private static final int HISTORY_SIZE = 100;
    private static final java.time.format.DateTimeFormatter TIME = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
    /** Lịch sử theo phòng, mỗi phần tử là "HH:mm<TAB>người gửi<TAB>nội dung". */
    private final Map<String, java.util.Deque<String>> history = new HashMap<>();

    /** @param logger nơi ghi log (tầng mạng truyền vào, ví dụ Server::log) */
    public RoomManager(Consumer<String> logger) {
        this.logger = logger;
        rooms.put(LOBBY, new LinkedHashSet<>());
    }

    // ---------- Người dùng ----------

    synchronized boolean register(ChatUser u, String nick) {
        String key = nick.toLowerCase();
        if (users.containsKey(key)) return false;
        users.put(key, u);
        u.setName(nick);
        logger.accept(nick + " đăng nhập từ " + u.getAddress());
        return true;
    }

    /** Gọi khi client ngắt kết nối (bình thường hoặc đột ngột). */
    synchronized void disconnect(ChatUser u) {
        if (u.getName() == null) return;
        leaveCurrentRoom(u);
        users.remove(u.getName().toLowerCase());
        logger.accept(u.getName() + " đã thoát");
        u.setName(null);
        broadcastLists();
    }

    // ---------- Phòng ----------

    synchronized void join(ChatUser u, String room) {
        room = room.toLowerCase();
        if (room.equals(u.getRoom())) {
            u.send("INFO\tBạn đang ở phòng '" + room + "' rồi");
            return;
        }
        leaveCurrentRoom(u);
        boolean created = !rooms.containsKey(room);
        rooms.computeIfAbsent(room, k -> new LinkedHashSet<>()).add(u);
        u.setRoom(room);
        u.send("JOINED\t" + room);
        java.util.Deque<String> past = history.get(room);
        if (past != null) for (String h : past) u.send("HISTORY\t" + room + "\t" + h);   // phát lại tin cũ
        if (created) u.send("INFO\tĐã tạo phòng mới '" + room + "'");
        broadcastRoom(room, "SYSTEM\t" + room + "\t" + u.getName() + " đã vào phòng", u);
        logger.accept(u.getName() + " vào phòng " + room);
        broadcastLists();
    }

    /** Rời phòng hiện tại (không gửi danh sách). Phòng đã tạo được giữ lại kể cả khi không còn ai. */
    private void leaveCurrentRoom(ChatUser u) {
        String old = u.getRoom();
        if (old == null) return;
        Set<ChatUser> members = rooms.get(old);
        if (members != null) {
            members.remove(u);
            broadcastRoom(old, "SYSTEM\t" + old + "\t" + u.getName() + " đã rời phòng", null);
        }
        u.setRoom(null);
    }

    // ---------- Tin nhắn ----------

    synchronized void chat(ChatUser u, String text) {
        if (u.getRoom() == null) {
            u.send("ERROR\tBạn chưa ở trong phòng nào");
            return;
        }
        java.util.Deque<String> h = history.computeIfAbsent(u.getRoom(), k -> new java.util.ArrayDeque<>());
        h.addLast(java.time.LocalTime.now().format(TIME) + "\t" + u.getName() + "\t" + text);
        if (h.size() > HISTORY_SIZE) h.removeFirst();
        broadcastRoom(u.getRoom(), "CHAT\t" + u.getRoom() + "\t" + u.getName() + "\t" + text, null);
    }

    synchronized void privateMessage(ChatUser from, String toNick, String text) {
        ChatUser to = users.get(toNick.toLowerCase());
        if (to == null) {
            from.send("ERROR\tKhông tìm thấy người dùng '" + toNick + "'");
            return;
        }
        String line = "PM\t" + from.getName() + "\t" + to.getName() + "\t" + text;
        to.send(line);
        if (to != from) from.send(line);
    }

    private void broadcastRoom(String room, String line, ChatUser exclude) {
        Set<ChatUser> members = rooms.get(room);
        if (members == null) return;
        for (ChatUser m : new ArrayList<>(members)) {
            if (m != exclude) m.send(line);
        }
    }

    // ---------- Danh sách phòng / người dùng ----------

    synchronized String roomsLine() {
        StringJoiner sj = new StringJoiner(",");
        for (Map.Entry<String, Set<ChatUser>> e : rooms.entrySet()) {
            sj.add(e.getKey() + ":" + e.getValue().size());
        }
        return "ROOMS\t" + sj;
    }

    synchronized String usersLine(String room) {
        StringJoiner sj = new StringJoiner(",");
        Set<ChatUser> members = room == null ? null : rooms.get(room);
        if (members != null) for (ChatUser m : members) sj.add(m.getName());
        return "USERS\t" + (room == null ? "" : room) + "\t" + sj;
    }

    /** Gửi danh sách phòng + người dùng cho client đã bật WATCH (GUI/web dùng). */
    synchronized void pushTo(ChatUser u) {
        if (!u.isWatching()) return;
        u.send(roomsLine());
        u.send(usersLine(u.getRoom()));
    }

    private void broadcastLists() {
        for (ChatUser u : new ArrayList<>(users.values())) pushTo(u);
    }
}
