package com.unidbgtestdemo;

import com.github.unidbg.*;
import com.github.unidbg.arm.HookStatus;
import com.github.unidbg.hook.HookContext;
import com.github.unidbg.hook.ReplaceCallback;
import com.github.unidbg.hook.hookzz.HookZz;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.dvm.*;
import com.github.unidbg.memory.Memory;
import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgPointer;
import keystone.Keystone;
import keystone.KeystoneArchitecture;
import keystone.KeystoneMode;
import org.apache.commons.io.HexDump;
import unicorn.Arm64Const;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

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
        System.out.println(ret);

        // 动态注册需要指定函数签名
        DvmObject<?> stringFromJNIDyn = jni.callStaticJniMethodObject(emulator, "stringFromJNIDynReg()Ljava/lang/String;");
        String retDyn = stringFromJNIDyn.getValue().toString();
        System.out.println(retDyn);

        MemoryBlock malloc = memory.malloc(10, true);
        UnidbgPointer strPoint = malloc.getPointer();
        strPoint.write("hello".getBytes());

//        int getStringLen = jni.callStaticJniMethodInt(emulator, "getStringLen(Ljava/lang/String;)I", new PointerNumber(strPoint));
        int getStringLen = jni.callStaticJniMethodInt(emulator, "getStringLen(Ljava/lang/String;)I", "hello");
        System.out.println(getStringLen);

        // 还可以直接指定偏移量进行调用
//        extern "C" JNIEXPORT void exportFunction()
        module.callFunction(emulator, "exportFunction");
        // static jint myAddNoExport(int n1, int n2)
//        System.out.println("0xf9d8(1,2)=" + module.callFunction(emulator, 0xF9D8, 1, 2));

        DvmObject<?> demoTest = vm.resolveClass("com/netease/unidbgtestdemo/DemoTest").newObject(null);
        int sum = demoTest.callJniMethodInt(emulator, "myAdd(II)I", 1, 2);
        System.out.println("myAdd(1,2)=" + sum);

        // ????
        demoTest = vm.resolveClass("com/netease/unidbgtestdemo/DemoTest").newObject("unidbg");
        demoTest = vm.resolveClass("com/netease/unidbgtestdemo/DemoTest").newObject(111);

        String name = jni.callStaticJniMethodObject(emulator, "usingRefJava()Ljava/lang/String;", vm.getJNIEnv(), null).toString();
        print("name:" + name);
    }

    void hookZZ() {
        HookZz hook = HookZz.getInstance(emulator);
        Symbol myAddImpl = module.findSymbolByName("_Z9myAddImplP7_JNIEnvP8_jobjectii");
        print("myAddImpl addr:"+myAddImpl.getAddress());
        // 调用顺序 onCall onCall(no context) postCall https://blog.csdn.net/Qiled/article/details/122149949
        hook.replace(0x4000F7BC,
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

    void patchCodeWithPointer() {
        // https://armconverter.com/
        // .text:000000000000F7D8 00 01 09 0B                 ADD             W0, W8, W9
        // ==> SUB   0001094B
        UnidbgPointer pointer = UnidbgPointer.pointer(emulator, module.base + 0xf7d8);
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
//        patchCodeWithPointer();
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

}
