package com.bigsing.changer;

import com.bigsing.changer.constant.Constant;
import com.bigsing.changer.config.ScopeMatcher;
import com.bigsing.changer.hook.PhoneHooker;
import com.bigsing.changer.legacy.LegacyConfig;
import com.bigsing.util.XposedLog;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

/** 传统 Xposed API 53 入口。 */

public class MainHook implements IXposedHookLoadPackage {
    /**
     * 是否是Android系统的包
     *
     * @param packageName
     * @return
     */
    private static boolean isPackageIgnored(String packageName) {
        return packageName == null
                || packageName.equals("android")
                || packageName.equals("com.android.providers.settings")
                || packageName.equals("com.android.server.telecom")
                || packageName.equals("com.android.location.fused")
                || packageName.equals("com.qualcomm.location");
    }

    /**
     * 是否是厂商的包
     *
     * @param packageName
     * @return
     */
    private static boolean isVendorPackage(String packageName) {
        return (packageName.startsWith("com.sonymobile")) || (packageName.startsWith("com.sonyericsson"));
    }

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        LegacyConfig.reload();
        XposedLog.configure();
        XposedLog.logd("handleLoadPackage");

        // 过滤Android系统包、厂商的包
        if (isPackageIgnored(param.packageName) || isVendorPackage(param.packageName)) {
            return;
        }

        // 利用当前APP的包判断本模块是否激活
        if (param.packageName.equals(Constant.PACKAGE_THIS_TOOL)) {
            XC_MethodHook.Unhook unhook = findAndHookMethod(XposedActive.class.getName(), param.classLoader, "isActive", XC_MethodReplacement.returnConstant(true));
            if (unhook != null) {
                XposedLog.logd("isActive HOOK OK!!!");
            } else {
                XposedLog.loge("isActive HOOK FAILED error here: class [XposedActive.isActive] maybe optimized!!! shoul keep it in proguard-rules.pro");
            }
            return;
        }

        // 始终读取模块自己的中心配置。传统 Xposed 使用可读偏好文件，
        // LSPosed/EdXposed 可通过框架提供的 XSharedPreferences 服务读取。
        boolean bHookAllApp = LegacyConfig.isHookAllApps();
        if (bHookAllApp) {
            XposedLog.log("All apps allowed by the framework scope are enabled.");
        } else {
            String hookPackages = LegacyConfig.getPackages();
            if (hookPackages.trim().length() == 0) {
                XposedLog.logd("App Hook List: null");
                return;
            } else {
                XposedLog.logd("App hook list loaded");
                if (!ScopeMatcher.contains(hookPackages, param.packageName)) {
                    return;
                }
            }
        }
        XposedLog.log("hook begin");

        PhoneHooker.hook(param);
        XposedLog.log("hook end");
    }

}
