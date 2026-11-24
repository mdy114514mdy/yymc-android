package cn.mdy.yymc.relay;

// 上游房间服务地址（默认官方；DevMain 可改成 127.0.0.1 做本地自测）
final class Upstream {

    static volatile String HOST = "xz.honghemc.cn";
    static volatile int PORT = 8443;
    static volatile boolean TLS = true;

    static void use(String host, int port, boolean tls) {
        HOST = host;
        PORT = port;
        TLS = tls;
    }

    static String base() {
        return (TLS ? "https" : "http") + "://" + HOST + ":" + PORT;
    }

    static String origin() {
        return "https://www.bilibili.com";
    }

    static String referer() {
        return "https://www.bilibili.com/";
    }

    private Upstream() {
    }
}
