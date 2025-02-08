package com.virtualboxdemo;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Emulator;
import com.github.unidbg.Module;
import com.github.unidbg.ModuleListener;
import com.github.unidbg.arm.backend.Unicorn2Factory;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.IOResolver;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.dvm.AbstractJni;
import com.github.unidbg.linux.android.dvm.DalvikModule;
import com.github.unidbg.linux.android.dvm.VM;
import com.github.unidbg.memory.Memory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

class virtualbox extends AbstractJni implements IOResolver {
    @Override
    public FileResult resolve(Emulator emulator, String pathname, int oflags) {
        System.out.println("file open:"+pathname);
        return null;
    }


    private final AndroidEmulator emulator;
    private final VM vm;
    private final Module module;
    private final Memory memory;
    private PrintStream traceStream;
    private virtualbox(){
        emulator = AndroidEmulatorBuilder
                .for64Bit()
                .setProcessName("xxx")
                .addBackendFactory(new Unicorn2Factory(true))
                .build();
        emulator.getSyscallHandler().setVerbose(true);
        emulator.getSyscallHandler().setEnableThreadDispatcher(true);


        memory = emulator.getMemory();
        memory.setLibraryResolver(new AndroidResolver(23));
        memory.setCallInitFunction(true);
//        memory.addModuleListener(new ModuleListener() { // trace _init_proc、init_array
//            @Override
//            public void onLoaded(Emulator<?> emulator, Module module) {
//                if(module.name.contains("virboxdemo64")){
//                    try {
//                        emulator.traceCode(module.base, module.base+module.size).setRedirect(new PrintStream(new FileOutputStream("trace111111.txt"),true));
//                    } catch (FileNotFoundException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        });
        File file = new File("unidbg-android/src/test/resources/virtualboxdemo/virboxdemo64");
        vm = emulator.createDalvikVM();
        vm.setJni(this);
        vm.setVerbose(true);
        emulator.getSyscallHandler().addIOResolver(this);// 设置文件处理器

        DalvikModule dm = vm.loadLibrary(file, true);

        module = dm.getModule();

        emulator.traceCode(module.base, module.base+module.size);
//        dm.callJNI_OnLoad(emulator); // 调用目标 SO 的 JNI_OnLoad
//        module.callEntry(emulator, ""); // 这种方式会从_start进入
        module.callFunction(emulator, 0x6E0, ""); // 直接调用main入口
    }

    public static void main(String[] args){
        virtualbox xxx= new virtualbox();
    }
}

