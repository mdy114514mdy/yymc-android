package cn.mdy.yymc.relay;

import java.io.IOException;
import java.io.InputStream;

// 资源来源抽象：Android 侧包 AssetManager，桌面侧包文件系统，中继逻辑两边共用。
public interface AssetSource {
    InputStream open(String path) throws IOException;
}
