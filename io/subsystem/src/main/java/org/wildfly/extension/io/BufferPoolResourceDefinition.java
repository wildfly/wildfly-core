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
 */

package org.wildfly.extension.io;

import io.undertow.connector.ByteBufferPool;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.xnio.Pool;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
class BufferPoolResourceDefinition extends PersistentResourceDefinition {

    static final RuntimeCapability<Void> IO_POOL_RUNTIME_CAPABILITY =
            RuntimeCapability.Builder.of(IOServices.BUFFER_POOL_CAPABILITY_NAME, true, Pool.class).build();
    static final RuntimeCapability<Void> IO_BYTE_BUFFER_POOL_RUNTIME_CAPABILITY =
            RuntimeCapability.Builder.of(IOServices.BYTE_BUFFER_POOL_CAPABILITY_NAME, true, ByteBufferPool.class).build();


    static final int defaultBufferSize;
    static final int defaultBuffersPerRegion;
    static final boolean defaultDirectBuffers;

    static {
        long maxMemory = Runtime.getRuntime().maxMemory();
        // smaller than 64mb of ram we use 512b buffers
        if (maxMemory < 64 * 1024 * 1024) {
            // use 512b buffers
            defaultDirectBuffers = false;
            defaultBufferSize = 512;
            defaultBuffersPerRegion = 10;
        } else if (maxMemory < 128 * 1024 * 1024) {
            // use 1k buffers
            defaultDirectBuffers = true;
            defaultBufferSize = 1024;
            defaultBuffersPerRegion = 10;
        } else {
            // use 16k buffers for best performance
            // as 16k is generally the max amount of data that can be sent in a single write() call
            defaultDirectBuffers = true;
            defaultBufferSize = 1024 * 16;
            defaultBuffersPerRegion = 20;
        }
    }

    static final SimpleAttributeDefinition BUFFER_SIZE = new SimpleAttributeDefinitionBuilder(Constants.BUFFER_SIZE, ModelType.INT, true)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setAllowExpression(true)
            .setValidator(new IntRangeValidator(1, true, true))
            .setDefaultValue(new ModelNode(defaultBufferSize))
            .build();
    static final SimpleAttributeDefinition BUFFER_PER_SLICE = new SimpleAttributeDefinitionBuilder(Constants.BUFFER_PER_SLICE, ModelType.INT, true)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setAllowExpression(true)
            .setValidator(new IntRangeValidator(1, true, true))
            .setDefaultValue(new ModelNode(defaultBuffersPerRegion))
            .build();
    static final SimpleAttributeDefinition DIRECT_BUFFERS = new SimpleAttributeDefinitionBuilder(Constants.DIRECT_BUFFERS, ModelType.BOOLEAN, true)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(defaultDirectBuffers))
            .build();

    /* <buffer-pool name="default" buffer-size="1024" buffers-per-slice="1024"/> */

    static List<SimpleAttributeDefinition> ATTRIBUTES = Arrays.asList(BUFFER_SIZE, BUFFER_PER_SLICE, DIRECT_BUFFERS);

    public static final BufferPoolResourceDefinition INSTANCE = new BufferPoolResourceDefinition();

    private BufferPoolResourceDefinition() {
        super(new SimpleResourceDefinition.Parameters(IOExtension.BUFFER_POOL_PATH,
                IOExtension.getResolver(Constants.BUFFER_POOL))
                .setAddHandler(new BufferPoolAdd())
                .setRemoveHandler(new ReloadRequiredRemoveStepHandler())
                .addCapabilities(IO_POOL_RUNTIME_CAPABILITY,
                        IO_BYTE_BUFFER_POOL_RUNTIME_CAPABILITY)
                .setDeprecatedSince(ModelVersion.create(4))
        );
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return (Collection) ATTRIBUTES;
    }

    @Override
    public void registerCapabilities(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerCapability(IO_POOL_RUNTIME_CAPABILITY);
        resourceRegistration.registerCapability(IO_BYTE_BUFFER_POOL_RUNTIME_CAPABILITY);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadOnlyAttribute(BUFFER_SIZE, new BufferReadAttributeHandler(BUFFER_SIZE));
        resourceRegistration.registerReadOnlyAttribute(BUFFER_PER_SLICE, new BufferReadAttributeHandler(BUFFER_PER_SLICE));
        resourceRegistration.registerReadOnlyAttribute(DIRECT_BUFFERS, new BufferReadAttributeHandler(DIRECT_BUFFERS));
    }

    private static class BufferReadAttributeHandler implements OperationStepHandler {

        final SimpleAttributeDefinition definition;

        public BufferReadAttributeHandler(SimpleAttributeDefinition definition) {
            this.definition = definition;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            if ( definition.getType() == ModelType.INT )
                context.getResult().set(new ModelNode(definition.getDefaultValue().asInt()));
            else if ( definition.getType() == ModelType.BOOLEAN )
                context.getResult().set(new ModelNode(definition.getDefaultValue().asBoolean()));
            else
                throw new IllegalArgumentException("Arguments type can only be INT or BOOL: " + ATTRIBUTES );
        }
    }
}
