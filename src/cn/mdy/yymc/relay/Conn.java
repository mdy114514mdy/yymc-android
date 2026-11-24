package cn.mdy.yymc.relay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.TreeMap;

// 每条连接的入口：读请求头，按路径分发到静态 / HTTP 反代 / WS 中继
final class Conn implements Runnable {

    private final Relay relay;
    private final Socket sock;

    Conn(Relay relay, Socket sock) {
        this.relay = relay;
        this.sock = sock;
    }

    @Override
    public void run() {
        try {
            sock.setSoTimeout(30000);
            HttpIn in = new HttpIn(sock.getInputStream());
            String line = in.line();
            if (line == null || line.length() == 0) {
                close();
                return;
            }
            String[] parts = line.split(" ");
            if (parts.length < 2) {
                head(400, "bad request line");
                return;
            }
            String method = parts[0];
            String target = parts[1];

            Map<String, String> headers = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
            String h;
            while ((h = in.line()) != null && h.length() > 0) {
                int i = h.indexOf(':');
                if (i > 0) headers.put(h.substring(0, i).trim(), h.substring(i + 1).trim());
            }

            String path = target;
            int abs = path.indexOf("://");
            if (abs > 0) {
                int slash = path.indexOf('/', abs + 3);
                path = slash >= 0 ? path.substring(slash) : "/";
            }

            boolean ws = "websocket".equalsIgnoreCase(headers.get("Upgrade"));
            if (ws) {
                // 浏览器打的是 /rooms/p2p，上游要的是 /p2p
                String upPath = path.startsWith("/rooms") ? path.substring("/rooms".length()) : path;
                if (upPath.isEmpty()) upPath = "/";
                WsTunnel.run(sock, upPath, headers, in);
                return;
            }

            byte[] body = null;
            String cl = headers.get("Content-Length");
            if (cl != null) {
                try {
                    int n = Integer.parseInt(cl.trim());
                    if (n > 0 && n <= (64 << 20)) body = in.readN(n);
                } catch (NumberFormatException e) {
                    // 没 body
                }
            }

            if (path.startsWith("/rooms/")) {
                RoomsProxy.handle(sock, method, path.substring("/rooms".length()), headers, body);
                return;
            }
            AssetHandler.handle(relay.assets(), sock, method, path, headers);
        } catch (Throwable t) {
            // 单条连接出错不影响服务
        } finally {
            close();
        }
    }

    private void head(int code, String msg) {
        try {
            byte[] b = (msg + "\n").getBytes("UTF-8");
            String sb = "HTTP/1.1 " + code + " Relay\r\nContent-Type: text/plain; charset=utf-8\r\n"
                    + "Content-Length: " + b.length + "\r\nConnection: close\r\n\r\n";
            OutputStream o = sock.getOutputStream();
            o.write(sb.getBytes("ISO-8859-1"));
            o.write(b);
            o.flush();
        } catch (IOException e) {
            // ignore
        }
    }

    private void close() {
        try {
            sock.close();
        } catch (IOException e) {
            // ignore
        }
    }

    static InputStream unused() {
        return null;
    }
}
