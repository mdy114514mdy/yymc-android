package cn.mdy.yymc.relay;

import java.util.HashMap;
import java.util.Map;

// 扩展名 → MIME（只需要覆盖站点里实际出现的类型）
final class Mime {

    private static final Map<String, String> T = new HashMap<String, String>();

    static {
        T.put("html", "text/html; charset=utf-8");
        T.put("htm", "text/html; charset=utf-8");
        T.put("js", "text/javascript; charset=utf-8");
        T.put("mjs", "text/javascript; charset=utf-8");
        T.put("css", "text/css; charset=utf-8");
        T.put("json", "application/json; charset=utf-8");
        T.put("svg", "image/svg+xml");
        T.put("png", "image/png");
        T.put("ico", "image/x-icon");
        T.put("webp", "image/webp");
        T.put("m4a", "audio/mp4");
        T.put("mp4", "audio/mp4");
        T.put("ogg", "audio/ogg");
        T.put("txt", "text/plain; charset=utf-8");
    }

    static String of(String path) {
        int i = path.lastIndexOf('.');
        if (i < 0) return "application/octet-stream";
        String e = path.substring(i + 1).toLowerCase();
        String m = T.get(e);
        return m != null ? m : "application/octet-stream";
    }
}
