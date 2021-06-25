package com.hayden.dynamicparse.decompile;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.NotFoundException;
import lombok.NoArgsConstructor;
import org.jd.core.v1.api.loader.Loader;
import org.springframework.stereotype.Component;

import java.io.IOException;

public class LoadClass implements Loader {

    @Override
    public boolean canLoad(String internalName) {
        try {
            return this.getClass().getResource("/" + internalName + ".class") != null || ClassPool.getDefault().get(internalName) != null;
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public byte[] load(String internalName) {
        try {
            var clzz = ClassPool.getDefault().get(internalName);
            if(clzz.isFrozen())
                clzz.defrost();
            return clzz.toBytecode();
        } catch (IOException | CannotCompileException | NotFoundException e) {
            e.printStackTrace();
        }
        return new byte[0];
    }
}
