package com.swift.sandhook.xposedcompat.methodgen;

import android.os.Trace;

import com.swift.sandhook.SandHook;
import com.swift.sandhook.blacklist.HookBlackList;
import com.swift.sandhook.wrapper.HookWrapper;
import com.swift.sandhook.xposedcompat.XposedCompat;
import com.swift.sandhook.xposedcompat.classloaders.ProxyClassLoader;
import com.swift.sandhook.xposedcompat.hookstub.HookMethodEntity;
import com.swift.sandhook.xposedcompat.hookstub.HookStubManager;
import com.swift.sandhook.xposedcompat.utils.DexLog;
import com.swift.sandhook.xposedcompat.utils.FileUtils;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XposedBridge;

public final class DynamicBridge {

    private static HookMaker defaultHookMaker = XposedCompat.useNewCallBackup ? new HookerDexMakerNew() : new HookerDexMaker();
    private static final AtomicBoolean dexPathInited = new AtomicBoolean(false);
    private static File dexDir;

    //use internal stubs
    private final static Map<Member, HookMethodEntity> entityMap = new HashMap<>();
    //use dex maker
    private final static HashMap<Member, Method> hookedInfo = new HashMap<>();

    public static synchronized void hookMethod(Member hookMethod, XposedBridge.AdditionalHookInfo additionalHookInfo) {


        DexLog.d("sanbo",
                "==================================================================================="
                        + "\r\n\t==================================================================================="
                        + "\r\n\t===inside hookMethod(" + hookMethod + ")==="
                        + "\r\n\t==================================================================================="
                        + "\r\n\t==================================================================================="
        );
        if (!checkMember(hookMethod)) {
            return;
        }

        if (hookedInfo.containsKey(hookMethod) || entityMap.containsKey(hookMethod)) {
            DexLog.w("already hook method:" + hookMethod.toString());
            return;
        }

        try {
            if (dexPathInited.compareAndSet(false, true)) {
                try {
                    String fixedAppDataDir = XposedCompat.getCacheDir().getAbsolutePath();
                    dexDir = new File(fixedAppDataDir, "/sandxposed/");
                    if (!dexDir.exists())
                        dexDir.mkdirs();
                } catch (Throwable throwable) {
                    DexLog.e("error when init dex path", throwable);
                }
            }
            Trace.beginSection("SandHook-Xposed");
            long timeStart = System.currentTimeMillis();
            HookMethodEntity stub = null;
            if (XposedCompat.useInternalStub && !HookBlackList.canNotHookByStub(hookMethod) && !HookBlackList.canNotHookByBridge(hookMethod)) {

                DexLog.d("sanbo", "hookMethod----->" + hookMethod + "-------------" + ((Method) hookMethod).getParameterTypes().length);
                DexLog.d("sanbo", "hookMethod additionalHookInfo----->" + additionalHookInfo);
                stub = HookStubManager.getHookMethodEntity(hookMethod, additionalHookInfo);
                DexLog.i("sanbo", "hookMethod --------stub:\r\n\t" + stub);
            }
            if (stub != null) {
                DexLog.d("sanbo", "1 hookMethod 即将开始hook");
                SandHook.hook(new HookWrapper.HookEntity(hookMethod, stub.hook, stub.backup, false));
                DexLog.d("sanbo", "1 hookMethod----->" + stub.origin);
                DexLog.d("sanbo", "1 backupMethod----->" + stub.backup);
                DexLog.d("sanbo", "1 callBackupMethod----->" + stub.hook);
                entityMap.put(hookMethod, stub);
            } else {
                HookMaker hookMaker;
                if (HookBlackList.canNotHookByBridge(hookMethod)) {
                    hookMaker = new HookerDexMaker();
                } else {
                    hookMaker = defaultHookMaker;
                }
                hookMaker.start(hookMethod, additionalHookInfo,
                        new ProxyClassLoader(DynamicBridge.class.getClassLoader(), hookMethod.getDeclaringClass().getClassLoader()), dexDir == null ? null : dexDir.getAbsolutePath());
                DexLog.d("sanbo", "2 hookMethod----->" + hookMaker.getHookMethod());
                DexLog.d("sanbo", "2 backupMethod----->" + hookMaker.getBackupMethod());
                DexLog.d("sanbo", "2 callBackupMethod----->" + hookMaker.getCallBackupMethod());
                hookedInfo.put(hookMethod, hookMaker.getCallBackupMethod());
            }
            DexLog.v("sanbo", "结果 hookedInfo:" + hookedInfo
                    + "\r\n\tentityMap:" + entityMap);
            DexLog.d("sanbo", "hook method <" + hookMethod.toString() + "> cost " + (System.currentTimeMillis() - timeStart) + " ms, by " + (stub != null ? "internal stub" : "dex maker"));
            DexLog.d("hook method <" + hookMethod.toString() + "> cost " + (System.currentTimeMillis() - timeStart) + " ms, by " + (stub != null ? "internal stub" : "dex maker"));
            Trace.endSection();
        } catch (Throwable e) {
            DexLog.e("error occur when hook method <" + hookMethod.toString() + ">", e);
        }
    }

    public static void clearOatFile() {
        String fixedAppDataDir = XposedCompat.getCacheDir().getAbsolutePath();
        File dexOatDir = new File(fixedAppDataDir, "/sandxposed/oat/");
        if (!dexOatDir.exists())
            return;
        try {
            FileUtils.delete(dexOatDir);
            dexOatDir.mkdirs();
        } catch (Throwable throwable) {
        }
    }

    private static boolean checkMember(Member member) {

        if (member instanceof Method) {
            return true;
        } else if (member instanceof Constructor<?>) {
            return true;
        } else if (member.getDeclaringClass().isInterface()) {
            DexLog.e("Cannot hook interfaces: " + member.toString());
            return false;
        } else if (Modifier.isAbstract(member.getModifiers())) {
            DexLog.e("Cannot hook abstract methods: " + member.toString());
            return false;
        } else {
            DexLog.e("Only methods and constructors can be hooked: " + member.toString());
            return false;
        }
    }
}


