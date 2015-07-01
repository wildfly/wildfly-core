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
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.ResourceTransformationContext;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.controller.transform.TransformationTarget;
import org.jboss.as.controller.transform.TransformerRegistry;
import org.jboss.as.controller.transform.TransformersSubRegistration;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescription;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
import org.jboss.as.domain.controller.operations.DomainServerLifecycleHandlers;
import org.jboss.as.domain.controller.resources.ProfileResourceDefinition;
import org.jboss.as.version.Version;

/**
 * Global transformation rules for the domain, host and server-config model.
 *
 * @author Emanuel Muckenhuber
 */
public class DomainTransformers {

    /** Dummy version for ignored subsystems. */
    static final ModelVersion IGNORED_SUBSYSTEMS = ModelVersion.create(-1);

    private static final PathElement JSF_EXTENSION = PathElement.pathElement(ModelDescriptionConstants.EXTENSION, "org.jboss.as.jsf");

    // EAP 6.2.0
    static final ModelVersion VERSION_1_5 = ModelVersion.create(1, 5, 0);
    // EAP 6.3.0
    static final ModelVersion VERSION_1_6 = ModelVersion.create(1, 6, 0);
    // EAP 6.4.0
    static final ModelVersion VERSION_1_7 = ModelVersion.create(1, 7, 0);
    //WF 8.0.0.Final
    static final ModelVersion VERSION_2_0 = ModelVersion.create(2, 0, 0);
    //WF 8.1.0.Final
    static final ModelVersion VERSION_2_1 = ModelVersion.create(2, 1, 0);

    /**
     * Initialize the domain registry.
     *
     * @param registry the domain registry
     */
    public static void initializeDomainRegistry(final TransformerRegistry registry) {
        initializeChainedDomainRegistry(registry);
    }


    private static void initializeChainedDomainRegistry(TransformerRegistry registry) {
        ModelVersion currentVersion = ModelVersion.create(Version.MANAGEMENT_MAJOR_VERSION, Version.MANAGEMENT_MINOR_VERSION, Version.MANAGEMENT_MICRO_VERSION);

        //The chains for transforming will be as follows
        //For JBoss EAP: 3.0.0 -> 1.7.0 -> 1.6.0 -> 1.5.0

        registerRootTransformers(registry);
        registerChainedManagementTransformers(registry, currentVersion);
        registerChainedServerGroupTransformers(registry, currentVersion);
        registerProfileTransformers(registry, currentVersion);
        registerSocketBindingGroupTransformers(registry, currentVersion);
    }

    private static void registerRootTransformers(TransformerRegistry registry) {
        //todo we should use ChainedTransformationDescriptionBuilder but it doesn't work properly with "root" resources
        ResourceTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createInstance(null);
        DomainServerLifecycleHandlers.registerServerLifeCycleOperationsTransformers(builder);
        ModelVersion[] versions = {VERSION_1_5, VERSION_1_6, VERSION_1_7};
        for (ModelVersion version : versions){
            TransformersSubRegistration domain = registry.getDomainRegistration(version);
            TransformationDescription.Tools.register(builder.build(), domain);
        }
    }

    private static void registerChainedManagementTransformers(TransformerRegistry registry, ModelVersion currentVersion) {
        ChainedTransformationDescriptionBuilder builder = ManagementTransformers.buildTransformerChain(currentVersion);
        registerChainedTransformer(registry, builder, VERSION_1_5, VERSION_1_6, VERSION_1_7);
    }

    private static void registerChainedServerGroupTransformers(TransformerRegistry registry, ModelVersion currentVersion) {
        ChainedTransformationDescriptionBuilder builder = ServerGroupTransformers.buildTransformerChain(currentVersion);
        registerChainedTransformer(registry, builder, VERSION_1_5, VERSION_1_6, VERSION_1_7);
    }

    private static void registerProfileTransformers(TransformerRegistry registry, ModelVersion currentVersion) {
        //Do NOT use chained transformers for the profile. The placeholder registry takes precedence over the actual
        //transformer registry, which means we would not get subsystem transformation
        ModelVersion[] versions = new ModelVersion[]{VERSION_1_7, VERSION_1_6, VERSION_1_5};
        for (ModelVersion version : versions) {
            ResourceTransformationDescriptionBuilder builder =
                    ResourceTransformationDescriptionBuilder.Factory.createInstance(ProfileResourceDefinition.PATH);
            builder.getAttributeBuilder()
                    .addRejectCheck(RejectAttributeChecker.DEFINED, ProfileResourceDefinition.INCLUDES)
                    .setDiscard(DiscardAttributeChecker.UNDEFINED, ProfileResourceDefinition.INCLUDES)
                    .end();
            TransformersSubRegistration domain = registry.getDomainRegistration(version);
            TransformationDescription.Tools.register(builder.build(), domain);
        }
    }

    private static void registerSocketBindingGroupTransformers(TransformerRegistry registry, ModelVersion currentVersion) {
        ChainedTransformationDescriptionBuilder builder = SocketBindingGroupTransformers.buildTransformerChain(currentVersion);
        registerChainedTransformer(registry, builder, VERSION_1_7, VERSION_1_6, VERSION_1_5);
    }

    private static void registerChainedTransformer(TransformerRegistry registry, ChainedTransformationDescriptionBuilder builder , ModelVersion...versions) {
        for (Map.Entry<ModelVersion, TransformationDescription> entry : builder.build(versions).entrySet()) {
            TransformersSubRegistration domain = registry.getDomainRegistration(entry.getKey());
            TransformationDescription.Tools.register(entry.getValue(), domain);
        }
    }

    /**
     * Special resource transformer automatically ignoring all subsystems registered by an extension.
     */
    private static class IgnoreExtensionResourceTransformer implements ResourceTransformer {

        private final String[] subsystems;

        private IgnoreExtensionResourceTransformer(String... subsystems) {
            this.subsystems = subsystems;
        }

        @Override
        public void transformResource(final ResourceTransformationContext context, final PathAddress address, final Resource resource) throws OperationFailedException {
            // we just ignore this resource  - so don't add it: context.addTransformedResource(...)

            final TransformationTarget target = context.getTarget();

            if(subsystems != null) {
                for(final String name : subsystems) {
                    target.addSubsystemVersion(name, IGNORED_SUBSYSTEMS);
                }
            }
        }
    }
}
