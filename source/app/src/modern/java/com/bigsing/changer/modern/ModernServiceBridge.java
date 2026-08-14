package com.bigsing.changer.modern;

import android.app.Application;
import android.content.SharedPreferences;

import com.bigsing.changer.App;
import com.bigsing.changer.config.ScopeMatcher;
import com.bigsing.changer.constant.Constant;
import com.bigsing.util.PhoneInfo;
import com.bigsing.util.PreferencesUtils;

import java.util.LinkedHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** modern flavor 的应用侧服务桥；将本地配置镜像到框架 RemotePreferences。 */
public final class ModernServiceBridge implements XposedServiceHelper.OnServiceListener {
    private static final String PREFERENCE_GROUP = "device_profile";
    private static final ModernServiceBridge INSTANCE = new ModernServiceBridge();
    private static final int MAX_RETRIES = 3;
    private static boolean initialized;
    private static volatile Runnable frameworkStateListener;

    private volatile XposedService service;
    private final Object publishLock = new Object();
    private final ScheduledExecutorService publishExecutor =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "AndroidIdChanger-config");
                    thread.setDaemon(true);
                    return thread;
                }
            });
    private long requestedGeneration;
    private boolean publishScheduled;
    private int retryCount;

    private ModernServiceBridge() {
    }

    public static synchronized void initialize(Application application) {
        if (initialized) {
            return;
        }
        initialized = true;
        XposedServiceHelper.registerListener(INSTANCE);
    }

    public static boolean sync() {
        INSTANCE.requestPublish();
        return isFrameworkConnected();
    }

    public static boolean isFrameworkConnected() {
        XposedService currentService = INSTANCE.service;
        try {
            return currentService != null
                    && (currentService.getFrameworkProperties()
                    & XposedService.PROP_CAP_REMOTE) != 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static void setFrameworkStateListener(Runnable listener) {
        frameworkStateListener = listener;
        if (listener != null) {
            listener.run();
        }
    }

    @Override
    public void onServiceBind(XposedService boundService) {
        service = boundService;
        requestPublish();
        notifyFrameworkStateChanged();
    }

    @Override
    public void onServiceDied(XposedService deadService) {
        if (service == deadService) {
            service = null;
            notifyFrameworkStateChanged();
        }
    }

    private static void notifyFrameworkStateChanged() {
        Runnable listener = frameworkStateListener;
        if (listener != null) {
            listener.run();
        }
    }

    private void requestPublish() {
        synchronized (publishLock) {
            requestedGeneration++;
            retryCount = 0;
            schedulePublishLocked(0L);
        }
    }

    private void schedulePublishLocked(long delayMillis) {
        if (publishScheduled) {
            return;
        }
        publishScheduled = true;
        publishExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                publishPending();
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void publishPending() {
        long generation;
        synchronized (publishLock) {
            generation = requestedGeneration;
        }
        boolean success = publish();
        synchronized (publishLock) {
            publishScheduled = false;
            if (success && requestedGeneration == generation) {
                retryCount = 0;
                return;
            }
            if (success) {
                schedulePublishLocked(0L);
                return;
            }
            if (service != null && retryCount < MAX_RETRIES) {
                retryCount++;
                schedulePublishLocked(1000L);
            }
            // 重试耗尽后保留 generation；服务重绑或下一次配置变化会再次发布。
        }
    }

    private boolean publish() {
        XposedService currentService = service;
        try {
            if (currentService == null
                    || (currentService.getFrameworkProperties()
                    & XposedService.PROP_CAP_REMOTE) == 0) {
                return false;
            }

            SharedPreferences settings = App.getSharedPreferences();
            SharedPreferences savedProfile = App.getFakeSharedPreferences();
            if (settings == null || savedProfile == null) {
                return false;
            }

            boolean useHardcoded = PreferencesUtils.isUseHardcodedValue(App.getApplication());
            LinkedHashMap<String, String> defaults =
                    PhoneInfo.getReplaceablePhoneInfo(App.getApplication());
            String packages = ScopeMatcher.serialize(
                    ScopeMatcher.parse(settings.getString("packages", "")));
            boolean allPackages = settings.getBoolean("hook_all_app", false);

            SharedPreferences.Editor editor =
                    currentService.getRemotePreferences(PREFERENCE_GROUP).edit().clear();
            editor.putBoolean("profile.enabled", allPackages || packages.length() > 0);
            editor.putBoolean("scope.all", allPackages);
            editor.putString("scope.packages", packages);
            for (String key : defaults.keySet()) {
                String storageKey = Constant.PREF_VALUE_PREFIX + key;
                String savedValue = settings.contains(storageKey)
                        ? settings.getString(storageKey, "")
                        : savedProfile.getString(key, "");
                String value = useHardcoded ? defaults.get(key) : savedValue;
                editor.putString("value." + key, value == null ? "" : value);
                editor.putBoolean("enabled." + key,
                        PhoneInfo.isSupported(key) && settings.getBoolean(key, true));
            }
            return editor.commit();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}
