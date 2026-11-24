package cn.mdy.yymc.relay;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

// 桌面调试用的资源来源（本机验中继逻辑时用）
public final class FileAssets implements AssetSource {

    private final File root;

    public FileAssets(String dir) {
        this.root = new File(dir);
    }

    @Override
    public InputStream open(String path) throws IOException {
        if (path.indexOf("..") >= 0) throw new IOException("bad path");
        File f = new File(root, path.replace('/', File.separatorChar));
        if (!f.isFile()) throw new IOException("not found: " + path);
        return new FileInputStream(f);
    }
}
