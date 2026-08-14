package com.bigsing.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.bigsing.changer.App;
import com.bigsing.changer.constant.Constant;

/**
 */

public class PreferencesUtils {

    private static SharedPreferences mSP = null;

    private static SharedPreferences getPreferences(Context context) {
        if (mSP == null) {
            mSP = App.getSharedPreferences();
            if (mSP == null) {
                mSP = context.getSharedPreferences(
                        Constant.TAG, App.getPreferenceMode());
            }
        }
        return mSP;
    }

    public static boolean isUseHardcodedValue(Context context) {
        boolean b = false;
        if (mSP == null) {
            getPreferences(context);
        }
        if (mSP != null) {
            b = mSP.getBoolean("use_hardcoded_value", false);
        }
        return b;
    }

    public static boolean isDebugMode(Context context) {
        boolean b = false;
        if (mSP == null) {
            getPreferences(context);
        }
        if (mSP != null) {
            b = mSP.getBoolean("debug_mode", false);
        }
        return b;
    }


}
