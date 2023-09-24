/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import org.jboss.msc.service.ServiceName;
import org.xnio.Pool;
import org.xnio.XnioWorker;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
public final class IOServices {

    @Deprecated
    public static final ServiceName IO = ServiceName.JBOSS.append("io");
    @Deprecated
    public static final ServiceName WORKER = WorkerResourceDefinition.IO_WORKER_RUNTIME_CAPABILITY.getCapabilityServiceName(XnioWorker.class);
    @Deprecated
    public static final ServiceName BUFFER_POOL = BufferPoolResourceDefinition.IO_POOL_RUNTIME_CAPABILITY.getCapabilityServiceName(Pool.class);

    public static final String IO_WORKER_CAPABILITY_NAME = "org.wildfly.io.worker";

    public static final String BUFFER_POOL_CAPABILITY_NAME = "org.wildfly.io.buffer-pool";
    public static final String BYTE_BUFFER_POOL_CAPABILITY_NAME = "org.wildfly.undertow.byte-buffer-pool";
}
