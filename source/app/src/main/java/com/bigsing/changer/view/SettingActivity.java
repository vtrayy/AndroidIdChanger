package com.bigsing.changer.view;

import android.annotation.TargetApi;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.text.TextUtils;
import android.view.View;

import com.bigsing.BaseActivity;
import com.bigsing.changer.App;
import com.bigsing.changer.DeviceInfo;
import com.bigsing.changer.R;
import com.bigsing.changer.constant.Constant;
import com.bigsing.changer.hook.PhoneKey;
import com.bigsing.util.PreferencesUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public class SettingActivity extends BaseActivity implements View.OnClickListener {
    private static String TAG = "SettingActivity";
    private SettingsFragment mSettingsFragment;

    //
    private boolean mUseHardcodedLastValue;

    //保存配置信息
    private SharedPreferences msp;

    public static String getDeviceInfoHtml(Context context) {
        DeviceInfo deviceInfo = new DeviceInfo();
        StringBuilder textDevice = new StringBuilder(
                "<html><body><p>敏感标识符已默认脱敏。</p><table border=\"1\" width=\"100%\">\n"
                        + "<tr align=\"left\"><th>Key</th><th>Value</th></tr>");
        LinkedHashMap<String, String> maps = deviceInfo.getValues(context);
        for (Map.Entry<String, String> entry : maps.entrySet()) {
            String value = normalizedValue(entry.getValue());
            if (isSensitive(entry.getKey())) {
                value = mask(value);
            }
            textDevice.append("\n<tr><td>")
                    .append(escapeHtml(entry.getKey()))
                    .append("</td><td>")
                    .append(escapeHtml(value))
                    .append("</td></tr>\n");
        }
        return textDevice.append("</table></body></html>").toString();
    }

    public static String getDeviceInfoText(Context context) {
        return getDeviceInfoText(context, false);
    }

    /** 分享用途的纯文本；与 HTML 导出使用相同的敏感字段脱敏策略。 */
    public static String getRedactedDeviceInfoText(Context context) {
        return getDeviceInfoText(context, true);
    }

    private static String getDeviceInfoText(Context context, boolean redactSensitive) {
        DeviceInfo deviceInfo = new DeviceInfo();
        StringBuilder textDevice = new StringBuilder();
        LinkedHashMap<String, String> maps = deviceInfo.getValues(context);
        for (Map.Entry<String, String> entry : maps.entrySet()) {
            String value = normalizedValue(entry.getValue());
            if (redactSensitive && isSensitive(entry.getKey())) {
                value = mask(value);
            }
            textDevice.append(String.format("%-24s: %s\n",
                    entry.getKey(), value));
        }
        return textDevice.toString();
    }

    private static String normalizedValue(String value) {
        return TextUtils.isEmpty(value) ? "[不可用]" : value;
    }

    private static boolean isSensitive(String key) {
        return PhoneKey.IMEI.equals(key)
                || PhoneKey.MEID.equals(key)
                || PhoneKey.AndroidId.equals(key)
                || PhoneKey.SerialNo.equals(key)
                || PhoneKey.WifiMac.equals(key)
                || PhoneKey.Wifissid.equals(key)
                || PhoneKey.WifiBssid.equals(key)
                || PhoneKey.BluetoothMac.equals(key)
                || PhoneKey.PhoneNumber.equals(key)
                || PhoneKey.SimSerialNo.equals(key)
                || PhoneKey.SubscriberId.equals(key)
                || PhoneKey.IP.equals(key);
    }

    private static String mask(String value) {
        if (value.startsWith("[")) {
            return value;
        }
        int visible = Math.min(4, value.length());
        return "****" + value.substring(value.length() - visible);
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    @Override
    public String setActName() {
        return TAG;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.DefaultTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        if (savedInstanceState == null) {
            mSettingsFragment = new SettingsFragment();
            replaceFragment(R.id.settings_container, mSettingsFragment);
        }

        msp = App.getSharedPreferences();
        mUseHardcodedLastValue = PreferencesUtils.isUseHardcodedValue(this);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void replaceFragment(int viewId, android.app.Fragment fragment) {
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction().replace(viewId, fragment).commit();
    }

    //控件点击事件
    public void onClick(View view) {
        switch (view.getId()) {
            default:
                break;
        }
    }

    @Override
    protected void handleBackNavigation() {
        //切换使用硬编码的时候UI上需要更新下硬编码的值
        Intent intent = new Intent();
        intent.putExtra("needupdate", mUseHardcodedLastValue != PreferencesUtils.isUseHardcodedValue(this));
        setResult(RESULT_OK, intent);
        finish();
    }

    ////////////////////

    /**
     * A placeholder fragment containing a settings view.
     */
    public static class SettingsFragment extends PreferenceFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesName(Constant.TAG);
            getPreferenceManager().setSharedPreferencesMode(App.getPreferenceMode());
            addPreferencesFromResource(R.xml.pref_setting);

            final Preference dice_numPreference = findPreference("use_hardcoded_value");
            dice_numPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean b = (boolean) newValue;
                    return true;
                }
            });
        }
    }
    ////////////////////
}
