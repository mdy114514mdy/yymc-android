package cn.mdy.yymc.relay;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

// 极简 HTTP 读端：按行读 + 定长读 + 把缓冲里剩下的字节交出来（WS 升级后要接着转发）
final class HttpIn {

    private final InputStream in;
    private final byte[] buf = new byte[8192];
    private int pos = 0;
    private int len = 0;

    HttpIn(InputStream in) {
        this.in = in;
    }

    private int readByte() throws IOException {
        if (pos >= len) {
            len = in.read(buf, 0, buf.length);
            pos = 0;
            if (len <= 0) return -1;
        }
        return buf[pos++] & 0xff;
    }

    String line() throws IOException {
        StringBuilder s = new StringBuilder();
        int c;
        while ((c = readByte()) != -1) {
            if (c == '\n') {
                int l = s.length();
                if (l > 0 && s.charAt(l - 1) == '\r') s.setLength(l - 1);
                return s.toString();
            }
            s.append((char) c);
            if (s.length() > 16384) break;
        }
        return s.length() == 0 ? null : s.toString();
    }

    byte[] readN(int n) throws IOException {
        byte[] out = new byte[n];
        int got = 0;
        while (got < n) {
            if (pos < len) {
                int r = Math.min(n - got, len - pos);
                System.arraycopy(buf, pos, out, got, r);
                pos += r;
                got += r;
            } else {
                len = in.read(buf, 0, buf.length);
                pos = 0;
                if (len <= 0) break;
            }
        }
        return got == n ? out : Arrays.copyOf(out, got);
    }

    // 缓冲里还没消费的字节（WS 握手后可能紧跟首帧）
    byte[] drain() {
        if (pos >= len) return new byte[0];
        byte[] out = Arrays.copyOfRange(buf, pos, len);
        pos = len;
        return out;
    }
}
