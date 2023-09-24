/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLParser;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
class IOSubsystemParser_1_0 extends PersistentResourceXMLParser {

    private final PersistentResourceXMLDescription xmlDescription;

    IOSubsystemParser_1_0() {
        xmlDescription = builder(IORootDefinition.INSTANCE.getPathElement())
                .addChild(
                        builder(WorkerResourceDefinition.INSTANCE.getPathElement())
                                .addAttribute(WorkerResourceDefinition.WORKER_IO_THREADS, new AttributeParser.DiscardOldDefaultValueParser("3"))
                                .addAttributes(
                                        WorkerResourceDefinition.WORKER_TASK_KEEPALIVE,
                                        WorkerResourceDefinition.WORKER_TASK_MAX_THREADS,
                                        WorkerResourceDefinition.STACK_SIZE)
                )
                .addChild(
                        builder(BufferPoolResourceDefinition.INSTANCE.getPathElement())
                                .addAttribute(BufferPoolResourceDefinition.BUFFER_SIZE, new AttributeParser.DiscardOldDefaultValueParser("16384"))
                                .addAttribute(BufferPoolResourceDefinition.BUFFER_PER_SLICE, new AttributeParser.DiscardOldDefaultValueParser("128"))
                                .addAttribute(BufferPoolResourceDefinition.DIRECT_BUFFERS)
                )
                .build();
    }

    @Override
    public PersistentResourceXMLDescription getParserDescription() {
        return xmlDescription;
    }
}

