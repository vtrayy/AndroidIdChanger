package com.bigsing.changer.model;

import android.graphics.drawable.Drawable;

/** 作用域选择器使用的最小应用信息模型。 */
public final class InstalledAppInfo {
    private final String name;
    private final Drawable icon;
    private final String packageName;
    private final String versionName;
    private final boolean system;
    private boolean checked;

    InstalledAppInfo(String name, Drawable icon, String packageName, String versionName,
                     boolean system) {
        this.name = name;
        this.icon = icon;
        this.packageName = packageName;
        this.versionName = versionName;
        this.system = system;
    }

    public String getName() {
        return name;
    }

    public Drawable getIcon() {
        return icon;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getVersionName() {
        return versionName == null ? "" : versionName;
    }

    public boolean isSystem() {
        return system;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    public void setAddedHook(boolean addedHook) {
        this.checked = addedHook;
    }
}
