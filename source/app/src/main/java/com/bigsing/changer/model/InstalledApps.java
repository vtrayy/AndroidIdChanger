package com.bigsing.changer.model;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.List;

/** 只读的已安装应用查询，不包含安装、卸载、清理或 root 操作。 */
public final class InstalledApps {
    private InstalledApps() {
    }

    public static InstalledAppInfo getAppInfo(Context context, String packageName) {
        try {
            PackageManager manager = context.getPackageManager();
            return toInfo(manager, manager.getPackageInfo(packageName, 0));
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    public static List<InstalledAppInfo> getAppsInfo(Context context) {
        PackageManager manager = context.getPackageManager();
        List<InstalledAppInfo> result = new ArrayList<>();
        for (PackageInfo packageInfo : manager.getInstalledPackages(0)) {
            InstalledAppInfo info = toInfo(manager, packageInfo);
            if (info != null) {
                result.add(info);
            }
        }
        return result;
    }

    public static String getServiceName(ComponentName componentName) {
        String className = componentName.getClassName();
        int separator = className.lastIndexOf('.');
        return separator >= 0 ? className.substring(separator + 1) : className;
    }

    private static InstalledAppInfo toInfo(PackageManager manager, PackageInfo packageInfo) {
        if (packageInfo == null || packageInfo.applicationInfo == null) {
            return null;
        }
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        return new InstalledAppInfo(
                String.valueOf(applicationInfo.loadLabel(manager)),
                applicationInfo.loadIcon(manager),
                packageInfo.packageName,
                packageInfo.versionName,
                (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
    }
}
