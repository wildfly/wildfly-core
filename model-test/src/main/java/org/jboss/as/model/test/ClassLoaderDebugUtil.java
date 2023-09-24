/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.model.test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ClassLoaderDebugUtil {

    public static void outputClass(Class<?> clazz) {
        System.out.println("****** Class " + clazz + " " + clazz.getClassLoader() + " *******");
        System.out.println("\nFields:");
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            System.out.println("    --" + field.getName() + "-" + field.getType().getName() + " (" + field.getType().getClassLoader() + ")");
        }
        System.out.println("\nConstructors");
        Constructor<?>[] ctors = clazz.getDeclaredConstructors();
        for (Constructor<?> ctor : ctors) {
            System.out.println("    --constructor [");
            for (Class<?> param : ctor.getParameterTypes()) {
                System.out.println("          " + param.getClass().getName() + " (" + param.getClass().getClassLoader() + ")");
            }
            System.out.println("      ]");
        }
        System.out.println("\nMethods:");
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            System.out.println("      " + method.getName() + " [");
            for (Class<?> param : method.getParameterTypes()) {
                System.out.println("        --" + param.getClass().getName() + " (" + param.getClass().getClassLoader());
            }
            System.out.println("      ]");
            System.out.println("      " + method.getReturnType().getName() + " (" + method.getReturnType().getClassLoader() + ")");
        }

    }

}
