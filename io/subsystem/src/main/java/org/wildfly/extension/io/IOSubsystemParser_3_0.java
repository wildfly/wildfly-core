/*
 * JBoss, Home of Professional Open Source
 * Copyright 2018, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.io;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLParser;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
class IOSubsystemParser_3_0 extends PersistentResourceXMLParser {

    @Override
    public PersistentResourceXMLDescription getParserDescription() {
        return builder(IORootDefinition.INSTANCE.getPathElement(), Namespace.IO_3_0.getUriString())
                .addChild(
                        builder(WorkerResourceDefinition.INSTANCE.getPathElement())
                                .addAttributes(
                                        WorkerResourceDefinition.WORKER_IO_THREADS,
                                        WorkerResourceDefinition.WORKER_TASK_KEEPALIVE,
                                        WorkerResourceDefinition.WORKER_TASK_CORE_THREADS,
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

