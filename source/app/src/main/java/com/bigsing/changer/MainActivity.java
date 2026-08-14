package com.bigsing.changer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bigsing.BaseActivity;
import com.bigsing.changer.config.XposedConfigBridge;
import com.bigsing.changer.constant.Constant;
import com.bigsing.changer.hook.ValueItem;
import com.bigsing.changer.view.AppsActivity;
import com.bigsing.changer.view.SettingActivity;
import com.bigsing.util.PhoneInfo;
import com.bigsing.util.PhoneInfoUtils;
import com.bigsing.util.PreferencesUtils;
import com.bigsing.util.ThemeUtils;
import com.bigsing.util.Utils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 普通应用界面：查看设备信息、编辑替换配置和选择最小作用域。 */
public class MainActivity extends BaseActivity {
    public static final String TAG = "MainActivity";
    public static final int REQUEST_ONE = 1;
    public static final int REQUEST_IMPORT_PROFILE = 2;
    public static final int REQUEST_DEVICE_PERMISSIONS = 3;
    public static final int LOAD_FINISHED = 1;
    private static final String STATE_TAB = "state.tab";
    private static final String STATE_KEYS = "state.keys";
    private static final String STATE_VALUES = "state.values";
    private static final String STATE_ENABLED = "state.enabled";

    private static String mCurrentTab = "";
    Button btnReadBefore;
    Button btnSave;
    Button btnRandomAll;
    RecyclerView lv_values;
    private View ll_loading;
    private ProgressBar progressBar;
    private TextView tv_info;

    private List<ValueItem> mValues = new ArrayList<>();
    private RecyclerView.Adapter mAdapter = null;

    private PhoneInfo mPhoneInfo;
    private LinkedHashMap<String, String> mPhoneInfoOrigin = new LinkedHashMap<>();
    private DrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;
    private boolean waitingForFrameworkConnection;
    private boolean frameworkConnectedLast;
    private boolean hasResumedOnce;

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == LOAD_FINISHED) {
                showTabOne(true);
                btnReadBefore.setEnabled(true);
                tv_info.setText((String) msg.obj);
                progressBar.setVisibility(View.GONE);
            }
        }
    };

    private final Runnable delayedFrameworkStatus = new Runnable() {
        @Override
        public void run() {
            if (!XposedActive.isActive()) {
                Utils.toast(MainActivity.this, getString(R.string.xpose_not_actived));
            }
        }
    };

    private final Runnable frameworkStateListener = new Runnable() {
        @Override
        public void run() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    boolean connected = XposedActive.isActive();
                    if (connected) {
                        handler.removeCallbacks(delayedFrameworkStatus);
                        if (!frameworkConnectedLast) {
                            Utils.toast(MainActivity.this, getString(R.string.xpose_actived));
                        }
                        if (waitingForFrameworkConnection
                                && Constant.TAB_NAME_ORIGNAL.equals(mCurrentTab)) {
                            showAfter();
                        }
                        waitingForFrameworkConnection = false;
                    }
                    frameworkConnectedLast = connected;
                    if (mAdapter != null) {
                        mAdapter.notifyDataSetChanged();
                    }
                }
            });
        }
    };

    public static String getCurrentTab() {
        return mCurrentTab;
    }

    @Override
    public String setActName() {
        return TAG;
    }

    private void setupDrawerContent(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        menuItem.setChecked(true);
                        mDrawerLayout.closeDrawers();
                        return true;
                    }
                });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Resources resources = this.getResources();
        DisplayMetrics dm = resources.getDisplayMetrics();
        Configuration config = resources.getConfiguration();
        // 应用用户选择语言
        config.locale = Locale.getDefault();
        resources.updateConfiguration(config, dm);

        initNavigationView();
        initView();
        restorePendingValues(savedInstanceState);

        isXposedActived();
        requestPermission();
    }

    @Override
    protected void onStart() {
        super.onStart();
        XposedConfigBridge.setFrameworkStateListener(frameworkStateListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (hasResumedOnce) {
            refreshDisplayedDeviceInformation();
        }
        hasResumedOnce = true;
    }

    @Override
    protected void onStop() {
        XposedConfigBridge.setFrameworkStateListener(null);
        handler.removeCallbacks(delayedFrameworkStatus);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (Constant.TAB_NAME_ORIGNAL.equals(mCurrentTab) || mValues.isEmpty()) {
            return;
        }
        ArrayList<String> keys = new ArrayList<>();
        ArrayList<String> values = new ArrayList<>();
        boolean[] enabled = new boolean[mValues.size()];
        int index = 0;
        for (ValueItem item : mValues) {
            keys.add(item.getKey());
            values.add(item.getValue() == null ? "" : item.getValue());
            enabled[index++] = item.isChange();
        }
        outState.putString(STATE_TAB, mCurrentTab);
        outState.putStringArrayList(STATE_KEYS, keys);
        outState.putStringArrayList(STATE_VALUES, values);
        outState.putBooleanArray(STATE_ENABLED, enabled);
    }

    private void restorePendingValues(Bundle state) {
        if (state == null) {
            return;
        }
        ArrayList<String> keys = state.getStringArrayList(STATE_KEYS);
        ArrayList<String> values = state.getStringArrayList(STATE_VALUES);
        boolean[] enabled = state.getBooleanArray(STATE_ENABLED);
        if (keys == null || values == null || enabled == null
                || keys.size() != values.size() || keys.size() != enabled.length) {
            return;
        }
        LinkedHashMap<String, String> restored = new LinkedHashMap<>();
        for (int index = 0; index < keys.size(); index++) {
            restored.put(keys.get(index), values.get(index));
        }
        mCurrentTab = state.getString(STATE_TAB, Constant.TAB_NAME_AFTER);
        int color = PreferencesUtils.isUseHardcodedValue(App.getApplication())
                ? Constant.COLOR_CHANGED_HARDCODED : Constant.COLOR_CHANGED_DEFAULT;
        updateUI(restored, color);
        for (int index = 0; index < mValues.size(); index++) {
            ValueItem item = mValues.get(index);
            item.setChange(item.isSupported() && enabled[index]);
        }
        mAdapter.notifyDataSetChanged();
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        List<String> permissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_PHONE_NUMBERS);
        }
        if (Build.VERSION.SDK_INT <= 32) {
            // Android 12 的定位授权必须同时请求粗略与精确级别，否则精确请求可能被忽略。
            boolean coarseGranted = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
            boolean fineGranted = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
            if (!fineGranted) {
                // 即使粗略定位已授予，升级到精确定位也必须再次成组请求两项。
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            } else if (!coarseGranted) {
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissions.toArray(new String[permissions.size()]),
                    REQUEST_DEVICE_PERMISSIONS);
        }
    }

    private void initNavigationView() {
        mDrawerLayout = findViewById(R.id.id_drawer_layout);
        mNavigationView = findViewById(R.id.id_navigator_menu);

        Toolbar toolbar = findViewById(R.id.id_toolbar);
        toolbar.setBackgroundColor(ThemeUtils.getToolBarColor());
        setSupportActionBar(toolbar);

        toolbar.setTitleTextColor(Color.WHITE); //设置标题颜色
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            //设置返回键可用
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(true);
        }
        //创建返回键，并实现打开关/闭监听
        ActionBarDrawerToggle mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar, R.string.open, R.string.close);
        mDrawerToggle.syncState();
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        setupDrawerContent(mNavigationView);
        mNavigationView.setItemIconTintList(ThemeUtils.getNaviItemIconTinkList());
        View headerView = mNavigationView.getHeaderView(0);
        int color = ThemeUtils.getToolBarColor();
        headerView.setBackgroundColor(color);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {  //need api >=21
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(color);
        }

        TextView appnameTextView = headerView.findViewById(R.id.appnameTextView);
        appnameTextView.setText(getString(R.string.header_name) + Utils.getVersionInfo(this));

        // 自己写的方法，设置NavigationView中menu的item被选中后要执行的操作
        onNavgationViewMenuItemSelected(mNavigationView);
    }

    private void initPhoneInfo() {
        if (mPhoneInfo == null) {
            mPhoneInfo = new PhoneInfo();
        }
        PhoneInfo.init(this);
    }

    @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
    private void initView() {
        ll_loading = findViewById(R.id.ll_loading);
        progressBar = findViewById(R.id.progressBar);
        tv_info = findViewById(R.id.tv_info);
        tv_info.setTypeface(Typeface.MONOSPACE);    // 设置等宽字体
        tv_info.setTextIsSelectable(true);
        tv_info.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                ClipboardManager clipboard = (ClipboardManager) getApplication().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = ClipData.newPlainText(null, tv_info.getText());
                clipboard.setPrimaryClip(clipData);
                Utils.toast("copy");
                return false;
            }
        });

        lv_values = findViewById(R.id.lv_values);
        lv_values.setLayoutManager(new LinearLayoutManager(MainActivity.this, LinearLayoutManager.VERTICAL, false));
        mAdapter = new MyAppsAdapter();
        lv_values.setAdapter(mAdapter);
        showTabOne(false);
        ///////////////////////////////////////////
        initPhoneInfo();
        boolean frameworkConnected = XposedActive.isActive();
        frameworkConnectedLast = frameworkConnected;
        waitingForFrameworkConnection = "modern".equals(BuildConfig.XPOSED_VARIANT)
                && !frameworkConnected;
        if (!frameworkConnected) {
            // 如果XP没激活 就显示原始值
            showOrigin();
        } else {
            // 模块自身只用于激活检测，不安装设备信息替换 Hook；直接刷新真实信息可补齐
            // 新版本增加的字段，也可避免旧缓存让随机页的只读字段为空。
            loadOriginalValue();
            //每次打开显示上次修改后的，如果强制指定了硬编码的值就用之。
            if (PreferencesUtils.isUseHardcodedValue(App.getApplication())) {
                mCurrentTab = Constant.TAB_NAME_AFTER;
                updateUI(mergeReplacementValues(PhoneInfo.getReplaceablePhoneInfo(this)),
                        Constant.COLOR_CHANGED_HARDCODED);
            } else {
                LinkedHashMap<String, String> info = PhoneInfoUtils.loadPhoneInfoFromXml(MainActivity.this, Constant.FILENAME_FAKEINFO);
                if (info == null) {
                    showOrigin();
                } else {
                    mCurrentTab = Constant.TAB_NAME_AFTER;
                    updateUI(mergeReplacementValues(info), Constant.COLOR_CHANGED_DEFAULT);
                }
            }
        }

        btnReadBefore = findViewById(R.id.btn_readbefore);
        btnRandomAll = findViewById(R.id.btn_randomall);
        btnSave = findViewById(R.id.btn_save);
        btnSave.setEnabled(!Constant.TAB_NAME_ORIGNAL.equals(mCurrentTab));

        btnReadBefore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTabOne(true);
                btnReadBefore.setEnabled(false);
                //showOrigin();

                new Thread(new DeviceInfoTask(MainActivity.this)).start();
            }
        });

        // 一键随机
        btnRandomAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTabOne(false);
                LinkedHashMap<String, String> replacementValues = RandomInfo.randomPhoneInfo();
                LinkedHashMap<String, Boolean> enabledFields = collectEnabledFields();
                // 随机值与当前暂存的字段开关一次提交，避免先发布开关或丢失未保存选择。
                try {
                    PhoneInfoUtils.savePhoneInfo(
                            MainActivity.this, replacementValues, enabledFields);
                    mCurrentTab = Constant.TAB_NAME_RANDOM;
                    updateUI(mergeReplacementValues(replacementValues),
                            Constant.COLOR_CHANGED_DEFAULT);
                    checkHookAppsOnSaving();
                } catch (Exception e) {
                    Utils.loge("save random profile failed");
                    Utils.toast(R.string.profile_operation_failed);
                }
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTabOne(false);
                saveData();
            }
        });
    }

    private LinkedHashMap<String, String> mergeReplacementValues(
            LinkedHashMap<String, String> replacementValues) {
        return PhoneInfo.mergeForDisplay(mPhoneInfoOrigin, replacementValues);
    }

    private LinkedHashMap<String, Boolean> collectEnabledFields() {
        LinkedHashMap<String, Boolean> enabledFields = new LinkedHashMap<>();
        for (ValueItem item : mValues) {
            if (item.isSupported()) {
                enabledFields.put(item.getKey(), item.isChange());
            }
        }
        return enabledFields;
    }

    private void showTabOne(boolean isShow) {
        if (isShow) {
            ll_loading.setVisibility(View.VISIBLE);
            lv_values.setVisibility(View.INVISIBLE);
        } else {
            ll_loading.setVisibility(View.INVISIBLE);
            lv_values.setVisibility(View.VISIBLE);
        }
    }

    // 显示原始值
    private void showOrigin() {
        mCurrentTab = Constant.TAB_NAME_ORIGNAL;
        loadOriginalValue();
        updateUI(mPhoneInfoOrigin, Constant.COLOR_ORIGINAL);
    }

    // 显示修改后的值
    private void showAfter() {
        mCurrentTab = Constant.TAB_NAME_AFTER;
        if (PreferencesUtils.isUseHardcodedValue(App.getApplication())) {
            updateUI(mergeReplacementValues(PhoneInfo.getReplaceablePhoneInfo(this)),
                    Constant.COLOR_CHANGED_HARDCODED);
        } else {
            LinkedHashMap<String, String> info = PhoneInfoUtils.loadPhoneInfoFromXml(MainActivity.this, Constant.FILENAME_FAKEINFO);
            updateUI(mergeReplacementValues(info), Constant.COLOR_CHANGED_DEFAULT);
        }
    }

    private void checkHookAppsOnSaving() {
        SharedPreferences sp = App.getSharedPreferences();
        String hookPackages = sp.getString("packages", null);
        boolean hookAllApps = sp.getBoolean("hook_all_app", false);
        if (!hookAllApps && TextUtils.isEmpty(hookPackages)) {
            //第一次安装使用，给出一个有情提示
            Utils.toast(MainActivity.this, getString(R.string.tip_already_saved_but_no_apps));
        } else {
            Utils.toast(App.getApplication(), getString(R.string.tip_saved_restart_target));
        }
    }

    private void loadOriginalValue() {
        DeviceInfo deviceInfo = new DeviceInfo();
        mPhoneInfoOrigin = new LinkedHashMap<>();
        LinkedHashMap<String, String> dataValues = deviceInfo.getValues(this);
        for (LinkedHashMap.Entry<String, String> entry : dataValues.entrySet()) {
            this.mPhoneInfoOrigin.put(entry.getKey(), entry.getValue());
        }
    }

    // 逐个控件更新
    private void updateValue(EditText edit, String sAfter, String sBefore, int clrDiff) {
        boolean bSame = false;
        if (sAfter == null) {
            if (sBefore == null) {
                bSame = true;
            }
        } else if (sAfter.equals(sBefore)) {
            bSame = true;
        }
        edit.setText(sAfter);
        edit.setTextColor(bSame ? Color.GRAY : clrDiff);
    }

    //参数二为改变的颜色，查看原始数据时为黑色，不变的为灰色，随机改变后为蓝色，使用硬编码为红色
    private void updateUI(LinkedHashMap<String, String> info, int clrDiff) {
        if (info == null) {
            return;
        }
        if (btnSave != null) {
            btnSave.setEnabled(!Constant.TAB_NAME_ORIGNAL.equals(mCurrentTab));
        }

        // 生成配置时可能会以无 Context 方式读取字段注册表，这会使其中的本地化标题变为空字符串。
        // 列表每次刷新前在 UI 层重建标题，避免“一键随机”后字段名消失。
        PhoneInfo.init(this);
        mValues.clear();
        SharedPreferences sp = App.getSharedPreferences();
        for (Map.Entry<String, String> entry : info.entrySet()) {
            boolean supported = PhoneInfo.isSupported(entry.getKey());
            boolean isChange = supported && sp.getBoolean(entry.getKey(), true);
            String uiName = mPhoneInfo.getUIName(entry.getKey());
            if (TextUtils.isEmpty(uiName)) {
                uiName = entry.getKey() + ":";
            }
            ValueItem item = new ValueItem(entry.getKey(), uiName, entry.getValue(),
                    mPhoneInfo.getValue(entry.getKey()), supported, isChange, clrDiff);
            mValues.add(item);
        }
        mAdapter.notifyDataSetChanged();
    }

    private int getValueTextColor(ValueItem item) {
        if (MainActivity.mCurrentTab.equals(Constant.TAB_NAME_ORIGNAL)) {
            return ContextCompat.getColor(this, R.color.value_text_original);
        }
        boolean changed = item.isChange()
                && !TextUtils.isEmpty(item.getValue())
                && !TextUtils.equals(item.getValue(), mPhoneInfoOrigin.get(item.getKey()));
        if (!changed) {
            return ContextCompat.getColor(this, R.color.value_text_unchanged);
        }
        if (item.getColorDiff() == Constant.COLOR_CHANGED_HARDCODED) {
            return ContextCompat.getColor(this, R.color.value_text_hardcoded);
        }
        return ContextCompat.getColor(this, R.color.value_text_changed);
    }

    private void saveData() {
        if (Constant.TAB_NAME_ORIGNAL.equals(mCurrentTab)) {
            Utils.toast(R.string.original_values_not_saved);
            return;
        }
        LinkedHashMap<String, String> info = new LinkedHashMap<>();
        LinkedHashMap<String, Boolean> enabledFields = new LinkedHashMap<>();
        for (ValueItem item : mValues) {
            //修改的代码已经让修改的数值更新到ValueItem里了，因此无需再获取一遍，直接读取mValues的数值
            //ref: 踩坑记-在 RecyclerView 中使用 EditText 滚动后数据消失 http://www.jianshu.com/p/af820fb21e62
//            if (item.edtValue != null) {
//                item.setValue(item.edtValue.getText().toString());
//            }
            if (item.isSupported()) {
                // 字段值与启用状态分别保存；只读展示值绝不进入替换配置。
                if (item.isChange() && (item.getValue() == null
                        || item.getValue().trim().length() == 0)) {
                    Utils.toast(R.string.enabled_value_required);
                    return;
                }
                info.put(item.getKey(), item.getValue());
                enabledFields.put(item.getKey(), item.isChange());
            }
        }

        try {
            PhoneInfoUtils.savePhoneInfo(MainActivity.this, info, enabledFields);
            checkHookAppsOnSaving();
        } catch (Exception e) {
            Utils.loge("save profile failed");
            Utils.toast(R.string.profile_operation_failed);
            return;
        }

        showAfter();
        startActivity(new Intent(MainActivity.this, AppsActivity.class));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.menu_navigation_view, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            mDrawerLayout.openDrawer(GravityCompat.START);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 设置NavigationView中menu的item被选中后要执行的操作
     *
     * @param mNav
     */
    private void onNavgationViewMenuItemSelected(NavigationView mNav) {
        mNav.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem menuItem) {
                int itemId = menuItem.getItemId();
                if (itemId == R.id.nav_menu_reset) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle(R.string.title_reset_device_info)
                            .setMessage(R.string.msg_are_you_sure)
                            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    try {
                                        loadOriginalValue();
                                        LinkedHashMap<String, String> clearedValues =
                                                new LinkedHashMap<>();
                                        LinkedHashMap<String, Boolean> disabledFields =
                                                new LinkedHashMap<>();
                                        for (String key : PhoneInfo.getHardcodedPhoneInfo(
                                                MainActivity.this).keySet()) {
                                            if (PhoneInfo.isLegacySupported(key)
                                                    || PhoneInfo.isModernSupported(key)) {
                                                clearedValues.put(key, "");
                                                disabledFields.put(key, false);
                                            }
                                        }
                                        PhoneInfoUtils.savePhoneInfo(
                                                MainActivity.this, clearedValues,
                                                disabledFields);
                                        showAfter();
                                        Utils.toast(MainActivity.this, getString(R.string.tip_already_saved));
                                    } catch (RuntimeException ignored) {
                                        Utils.loge("reset profile failed");
                                        Utils.toast(R.string.profile_operation_failed);
                                    }
                                }
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                } else if (itemId == R.id.nav_menu_export) {
                    shareDeviceInfo();
                } else if (itemId == R.id.nav_menu_profile_export) {
                    shareProfile();
                } else if (itemId == R.id.nav_menu_profile_import) {
                    chooseProfile();
                } else if (itemId == R.id.nav_menu_help) {
                    Utils.openUrl(MainActivity.this, Constant.URL_HOME);
                } else if (itemId == R.id.nav_menu_theme) {
                    showThemeSelector();
                } else if (itemId == R.id.nav_menu_app_lists) {
                    startActivity(new Intent(MainActivity.this, AppsActivity.class));
                } else if (itemId == R.id.nav_menu_setting) {
                    startActivityForResult(
                            new Intent(MainActivity.this, SettingActivity.class), REQUEST_ONE);
                }

                // Menu item点击后选中，并关闭Drawerlayout
                menuItem.setChecked(true);
                //drawerlayoutHome.closeDrawers();
                // Toast.makeText(MainActivity.this,msgString,Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    }

    private void shareDeviceInfo() {
        final String htmlDeviceInfo = SettingActivity.getDeviceInfoHtml(MainActivity.this);
        try {
            File exportDir = new File(getCacheDir(), "exports");
            if (!exportDir.exists() && !exportDir.mkdirs()) {
                throw new IllegalStateException("cannot create export directory");
            }
            File file = new File(exportDir, Constant.FILENAME_DEVICEINFO);
            FileOutputStream output = new FileOutputStream(file, false);
            try {
                output.write(htmlDeviceInfo.getBytes("UTF-8"));
            } finally {
                output.close();
            }
            shareFile(file, "text/html",
                    SettingActivity.getRedactedDeviceInfoText(MainActivity.this));
        } catch (Exception e) {
            Utils.loge("export device info failed: " + e.getMessage());
            Utils.toast(R.string.profile_operation_failed);
        }
    }

    private void shareFile(File file, String mimeType, String text) {
        Uri uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileProvider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri(file.getName(), uri));
        intent.putExtra(Intent.EXTRA_SUBJECT, Constant.TAG);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent, file.getName()));
    }

    private void shareProfile() {
        try {
            File exportDir = new File(getCacheDir(), "exports");
            if (!exportDir.exists() && !exportDir.mkdirs()) {
                throw new IllegalStateException("cannot create export directory");
            }
            File file = new File(exportDir, Constant.FILENAME_PROFILE_EXPORT);
            PhoneInfoUtils.exportProfile(this, file);
            shareFile(file, "text/plain", getString(R.string.profile_export_description));
        } catch (Exception e) {
            Utils.loge("export profile failed: " + e.getMessage());
            Utils.toast(R.string.profile_operation_failed);
        }
    }

    private void chooseProfile() {
        Intent intent = new Intent(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        startActivityForResult(intent, REQUEST_IMPORT_PROFILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ONE) {
            if (resultCode == RESULT_OK && data != null
                    && data.getBooleanExtra("needupdate", false)) {
                showAfter();
            }
        } else if (requestCode == REQUEST_IMPORT_PROFILE && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            try {
                InputStream input = getContentResolver().openInputStream(data.getData());
                if (input == null) {
                    throw new IllegalStateException("cannot open selected profile");
                }
                try {
                    PhoneInfoUtils.importProfile(this, input);
                } finally {
                    input.close();
                }
                showAfter();
                Utils.toast(R.string.profile_imported_restart_target);
            } catch (Exception e) {
                Utils.loge("import profile failed: " + e.getMessage());
                Utils.toast(R.string.profile_operation_failed);
            }
        }
    }

    private void isXposedActived() {
        if (waitingForFrameworkConnection) {
            handler.removeCallbacks(delayedFrameworkStatus);
            handler.postDelayed(delayedFrameworkStatus, 1000L);
            return;
        }
        if (!XposedActive.isActive()) {
            Utils.toast(this, getString(R.string.xpose_not_actived));
        } else {
            Utils.toast(this, getString(R.string.xpose_actived));
        }

        //这里不弹框提示了，这样本APP还可以作为普通查看设备信息的工具使用
        if (false) {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this, R.style.MyThemeGray);
            builder.setTitle(R.string.xpose_not_actived)
                    .setMessage(R.string.xpose_please_active)
                    .setIcon(R.mipmap.ic_launcher)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .show();
        }
    }

    private class MyAppsAdapter extends RecyclerView.Adapter {

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemLayout = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_hookvalue, parent, false);
            return new ItemHolder(itemLayout);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int i) {
            final ItemHolder viewHolder = (ItemHolder) holder;
            final ValueItem item = mValues.get(i);
            item.edtValue = viewHolder.edtValue;
            viewHolder.key = item.getKey();
            viewHolder.checkChange.setText(item.getUiName());
            viewHolder.checkChange.setOnCheckedChangeListener(null);
            viewHolder.checkChange.setChecked(item.isChange());
            viewHolder.checkChange.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    item.setChange(isChecked);
                    viewHolder.edtValue.setTextColor(getValueTextColor(item));
                }
            });

            viewHolder.tvUIName.setVisibility(View.GONE);

            boolean editable = !MainActivity.mCurrentTab.equals(Constant.TAB_NAME_ORIGNAL)
                    && item.isSupported();
            viewHolder.checkChange.setEnabled(editable);
            viewHolder.checkChange.setClickable(editable);
            viewHolder.edtValue.setEnabled(editable);

            ///////////////////////////////////////////////////////////////
            //viewHolder.edtValue.setText(item.getValue());
            //ref: 踩坑记-在 RecyclerView 中使用 EditText 滚动后数据消失 http://www.jianshu.com/p/af820fb21e62
            if (viewHolder.edtValue.getTag() instanceof TextWatcher) {
                viewHolder.edtValue.removeTextChangedListener((TextWatcher) viewHolder.edtValue.getTag());
            }
            viewHolder.edtValue.setText(item.getValue() == null ? "" : item.getValue());
            // 部分厂商 EditText 在 setText 时会重新应用状态颜色，因此必须在设置内容后再设置高对比度文字色。
            viewHolder.edtValue.setTextColor(getValueTextColor(item));
            TextWatcher watcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    item.setValue(viewHolder.edtValue.getText().toString());
                }
            };

            viewHolder.edtValue.addTextChangedListener(watcher);
            viewHolder.edtValue.setTag(watcher);
            ///////////////////////////////////////////////////////////////
        }

        @Override
        public int getItemCount() {
            return mValues.size();
        }


        class ItemHolder extends RecyclerView.ViewHolder {
            String key;
            private CheckBox checkChange;
            private TextView tvUIName;
            private EditText edtValue;

            public ItemHolder(View itemView) {
                super(itemView);
                checkChange = itemView.findViewById(R.id.checkChange);
                tvUIName = itemView.findViewById(R.id.tvUIName);
                edtValue = itemView.findViewById(R.id.edtValue);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_DEVICE_PERMISSIONS) {
            return;
        }

        // 无论授权还是拒绝都重新采集，使界面显示真实值或明确的“权限未授予”状态。
        refreshDisplayedDeviceInformation();
    }

    private void refreshDisplayedDeviceInformation() {
        loadOriginalValue();
        if (Constant.TAB_NAME_ORIGNAL.equals(mCurrentTab)) {
            updateUI(mPhoneInfoOrigin, Constant.COLOR_ORIGINAL);
            return;
        }
        for (ValueItem item : mValues) {
            if (!item.isSupported() && mPhoneInfoOrigin.containsKey(item.getKey())) {
                item.setValue(mPhoneInfoOrigin.get(item.getKey()));
            }
        }
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private static final class DeviceInfoTask implements Runnable {
        private final Context applicationContext;
        private final WeakReference<MainActivity> activityReference;

        private DeviceInfoTask(MainActivity activity) {
            applicationContext = activity.getApplicationContext();
            activityReference = new WeakReference<>(activity);
        }

        @Override
        public void run() {
            String text = SettingActivity.getDeviceInfoText(applicationContext);
            MainActivity activity = activityReference.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            Message message = Message.obtain();
            message.what = LOAD_FINISHED;
            message.obj = text;
            activity.handler.sendMessage(message);
        }
    }
}
