/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.transformers;


import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.createBuilder;
import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.createBuilderFromCurrent;

import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilderFactory;
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

    static ChainedTransformationDescriptionBuilder buildTransformerChain(ChainedTransformationDescriptionBuilderFactory factory) {
        ChainedTransformationDescriptionBuilder chainedBuilder = factory.createChainedTransformationDescriptionBuilder(ServerGroupResourceDefinition.PATH);

        //////////////////////////////////
        //The EAP/AS 7.x chains

        // graceful-startup attribute was introduced in Kernel 16 (Wildfly 23)
        ResourceTransformationDescriptionBuilder currentTo15 = createBuilderFromCurrent(chainedBuilder, KernelAPIVersion.VERSION_15_0)
                .getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.DEFAULT_VALUE, ServerGroupResourceDefinition.GRACEFUL_STARTUP)
                .addRejectCheck(RejectAttributeChecker.DEFINED, ServerGroupResourceDefinition.GRACEFUL_STARTUP)
                .end();

        // module-options was introduced in Kernel API 14 (WildFly Core 13, WildFly 21)
        ResourceTransformationDescriptionBuilder builder15to13 = createBuilder(chainedBuilder, KernelAPIVersion.VERSION_15_0, KernelAPIVersion.VERSION_13_0);
        JvmTransformers.registerTransformers13_AndBelow(builder15to13);

        //timeout attribute renamed to suspend-timeout in Version Kernel 9.0 (WildFly Core 7, WildFly 15). Must be renamed for 8.0 and below
        ResourceTransformationDescriptionBuilder builder10to8 = createBuilder(chainedBuilder, KernelAPIVersion.VERSION_10_0, KernelAPIVersion.VERSION_8_0);
        DomainServerLifecycleHandlers.registerTimeoutToSuspendTimeoutRename(builder10to8);

        // kill-servers and destroy-servers are rejected for Kernel 5.0 (WildFly Core 3, WildFly 11) and below
        ResourceTransformationDescriptionBuilder builder60to50 = createBuilder(chainedBuilder, KernelAPIVersion.VERSION_6_0, KernelAPIVersion.VERSION_5_0);
        DomainServerLifecycleHandlers.registerKillDestroyTransformers(builder60to50);

        // The use of default-interface attribute in socket-binding-group is rejected for 1.8 and below
        ResourceTransformationDescriptionBuilder builder20to18 = createBuilder(chainedBuilder, KernelAPIVersion.VERSION_2_0, KernelAPIVersion.VERSION_1_8)
                .getAttributeBuilder()
                .setDiscard(DiscardAttributeChecker.UNDEFINED, ServerGroupResourceDefinition.SOCKET_BINDING_DEFAULT_INTERFACE)
                .addRejectCheck(RejectAttributeChecker.DEFINED, ServerGroupResourceDefinition.SOCKET_BINDING_DEFAULT_INTERFACE)
                .end();

        //suspend and resume servers are rejected for Kernel 1.8 (EAP 6.4.0 CP07) and below
        DomainServerLifecycleHandlers.registerServerLifeCycleOperationsTransformers(builder20to18);

        // The use of launch-command is rejected since Kernel 2.1 (WildFly 8.1.0) and below
        ResourceTransformationDescriptionBuilder builder30To21 = createBuilder(chainedBuilder, KernelAPIVersion.VERSION_3_0, KernelAPIVersion.VERSION_2_1);
        JvmTransformers.registerTransformers2_1_AndBelow(builder30To21);

        return chainedBuilder;
    }
}
