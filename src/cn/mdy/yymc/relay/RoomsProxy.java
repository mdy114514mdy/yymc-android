package cn.mdy.yymc.relay;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.TreeMap;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

// /rooms/* 的 HTTP 反代：自己拼 HTTP/1.1 走裸 socket。
// 不用 HttpURLConnection —— JDK 把 Origin 列为 restricted header，会被静默丢掉，服务端就 403。
final class RoomsProxy {

    private RoomsProxy() {
    }

    static Socket connect() throws IOException {
        Socket s;
        if (Upstream.TLS) {
            SSLSocket ssl = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
            ssl.connect(new InetSocketAddress(Upstream.HOST, Upstream.PORT), 15000);
            ssl.setSoTimeout(30000);
            ssl.startHandshake();
            s = ssl;
        } else {
            s = new Socket();
            s.connect(new InetSocketAddress(Upstream.HOST, Upstream.PORT), 15000);
            s.setSoTimeout(30000);
        }
        return s;
    }

    static void handle(Socket client, String method, String path, Map<String, String> headers, byte[] body)
            throws IOException {
        Socket up = null;
        int code = 502;
        String ctype = "application/json; charset=utf-8";
        byte[] out = new byte[0];
        try {
            up = connect();
            StringBuilder sb = new StringBuilder(512);
            sb.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
            sb.append("Host: ").append(Upstream.HOST).append(':').append(Upstream.PORT).append("\r\n");
            sb.append("Origin: ").append(Upstream.origin()).append("\r\n");
            sb.append("Referer: ").append(Upstream.referer()).append("\r\n");
            sb.append("Accept: */*\r\n");
            sb.append("Accept-Encoding: identity\r\n");
            sb.append("User-Agent: YYMC-Android\r\n");
            sb.append("Connection: close\r\n");
            String ct = headers.get("Content-Type");
            if (ct != null) sb.append("Content-Type: ").append(ct).append("\r\n");
            if (body != null && body.length > 0) sb.append("Content-Length: ").append(body.length).append("\r\n");
            sb.append("\r\n");

            OutputStream uo = up.getOutputStream();
            uo.write(sb.toString().getBytes("ISO-8859-1"));
            if (body != null && body.length > 0) uo.write(body);
            uo.flush();

            HttpIn in = new HttpIn(up.getInputStream());
            String status = in.line();
            if (status == null) throw new IOException("no status");
            String[] parts = status.split(" ");
            if (parts.length >= 2) {
                try {
                    code = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    code = 502;
                }
            }
            Map<String, String> uh = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
            String l;
            while ((l = in.line()) != null && l.length() > 0) {
                int i = l.indexOf(':');
                if (i > 0) uh.put(l.substring(0, i).trim(), l.substring(i + 1).trim());
            }
            String hc = uh.get("Content-Type");
            if (hc != null) ctype = hc;

            String cl = uh.get("Content-Length");
            if (cl != null) {
                try {
                    out = in.readN(Integer.parseInt(cl.trim()));
                } catch (NumberFormatException e) {
                    out = readAll(up);
                }
            } else if ("chunked".equalsIgnoreCase(uh.get("Transfer-Encoding"))) {
                out = dechunk(in);
            } else {
                out = readAll(up);
            }
        } catch (IOException e) {
            out = ("{\"error\":\"relay: " + e.getMessage() + "\"}").getBytes("UTF-8");
            code = 502;
            ctype = "application/json; charset=utf-8";
        } finally {
            if (up != null) {
                try {
                    up.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        StringBuilder sb = new StringBuilder(256);
        sb.append("HTTP/1.1 ").append(code).append(" Relay\r\n");
        sb.append("Content-Type: ").append(ctype).append("\r\n");
        sb.append("Content-Length: ").append(out.length).append("\r\n");
        sb.append("Access-Control-Allow-Origin: *\r\n");
        sb.append("Cache-Control: no-store\r\n");
        sb.append("Connection: close\r\n\r\n");
        OutputStream co = client.getOutputStream();
        co.write(sb.toString().getBytes("ISO-8859-1"));
        if (!"HEAD".equals(method)) co.write(out);
        co.flush();
    }

    private static byte[] readAll(Socket s) throws IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(8192);
        byte[] b = new byte[16384];
        int r;
        try {
            while ((r = s.getInputStream().read(b)) > 0) bo.write(b, 0, r);
        } catch (IOException e) {
            // 对端关闭
        }
        return bo.toByteArray();
    }

    private static byte[] dechunk(HttpIn in) throws IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(8192);
        while (true) {
            String line = in.line();
            if (line == null) break;
            int semi = line.indexOf(';');
            if (semi >= 0) line = line.substring(0, semi);
            line = line.trim();
            if (line.length() == 0) continue;
            int n;
            try {
                n = Integer.parseInt(line, 16);
            } catch (NumberFormatException e) {
                break;
            }
            if (n == 0) break;
            bo.write(in.readN(n));
            in.line();
        }
        return bo.toByteArray();
    }
}
