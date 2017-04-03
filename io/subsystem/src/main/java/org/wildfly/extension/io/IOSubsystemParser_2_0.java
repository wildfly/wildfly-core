/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.extension.io;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLParser;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
class IOSubsystemParser_2_0 extends PersistentResourceXMLParser {

    private static final PersistentResourceXMLDescription xmlDescription;

    static {
        xmlDescription = builder(IORootDefinition.INSTANCE.getPathElement(), Namespace.CURRENT.getUriString())
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

    @Override
    public PersistentResourceXMLDescription getParserDescription() {
        return xmlDescription;
    }
}

