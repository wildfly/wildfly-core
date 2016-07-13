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

import java.util.Map;

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
import org.jboss.as.version.Version;
import org.jboss.dmr.ModelNode;

/**
 * Global transformation rules for the domain, host and server-config model.
 *
 * @author Emanuel Muckenhuber
 */
public class DomainTransformers {

    /** Dummy version for ignored subsystems. */
    static final ModelVersion IGNORED_SUBSYSTEMS = ModelVersion.create(-1);

    // EAP 6.2.0
    static final ModelVersion VERSION_1_5 = ModelVersion.create(1, 5, 0);
    // EAP 6.3.0
    static final ModelVersion VERSION_1_6 = ModelVersion.create(1, 6, 0);
    // EAP 6.4.0
    static final ModelVersion VERSION_1_7 = ModelVersion.create(1, 7, 0);
    // EAP 6.4.0 CP07
    static final ModelVersion VERSION_1_8 = ModelVersion.create(1, 8, 0);
    //WF 8.0.0.Final
    static final ModelVersion VERSION_2_0 = ModelVersion.create(2, 0, 0);
    //WF 8.1.0.Final
    static final ModelVersion VERSION_2_1 = ModelVersion.create(2, 1, 0);
    //WF 9.0.0 and 9.0.1
    static final ModelVersion VERSION_3_0 = ModelVersion.create(3, 0, 0);
    //WF 10.0.0
    static final ModelVersion VERSION_4_0 = ModelVersion.create(4, 0, 0);

    //EAP7.0.0
    static final ModelVersion VERSION_4_1 = ModelVersion.create(4, 1, 0);

    //All versions before WildFly 10/EAP 7, which do not understand /profile=xxx:clone
    private static final ModelVersion[] PRE_PROFILE_CLONE_VERSIONS = new ModelVersion[]{VERSION_3_0, VERSION_2_1, VERSION_2_0, VERSION_1_8, VERSION_1_7, VERSION_1_6, VERSION_1_5};

    //Current
    static final ModelVersion CURRENT = ModelVersion.create(Version.MANAGEMENT_MAJOR_VERSION, Version.MANAGEMENT_MINOR_VERSION, Version.MANAGEMENT_MICRO_VERSION);

    /**
     * Initialize the domain registry.
     *
     * @param registry the domain registry
     */
    public static void initializeDomainRegistry(final TransformerRegistry registry) {
        initializeChainedDomainRegistry(registry);
    }


    private static void initializeChainedDomainRegistry(TransformerRegistry registry) {
        //The chains for transforming will be as follows
        //For JBoss EAP: 4.0.0 -> 1.8.0 -> 1.7.0 -> 1.6.0 -> 1.5.0

        registerRootTransformers(registry);
        registerChainedManagementTransformers(registry, CURRENT);
        registerChainedServerGroupTransformers(registry, CURRENT);
        registerProfileTransformers(registry, CURRENT);
        registerSocketBindingGroupTransformers(registry, CURRENT);
    }

    private static void registerRootTransformers(TransformerRegistry registry) {
        //todo we should use ChainedTransformationDescriptionBuilder but it doesn't work properly with "root" resources
        ResourceTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createInstance(null);

        // 4.0 and earlier do not understand host-exclude. These are only used by
        // a DC, which must be running current version, so it's safe to discard them
        builder.discardChildResource(HostExcludeResourceDefinition.PATH_ELEMENT);
        ModelVersion[] versions = {VERSION_3_0, VERSION_4_0};
        for (ModelVersion version : versions){
            TransformersSubRegistration domain = registry.getDomainRegistration(version);
            TransformationDescription.Tools.register(builder.build(), domain);
        }

        // 1.8 and earlier do not understand suspend/resume
        DomainServerLifecycleHandlers.registerServerLifeCycleOperationsTransformers(builder);
        versions = new ModelVersion[]{VERSION_1_5, VERSION_1_6, VERSION_1_7, VERSION_1_8};
        for (ModelVersion version : versions){
            TransformersSubRegistration domain = registry.getDomainRegistration(version);
            TransformationDescription.Tools.register(builder.build(), domain);
        }
    }

    private static void registerChainedManagementTransformers(TransformerRegistry registry, ModelVersion currentVersion) {
        ChainedTransformationDescriptionBuilder builder = ManagementTransformers.buildTransformerChain(currentVersion);
        registerChainedTransformer(registry, builder, VERSION_1_5, VERSION_1_6, VERSION_1_7, VERSION_1_8, VERSION_4_1);
    }

    private static void registerChainedServerGroupTransformers(TransformerRegistry registry, ModelVersion currentVersion) {
        ChainedTransformationDescriptionBuilder builder = ServerGroupTransformers.buildTransformerChain(currentVersion);
        registerChainedTransformer(registry, builder, VERSION_1_5, VERSION_1_6, VERSION_1_7, VERSION_1_8);
    }

    private static void registerProfileTransformers(TransformerRegistry registry, ModelVersion currentVersion) {
        //Do NOT use chained transformers for the profile. The placeholder registry takes precedence over the actual
        //transformer registry, which means we would not get subsystem transformation

        //Registering for all previous WF versions (as well as the required EAP ones) isn't strictly necessary,
        //but it is very handy for testing the 'clone' behaviour
        for (ModelVersion version : PRE_PROFILE_CLONE_VERSIONS) {
            ResourceTransformationDescriptionBuilder builder =
                    ResourceTransformationDescriptionBuilder.Factory.createInstance(ProfileResourceDefinition.PATH);
            builder.getAttributeBuilder()
                    .addRejectCheck(RejectAttributeChecker.DEFINED, ProfileResourceDefinition.INCLUDES)
                    .setDiscard(DiscardAttributeChecker.UNDEFINED, ProfileResourceDefinition.INCLUDES)
                    .end();
            builder.addOperationTransformationOverride(ModelDescriptionConstants.CLONE)
                    .setCustomOperationTransformer(ProfileCloneOperationTransformer.INSTANCE);
            TransformersSubRegistration domain = registry.getDomainRegistration(version);
            TransformationDescription.Tools.register(builder.build(), domain);
        }
    }

    private static void registerSocketBindingGroupTransformers(TransformerRegistry registry, ModelVersion currentVersion) {
        ChainedTransformationDescriptionBuilder builder = SocketBindingGroupTransformers.buildTransformerChain(currentVersion);
        registerChainedTransformer(registry, builder, VERSION_1_8, VERSION_1_7, VERSION_1_6, VERSION_1_5);
    }

    private static void registerChainedTransformer(TransformerRegistry registry, ChainedTransformationDescriptionBuilder builder , ModelVersion...versions) {
        for (Map.Entry<ModelVersion, TransformationDescription> entry : builder.build(versions).entrySet()) {
            TransformersSubRegistration domain = registry.getDomainRegistration(entry.getKey());
            TransformationDescription.Tools.register(entry.getValue(), domain);
        }
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
