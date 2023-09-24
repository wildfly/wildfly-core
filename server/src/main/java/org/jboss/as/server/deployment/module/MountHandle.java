/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import java.io.Closeable;

import org.jboss.vfs.VFSUtils;
import org.wildfly.common.ref.CleanerReference;
import org.wildfly.common.ref.Reaper;
import org.wildfly.common.ref.Reference;

/**
 * Wrapper object to hold onto and close a VFS mount handle.
 *
 * If the provided mount handle is null then no action will be taken.
 *
 * @author John E. Bailey
 * @author Jason T. Greene
 * @author Stuart Douglas
 */
public class MountHandle implements Closeable {

    private static final Reaper<MountHandle, Closeable> REAPER = new Reaper<MountHandle, Closeable>() {
        @Override
        public void reap(Reference<MountHandle, Closeable> reference) {
            VFSUtils.safeClose(reference.getAttachment());
        }
    };

    public static MountHandle create(final Closeable handle) {
        MountHandle mountHandle = new MountHandle(handle);
        if (handle != null) {
            // Use a PhantomReference instead of overriding finalize() to ensure close gets called
            // CleanerReference handles ensuring there's a strong ref to itself so we can just construct it and move on
            new CleanerReference<MountHandle, Closeable>(mountHandle, handle, REAPER);
        }
        return mountHandle;
    }

    private final Closeable handle;

    /**
     * Construct new instance with the mount handle to close.
     *
     * @param handle The mount handle to close
     *
     */
    private MountHandle(final Closeable handle) {
        this.handle = handle;
    }

    /**
     * Forcefully close this handle. Use with caution.
     */
    public void close() {
        if (handle != null) {
            VFSUtils.safeClose(handle);
        }
    }
}
