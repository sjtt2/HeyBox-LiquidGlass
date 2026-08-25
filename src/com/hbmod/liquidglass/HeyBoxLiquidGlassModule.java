package com.hbmod.liquidglass;

import android.app.Activity;
import android.app.Instrumentation;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Method;

public class HeyBoxLiquidGlassModule extends XposedModule {

    private static final String TAG = "HeyBoxLiquidGlass";

    static final String TARGET_PKG = "com.max.xiaoheihe";
    private static final String TARGET_ACTIVITY = "com.max.xiaoheihe.MainActivity";

    private static volatile int sResumeHits;

    public HeyBoxLiquidGlassModule() {
        super();
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        String proc = param.getProcessName();
        log(android.util.Log.INFO, TAG, "onModuleLoaded process=" + proc
                + " api=" + getApiVersion()
                + " framework=" + getFrameworkName() + " " + getFrameworkVersion());
        if (!TARGET_PKG.equals(proc)) {
            log(android.util.Log.INFO, TAG,
                    "not target main process, detach");
            detach();
            return;
        }
        try {
            Method callOnResume = Instrumentation.class.getMethod(
                    "callActivityOnResume", Activity.class);
            hook(callOnResume)
                    .setExceptionMode(ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            Object arg0 = chain.getArg(0);
                            if (arg0 instanceof Activity
                                    && TARGET_ACTIVITY.equals(arg0.getClass().getName())) {
                                sResumeHits++;
                                if (sResumeHits <= 3 || sResumeHits % 20 == 0) {
                                    log(android.util.Log.INFO, TAG,
                                            "MainActivity onResume #" + sResumeHits);
                                }
                                LiquidGlassInstaller.scheduleInstall((Activity) arg0);
                            }
                        } catch (Throwable t) {
                            logErr("resume hook error", t);
                        }
                        return result;
                    });
            log(android.util.Log.INFO, TAG,
                    "hooked Instrumentation.callActivityOnResume");
        } catch (Throwable t) {
            logErr("install hook failed", t);
        }
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!TARGET_PKG.equals(param.getPackageName())) {
            return;
        }
        log(android.util.Log.INFO, TAG,
                "target package loaded: " + param.getPackageName());
    }

    static void log(int prio, String msg) {
        android.util.Log.println(prio, TAG, msg);
    }

    static void logErr(String msg, Throwable t) {
        android.util.Log.e(TAG, msg, t);
    }
}
