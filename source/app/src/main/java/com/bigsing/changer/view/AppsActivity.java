package com.bigsing.changer.view;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.bigsing.changer.App;
import com.bigsing.changer.R;
import com.bigsing.changer.config.ScopeMatcher;
import com.bigsing.changer.constant.Constant;
import com.bigsing.changer.model.InstalledAppInfo;
import com.bigsing.changer.model.InstalledApps;
import com.bigsing.util.ThemeUtils;
import com.bigsing.util.Utils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppsActivity extends Activity {
    public static final int LOAD_FINISHED = 1;
    public static final int LOAD_FAILED = 2;
    private static final String TAG = "AppsActivity";
    private static final String STATE_RUNNING_TAB = "state.runningTab";
    private boolean mIsForSelect = false;

    private View view_background;
    private CheckBox checkbox_all;
    private CheckBox checkbox_sysapp_included;
    private Button bt_runningApps;
    private Button bt_installedApps;
    private View ll_loading;
    private RecyclerView lv_apps;

    //hook包名列表com.x.a com.x.b
    private List<InstalledAppInfo> mUserApps = new ArrayList<InstalledAppInfo>();
    private RecyclerView.Adapter adapter = new MyAppsAdapter();
    private int loadGeneration;
    private volatile boolean destroyed;

    //显示哪个TAB
    private boolean mIsShowRunningApp = false;

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        @SuppressWarnings("unchecked")
        public void handleMessage(Message msg) {
            if (destroyed || msg.arg1 != loadGeneration) {
                return;
            }
            ll_loading.setVisibility(View.INVISIBLE);
            if (msg.what == LOAD_FINISHED) {
                mUserApps = (List<InstalledAppInfo>) msg.obj;
                adapter.notifyDataSetChanged();
            } else if (msg.what == LOAD_FAILED) {
                Utils.toast(R.string.running_apps_unavailable);
            }
        }
    };

    public static void addHookList(String packageName, boolean addremove) {
        if (packageName.equals(Constant.PACKAGE_THIS_TOOL)) {
            Utils.toast(R.string.cannothookthisapp);
            return;
        }

        SharedPreferences sp = App.getSharedPreferences();
        Set<String> selectedPackages = ScopeMatcher.parse(sp.getString("packages", ""));

        if (addremove) {
            selectedPackages.add(packageName);
        } else {
            selectedPackages.remove(packageName);
        }
        sp.edit().putString("packages", ScopeMatcher.serialize(selectedPackages)).apply();
        Utils.toast(R.string.scope_saved_restart_target);
    }

    //////////////////////////////////////////////////////////////////////////////
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apps);

        //新页面接收数据
        Bundle bundle = this.getIntent().getExtras();
        if (bundle != null) {
            mIsForSelect = bundle.getBoolean("forselect");
        }
        mIsShowRunningApp = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_RUNNING_TAB, false)
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;

        view_background = findViewById(R.id.view_background);
        checkbox_all = findViewById(R.id.checkbox_all);
        checkbox_sysapp_included = findViewById(R.id.checkbox_sysapp_included);
        bt_runningApps = findViewById(R.id.bt_runningApps);
        bt_installedApps = findViewById(R.id.bt_installedApps);
        ll_loading = findViewById(R.id.ll_loading);
        lv_apps = findViewById(R.id.lv_apps);
        lv_apps.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        lv_apps.setAdapter(adapter);
        view_background.setBackgroundColor(ThemeUtils.getToolBarColor());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Android 5.0 起公开 API 不再能可靠枚举其他正在运行的应用。
            bt_runningApps.setVisibility(View.GONE);
        }

        //注册长按菜单
        registerForContextMenu(lv_apps);

        //查看正在运行的app列表
        bt_runningApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Utils.toast(R.string.running_apps_unavailable);
                    return;
                }
                mIsShowRunningApp = true;
                ll_loading.setVisibility(View.VISIBLE);
                bt_runningApps.setSelected(true);
                bt_installedApps.setSelected(false);
                final boolean isIncludeSysApp = checkbox_sysapp_included.isChecked();
                final int generation = ++loadGeneration;

                new Thread() {
                    @Override
                    public void run() {
                        try {
                            Map<String, InstalledAppInfo> runningApps = new LinkedHashMap<>();
                            ActivityManager manager = (ActivityManager) AppsActivity.this
                                    .getSystemService(Activity.ACTIVITY_SERVICE);
                            if (manager == null) {
                                throw new IllegalStateException("activity manager unavailable");
                            }
                            for (ActivityManager.RunningTaskInfo task
                                    : manager.getRunningTasks(1000)) {
                                ComponentName component = task.topActivity;
                                if (component != null) {
                                    addRunningApp(runningApps, component.getPackageName());
                                }
                            }
                            for (ActivityManager.RunningServiceInfo service
                                    : manager.getRunningServices(1000)) {
                                ComponentName component = service.service;
                                if (component != null) {
                                    addRunningApp(runningApps, component.getPackageName());
                                }
                            }

                            sendLoadResult(generation, LOAD_FINISHED,
                                    filterApps(new ArrayList<>(runningApps.values()),
                                            isIncludeSysApp));
                        } catch (RuntimeException ignored) {
                            sendLoadResult(generation, LOAD_FAILED, null);
                        }
                    }
                }.start();
            }
        });

        //查看已安装的app列表
        bt_installedApps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsShowRunningApp = false;
                ll_loading.setVisibility(View.VISIBLE);
                bt_runningApps.setSelected(false);
                bt_installedApps.setSelected(true);
                final boolean isIncludeSysApp = checkbox_sysapp_included.isChecked();
                final int generation = ++loadGeneration;

                new Thread() {
                    @Override
                    public void run() {
                        try {
                            List<InstalledAppInfo> apps =
                                    InstalledApps.getAppsInfo(AppsActivity.this);
                            sendLoadResult(generation, LOAD_FINISHED,
                                    filterApps(new ArrayList<>(apps), isIncludeSysApp));
                        } catch (RuntimeException ignored) {
                            sendLoadResult(generation, LOAD_FAILED, null);
                        }
                    }
                }.start();
            }
        });

        // 全选/全不选
        checkbox_all.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mUserApps != null) {
                    SharedPreferences preferences = App.getSharedPreferences();
                    Set<String> selectedPackages = ScopeMatcher.parse(
                            preferences.getString("packages", ""));
                    for (InstalledAppInfo app : mUserApps) {
                        app.setChecked(isChecked);
                        if (isChecked) {
                            selectedPackages.add(app.getPackageName());
                        } else {
                            selectedPackages.remove(app.getPackageName());
                        }
                    }
                    preferences.edit().putString("packages",
                            ScopeMatcher.serialize(selectedPackages)).apply();
                    Utils.toast(R.string.scope_saved_restart_target);
                    adapter.notifyDataSetChanged();
                }
            }
        });

        // 包含系统app
        checkbox_sysapp_included.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                fresh();
            }
        });

        //默认显示已安装的app
        fresh();
    }

    private void fresh() {
        if (mIsShowRunningApp) {
            bt_runningApps.performClick();
        } else {
            bt_installedApps.performClick();
        }
    }

    private void addRunningApp(Map<String, InstalledAppInfo> runningApps,
                               String packageName) {
        if (!runningApps.containsKey(packageName)) {
            InstalledAppInfo app = InstalledApps.getAppInfo(this, packageName);
            if (app != null) {
                runningApps.put(packageName, app);
            }
        }
    }

    private void sendLoadResult(int generation, int result,
                                List<InstalledAppInfo> apps) {
        if (destroyed) {
            return;
        }
        Message message = Message.obtain();
        message.what = result;
        message.arg1 = generation;
        message.obj = apps;
        handler.sendMessage(message);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_RUNNING_TAB, mIsShowRunningApp);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        loadGeneration++;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /**
     * 区分出用户程序和系统程序
     */
    private List<InstalledAppInfo> filterApps(
            List<InstalledAppInfo> apps, boolean isInlcudeSysApp) {
        boolean bGotThis = false;
        int nTopIndex = 0;
        boolean bHooked = false;
        SharedPreferences preferences = App.getSharedPreferences();
        String hookPackages = preferences.getString("packages", "");

        //先排序
        Collections.sort(apps, new SortByName());
        List<InstalledAppInfo> filteredApps = new ArrayList<InstalledAppInfo>();
        for (InstalledAppInfo appinfo : apps) {
            if (isInlcudeSysApp || !appinfo.isSystem()) {
                bHooked = ScopeMatcher.contains(hookPackages, appinfo.getPackageName());
                appinfo.setAddedHook(bHooked);
                appinfo.setChecked(bHooked);

                if (!bGotThis) {
                    if (appinfo.getPackageName().equals(Constant.PACKAGE_THIS_TOOL)) {
                        bGotThis = true;
                        // 本App不添加到列表。
                    } else {
                        if (bHooked) {
                            filteredApps.add(nTopIndex, appinfo);
                            nTopIndex++;
                        } else {
                            filteredApps.add(appinfo);
                        }
                    }
                } else {
                    if (bHooked) {
                        filteredApps.add(nTopIndex, appinfo);
                        nTopIndex++;
                    } else {
                        filteredApps.add(appinfo);
                    }
                }
            }
        }
        return filteredApps;
    }

    private void clickOneItem(int position) {
        if (mIsForSelect) {
            InstalledAppInfo info = mUserApps.get(position);
            Intent intent = getIntent();
            Bundle bundle = new Bundle();
            bundle.putSerializable("view", AppsActivity.class.getName());
            bundle.putSerializable("package", info.getPackageName());
            intent.putExtras(bundle);
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    class SortByName implements Comparator {
        public int compare(Object o1, Object o2) {
            InstalledAppInfo app1 = (InstalledAppInfo) o1;
            InstalledAppInfo app2 = (InstalledAppInfo) o2;
            if (app1.isSystem() == app2.isSystem()) {
                return app1.getName().compareTo(app2.getName());
            }

            return app1.isSystem() ? 1 : -1;
        }
    }

    // 滚动问题解决参考：https://stackoverflow.com/questions/32427889/checkbox-in-recyclerview-keeps-on-checking-different-items
    private class MyAppsAdapter extends RecyclerView.Adapter {

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemLayout = LayoutInflater.from(AppsActivity.this).inflate(R.layout.item_app, parent, false);
            return new ItemHolder(itemLayout);
        }

        @Override
        public int getItemCount() {
            if (mUserApps != null) {
                return mUserApps.size();
            }
            return 0;
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int i) {
            final ItemHolder viewHolder = (ItemHolder) holder;
            final InstalledAppInfo appInfo = mUserApps.get(i);

            viewHolder.packName = appInfo.getPackageName();
            viewHolder.iv_icon.setImageDrawable(appInfo.getIcon());
            viewHolder.tv_name.setText(appInfo.getName() + " " + appInfo.getVersionName());
            if (appInfo.isChecked()) {
                viewHolder.tv_name.setTextColor(Color.RED);
            } else if (appInfo.isSystem()) {
                viewHolder.tv_name.setTextColor(Color.BLUE);
            } else {
                viewHolder.tv_name.setTextColor(Color.BLACK);
            }
            viewHolder.tv_version.setText(appInfo.getPackageName());

            viewHolder.checkBox.setTag(appInfo);
            viewHolder.checkBox.setOnCheckedChangeListener(null);
            viewHolder.checkBox.setChecked(appInfo.isChecked());
            viewHolder.checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    appInfo.setChecked(isChecked);
                    addHookList(appInfo.getPackageName(), isChecked);
                    viewHolder.tv_name.setTextColor(isChecked ? Color.RED : Color.BLACK);
                }
            });

        }

        class ItemHolder extends RecyclerView.ViewHolder {
            CheckBox checkBox;
            ImageView iv_icon;
            TextView tv_name;
            TextView tv_version;
            String packName;

            public ItemHolder(View itemView) {
                super(itemView);
                checkBox = itemView.findViewById(R.id.check_box);
                iv_icon = itemView.findViewById(R.id.iv_appmanager_icon);
                tv_name = itemView.findViewById(R.id.tv_appName);
                tv_version = itemView.findViewById(R.id.tv_appPackage);
                View.OnClickListener listener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        clickOneItem(getLayoutPosition());
                    }
                };
                iv_icon.setOnClickListener(listener);
            }
        }


    }
}
