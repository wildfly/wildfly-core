/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import org.jboss.msc.service.ServiceName;
import org.wildfly.io.IOServiceDescriptor;
import org.xnio.XnioWorker;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 * @deprecated Capabilities of IO subsystem are described by {@link IOServiceDescriptor}.
 */
@Deprecated(forRemoval = true)
public final class IOServices {

    public static final ServiceName IO = ServiceName.JBOSS.append("io");

    public static final ServiceName WORKER = WorkerResourceDefinition.CAPABILITY.getCapabilityServiceName(XnioWorker.class);

    public static final ServiceName BUFFER_POOL = BufferPoolResourceDefinition.IO_POOL_RUNTIME_CAPABILITY.getCapabilityServiceName(org.xnio.Pool.class);


    public static final String IO_WORKER_CAPABILITY_NAME = IOServiceDescriptor.WORKER.getName();

    public static final String BUFFER_POOL_CAPABILITY_NAME = "org.wildfly.io.buffer-pool";

    public static final String BYTE_BUFFER_POOL_CAPABILITY_NAME = "org.wildfly.undertow.byte-buffer-pool";
}
