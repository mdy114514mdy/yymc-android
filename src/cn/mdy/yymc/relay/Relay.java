package cn.mdy.yymc.relay;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

// 设备内中继：只绑 127.0.0.1，对外提供静态站 + /rooms 反代（HTTP 与 WebSocket 都在本机完成）。
public final class Relay {

    private final AssetSource assets;
    private final int wantPort;
    private ServerSocket server;
    private volatile boolean running;
    private int port;

    public Relay(AssetSource assets, int wantPort) {
        this.assets = assets;
        this.wantPort = wantPort;
    }

    AssetSource assets() {
        return assets;
    }

    public int start() throws IOException {
        InetAddress loop = InetAddress.getByName("127.0.0.1");
        ServerSocket ss = null;
        int p = wantPort;
        IOException last = null;
        for (int i = 0; i < 20; i++) {
            try {
                ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress(loop, p + i), 64);
                p = p + i;
                last = null;
                break;
            } catch (IOException e) {
                last = e;
                ss = null;
            }
        }
        if (ss == null) throw (last != null ? last : new IOException("no port"));
        server = ss;
        port = ss.getLocalPort();
        running = true;
        Thread t = new Thread(new Acceptor(this, ss));
        t.setName("yymc-relay");
        t.setDaemon(true);
        t.start();
        return port;
    }

    public int port() {
        return port;
    }

    public void stop() {
        running = false;
        if (server != null) {
            try {
                server.close();
            } catch (IOException e) {
                // ignore
            }
            server = null;
        }
    }
}
