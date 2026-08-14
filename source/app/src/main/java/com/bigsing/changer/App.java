package com.bigsing.changer;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.bigsing.changer.config.XposedConfigBridge;
import com.bigsing.changer.constant.Constant;
/**
 * 应用进程入口。配置只保存在本机，不接入任何统计或崩溃上报服务。
 */

public class App extends Application {
    private static final String LEGACY_ORIGINAL_SNAPSHOT = "phone";
    private static Application mApp;
    private static int mPreferenceMode = MODE_PRIVATE;
    //程序通用的配置文件
    private static SharedPreferences mSP;
    private static SharedPreferences mSPFake;
    private final Handler configSyncHandler = new Handler(Looper.getMainLooper());
    private final Runnable configSync = new Runnable() {
        @Override
        public void run() {
            XposedConfigBridge.sync();
        }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener syncListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    // SharedPreferences 会为一次批量提交逐键回调；合并到同一主线程队列，
                    // 只向现代框架发布一次完整快照。
                    configSyncHandler.removeCallbacks(configSync);
                    configSyncHandler.post(configSync);
                }
            };

    public static Application getApplication() {
        return mApp;
    }

    public static SharedPreferences getSharedPreferences() {
        return mSP;
    }

    public static SharedPreferences getFakeSharedPreferences() {
        return mSPFake;
    }

    public static int getPreferenceMode() {
        return mPreferenceMode;
    }

    /**
     * LSPosed 的 legacy 兼容层会仅针对已激活模块放行该模式；普通应用环境会抛出
     * SecurityException，此时安全回退到私有配置。
     */
    @SuppressLint("WorldReadableFiles")
    @SuppressWarnings("deprecation")
    private int resolvePreferenceMode() {
        try {
            getSharedPreferences(Constant.TAG, MODE_WORLD_READABLE);
            return MODE_WORLD_READABLE;
        } catch (SecurityException ignored) {
            return MODE_PRIVATE;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mApp = this;
        // 旧私有版本曾缓存整份真实设备信息，但程序从未读取它；升级时主动删除。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            deleteSharedPreferences(LEGACY_ORIGINAL_SNAPSHOT);
        } else {
            getSharedPreferences(LEGACY_ORIGINAL_SNAPSHOT, MODE_PRIVATE)
                    .edit().clear().commit();
        }
        mPreferenceMode = resolvePreferenceMode();
        mSP = getSharedPreferences(Constant.TAG, mPreferenceMode);
        mSPFake = getSharedPreferences(Constant.FILENAME_FAKEINFO, mPreferenceMode);
        mSP.registerOnSharedPreferenceChangeListener(syncListener);
        mSPFake.registerOnSharedPreferenceChangeListener(syncListener);
        XposedConfigBridge.initialize(this);
    }
}
