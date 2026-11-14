package chatapp.server;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Xử lý một client (chạy trong luồng riêng).
 *
 * Lệnh client -> server (mỗi lệnh một dòng):
 *   NICK <tên> | JOIN <phòng> | LEAVE | MSG <nội dung> | PM <tên> <nội dung>
 *   ROOMS | USERS | WATCH | QUIT
 *
 * Phản hồi server -> client (các trường cách nhau bằng TAB):
 *   INFO, ERROR, NICKOK, JOINED, SYSTEM, CHAT, PM, ROOMS, USERS
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final Server server;
    private PrintWriter out;

    volatile String name;   // null nếu chưa đặt nickname
    volatile String room;   // phòng hiện tại
    volatile boolean watch; // true nếu client muốn nhận danh sách tự động

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            out = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);
            send("INFO\tChào mừng! Hãy đặt nickname bằng lệnh: NICK <tên>");
            String line;
            while ((line = in.readLine()) != null) {
                if (!handle(line.trim())) break;
            }
        } catch (IOException ignored) {
            // client ngắt đột ngột
        } finally {
            server.disconnect(this);
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    private boolean handle(String line) {
        if (line.isEmpty()) return true;
        String[] p = line.split("\\s+", 2);
        String cmd = p[0].toUpperCase();
        String arg = p.length > 1 ? p[1].trim() : "";

        if (name == null && !cmd.equals("NICK") && !cmd.equals("QUIT")) {
            send("ERROR\tHãy đặt nickname trước: NICK <tên>");
            return true;
        }

        switch (cmd) {
            case "NICK":
                if (name != null) {
                    send("ERROR\tBạn đã có nickname là '" + name + "'");
                } else if (!valid(arg, 2, 20)) {
                    send("ERROR\tNickname 2-20 ký tự (chữ, số, _ hoặc -)");
                } else if (!server.register(this, arg)) {
                    send("ERROR\tNickname '" + arg + "' đã có người dùng");
                } else {
                    send("NICKOK\t" + name);
                    Server.log(name + " đăng nhập từ " + socket.getRemoteSocketAddress());
                    server.join(this, Server.LOBBY);
                }
                break;
            case "JOIN":
                if (!valid(arg, 1, 20)) send("ERROR\tTên phòng 1-20 ký tự (chữ, số, _ hoặc -)");
                else server.join(this, arg);
                break;
            case "LEAVE":
                server.join(this, Server.LOBBY);
                break;
            case "MSG":
                if (!arg.isEmpty()) server.chat(this, arg.replace('\t', ' '));
                break;
            case "PM":
                String[] q = arg.split("\\s+", 2);
                if (q.length < 2 || q[1].isBlank()) send("ERROR\tCú pháp: PM <tên> <nội dung>");
                else server.privateMessage(this, q[0], q[1].replace('\t', ' '));
                break;
            case "ROOMS":
                send(server.roomsLine());
                break;
            case "USERS":
                send(server.usersLine(room));
                break;
            case "WATCH":
                watch = true;
                server.pushTo(this);
                break;
            case "QUIT":
                send("INFO\tTạm biệt!");
                return false;
            default:
                send("ERROR\tLệnh không hợp lệ: " + cmd);
        }
        return true;
    }

    private static boolean valid(String s, int min, int max) {
        return s.length() >= min && s.length() <= max && s.matches("[\\p{L}\\p{N}_-]+");
    }

    /** Gửi một dòng cho client này (an toàn khi nhiều luồng cùng gửi). */
    synchronized void send(String line) {
        if (out != null) out.println(line);
    }
}
