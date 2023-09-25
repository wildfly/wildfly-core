/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLParser;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
class IOSubsystemParser_2_0 extends PersistentResourceXMLParser {

    @Override
    public PersistentResourceXMLDescription getParserDescription() {
        return builder(IORootDefinition.INSTANCE.getPathElement(), Namespace.IO_2_0.getUriString())
                .addChild(
                        builder(WorkerResourceDefinition.INSTANCE.getPathElement())
                                .addAttributes(
                                        WorkerResourceDefinition.WORKER_IO_THREADS,
                                        WorkerResourceDefinition.WORKER_TASK_KEEPALIVE,
                                        WorkerResourceDefinition.WORKER_TASK_MAX_THREADS,
                                        WorkerResourceDefinition.STACK_SIZE)
                                .addChild(
                                        builder(OutboundBindAddressResourceDefinition.getInstance().getPathElement())
                                                .addAttributes(
                                                        OutboundBindAddressResourceDefinition.MATCH,
                                                        OutboundBindAddressResourceDefinition.BIND_ADDRESS,
                                                        OutboundBindAddressResourceDefinition.BIND_PORT
                                                )
                                )
                )
                .addChild(
                        builder(BufferPoolResourceDefinition.INSTANCE.getPathElement())
                                .addAttributes(BufferPoolResourceDefinition.BUFFER_SIZE,
                                        BufferPoolResourceDefinition.BUFFER_PER_SLICE,
                                        BufferPoolResourceDefinition.DIRECT_BUFFERS)
                )
                .build();
    }
}

