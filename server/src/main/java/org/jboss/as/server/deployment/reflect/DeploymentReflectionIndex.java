/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.reflect;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.security.ServerPermission;

/**
 * A reflection index for a deployment.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class DeploymentReflectionIndex {
    private final Map<Class<?>, ClassReflectionIndex> classes = new HashMap<Class<?>, ClassReflectionIndex>();

    DeploymentReflectionIndex() {
    }

    /**
     * Construct a new instance.
     *
     * @return the new instance
     */
    public static DeploymentReflectionIndex create() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ServerPermission.CREATE_DEPLOYMENT_REFLECTION_INDEX);
        }
        return new DeploymentReflectionIndex();
    }

    /**
     * Get the (possibly cached) index for a given class.
     *
     * @param clazz the class
     * @return the index
     */
    @SuppressWarnings({"unchecked"})
    public synchronized ClassReflectionIndex getClassIndex(Class clazz) {
        try {
            ClassReflectionIndex index = classes.get(clazz);
            if (index == null) {
                final SecurityManager sm = System.getSecurityManager();
                if (sm == null) {
                    index = new ClassReflectionIndex(clazz, this);
                } else {
                    index = AccessController.doPrivileged((PrivilegedAction<ClassReflectionIndex>) () -> new ClassReflectionIndex(clazz, this));
                }
                classes.put(clazz, index);
            }
            return index;
        } catch (Throwable e) {
            throw ServerLogger.ROOT_LOGGER.errorGettingReflectiveInformation(clazz, clazz.getClassLoader(), e);
        }
    }
}
