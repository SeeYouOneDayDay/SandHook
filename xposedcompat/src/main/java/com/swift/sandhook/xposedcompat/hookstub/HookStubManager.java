package com.swift.sandhook.xposedcompat.hookstub;

import android.util.Log;

import com.swift.sandhook.SandHook;
import com.swift.sandhook.SandHookMethodResolver;
import com.swift.sandhook.utils.ParamWrapper;
import com.swift.sandhook.wrapper.StubMethodsFactory;
import com.swift.sandhook.xposedcompat.XposedCompat;
import com.swift.sandhook.xposedcompat.utils.DexLog;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class HookStubManager {

    public static volatile boolean is64Bit;
    //64bits arg0 - arg7 is in reg x1 - x7 and > 7 is in stack, but can not match
    public final static int MAX_64_ARGS = 7;

    public static int MAX_STUB_ARGS = 0;

    public static int[] stubSizes;

    public static boolean hasStubBackup;

    public static AtomicInteger[] curUseStubIndexes;

    public static int ALL_STUB = 0;

    public static Member[] originMethods;
    public static HookMethodEntity[] hookMethodEntities;
    public static XposedBridge.AdditionalHookInfo[] additionalHookInfos;

    static {
        is64Bit = SandHook.is64Bit();
        Class stubClass = is64Bit ? MethodHookerStubs64.class : MethodHookerStubs32.class;
        stubSizes = (int[]) XposedHelpers.getStaticObjectField(stubClass, "stubSizes");
        Boolean hasBackup = (Boolean) XposedHelpers.getStaticObjectField(stubClass, "hasStubBackup");
        hasStubBackup = hasBackup != null && (hasBackup && !XposedCompat.useNewCallBackup);
        if (stubSizes != null && stubSizes.length > 0) {
            MAX_STUB_ARGS = stubSizes.length - 1;
            curUseStubIndexes = new AtomicInteger[MAX_STUB_ARGS + 1];
            for (int i = 0; i < MAX_STUB_ARGS + 1; i++) {
                curUseStubIndexes[i] = new AtomicInteger(0);
                ALL_STUB += stubSizes[i];
            }
            originMethods = new Member[ALL_STUB];
            hookMethodEntities = new HookMethodEntity[ALL_STUB];
            additionalHookInfos = new XposedBridge.AdditionalHookInfo[ALL_STUB];
        }
    }


    public static HookMethodEntity getHookMethodEntity(Member origin, XposedBridge.AdditionalHookInfo additionalHookInfo) {
        DexLog.i("sanbo", "===============inside getHookMethodEntity============" + origin);

        if (!support()) {
            DexLog.d("sanbo", "getHookMethodEntity not support support ");
            return null;
        }


        Class[] parType;
        Class retType;
        boolean isStatic = Modifier.isStatic(origin.getModifiers());
        DexLog.d("sanbo", "getHookMethodEntity isStatic:" + isStatic);

        if (origin instanceof Method) {
            Method method = (Method) origin;
            retType = method.getReturnType();
            parType = method.getParameterTypes();
        } else if (origin instanceof Constructor) {
            Constructor constructor = (Constructor) origin;
            retType = Void.TYPE;
            parType = constructor.getParameterTypes();
        } else {
            return null;
        }
        DexLog.d("sanbo", "getHookMethodEntity retType:" + retType
                + "\r\n\tparType:" + Arrays.asList(parType)
        );

        if (!ParamWrapper.support(retType)) {
            DexLog.d("sanbo", "getHookMethodEntity not support " + retType);
            return null;
        }


        // 非静态方法，增加一个参数
        // 理解增加的是 自身
        int needStubArgCount = isStatic ? 0 : 1;
        DexLog.d("sanbo", "getHookMethodEntity needStubArgCount(非静态+1):" + needStubArgCount);
        if (parType != null) {
            needStubArgCount += parType.length;
            if (needStubArgCount > MAX_STUB_ARGS)
                return null;
            if (is64Bit && needStubArgCount > MAX_64_ARGS)
                return null;
            for (Class par : parType) {
                if (!ParamWrapper.support(par))
                    return null;
            }
        } else {
            parType = new Class[0];
        }
        DexLog.d("sanbo", "getHookMethodEntity needStubArgCount:" + needStubArgCount);

        synchronized (HookStubManager.class) {
            // 拥有方法，还未进行绑定
            StubMethodsInfo stubMethodInfo = getStubMethodPair(is64Bit, needStubArgCount);
            DexLog.d("sanbo", "getHookMethodEntity stubMethodInfo 获取完成。详情如下:\r\n" + stubMethodInfo);

            if (stubMethodInfo == null) {
                return null;
            }
            // 交换 几个方法到一个数据结构
            HookMethodEntity entity = new HookMethodEntity(origin, stubMethodInfo.hook, stubMethodInfo.backup);
            entity.retType = retType;
            entity.parType = parType;

            DexLog.d("sanbo", "getHookMethodEntity"
                    + "\r\n\thasStubBackup: " + hasStubBackup
                    +(hasStubBackup?("\r\n\ttryCompileAndResolveCallOriginMethod:" + tryCompileAndResolveCallOriginMethod(entity.backup, stubMethodInfo.args, stubMethodInfo.index)):"")
            );
            if (hasStubBackup && !tryCompileAndResolveCallOriginMethod(entity.backup, stubMethodInfo.args, stubMethodInfo.index)) {
                DexLog.w("internal stub <" + entity.hook.getName() + "> call origin compile failure, skip use internal stub");
                DexLog.d("sanbo", "getHookMethodEntity internal stub <" + entity.hook.getName() + "> call origin compile failure, skip use internal stub");
                return null;
            } else {
                // 获取方法ID
                int id = getMethodId(stubMethodInfo.args, stubMethodInfo.index);
                DexLog.d("sanbo", "getHookMethodEntity getMethodId "
                        //int args, int index
                        +  "\r\n\targs:  " + stubMethodInfo.args
                        +  "\r\n\tindex:  " + stubMethodInfo.index
                        +  "\r\n\tResult:  " + id
                        + "\r\n\torigin:  " + origin
                        + "\r\n\tentity:\r\n\t\t" + entity
                        + "\r\n\tadditionalHookInfo:  " + additionalHookInfo
                );
                originMethods[id] = origin;
                hookMethodEntities[id] = entity;
                additionalHookInfos[id] = additionalHookInfo;
                return entity;
            }
        }
    }

    public static int getMethodId(int args, int index) {
        int id = index;
        DexLog.d("sanbo", "\r\n\t\ngetMethodId -----stubSizes"+Arrays.toString(stubSizes));
        for (int i = 0; i < args; i++) {
            id += stubSizes[i];
        }
        return id;
    }

    public static String getHookMethodName(int index) {
        return "stub_hook_" + index;
    }

    public static String getBackupMethodName(int index) {
        return "stub_backup_" + index;
    }

    public static String getCallOriginClassName(int args, int index) {
        return "call_origin_" + args + "_" + index;
    }


    static class StubMethodsInfo {
        int args = 0;
        int index = 0;
        Method hook;
        Method backup;

        public StubMethodsInfo(int args, int index, Method hook, Method backup) {
            this.args = args;
            this.index = index;
            this.hook = hook;
            this.backup = backup;
        }

        @Override
        public String toString() {
            return "StubMethodsInfo{"
                    + "\r\n\t args=" + args
                    + ",\r\n\t index=" + index
                    + ",\r\n\t hook=" + hook
                    + ",\r\n\t backup=" + backup
                    + '}';
        }
    }

    private static synchronized StubMethodsInfo getStubMethodPair(boolean is64Bit, int stubArgs) {
        DexLog.d("sanbo", "\r\n\t\tinside getStubMethodPair ( " + stubArgs + " ).....");

        stubArgs = getMatchStubArgsCount(stubArgs);
        DexLog.d("sanbo", "\r\n\t\tgetStubMethodPair .....stubArgs:" + stubArgs);

        if (stubArgs < 0)
            return null;

        int curUseStubIndex = curUseStubIndexes[stubArgs].getAndIncrement();

        DexLog.d("sanbo", "\r\n\t\tgetStubMethodPair.....curUseStubIndex:" + curUseStubIndex);

        // 64位 每个参数都是long, 32位全是int
        Class[] pars = getFindMethodParTypes(is64Bit, stubArgs);
        DexLog.d("sanbo", "\r\n\t\tgetStubMethodPair.....pars:" + Arrays.asList(pars));

        try {
            if (is64Bit) {
                // 获取方法名字
                String name = getHookMethodName(curUseStubIndex);
                DexLog.d("sanbo", "\r\n\t\tgetStubMethodPair.....64....HookMethodName:" + name);
                // 获取hook方法
                Method hook = MethodHookerStubs64.class.getDeclaredMethod(name, pars);
                DexLog.d("sanbo", "\r\n\t\tgetStubMethodPair.....64....hook:" + hook);
                DexLog.d("sanbo", "\r\n\t\tgetStubMethodPair.....64....hasStubBackup:" + hasStubBackup);
                // 获取backuo方法

                String backupMethodName = getBackupMethodName(curUseStubIndex);
                DexLog.d("sanbo", "\r\n\t\tgetStubMethodPair.....64....backupMethodName(" + (hasStubBackup ? "使用" : "不使用") + "):" + backupMethodName);
                Method backup = hasStubBackup ? MethodHookerStubs64.class.getDeclaredMethod(backupMethodName, pars) : StubMethodsFactory.getStubMethod();
                DexLog.d("sanbo", "\r\n\t\tgetStubMethodPair.....64....backup:" + backup);
                if (hook == null || backup == null)
                    return null;
                // 存储到对象中统一返回
                return new StubMethodsInfo(stubArgs, curUseStubIndex, hook, backup);
            } else {
                String name = getHookMethodName(curUseStubIndex);
                DexLog.d("sanbo", "\r\n\t\tgetStubMethodPair.....32....HookMethodName:" + name);
                Method hook = MethodHookerStubs32.class.getDeclaredMethod(name, pars);
                DexLog.d("sanbo", "\r\n\t\tgetStubMethodPair.....32....hook:" + hook);
                DexLog.d("sanbo", "\r\n\t\tgetStubMethodPair.....32....hasStubBackup:" + hasStubBackup);

                String backupMethodName = getBackupMethodName(curUseStubIndex);
                DexLog.d("sanbo", "\r\n\t\tgetStubMethodPair.....32....backupMethodName(" + (hasStubBackup ? "使用" : "不使用") + "):" + backupMethodName);
                Method backup = hasStubBackup ? MethodHookerStubs32.class.getDeclaredMethod(backupMethodName, pars) : StubMethodsFactory.getStubMethod();
                DexLog.d("sanbo", "\r\n\t\tgetStubMethodPair.....32....backup:" + backup);
                if (hook == null || backup == null)
                    return null;
                return new StubMethodsInfo(stubArgs, curUseStubIndex, hook, backup);
            }
        } catch (Throwable throwable) {
            DexLog.d("sanbo", Log.getStackTraceString(throwable));
            return null;
        }
    }

    public static Method getCallOriginMethod(int args, int index) {
        Class stubClass = is64Bit ? MethodHookerStubs64.class : MethodHookerStubs32.class;
        String className = stubClass.getName();
        className += "$";
        className += getCallOriginClassName(args, index);
        try {
            Class callOriginClass = Class.forName(className, true, stubClass.getClassLoader());
            return callOriginClass.getDeclaredMethod("call", long[].class);
        } catch (Throwable e) {
            Log.e("HookStubManager", "load call origin class error!", e);
            return null;
        }
    }

    public static boolean tryCompileAndResolveCallOriginMethod(Method backupMethod, int args, int index) {

        DexLog.d("sanbo", "\r\n\t\t\tinside tryCompileAndResolveCallOriginMethod " +
                "\r\n\t\t\t\tbackupMethod:" + backupMethod
                + "\r\n\t\t\t\targs:" + args
                + "\r\n\t\t\t\tindex:" + index
        );

        Method method = getCallOriginMethod(args, index);

        DexLog.d("sanbo", "\r\n\t\t\t\ttryCompileAndResolveCallOriginMethod getCallOriginMethod method:" + method);
        if (method != null) {
            SandHookMethodResolver.resolveMethod(method, backupMethod);
            DexLog.d("sanbo", "\r\n\t\t\t\ttryCompileAndResolveCallOriginMethod will build! call SandHook.compileMethod");

            return SandHook.compileMethod(method);
        } else {
            return false;
        }
    }

    public static int getMatchStubArgsCount(int stubArgs) {
        for (int i = stubArgs; i <= MAX_STUB_ARGS; i++) {
            if (curUseStubIndexes[i].get() < stubSizes[i])
                return i;
        }
        return -1;
    }

    public static Class[] getFindMethodParTypes(boolean is64Bit, int stubArgs) {
        if (stubArgs == 0)
            return null;
        Class[] args = new Class[stubArgs];
        if (is64Bit) {
            for (int i = 0; i < stubArgs; i++) {
                args[i] = long.class;
            }
        } else {
            for (int i = 0; i < stubArgs; i++) {
                args[i] = int.class;
            }
        }
        return args;
    }

    public static long hookBridge(int id, CallOriginCallBack callOrigin, long... stubArgs) throws Throwable {

        Member originMethod = originMethods[id];
        HookMethodEntity entity = hookMethodEntities[id];

        Object thiz = null;
        Object[] args = null;

        if (hasArgs(stubArgs)) {
            thiz = entity.getThis(stubArgs[0]);
            args = entity.getArgs(stubArgs);
        }

        if (XposedBridge.disableHooks) {
            if (hasStubBackup) {
                return callOrigin.call(stubArgs);
            } else {
                return callOrigin(entity, originMethod, thiz, args);
            }
        }

        DexLog.printMethodHookIn(originMethod);

        Object[] snapshot = additionalHookInfos[id].callbacks.getSnapshot();

        if (snapshot == null || snapshot.length == 0) {
            if (hasStubBackup) {
                return callOrigin.call(stubArgs);
            } else {
                return callOrigin(entity, originMethod, thiz, args);
            }
        }

        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();

        param.method = originMethod;
        param.thisObject = thiz;
        param.args = args;

        int beforeIdx = 0;
        do {
            try {
                ((XC_MethodHook) snapshot[beforeIdx]).callBeforeHookedMethod(param);
            } catch (Throwable t) {
                // reset result (ignoring what the unexpectedly exiting callback did)
                param.setResult(null);
                param.returnEarly = false;
                continue;
            }

            if (param.returnEarly) {
                // skip remaining "before" callbacks and corresponding "after" callbacks
                beforeIdx++;
                break;
            }
        } while (++beforeIdx < snapshot.length);

        // call original method if not requested otherwise
        if (!param.returnEarly) {
            try {
                if (hasStubBackup) {
                    //prepare new args
                    long[] newArgs = entity.getArgsAddress(stubArgs, param.args);
                    param.setResult(entity.getResult(callOrigin.call(newArgs)));
                } else {
                    param.setResult(SandHook.callOriginMethod(originMethod, entity.backup, thiz, param.args));
                }
            } catch (Throwable e) {
                XposedBridge.log(e);
                param.setThrowable(e);
            }
        }

        // call "after method" callbacks
        int afterIdx = beforeIdx - 1;
        do {
            Object lastResult = param.getResult();
            Throwable lastThrowable = param.getThrowable();

            try {
                ((XC_MethodHook) snapshot[afterIdx]).callAfterHookedMethod(param);
            } catch (Throwable t) {
                XposedBridge.log(t);
                if (lastThrowable == null)
                    param.setResult(lastResult);
                else
                    param.setThrowable(lastThrowable);
            }
        } while (--afterIdx >= 0);
        if (!param.hasThrowable()) {
            return entity.getResultAddress(param.getResult());
        } else {
            throw param.getThrowable();
        }
    }

    public static Object hookBridge(Member origin, Method backup, XposedBridge.AdditionalHookInfo additionalHookInfo, Object thiz, Object... args) throws Throwable {


        if (XposedBridge.disableHooks) {
            return SandHook.callOriginMethod(origin, backup, thiz, args);
        }

        DexLog.printMethodHookIn(origin);

        Object[] snapshot = additionalHookInfo.callbacks.getSnapshot();

        if (snapshot == null || snapshot.length == 0) {
            return SandHook.callOriginMethod(origin, backup, thiz, args);
        }

        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();

        param.method = origin;
        param.thisObject = thiz;
        param.args = args;

        int beforeIdx = 0;
        do {
            try {
                ((XC_MethodHook) snapshot[beforeIdx]).callBeforeHookedMethod(param);
            } catch (Throwable t) {
                // reset result (ignoring what the unexpectedly exiting callback did)
                param.setResult(null);
                param.returnEarly = false;
                continue;
            }

            if (param.returnEarly) {
                // skip remaining "before" callbacks and corresponding "after" callbacks
                beforeIdx++;
                break;
            }
        } while (++beforeIdx < snapshot.length);

        // call original method if not requested otherwise
        if (!param.returnEarly) {
            try {
                param.setResult(SandHook.callOriginMethod(origin, backup, thiz, param.args));
            } catch (Throwable e) {
                XposedBridge.log(e);
                param.setThrowable(e);
            }
        }

        // call "after method" callbacks
        int afterIdx = beforeIdx - 1;
        do {
            Object lastResult = param.getResult();
            Throwable lastThrowable = param.getThrowable();

            try {
                ((XC_MethodHook) snapshot[afterIdx]).callAfterHookedMethod(param);
            } catch (Throwable t) {
                XposedBridge.log(t);
                if (lastThrowable == null)
                    param.setResult(lastResult);
                else
                    param.setThrowable(lastThrowable);
            }
        } while (--afterIdx >= 0);
        if (!param.hasThrowable()) {
            return param.getResult();
        } else {
            throw param.getThrowable();
        }
    }

    public final static long callOrigin(HookMethodEntity entity, Member origin, Object thiz, Object[] args) throws Throwable {
        Object res = SandHook.callOriginMethod(origin, entity.backup, thiz, args);
        return entity.getResultAddress(res);
    }

    private static boolean hasArgs(long... args) {
        return args != null && args.length > 0;
    }

    public static boolean support() {
        return MAX_STUB_ARGS > 0 && SandHook.canGetObject() && SandHook.canGetObjectAddress();
    }

}
