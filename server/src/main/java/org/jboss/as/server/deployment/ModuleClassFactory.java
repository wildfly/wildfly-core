/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import org.jboss.classfilewriter.ClassFactory;
import org.jboss.modules.ClassDefiner;
import org.jboss.modules.Module;

import java.security.ProtectionDomain;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class ModuleClassFactory implements ClassFactory {

    public static final ClassFactory INSTANCE = new ModuleClassFactory();

    private ModuleClassFactory() {
        // forbidden instantiation
    }

    @Override
    public Class<?> defineClass(final ClassLoader classLoader, final String name, final byte[] b, final int off, final int len, final ProtectionDomain protectionDomain) throws ClassFormatError {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            final int index = name.lastIndexOf('.');
            final String packageName;
            if(index == -1 ) {
                packageName = "";
            } else {
                packageName = name.substring(0, index);
            }
            RuntimePermission permission = new RuntimePermission("defineClassInPackage." + packageName);
            sm.checkPermission(permission);
        }
        final Module module = Module.forClassLoader(classLoader, false);
        return ClassDefiner.getInstance().defineClass(module, name, protectionDomain, b, off, len);
    }

}
