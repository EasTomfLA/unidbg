package com.flycat;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.Module;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.ElfLibraryFile;
import com.github.unidbg.linux.android.dvm.*;
import com.github.unidbg.memory.Memory;
import net.fornwall.jelf.ElfFile;
import net.fornwall.jelf.ElfSection;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class flycat extends AbstractJni {
    AndroidEmulator emulator;
    Memory memory;
    VM vm;
    Module module;

    class sectionInfo {
        sectionInfo(int index, String name, long add, long size) {
            this.index = index;
            this.name = name;
            this.address = add;
            this.size = size;
        }
        int index;
        String name;
        long address;
        long size;
    }

    Map<String, sectionInfo> sections = new HashMap<String, sectionInfo>();

    flycat() {
        emulator = AndroidEmulatorBuilder.for32Bit().build();
        memory = emulator.getMemory();
        memory.setLibraryResolver(new AndroidResolver(23));

        vm = emulator.createDalvikVM(new File("unidbg-android/src/test/resources/ttb/flycat.apk"));
        vm.setVerbose(true);
        vm.setJni(this);
        File file = new File("unidbg-android/src/test/resources/ttb/libassist.so");
        colectionSectionsInfo(file);
        DalvikModule dm = vm.loadLibrary(file, true);
        module = dm.getModule();
        Module cm = dm.getModule();
        print(String.format("%s   %x  %x  ", cm.name,cm.base,cm.size));

        sectionInfo rodata = sections.get(".rodata");
        sectionInfo bss = sections.get(".bss");
//        print("section:" + rodata.name + " address:" + Long.toHexString(rodata.address) +
//                " size:" + Long.toHexString(rodata.size));
        showSectionInfo(".rodata");
        showSectionInfo(".bss");
        byte[] dumpData = emulator.getBackend().mem_read(cm.base + rodata.address, rodata.size);
        bytesToFile(dumpData, new File("unidbg-android/src/test/resources/ttb/flycat.rodata").getAbsolutePath());
        dm.callJNI_OnLoad(emulator);
        dumpData = emulator.getBackend().mem_read(cm.base + bss.address, bss.size);
        bytesToFile(dumpData, new File("unidbg-android/src/test/resources/ttb/flycat.bss").getAbsolutePath());
    }

    public void colectionSectionsInfo(File file) {
        try {
            ElfLibraryFile elfLibraryFile = new ElfLibraryFile(file, emulator.is64Bit());
            ElfFile elfFile = null;
            elfFile = ElfFile.fromBytes(elfLibraryFile.mapBuffer());
            for (int i = 0; i < elfFile.num_sh; i++) {
                ElfSection section = elfFile.getSection(i);
//                System.out.println(String.format("module:%s section:%d name:%s name_ndx:%x, address:%x size:%x",
//                        elfLibraryFile.getName(), i, section.getName(), section.name_ndx, section.address, section.size));
                sectionInfo sectionInfo = new sectionInfo(i, section.getName(), section.address, section.size);
                sections.put(section.getName(), sectionInfo);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showSectionInfo(String sectionName) {
        sectionInfo rodata = sections.get(sectionName);
        print("section:" + rodata.name + " address:" + Long.toHexString(rodata.address) +
                " size:" + Long.toHexString(rodata.size));
    }

    public static void bytesToFile(byte[] data, String path) {
        File file = new File(path);
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(data);
            fileOutputStream.close();
            print("文件写出成功 " + path);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    static void print(String log) {
        System.out.println(log);
    }

    public static void main(String[] args) {
        flycat main = new flycat();
    }

    @Override
    public DvmObject<?> getStaticObjectField(BaseVM vm, DvmClass dvmClass, String signature) {
        switch (signature) {
            case "android/os/Build->MODEL:Ljava/lang/String;":
                return new StringObject(vm, "MI 8 SE");
        }
        return super.getStaticObjectField(vm, dvmClass, signature);
    }

    @Override
    public int getStaticIntField(BaseVM vm, DvmClass dvmClass, String signature) {
        switch (signature) {
            case "android/os/Build$VERSION->SDK_INT:I":
                return 23;
        }
        return super.getStaticIntField(vm, dvmClass, signature);
    }

    @Override
    public DvmObject<?> callObjectMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        switch (signature) {
            case "java/lang/Thread->getContextClassLoader()Ljava/lang/ClassLoader;":
                DvmClass clazz = vm.resolveClass("java/lang/ClassLoader");
                return clazz;
        }
        return super.callObjectMethodV(vm, dvmObject, signature, vaList);
    }

    @Override
    public DvmObject<?> callStaticObjectMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        switch (signature) {
            case "android/app/ActivityThread->currentPackageName()Ljava/lang/String;":
                return new StringObject(vm, "hello");
            case "android/os/SystemProperties->get(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;":
                return new StringObject(vm, "王德法");
            case "java/lang/Thread->currentThread()Ljava/lang/Thread;":
                return dvmClass.newObject(null);
        }
        return super.callStaticObjectMethodV(vm, dvmClass, signature, vaList);
    }

}
