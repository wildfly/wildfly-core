/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc., and individual contributors as indicated
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

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLDescription.PersistentResourceXMLBuilder;
import org.jboss.as.controller.PersistentResourceXMLParser;
import org.jboss.dmr.ModelNode;

/**
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public class ThreadsParser2_0 extends PersistentResourceXMLParser {
    static final ThreadsParser2_0 INSTANCE = new ThreadsParser2_0();

    public static final PersistentResourceXMLBuilder THREAD_FACTORY_PARSER = getThreadFactoryParser(ThreadFactoryResourceDefinition.DEFAULT_INSTANCE);


    private static final PersistentResourceXMLDescription xmlDescription = builder(new ThreadSubsystemResourceDefinition(), Namespace.CURRENT.getUriString())
            .addChild(THREAD_FACTORY_PARSER)
            .addChild(getUnboundedQueueThreadPoolParser(UnboundedQueueThreadPoolResourceDefinition.INSTANCE))
            .addChild(getBoundedQueueThreadPoolParser(BoundedQueueThreadPoolResourceDefinition.NON_BLOCKING))
            .addChild(getBoundedQueueThreadPoolParser(BoundedQueueThreadPoolResourceDefinition.BLOCKING))
            .addChild(getQueuelessThreadPoolParser(QueuelessThreadPoolResourceDefinition.NON_BLOCKING))
            .addChild(getQueuelessThreadPoolParser(QueuelessThreadPoolResourceDefinition.BLOCKING))
            .addChild(getScheduledThreadPoolParser(ScheduledThreadPoolResourceDefinition.INSTANCE))
            .build();


    @Override
    public PersistentResourceXMLDescription getParserDescription() {
        return xmlDescription;
    }


    public static PersistentResourceXMLBuilder getThreadFactoryParser(ThreadFactoryResourceDefinition factoryResourceDefinition) {
        return builder(factoryResourceDefinition)
                .addAttributes(PoolAttributeDefinitions.GROUP_NAME, PoolAttributeDefinitions.THREAD_NAME_PATTERN, PoolAttributeDefinitions.PRIORITY);

    }

    public static PersistentResourceXMLBuilder getUnboundedQueueThreadPoolParser(UnboundedQueueThreadPoolResourceDefinition resourceDefinition) {
        return builder(resourceDefinition)
                .addAttribute(KeepAliveTimeAttributeDefinition.KEEPALIVE_TIME_UNIT, NullAttributeParser.INSTANCE) //hack
                .addAttributes(PoolAttributeDefinitions.KEEPALIVE_TIME,
                        PoolAttributeDefinitions.MAX_THREADS, PoolAttributeDefinitions.THREAD_FACTORY);

    }

    public static PersistentResourceXMLBuilder getScheduledThreadPoolParser(ScheduledThreadPoolResourceDefinition resourceDefinition) {
        return builder(resourceDefinition)
                .addAttribute(KeepAliveTimeAttributeDefinition.KEEPALIVE_TIME_UNIT, NullAttributeParser.INSTANCE)//hack
                .addAttributes(PoolAttributeDefinitions.KEEPALIVE_TIME,
                        PoolAttributeDefinitions.MAX_THREADS, PoolAttributeDefinitions.THREAD_FACTORY);

    }

    public static PersistentResourceXMLBuilder getQueuelessThreadPoolParser(QueuelessThreadPoolResourceDefinition definition) {
        PersistentResourceXMLBuilder builder = builder(definition)
                .addAttribute(KeepAliveTimeAttributeDefinition.KEEPALIVE_TIME_UNIT, NullAttributeParser.INSTANCE)//hack
                .addAttributes(PoolAttributeDefinitions.KEEPALIVE_TIME, PoolAttributeDefinitions.MAX_THREADS, PoolAttributeDefinitions.THREAD_FACTORY);

        if (!definition.isBlocking()) {
            builder.addAttribute(PoolAttributeDefinitions.HANDOFF_EXECUTOR);
        }
        return builder;
    }

    public static PersistentResourceXMLBuilder getBoundedQueueThreadPoolParser(BoundedQueueThreadPoolResourceDefinition definition) {
        PersistentResourceXMLBuilder builder = builder(definition)
                .addAttribute(PoolAttributeDefinitions.KEEPALIVE_TIME)
                .addAttribute(KeepAliveTimeAttributeDefinition.KEEPALIVE_TIME_UNIT, NullAttributeParser.INSTANCE)//hack
                .addAttributes(
                        PoolAttributeDefinitions.MAX_THREADS, PoolAttributeDefinitions.THREAD_FACTORY,
                        PoolAttributeDefinitions.CORE_THREADS, PoolAttributeDefinitions.QUEUE_LENGTH,
                        PoolAttributeDefinitions.ALLOW_CORE_TIMEOUT);

        if (!definition.isBlocking()) {
            builder.addAttribute(PoolAttributeDefinitions.HANDOFF_EXECUTOR);
        }
        return builder;
    }

    private static class NullAttributeParser extends AttributeParser {
        public static final NullAttributeParser INSTANCE = new NullAttributeParser();
        @Override
        public void parseAndSetParameter(AttributeDefinition attribute, String value, ModelNode operation, XMLStreamReader reader) throws XMLStreamException {
            // do noting
        }

        @Override
        public ModelNode parse(AttributeDefinition attribute, String value, XMLStreamReader reader) throws XMLStreamException {
            return null;
        }
    }
}
