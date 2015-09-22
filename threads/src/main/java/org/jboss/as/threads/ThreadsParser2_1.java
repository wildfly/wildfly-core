/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
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

package org.jboss.as.threads;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLDescription.PersistentResourceXMLBuilder;

/**
 * @author panos (c) 2016 Red Hat Inc.
 */
public class ThreadsParser2_1 extends ThreadsParser2_0 {
    static final ThreadsParser2_1 INSTANCE = new ThreadsParser2_1();

    @SuppressWarnings("deprecation")
    private static final PersistentResourceXMLDescription xmlDescription = builder(new ThreadSubsystemResourceDefinition(false), Namespace.CURRENT.getUriString())
            .addChild(THREAD_FACTORY_PARSER)
            .addChild(getUnboundedQueueThreadPoolParser(UnboundedQueueThreadPoolResourceDefinition.create(false)))
            .addChild(getBoundedQueueThreadPoolParser(BoundedQueueThreadPoolResourceDefinition.create(false, false)))
            .addChild(getBoundedQueueThreadPoolParser(BoundedQueueThreadPoolResourceDefinition.create(true, false)))
            .addChild(getQueuelessThreadPoolParser(QueuelessThreadPoolResourceDefinition.create(false, false)))
            .addChild(getQueuelessThreadPoolParser(QueuelessThreadPoolResourceDefinition.create(true, false)))
            .addChild(getScheduledThreadPoolParser(ScheduledThreadPoolResourceDefinition.create(false)))
            .build();


    @Override
    public PersistentResourceXMLDescription getParserDescription() {
        return xmlDescription;
    }

    public static PersistentResourceXMLBuilder getUnboundedQueueThreadPoolParser(UnboundedQueueThreadPoolResourceDefinition resourceDefinition) {
        return builder(resourceDefinition)
                .addAttributes(PoolAttributeDefinitions.KEEPALIVE_TIME, PoolAttributeDefinitions.CORE_THREADS,
                        PoolAttributeDefinitions.MAX_THREADS, PoolAttributeDefinitions.THREAD_FACTORY);

    }

}
