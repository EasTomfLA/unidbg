package com.unidbgtestdemo;

import com.github.unidbg.*;
import com.github.unidbg.arm.HookStatus;
import com.github.unidbg.arm.context.RegisterContext;
import com.github.unidbg.debugger.BreakPointCallback;
import com.github.unidbg.debugger.Debugger;
import com.github.unidbg.hook.HookContext;
import com.github.unidbg.hook.ReplaceCallback;
import com.github.unidbg.hook.hookzz.HookZz;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.dvm.*;
import com.github.unidbg.linux.android.dvm.array.ArrayObject;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
import com.github.unidbg.linux.android.dvm.wrapper.DvmInteger;
import com.github.unidbg.memory.Memory;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.utils.Inspector;
import keystone.Keystone;
import keystone.KeystoneArchitecture;
import keystone.KeystoneMode;
import org.apache.commons.io.HexDump;
import unicorn.Arm64Const;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class unidbgtestdemomain extends AbstractJni {
    AndroidEmulator emulator;
    Memory memory;
    VM vm;
    Module module;

    unidbgtestdemomain() {
        emulator = AndroidEmulatorBuilder.for64Bit().build();
        memory = emulator.getMemory();
        memory.setLibraryResolver(new AndroidResolver(23));

        vm = emulator.createDalvikVM(new File("unidbg-android/src/test/resources//unidbgtestdemo/app-debug.apk"));
        vm.setVerbose(true);
        vm.setJni(this);

        DalvikModule dm = vm.loadLibrary(new File("unidbg-android/src/test/resources/unidbgtestdemo/libnativetest.so"), true);
        module = dm.getModule();
        dm.callJNI_OnLoad(emulator);
    }

    void print(String log) {
        System.out.println(log);
    }

    void functionCallTest() {
        DvmClass jni = vm.resolveClass("com/netease/unidbgtestdemo/MainActivity");
        // 静态注册的直接使用函数名可以,
//        DvmObject<?> stringFromJNI = jni.callStaticJniMethodObject(emulator, "stringFromJNI");
        DvmObject<?> stringFromJNI = jni.callStaticJniMethodObject(emulator, "stringFromJNI()Ljava/lang/String;");
        String ret = stringFromJNI.getValue().toString();
        System.out.println("stringFromJNI ret:" + ret);

        // 动态注册需要指定函数签名
        DvmObject<?> stringFromJNIDyn = jni.callStaticJniMethodObject(emulator, "stringFromJNIDynReg()Ljava/lang/String;");
        String retDyn = stringFromJNIDyn.getValue().toString();
        System.out.println("stringFromJNIDynReg ret:" +retDyn);

        String str = "hello world";
        StringObject ss = new StringObject(vm, str);
        int getStringLen = jni.callStaticJniMethodInt(emulator, "getStringLen(Ljava/lang/String;)I", ss);
        System.out.println("getStringLen(" + ss + ") ret:" + getStringLen);
        str = "hello";
        getStringLen = jni.callStaticJniMethodInt(emulator, "getStringLen(Ljava/lang/String;)I", str);
        System.out.println("getStringLen(" + str + ") ret:" + getStringLen);

        callFunctionUsingExportSymbolOrOffset();

        callMyAddUsingJniMethod();

//        emulator.traceCode();
        String name = jni.callStaticJniMethodObject(emulator, "usingRefJava()Ljava/lang/String;", vm.getJNIEnv(), null).toString();
        print("name:" + name);
    }
    void callFunctionUsingExportSymbolOrOffset() {
//        extern "C" JNIEXPORT void exportFunction()
        module.callFunction(emulator, "exportFunction");
        // 还可以直接指定偏移量进行调用
        // static jint myAddNoExport(int n1, int n2)
//        System.out.println("myAddNoExport(1,2)=" + module.callFunction(emulator, 0xfec0, 1, 2).intValue());
    }

    void callMyAddUsingJniMethod() {
        DvmObject<?> demoTest = vm.resolveClass("com/netease/unidbgtestdemo/DemoTest").newObject(null);
        int sum = demoTest.callJniMethodInt(emulator, "myAdd(II)I", 3, 2);
        System.out.println("myAdd(3,2)=" + sum);

        Symbol initImpl = module.findSymbolByName("_Z8initImplP7_JNIEnvP7_jclassiP13_jobjectArray");
        List<Object> params = new ArrayList<>();
        params.add(vm.getJNIEnv());
        params.add(0);
        params.add(1024);
        StringObject key = new StringObject(vm, "this is fake key");
        vm.addLocalObject(key);
        DvmInteger dInt = DvmInteger.valueOf(vm, 0);
        vm.addLocalObject(dInt);
        vm.addLocalObject(demoTest);
        ArrayObject paramArray = new ArrayObject(key, dInt);
        params.add(vm.addLocalObject(paramArray));

        Number number = module.callFunction(emulator, initImpl.getAddress() - module.base, params.toArray());
        System.out.println("initImpl ret=" + number);
        int i = number.intValue();
        System.out.println("initImpl result:"+ i);

        params.clear();
        params.add(vm.getJNIEnv());
        params.add(0);
        String inputStr = "helloworld";
        params.add(vm.addLocalObject(new ByteArray(vm, inputStr.getBytes())));
        params.add(0);
        debugNativeFunc();
        number = module.callFunction(emulator, module.findSymbolByName("Java_com_netease_unidbgtestdemo_DemoTest_encrypt").getAddress() - module.base, params.toArray());
        ByteArray retByteArr = vm.getObject(number.intValue());
//        String retStr = Base64.getEncoder().encodeToString(retByteArr.getValue());
//        System.out.println("Java_com_netease_unidbgtestdemo_DemoTest_encrypt(\"helloworld\") result:"+ retStr);
        System.out.println("Java_com_netease_unidbgtestdemo_DemoTest_encrypt(\"helloworld\") input="+ byteArrayToHexString(inputStr.getBytes()));
        System.out.println("Java_com_netease_unidbgtestdemo_DemoTest_encrypt(\"helloworld\") result="+ byteArrayToHexString(retByteArr.getValue()));
    }

    void hookZZ() {
        HookZz hook = HookZz.getInstance(emulator);
        Symbol myAddImpl = module.findSymbolByName("_Z9myAddImplP7_JNIEnvP8_jobjectii");
        print("myAddImpl addr:"+myAddImpl.getAddress());
        // 调用顺序 onCall onCall(no context) postCall https://blog.csdn.net/Qiled/article/details/122149949
        hook.replace(myAddImpl.getAddress(),
                new ReplaceCallback() {
                    @Override
                    public HookStatus onCall(Emulator<?> emulator, long originFunction) {
                        print("onCall no context");
                        return super.onCall(emulator, originFunction);
                    }

                    @Override
                    public HookStatus onCall(Emulator<?> emulator, HookContext context, long originFunction) {
                        print("onCall ");
                        // 在此读取参数使用 context+寄存器
                        print(String.format("X2:%d, X3:%d", context.getIntArg(2),
                                context.getIntArg(3)));
                        emulator.getBackend().reg_write(Arm64Const.UC_ARM64_REG_X3, 5);
                        return super.onCall(emulator, context, originFunction);
                    }

                    @Override
                    public void postCall(Emulator<?> emulator, HookContext context) {
                        print("postCall ");
                        // 在此修改函数调用返回值,使用的后端+寄存器方式
                        print(String.format("X0:%d",
                                emulator.getBackend().reg_read(Arm64Const.UC_ARM64_REG_X0)));
                        emulator.getBackend().reg_write(Arm64Const.UC_ARM64_REG_X0, 10);
                        super.postCall(emulator, context);
                    }
                },true);
    }

    void hookTest() {
        hookZZ();
    }

    void debugNativeFunc() {
        Debugger debugger = emulator.attach();
//        debugger.addBreakPoint(module.findSymbolByName("Java_com_netease_unidbgtestdemo_DemoTest_encrypt").getAddress(),
//                new BreakPointCallback() {
//            @Override
//            public boolean onHit(Emulator<?> emulator, long address) {
//                return false;
//            }
//        });
        debugger.addBreakPoint(module.findSymbolByName("Java_com_netease_unidbgtestdemo_DemoTest_encrypt").getAddress() + (0x176E8 - 0x1769C),
                new BreakPointCallback() {
                    UnidbgPointer pointer;
                    RegisterContext ctx = emulator.getContext();
                    @Override
                    public boolean onHit(Emulator<?> emulator, long address) {
                        pointer = ctx.getPointerArg(0);
                        Inspector.inspect(pointer.getByteArray(0, 16), "before");
                        pointer.setByte(randint(0, 15), (byte)randint(0, 0xff));
                        Inspector.inspect(pointer.getByteArray(0, 16), "after");
                        return true;
                    }
                });
    }

    int randint(int min, int max) {
        Random random = new Random();
        return random.nextInt((max - min) + 1) + min;
    }

    void patchCodeWithPointer() {
        // https://armconverter.com/
        // ; myAddImpl(_JNIEnv *, _jobject *, int, int)
        // .text:000000000000F7D8 00 01 09 0B                 ADD             W0, W8, W9
        // ==> SUB   0001094B
        Symbol myAddImpl = module.findSymbolByName("_Z9myAddImplP7_JNIEnvP8_jobjectii");
        long l = module.base + myAddImpl.getAddress() + (0xfcc0 - 0xfca4);
        UnidbgPointer pointer = UnidbgPointer.pointer(emulator, myAddImpl.getAddress() + (0xfcc0-0xfca4));
        if (pointer != null) {
            byte[] bytes = new byte[4];
            pointer.read(0, bytes, 0, 4);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try {
                HexDump.dump(bytes, 0, buf, 0);
                print(String.format("origin code:"+buf.toString()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            buf.reset();
            byte[] code = new byte[] {0x00, 0x01, 0x09, 0x4b};
            pointer.write(code);
            pointer.read(0, bytes, 0, 4);
            try {
                HexDump.dump(bytes, 0, buf, 0);
                print(String.format("after patch code:"+buf.toString()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else
            print("pointer == null");
    }

    void patchWithKeystore() {
        UnidbgPointer pointer = UnidbgPointer.pointer(emulator, module.base + 0xf7d8);
        if (pointer != null) {
            Keystone keystone = new Keystone(KeystoneArchitecture.Arm64, KeystoneMode.LittleEndian);
            String s = "sub w0, w8, w9";
            byte[] machineCode = keystone.assemble(s).getMachineCode();
            pointer.write(machineCode);
        }
    }

    void patchCodeTest() {
        patchCodeWithPointer();
//        patchWithKeystore();
    }

    public static void main(String[] args) {
        unidbgtestdemomain main = new unidbgtestdemomain();
//        main.hookTest();
//        main.patchCodeTest();
        main.functionCallTest();
    }

    @Override
    public DvmObject<?> callStaticObjectMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        switch (signature) {
            case "android/app/ActivityThread->currentPackageName()Ljava/lang/String;":
                return new StringObject(vm, "hello");
            case "android/os/SystemProperties->get(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;":
                return new StringObject(vm, "王德法");
        }
        return super.callStaticObjectMethodV(vm, dvmClass, signature, vaList);
    }

    @Override
    public DvmObject<?> callObjectMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        switch (signature) {
            case "android/app/ActivityThread->getApplication()Landroid/app/Application;":
                return vm.resolveClass("android/app/Application").newObject(null);
            case "android/app/Application->getApplicationContext()Landroid/content/Context;":
                return vm.resolveClass("android/content/Context").newObject(null);
            case "android/content/Context->getPackageCodePath()Ljava/lang/String;":
                return new StringObject(vm, "/data/app/com.netease.unidbgtestdemo-J4DLVxvi6nwuCoHSFmXCAg==/base.apk");
            case "android/content/Context->getPackageName()Ljava/lang/String;":
                return new StringObject(vm, "this is fake packget name");
            case "android/content/Context->getAssets()Landroid/content/res/AssetManager;":
                return vm.resolveClass("Landroid/content/res/AssetManager").newObject(null);
            case "Landroid/content/res/AssetManager->toString()Ljava/lang/String;":
                return new StringObject(vm, "this is fake assets manager string");
        }
        return super.callObjectMethodV(vm, dvmObject, signature, vaList);
    }

    public static String byteArrayToHexString(byte[] byteArray) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : byteArray) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
