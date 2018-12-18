/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.controller.transformers;


import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.createBuilder;
import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.createBuilderFromCurrent;
import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.createChainFromCurrent;

import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.domain.controller.operations.DomainServerLifecycleHandlers;
import org.jboss.as.domain.controller.resources.ServerGroupResourceDefinition;

/**
 * Transformer registration for the server-group resources.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
class ServerGroupTransformers {

    static ChainedTransformationDescriptionBuilder buildTransformerChain() {
        ChainedTransformationDescriptionBuilder chainedBuilder = createChainFromCurrent(ServerGroupResourceDefinition.PATH);

        //////////////////////////////////
        //The EAP/AS 7.x chains

        //timeout attribute renamed to suspend-timeout in Version 9.0. Must be renamed for 8.0 and below
        ResourceTransformationDescriptionBuilder currentTo80 = createBuilderFromCurrent(chainedBuilder, KernelAPIVersion.VERSION_8_0);
        DomainServerLifecycleHandlers.registerTimeoutToSuspendTimeoutRename(currentTo80);

        // kill-servers and destroy-servers are rejected since 5.0 and below
        ResourceTransformationDescriptionBuilder builder60to50 = createBuilder(chainedBuilder, KernelAPIVersion.VERSION_6_0, KernelAPIVersion.VERSION_5_0);
        DomainServerLifecycleHandlers.registerKillDestroyTransformers(builder60to50);

        // The use of default-interface attribute in socket-binding-group is rejected since 1.8 and below
        ResourceTransformationDescriptionBuilder builder20to18 = createBuilder(chainedBuilder, KernelAPIVersion.VERSION_2_0, KernelAPIVersion.VERSION_1_8)
                .getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.UNDEFINED, ServerGroupResourceDefinition.SOCKET_BINDING_DEFAULT_INTERFACE)
                .addRejectCheck(RejectAttributeChecker.DEFINED, ServerGroupResourceDefinition.SOCKET_BINDING_DEFAULT_INTERFACE)
                .end();

        //suspend and resume servers are rejected since 1.8 and below
        DomainServerLifecycleHandlers.registerServerLifeCycleOperationsTransformers(builder20to18);

        // The use of launch-command is rejected since 2.1 and below
        ResourceTransformationDescriptionBuilder builder30To21 = createBuilder(chainedBuilder, KernelAPIVersion.VERSION_3_0, KernelAPIVersion.VERSION_2_1);
        JvmTransformers.registerTransformers2_1_AndBelow(builder30To21);

        return chainedBuilder;
    }
}
