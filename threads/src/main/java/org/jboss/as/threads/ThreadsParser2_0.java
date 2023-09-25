/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLDescription.PersistentResourceXMLBuilder;
import org.jboss.as.controller.PersistentResourceXMLParser;

/**
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public class ThreadsParser2_0 extends PersistentResourceXMLParser {

    public static final PersistentResourceXMLBuilder THREAD_FACTORY_PARSER = getThreadFactoryParser(ThreadFactoryResourceDefinition.DEFAULT_INSTANCE);


    @SuppressWarnings("deprecation")
    private final PersistentResourceXMLDescription xmlDescription = builder(new ThreadSubsystemResourceDefinition(false).getPathElement(), Namespace.CURRENT.getUriString())
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


    public static PersistentResourceXMLBuilder getThreadFactoryParser(ThreadFactoryResourceDefinition factoryResourceDefinition) {
        return builder(factoryResourceDefinition.getPathElement())
                .addAttributes(PoolAttributeDefinitions.GROUP_NAME, PoolAttributeDefinitions.THREAD_NAME_PATTERN, PoolAttributeDefinitions.PRIORITY);

    }

    public static PersistentResourceXMLBuilder getUnboundedQueueThreadPoolParser(UnboundedQueueThreadPoolResourceDefinition resourceDefinition) {
        return builder(resourceDefinition.getPathElement())
                .addAttributes(PoolAttributeDefinitions.KEEPALIVE_TIME,
                        PoolAttributeDefinitions.MAX_THREADS, PoolAttributeDefinitions.THREAD_FACTORY);

    }

    public static PersistentResourceXMLBuilder getScheduledThreadPoolParser(ScheduledThreadPoolResourceDefinition resourceDefinition) {
        return builder(resourceDefinition.getPathElement())
                .addAttributes(PoolAttributeDefinitions.KEEPALIVE_TIME,
                        PoolAttributeDefinitions.MAX_THREADS, PoolAttributeDefinitions.THREAD_FACTORY);

    }

    public static PersistentResourceXMLBuilder getQueuelessThreadPoolParser(QueuelessThreadPoolResourceDefinition definition) {
        PersistentResourceXMLBuilder builder = builder(definition.getPathElement())
                .addAttributes(PoolAttributeDefinitions.KEEPALIVE_TIME, PoolAttributeDefinitions.MAX_THREADS, PoolAttributeDefinitions.THREAD_FACTORY);

        if (!definition.isBlocking()) {
            builder.addAttribute(PoolAttributeDefinitions.HANDOFF_EXECUTOR);
        }
        return builder;
    }

    public static PersistentResourceXMLBuilder getBoundedQueueThreadPoolParser(BoundedQueueThreadPoolResourceDefinition definition) {
        PersistentResourceXMLBuilder builder = builder(definition.getPathElement())
                .addAttributes(
                        PoolAttributeDefinitions.KEEPALIVE_TIME,
                        PoolAttributeDefinitions.MAX_THREADS, PoolAttributeDefinitions.THREAD_FACTORY,
                        PoolAttributeDefinitions.CORE_THREADS, PoolAttributeDefinitions.QUEUE_LENGTH,
                        PoolAttributeDefinitions.ALLOW_CORE_TIMEOUT);

        if (!definition.isBlocking()) {
            builder.addAttribute(PoolAttributeDefinitions.HANDOFF_EXECUTOR);
        }
        return builder;
    }
}
