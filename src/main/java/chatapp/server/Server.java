package chatapp.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Multi-room chat server (TCP, mỗi client một luồng).
 * Chạy: java Server [port]   (mặc định 5000)
 */
public class Server {
    public static final int DEFAULT_PORT = 5000;
    public static final String LOBBY = "lobby";

    private final int port;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    // Toàn bộ trạng thái dưới đây được bảo vệ bởi synchronized(this)
    private final Map<String, ClientHandler> users = new HashMap<>();             // nick(lowercase) -> handler
    private final Map<String, Set<ClientHandler>> rooms = new TreeMap<>();        // tên phòng(lowercase) -> thành viên

    public Server(int port) {
        this.port = port;
        rooms.put(LOBBY, new LinkedHashSet<>());
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log("Server đang lắng nghe tại cổng " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                log("Kết nối mới từ " + socket.getRemoteSocketAddress());
                pool.execute(new ClientHandler(socket, this));
            }
        }
    }

    // ---------- Quản lý người dùng ----------

    synchronized boolean register(ClientHandler h, String nick) {
        String key = nick.toLowerCase();
        if (users.containsKey(key)) return false;
        users.put(key, h);
        h.name = nick;
        return true;
    }

    /** Gọi khi client ngắt kết nối (bình thường hoặc đột ngột). */
    synchronized void disconnect(ClientHandler h) {
        if (h.name == null) return;
        leaveCurrentRoom(h);
        users.remove(h.name.toLowerCase());
        log(h.name + " đã thoát");
        h.name = null;
        broadcastLists();
    }

    // ---------- Quản lý phòng ----------

    synchronized void join(ClientHandler h, String room) {
        room = room.toLowerCase();
        if (room.equals(h.room)) {
            h.send("INFO\tBạn đang ở phòng '" + room + "' rồi");
            return;
        }
        leaveCurrentRoom(h);
        boolean created = !rooms.containsKey(room);
        rooms.computeIfAbsent(room, k -> new LinkedHashSet<>()).add(h);
        h.room = room;
        h.send("JOINED\t" + room);
        if (created) h.send("INFO\tĐã tạo phòng mới '" + room + "'");
        broadcastRoom(room, "SYSTEM\t" + room + "\t" + h.name + " đã vào phòng", h);
        log(h.name + " vào phòng " + room);
        broadcastLists();
    }

    /** Rời phòng hiện tại (không gửi danh sách). Phòng trống (trừ lobby) sẽ bị xoá. */
    private void leaveCurrentRoom(ClientHandler h) {
        if (h.room == null) return;
        String old = h.room;
        Set<ClientHandler> members = rooms.get(old);
        if (members != null) {
            members.remove(h);
            broadcastRoom(old, "SYSTEM\t" + old + "\t" + h.name + " đã rời phòng", null);
            if (members.isEmpty() && !old.equals(LOBBY)) rooms.remove(old);
        }
        h.room = null;
    }

    // ---------- Tin nhắn ----------

    synchronized void chat(ClientHandler h, String text) {
        if (h.room == null) {
            h.send("ERROR\tBạn chưa ở trong phòng nào");
            return;
        }
        broadcastRoom(h.room, "CHAT\t" + h.room + "\t" + h.name + "\t" + text, null);
    }

    synchronized void privateMessage(ClientHandler from, String toNick, String text) {
        ClientHandler to = users.get(toNick.toLowerCase());
        if (to == null) {
            from.send("ERROR\tKhông tìm thấy người dùng '" + toNick + "'");
            return;
        }
        to.send("PM\t" + from.name + "\t" + to.name + "\t" + text);
        if (to != from) from.send("PM\t" + from.name + "\t" + to.name + "\t" + text);
    }

    private void broadcastRoom(String room, String line, ClientHandler exclude) {
        Set<ClientHandler> members = rooms.get(room);
        if (members == null) return;
        for (ClientHandler m : new ArrayList<>(members)) {
            if (m != exclude) m.send(line);
        }
    }

    // ---------- Danh sách (phòng / người dùng) ----------

    synchronized String roomsLine() {
        StringJoiner sj = new StringJoiner(",");
        for (Map.Entry<String, Set<ClientHandler>> e : rooms.entrySet()) {
            sj.add(e.getKey() + ":" + e.getValue().size());
        }
        return "ROOMS\t" + sj;
    }

    synchronized String usersLine(String room) {
        StringJoiner sj = new StringJoiner(",");
        Set<ClientHandler> members = room == null ? null : rooms.get(room);
        if (members != null) for (ClientHandler m : members) sj.add(m.name);
        return "USERS\t" + (room == null ? "" : room) + "\t" + sj;
    }

    /** Gửi danh sách phòng + user cho client đã bật WATCH (dùng cho GUI). */
    synchronized void pushTo(ClientHandler h) {
        if (!h.watch) return;
        h.send(roomsLine());
        h.send(usersLine(h.room));
    }

    private void broadcastLists() {
        for (ClientHandler h : new ArrayList<>(users.values())) pushTo(h);
    }

    private static final java.io.PrintStream OUT =
            new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true,
                    java.nio.charset.StandardCharsets.UTF_8);

    public static void log(String msg) {
        OUT.println("[" + java.time.LocalTime.now().withNano(0) + "] " + msg);
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        new Server(port).start();
    }
}
