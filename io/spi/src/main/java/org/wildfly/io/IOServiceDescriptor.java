/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.io;

import org.wildfly.service.descriptor.NullaryServiceDescriptor;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;
import org.xnio.XnioWorker;

/**
 * Describes capabilities exposed by IO subsystem.
 */
public interface IOServiceDescriptor {
    /** Describes the maximum number of threads allocated to all active workers */
    NullaryServiceDescriptor<Integer> MAX_THREADS = NullaryServiceDescriptor.of("org.wildfly.io.max-threads", Integer.class);

    /** Describes the default worker */
    NullaryServiceDescriptor<XnioWorker> DEFAULT_WORKER = NullaryServiceDescriptor.of("org.wildfly.io.default-worker", XnioWorker.class);
    /** Describes a named worker */
    UnaryServiceDescriptor<XnioWorker> WORKER = UnaryServiceDescriptor.of("org.wildfly.io.worker", DEFAULT_WORKER);
}
