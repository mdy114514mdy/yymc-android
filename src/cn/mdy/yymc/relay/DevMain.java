package cn.mdy.yymc.relay;

// 桌面自测：同一个 Relay 跑本机资源，可把上游指到本地 mock
// 用法: DevMain <root> <port> [upHost upPort plain]
public final class DevMain {

    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : "E:/deepseek/mirror";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8733;
        if (args.length > 3) {
            Upstream.use(args[2], Integer.parseInt(args[3]), !(args.length > 4 && args[4].equals("plain")));
        }
        Relay r = new Relay(new FileAssets(dir), port);
        int p = r.start();
        System.out.println("relay   http://127.0.0.1:" + p + "/  root=" + dir);
        System.out.println("rooms   /rooms/*  ->  " + Upstream.base() + "  (origin=" + Upstream.origin() + ")");
        System.out.flush();
        Thread.sleep(Long.MAX_VALUE);
    }
}
