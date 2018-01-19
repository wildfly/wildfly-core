/*
 *
 *  JBoss, Home of Professional Open Source.
 *  Copyright 2013, Red Hat, Inc., and individual contributors
 *  as indicated by the @author tags. See the copyright.txt file in the
 *  distribution for a full listing of individual contributors.
 *
 *  This is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this software; if not, write to the Free
 *  Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 *  02110-1301 USA, or see the FSF site: http://www.fsf.org.
 * /
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
