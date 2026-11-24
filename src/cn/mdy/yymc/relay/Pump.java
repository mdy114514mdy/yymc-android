package cn.mdy.yymc.relay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// 单向搬运（顶层类：d8 对 $ 类有问题）
final class Pump implements Runnable {

    private final InputStream in;
    private final OutputStream out;
    private final byte[] early;

    Pump(InputStream in, OutputStream out, byte[] early) {
        this.in = in;
        this.out = out;
        this.early = early;
    }

    @Override
    public void run() {
        try {
            if (early != null && early.length > 0) {
                out.write(early);
                out.flush();
            }
            byte[] b = new byte[16384];
            while (true) {
                int r = in.read(b);
                if (r <= 0) break;
                out.write(b, 0, r);
                out.flush();
            }
        } catch (IOException e) {
            // 对端关闭
        }
    }
}
