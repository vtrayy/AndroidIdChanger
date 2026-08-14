package com.bigsing;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;

import com.bigsing.adapter.EasyRecyclerViewAdapter;
import com.bigsing.adapter.ThemeColorAdapter;
import com.bigsing.changer.R;
import com.bigsing.util.ActivityCollector;
import com.bigsing.util.BackNavigation;
import com.bigsing.util.ThemeColor;
import com.bigsing.util.ThemeUtils;

import java.util.ArrayList;

/**
 */

public abstract class BaseActivity extends AppCompatActivity {
    protected String actName;//用于友盟页面统计
    private ArrayList<ThemeColor> themeColorList = new ArrayList<>();
    private ThemeColorAdapter themeColorAdapter = new ThemeColorAdapter();
    private Object backCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityCollector.getInstance().addActivity(this);
        actName = setActName();
        initThemeData();
        backCallback = BackNavigation.register(this, new Runnable() {
            @Override
            public void run() {
                handleBackNavigation();
            }
        });
    }

    public abstract String setActName();


    private void initThemeData() {
        themeColorAdapter = new ThemeColorAdapter();
        themeColorList.add(new ThemeColor(R.color.theme_red_base));
        themeColorList.add(new ThemeColor(R.color.theme_blue));
        themeColorList.add(new ThemeColor(R.color.theme_blue_light));
        themeColorList.add(new ThemeColor(R.color.theme_balck));
        themeColorList.add(new ThemeColor(R.color.theme_teal));
        themeColorList.add(new ThemeColor(R.color.theme_brown));
        themeColorList.add(new ThemeColor(R.color.theme_green));
        themeColorList.add(new ThemeColor(R.color.theme_red));
        themeColorAdapter.setDatas(themeColorList);
        themeColorAdapter.setOnItemClickListener(new EasyRecyclerViewAdapter.OnItemClickListener() {
            @Override
            public void OnItemClick(View view, int position, Object data) {
                for (ThemeColor themeColor : themeColorList) {
                    themeColor.setChosen(false);
                }
                themeColorList.get(position).setChosen(true);
                themeColorAdapter.notifyDataSetChanged();
            }
        });
    }

    public void showThemeSelector() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_theme_color, null, false);
        RecyclerView recyclerView = view.findViewById(R.id.theme_recycler_view);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 4));
        recyclerView.setAdapter(themeColorAdapter);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.title_select_theme)
                .setView(view)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ThemeUtils.setThemeColor(getResources().getColor(themeColorList.get(themeColorAdapter.getPosition()).getColor()));// 不要变换位置
                        ThemeUtils.setThemePosition(themeColorAdapter.getPosition());
                        // finish();
                        new Handler().postDelayed(new Runnable() {
                            public void run() {
                                ActivityCollector.getInstance().refreshAllActivity();
                                // closeHandler.sendEmptyMessageDelayed(MSG_CLOSE_ACTIVITY, 300);
                            }
                        }, 100);
                    }
                })
                .show();
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onRestart() {
        super.onRestart();

    }

    public void onResume() {
        super.onResume();
        // MobclickAgent.onPageStart(actName);
        // MobclickAgent.onResume(this);
    }

    public void onPause() {
        super.onPause();
        //  MobclickAgent.onPageEnd(actName);
        // MobclickAgent.onPause(this);
    }


    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        BackNavigation.unregister(this, backCallback);
        ActivityCollector.getInstance().removeActivity(this);
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }

    protected void handleBackNavigation() {
        finish();
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        handleBackNavigation();
    }
}
