package com.bigsing.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/** 平台级预测性返回适配，不引入 AndroidX。 */
public final class BackNavigation {
    private BackNavigation() {
    }

    public static Object register(Activity activity, Runnable action) {
        if (Build.VERSION.SDK_INT < 33) {
            return null;
        }
        return Api33.register(activity, action);
    }

    public static void unregister(Activity activity, Object callback) {
        if (Build.VERSION.SDK_INT >= 33 && callback != null) {
            Api33.unregister(activity, callback);
        }
    }

    @TargetApi(33)
    private static final class Api33 {
        private Api33() {
        }

        static Object register(Activity activity, final Runnable action) {
            OnBackInvokedCallback callback = new OnBackInvokedCallback() {
                @Override
                public void onBackInvoked() {
                    action.run();
                }
            };
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return callback;
        }

        static void unregister(Activity activity, Object callback) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                    (OnBackInvokedCallback) callback);
        }
    }
}
