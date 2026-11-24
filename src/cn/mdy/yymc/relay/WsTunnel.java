package cn.mdy.yymc.relay;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

// /rooms/p2p 的 WebSocket 中继：本地替浏览器完成握手（算 Sec-WebSocket-Accept），
// 再用一条带 Origin 的上游连接跟官方信令服务握手，之后按帧原样双向搬运。
final class WsTunnel {

    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final char[] B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private WsTunnel() {
    }

    static String base64(byte[] d) {
        StringBuilder sb = new StringBuilder((d.length + 2) / 3 * 4);
        int i = 0;
        while (i + 2 < d.length) {
            int n = ((d[i] & 0xff) << 16) | ((d[i + 1] & 0xff) << 8) | (d[i + 2] & 0xff);
            sb.append(B64[(n >> 18) & 63]).append(B64[(n >> 12) & 63]).append(B64[(n >> 6) & 63]).append(B64[n & 63]);
            i += 3;
        }
        int rem = d.length - i;
        if (rem == 1) {
            int n = (d[i] & 0xff) << 16;
            sb.append(B64[(n >> 18) & 63]).append(B64[(n >> 12) & 63]).append("==");
        } else if (rem == 2) {
            int n = ((d[i] & 0xff) << 16) | ((d[i + 1] & 0xff) << 8);
            sb.append(B64[(n >> 18) & 63]).append(B64[(n >> 12) & 63]).append(B64[(n >> 6) & 63]).append('=');
        }
        return sb.toString();
    }

    static String acceptOf(String key) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-1");
        return base64(sha.digest((key.trim() + GUID).getBytes("ISO-8859-1")));
    }

    static void run(Socket sock, String path, Map<String, String> headers, HttpIn in) throws Exception {
        String key = headers.get("Sec-WebSocket-Key");
        if (key == null) {
            plain(sock, 400, "missing Sec-WebSocket-Key");
            return;
        }
        String accept = acceptOf(key);

        byte[] rnd = new byte[16];
        new SecureRandom().nextBytes(rnd);
        String myKey = base64(rnd);

        java.net.Socket up;
        if (Upstream.TLS) {
            SSLSocket ssl = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
            ssl.connect(new InetSocketAddress(Upstream.HOST, Upstream.PORT), 15000);
            ssl.setSoTimeout(20000);
            ssl.startHandshake();
            up = ssl;
        } else {
            up = new java.net.Socket();
            up.connect(new InetSocketAddress(Upstream.HOST, Upstream.PORT), 15000);
            up.setSoTimeout(20000);
        }

        String req = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + Upstream.HOST + ":" + Upstream.PORT + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + myKey + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "Origin: " + Upstream.origin() + "\r\n"
                + "Referer: " + Upstream.referer() + "\r\n"
                + "\r\n";
        OutputStream uo = up.getOutputStream();
        uo.write(req.getBytes("ISO-8859-1"));
        uo.flush();

        HttpIn uin = new HttpIn(up.getInputStream());
        String status = uin.line();
        if (status == null || status.indexOf(" 101") < 0) {
            up.close();
            plain(sock, 502, "upstream refused: " + status);
            return;
        }
        String l;
        while ((l = uin.line()) != null && l.length() > 0) {
            // 丢弃上游头（Accept 我们自己算，其余无用）
        }

        String resp = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n"
                + "\r\n";
        OutputStream co = sock.getOutputStream();
        co.write(resp.getBytes("ISO-8859-1"));
        co.flush();

        sock.setSoTimeout(0);
        up.setSoTimeout(0);

        byte[] earlyUp = uin.drain();
        byte[] earlyClient = in.drain();

        Pump up2c = new Pump(up.getInputStream(), co, earlyUp);
        Pump c2up = new Pump(sock.getInputStream(), uo, earlyClient);
        Thread t1 = new Thread(up2c);
        Thread t2 = new Thread(c2up);
        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();
        t1.join();
        try {
            up.close();
        } catch (IOException e) {
            // ignore
        }
    }

    private static void plain(Socket sock, int code, String msg) {
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
}
