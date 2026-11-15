package chatapp.client;


import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.Scanner;

/** Client dòng lệnh. Chạy: java CliClient [host] [port] */
public class CliClient {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : Protocol.DEFAULT_PORT;
        PrintStream console = new PrintStream(System.out, true, StandardCharsets.UTF_8);

        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8)) {

            console.println("Đã kết nối " + host + ":" + port);

            // Luồng nhận tin từ server
            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) console.println(format(line));
                } catch (IOException ignored) { }
                console.println("Đã mất kết nối tới server.");
                System.exit(0);
            });
            reader.setDaemon(true);
            reader.start();

            // Đặt nickname
            console.print("Nickname: ");
            console.flush();
            out.println("NICK " + sc.nextLine().trim());
            console.println(Protocol.HELP);

            while (sc.hasNextLine()) {
                String input = sc.nextLine().trim();
                if (input.isEmpty()) continue;
                if (input.equalsIgnoreCase("/help")) { console.println(Protocol.HELP); continue; }
                String wire = Protocol.toWire(input);
                if (wire == null) { console.println(Protocol.HELP); continue; }
                out.println(wire);
                if (wire.equals("QUIT")) { Thread.sleep(200); break; }
            }
        }
    }

    private static String format(String line) {
        String[] f = line.split("\t");
        String t = LocalTime.now().withNano(0).toString();
        switch (f[0]) {
            case "CHAT":   return "[" + t + "][" + f[1] + "] " + f[2] + ": " + f[3];
            case "HISTORY": return "[" + f[2] + "][" + f[1] + "] " + f[3] + ": " + f[4];
            case "PM":     return "[" + t + "][riêng] " + f[1] + " -> " + f[2] + ": " + f[3];
            case "SYSTEM": return "[" + t + "][" + f[1] + "] * " + f[2];
            case "INFO":   return "* " + f[1];
            case "ERROR":  return "! " + f[1];
            case "NICKOK": return "* Xin chào, " + f[1] + "!";
            case "JOINED": return "* Bạn đang ở phòng: " + f[1];
            case "ROOMS":  return "* Các phòng: " + (f.length > 1 ? f[1].replace(":", " (").replace(",", "), ") + ")" : "(trống)");
            case "USERS":  return "* Người dùng trong '" + f[1] + "': " + (f.length > 2 ? f[2].replace(",", ", ") : "");
            default:       return line;
        }
    }
}
