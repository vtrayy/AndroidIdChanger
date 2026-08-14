package com.bigsing.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;

import com.bigsing.changer.App;
import com.bigsing.changer.R;

/**
 */

public class ThemeUtils {
    private static int defalutThemeColor = Color.rgb(0, 0x77, 0xD9);    //R.color.blue_base

    private static Context context() {
        return App.getApplication();
    }

    public static int getThemePosition() {
        SharedPreferences pref = context().getSharedPreferences("ThemeColor", Context.MODE_PRIVATE);
        return pref.getInt("position", 0);
    }

    public static void setThemePosition(int position) {
        SharedPreferences.Editor editor = context().getSharedPreferences("ThemeColor", Context.MODE_PRIVATE).edit();
        editor.putInt("position", position);
        editor.apply();
    }

    public static int getToolBarColor() {

        //if(MyApplication.getInstance().isNightMode()){
        // return context.getResources().getColor(R.color.green_dark);
        //   }else {
        return getThemeColor();
        // }
    }

    public static int getThemeColor() {
        SharedPreferences pref = context().getSharedPreferences("ThemeColor", Context.MODE_PRIVATE);
        return pref.getInt("themeColor", defalutThemeColor);
    }

    public static void setThemeColor(int color) {
        SharedPreferences.Editor editor = context().getSharedPreferences("ThemeColor", Context.MODE_PRIVATE).edit();
        editor.putInt("themeColor", color);
        editor.apply();
    }

    public static ColorStateList getNaviItemIconTinkList() {
        int position = getThemePosition();
        Resources resource = context().getResources();
        ColorStateList csl;
        switch (position) {
            case 0:
                csl = resource.getColorStateList(R.color.theme_redbase_nav_menu_icontint);
                return csl;
            case 1:
                csl = resource.getColorStateList(R.color.theme_blue_navi_menu_icontint);
                return csl;
            case 2:
                csl = resource.getColorStateList(R.color.theme_lightblue_navi_menu_icontint);
                return csl;
            case 3:
                csl = resource.getColorStateList(R.color.theme_black_navi_menu_icontint);
                return csl;
            case 4:
                csl = resource.getColorStateList(R.color.theme_teal_navi_menu_icontink);
                return csl;
            case 5:
                csl = resource.getColorStateList(R.color.theme_brown_navi_menu_icontint);
                return csl;
            case 6:
                csl = resource.getColorStateList(R.color.theme_green_navi_menu_icontink);
                return csl;
            case 7:
                csl = resource.getColorStateList(R.color.theme_red_navi_menu_icontink);
                return csl;

        }
        csl = resource.getColorStateList(R.color.theme_redbase_tablayout_text_colorlist);
        return csl;
    }


    public static ColorStateList getTabTextColorStateList() {
        int position = getThemePosition();
        Resources resource = context().getResources();
        ColorStateList csl;
        switch (position) {
            case 0:
                csl = resource.getColorStateList(R.color.theme_redbase_tablayout_text_colorlist);
                return csl;
            case 1:
                csl = resource.getColorStateList(R.color.theme_blue_tablayout_text_corlorlist);
                return csl;
            case 2:
                csl = resource.getColorStateList(R.color.theme_lightblue_tablayout_text_colorlist);
                return csl;
            case 3:
                csl = resource.getColorStateList(R.color.theme_black_tablayout_text_colorlist);
                return csl;
            case 4:
                csl = resource.getColorStateList(R.color.theme_teal_tablayout_text_colorlist);
                return csl;
            case 5:
                csl = resource.getColorStateList(R.color.theme_brown_tablayout_text_colorlist);
                return csl;
            case 6:
                csl = resource.getColorStateList(R.color.theme_green_tablayout_text_colorlist);
                return csl;
            case 7:
                csl = resource.getColorStateList(R.color.theme_red_tablayout_text_colorlist);
                return csl;


        }
        csl = resource.getColorStateList(R.color.theme_redbase_tablayout_text_colorlist);
        return csl;

    }
}
