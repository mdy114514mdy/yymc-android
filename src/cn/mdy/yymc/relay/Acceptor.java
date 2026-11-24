package cn.mdy.yymc.relay;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

// 监听循环（顶层类：d8 对 $ 类有问题）
final class Acceptor implements Runnable {

    private final Relay relay;
    private final ServerSocket server;

    Acceptor(Relay relay, ServerSocket server) {
        this.relay = relay;
        this.server = server;
    }

    @Override
    public void run() {
        while (!server.isClosed()) {
            Socket s;
            try {
                s = server.accept();
            } catch (IOException e) {
                break;
            }
            try {
                s.setTcpNoDelay(true);
            } catch (Exception e) {
                // ignore
            }
            Thread w = new Thread(new Conn(relay, s));
            w.setDaemon(true);
            w.start();
        }
    }
}
