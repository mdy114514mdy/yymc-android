package cn.mdy.yymc;

import android.content.res.AssetManager;
import cn.mdy.yymc.relay.AssetSource;
import java.io.IOException;
import java.io.InputStream;

// 把 APK 里的 assets/site/ 当作中继的资源根
public final class AndroidAssets implements AssetSource {

    private final AssetManager am;

    public AndroidAssets(AssetManager am) {
        this.am = am;
    }

    @Override
    public InputStream open(String path) throws IOException {
        return am.open("site/" + path);
    }
}
