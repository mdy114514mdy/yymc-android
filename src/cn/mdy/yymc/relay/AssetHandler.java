package cn.mdy.yymc.relay;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;

// 从 AssetSource 取文件并按 HTTP 语义返回（含 Range）
final class AssetHandler {

    private AssetHandler() {
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(1 << 16);
        byte[] b = new byte[1 << 16];
        try {
            int r;
            while ((r = in.read(b)) > 0) bo.write(b, 0, r);
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                // ignore
            }
        }
        return bo.toByteArray();
    }

    static void handle(AssetSource src, Socket sock, String method, String target,
                       Map<String, String> headers) throws IOException {
        String path = target;
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        q = path.indexOf('#');
        if (q >= 0) path = path.substring(0, q);
        if (path.isEmpty() || path.equals("/")) path = "/index.html";
        if (path.endsWith("/")) path = path + "index.html";

        String rel = path.startsWith("/") ? path.substring(1) : path;
        byte[] data = null;
        try {
            data = readAll(src.open(rel));
        } catch (IOException e) {
            try {
                rel = rel + ".html";
                data = readAll(src.open(rel));
            } catch (IOException e2) {
                byte[] msg = ("404 " + rel).getBytes("UTF-8");
                head(sock, 404, "text/plain; charset=utf-8", msg.length, null, null);
                OutputStream o = sock.getOutputStream();
                o.write(msg);
                o.flush();
                return;
            }
        }

        String mime = Mime.of(rel);
        String range = headers.get("Range");
        if (range != null && range.startsWith("bytes=")) {
            int dash = range.indexOf('-', 6);
            if (dash > 0) {
                try {
                    String a = range.substring(6, dash).trim();
                    String b = range.substring(dash + 1).trim();
                    long total = data.length;
                    long start = a.length() == 0 ? 0 : Long.parseLong(a);
                    long end = b.length() == 0 ? total - 1 : Long.parseLong(b);
                    if (end > total - 1) end = total - 1;
                    if (start <= end && start < total) {
                        int n = (int) (end - start + 1);
                        head(sock, 206, mime, n,
                                "Content-Range: bytes " + start + "-" + end + "/" + total, null);
                        OutputStream o = sock.getOutputStream();
                        o.write(data, (int) start, n);
                        o.flush();
                        return;
                    }
                } catch (NumberFormatException e) {
                    // 落到整份返回
                }
            }
        }

        boolean cacheable = !rel.endsWith(".html");
        head(sock, 200, mime, data.length, null,
                cacheable ? "public, max-age=31536000" : "no-cache");
        if ("HEAD".equals(method)) return;
        OutputStream o = sock.getOutputStream();
        o.write(data);
        o.flush();
    }

    private static void head(Socket sock, int code, String mime, int len, String extra, String cache)
            throws IOException {
        StringBuilder sb = new StringBuilder(256);
        sb.append("HTTP/1.1 ").append(code).append(code == 206 ? " Partial Content" : code == 404 ? " Not Found" : " OK").append("\r\n");
        sb.append("Content-Type: ").append(mime).append("\r\n");
        sb.append("Content-Length: ").append(len).append("\r\n");
        sb.append("Accept-Ranges: bytes\r\n");
        sb.append("Access-Control-Allow-Origin: *\r\n");
        if (cache != null) sb.append("Cache-Control: ").append(cache).append("\r\n");
        if (extra != null) sb.append(extra).append("\r\n");
        sb.append("Connection: close\r\n\r\n");
        OutputStream o = sock.getOutputStream();
        o.write(sb.toString().getBytes("ISO-8859-1"));
        o.flush();
    }
}
