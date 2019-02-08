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

import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.VERSION_1_7;
import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.VERSION_1_8;
import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.VERSION_2_0;
import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.VERSION_2_1;
import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.VERSION_3_0;
import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.VERSION_4_0;
import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.VERSION_4_1;
import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.VERSION_4_2;
import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.VERSION_5_0;
import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.VERSION_6_0;
import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.VERSION_7_0;
import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.VERSION_8_0;
import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.toModelVersions;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.transform.OperationRejectionPolicy;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.TransformerRegistry;
import org.jboss.as.controller.transform.TransformersSubRegistration;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescription;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.domain.controller.operations.DomainServerLifecycleHandlers;
import org.jboss.as.domain.controller.resources.HostExcludeResourceDefinition;
import org.jboss.as.domain.controller.resources.ProfileResourceDefinition;
import org.jboss.dmr.ModelNode;

/**
 * Global transformation rules for the domain, host and server-config model.
 *
 * @author Emanuel Muckenhuber
 */
public class DomainTransformers {

    /**
     * Initialize the domain registry.
     *
     * @param registry the domain registry
     */
    public static void initializeDomainRegistry(final TransformerRegistry registry) {

        //The chains for transforming will be as follows
        //For JBoss EAP: 8.0.0 -> 5.0.0 -> 4.0.0 -> 1.8.0 -> 1.7.0 -> 1.6.0 -> 1.5.0

        registerRootTransformers(registry);
        registerChainedManagementTransformers(registry);
        registerChainedServerGroupTransformers(registry);
        registerProfileTransformers(registry);
        registerSocketBindingGroupTransformers(registry);
        registerDeploymentTransformers(registry);
    }

    private static void registerRootTransformers(TransformerRegistry registry) {

        // Releases other than the current (latest) one may not understand the current host-release enum values
        // and releases prior to 4.0 do not understand host-exclude at all. Since host-exclude is only of use on the domain
        // controller (DC), and the DC must be running the same or later release than the slaves, we don't send it to slaves when
        // they are prior versions to current.
        // TODO: WFCORE-3252 make this filtering depend on the slaves version and only send that and prior known values?

        // To make this work properly, we first register transformers for any versions that require something beyond
        // this host-exclude transformation. We track which versions we've done this way so we know what's left.
        // Then we register a simple discard host-exclude transformer for what's left

        // A set of api versions for which we have not registered specific transformers
        Set<KernelAPIVersion> allOthers = EnumSet.complementOf(EnumSet.of(KernelAPIVersion.CURRENT));

        // FIRST: versions with special transformations

        //todo we should use ChainedTransformationDescriptionBuilder but it doesn't work properly with "root" resources
        ResourceTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createInstance(null);
        // discard host-exclude
        builder.discardChildResource(HostExcludeResourceDefinition.PATH_ELEMENT);

        DomainServerLifecycleHandlers.registerTimeoutToSuspendTimeoutRename(builder);
        registerNonChainedTransformers(allOthers, registry, builder, VERSION_8_0, VERSION_7_0, VERSION_6_0, VERSION_5_0, VERSION_4_2, VERSION_4_1);

        // 4.0 and earlier do not understand the concept of a suspended startup/reload
        DomainServerLifecycleHandlers.registerSuspendedStartTransformers(builder);
        registerNonChainedTransformers(allOthers, registry, builder, VERSION_4_0, VERSION_3_0, VERSION_2_1, VERSION_2_0);

        // 1.8 and earlier do not understand suspend/resume
        DomainServerLifecycleHandlers.registerServerLifeCycleOperationsTransformers(builder);
        registerNonChainedTransformers(allOthers, registry, builder, VERSION_1_7, VERSION_1_8);


        // NEXT:  We've registered all the versions that have special transformation needs.
        // Now, we register a transformer for every other version that discards host-exclude
        builder = TransformationDescriptionBuilder.Factory.createInstance(null);
        builder.discardChildResource(HostExcludeResourceDefinition.PATH_ELEMENT);
        for (KernelAPIVersion version : allOthers) {
            TransformersSubRegistration domain = registry.getDomainRegistration(version.modelVersion);
            TransformationDescription.Tools.register(builder.build(), domain);
        }
    }

    private static void registerNonChainedTransformers(Set<KernelAPIVersion> remainder, TransformerRegistry registry, ResourceTransformationDescriptionBuilder builder, KernelAPIVersion... toRegister) {
        for (KernelAPIVersion version : toRegister) {
            remainder.remove(version);
            TransformersSubRegistration domain = registry.getDomainRegistration(version.modelVersion);
            TransformationDescription.Tools.register(builder.build(), domain);
        }
    }

    private static void registerChainedManagementTransformers(TransformerRegistry registry) {
        ChainedTransformationDescriptionBuilder builder = ManagementTransformers.buildTransformerChain();
        registerChainedTransformer(registry, builder, VERSION_1_7, VERSION_1_8, VERSION_4_1);
    }

    private static void registerChainedServerGroupTransformers(TransformerRegistry registry) {
        ChainedTransformationDescriptionBuilder builder = ServerGroupTransformers.buildTransformerChain();
        registerChainedTransformer(registry, builder, VERSION_8_0, VERSION_7_0, VERSION_6_0, VERSION_5_0, VERSION_4_1, VERSION_4_0, VERSION_3_0, VERSION_2_1, VERSION_2_0, VERSION_1_8, VERSION_1_7);
    }

    private static void registerProfileTransformers(TransformerRegistry registry) {
        //Do NOT use chained transformers for the profile. The placeholder registry takes precedence over the actual
        //transformer registry, which means we would not get subsystem transformation

        //Registering for all previous WF versions (as well as the required EAP ones) isn't strictly necessary,
        //but it is very handy for testing the 'clone' behaviour
        final KernelAPIVersion[] PRE_PROFILE_CLONE_VERSIONS = new KernelAPIVersion[]{VERSION_3_0, VERSION_2_1, VERSION_2_0, VERSION_1_8, VERSION_1_7};
        for (KernelAPIVersion version : PRE_PROFILE_CLONE_VERSIONS) {
            ResourceTransformationDescriptionBuilder builder =
                    ResourceTransformationDescriptionBuilder.Factory.createInstance(ProfileResourceDefinition.PATH);
            builder.getAttributeBuilder()
                    .addRejectCheck(RejectAttributeChecker.DEFINED, ProfileResourceDefinition.INCLUDES)
                    .setDiscard(DiscardAttributeChecker.UNDEFINED, ProfileResourceDefinition.INCLUDES)
                    .end();
            builder.addOperationTransformationOverride(ModelDescriptionConstants.CLONE)
                    .setCustomOperationTransformer(ProfileCloneOperationTransformer.INSTANCE);
            TransformersSubRegistration domain = registry.getDomainRegistration(version.modelVersion);
            TransformationDescription.Tools.register(builder.build(), domain);
        }
    }

    private static void registerSocketBindingGroupTransformers(TransformerRegistry registry) {
        ChainedTransformationDescriptionBuilder builder = SocketBindingGroupTransformers.buildTransformerChain();
        registerChainedTransformer(registry, builder, VERSION_1_8, VERSION_1_7);
    }

    private static void registerChainedTransformer(TransformerRegistry registry, ChainedTransformationDescriptionBuilder builder, KernelAPIVersion...versions) {
        for (Map.Entry<ModelVersion, TransformationDescription> entry : builder.build(toModelVersions(versions)).entrySet()) {
            TransformersSubRegistration domain = registry.getDomainRegistration(entry.getKey());
            TransformationDescription.Tools.register(entry.getValue(), domain);
        }
    }

    private static void registerDeploymentTransformers(TransformerRegistry registry) {
        ChainedTransformationDescriptionBuilder builder = DeploymentTransformers.buildTransformerChain();
        registerChainedTransformer(registry, builder, VERSION_4_1, VERSION_4_0, VERSION_3_0, VERSION_2_1, VERSION_2_0, VERSION_1_8, VERSION_1_7);
    }

    private static class ProfileCloneOperationTransformer implements OperationTransformer {
        static ProfileCloneOperationTransformer INSTANCE = new ProfileCloneOperationTransformer();
        @Override
        public TransformedOperation transformOperation(final TransformationContext context, final PathAddress address, final ModelNode operation) throws OperationFailedException {
            if (context.getTarget().isIgnoreUnaffectedConfig()) {
                //Since the cloned profile is a new one it will definitely be ignored on the host with this setting,
                //so we can just discard it
                return OperationTransformer.DISCARD.transformOperation(context, address, operation);
            }

            return new TransformedOperation(operation, new OperationRejectionPolicy() {
                @Override
                public boolean rejectOperation(ModelNode preparedResult) {
                    return true;
                }

                @Override
                public String getFailureDescription() {
                    return DomainControllerLogger.ROOT_LOGGER.cloneOperationNotSupportedOnHost(context.getTarget().getHostName());
                }
            }, OperationResultTransformer.ORIGINAL_RESULT);
        }
    }
}
