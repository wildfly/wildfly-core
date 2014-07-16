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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;

import java.util.Map;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.extension.SubsystemInformation;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathResourceDefinition;
import org.jboss.as.controller.transform.ResourceTransformationContext;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.controller.transform.TransformationTarget;
import org.jboss.as.controller.transform.TransformerRegistry;
import org.jboss.as.controller.transform.TransformersSubRegistration;
import org.jboss.as.controller.transform.description.AttributeConverter;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.TransformationDescription;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
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

    //AS 7.1.2.Final / EAP 6.0.0
    static final ModelVersion VERSION_1_2 = ModelVersion.create(1, 2, 0);
    //AS 7.1.3.Final / EAP 6.0.1
    static final ModelVersion VERSION_1_3 = ModelVersion.create(1, 3, 0);
    //AS 7.2.0.Final / EAP 6.1.0 / EAP 6.1.1
    static final ModelVersion VERSION_1_4 = ModelVersion.create(1, 4, 0);
    // EAP 6.2.0
    static final ModelVersion VERSION_1_5 = ModelVersion.create(1, 5, 0);
    // EAP 6.3.0
    static final ModelVersion VERSION_1_6 = ModelVersion.create(1, 6, 0);
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
        //For WildFly: 3.0.0 -> 2.1.0 -> 2.0.0
        //For JBoss EAP and AS 7 releases: 3.0.0 -> 1.6.0 -> 1.5.0 -> 1.4.0 -> 1.3.0 -> 1.2.0

        registerChainedManagementTransformers(registry, currentVersion);
        registerChainedPathsTransformers(registry, currentVersion);
        registerChainedDeploymentTransformers(registry, currentVersion);
        registerChainedSystemPropertyTransformers(registry, currentVersion);
        registerChainedSocketBindingGroupTransformers(registry, currentVersion);
        registerChainedServerGroupTransformers(registry, currentVersion);
        registerChainedInterfaceTransformers(registry, currentVersion);

        registerJsfTransformers(registry, VERSION_1_2, VERSION_1_3);
    }

    private static void registerChainedManagementTransformers(TransformerRegistry registry, ModelVersion currentVersion) {
        ChainedTransformationDescriptionBuilder builder = ManagementTransformers.buildTransformerChain(currentVersion);
        registerChainedTransformer(registry, builder, VERSION_1_2, VERSION_1_3, VERSION_1_4);
    }

    private static void registerChainedPathsTransformers(TransformerRegistry registry, ModelVersion currentVersion) {
        ChainedTransformationDescriptionBuilder builder = PathsTransformers.buildTransformerChain(currentVersion);
        registerChainedTransformer(registry, builder, VERSION_1_2, VERSION_1_3);
    }

    private static void registerChainedDeploymentTransformers(TransformerRegistry registry, ModelVersion currentVersion) {
        ChainedTransformationDescriptionBuilder builder = DeploymentTransformers.buildTransformerChain(currentVersion);
        registerChainedTransformer(registry, builder, VERSION_1_2, VERSION_1_3);
    }

    private static void registerChainedServerGroupTransformers(TransformerRegistry registry, ModelVersion currentVersion) {
        ChainedTransformationDescriptionBuilder builder = ServerGroupTransformers.buildTransformerChain(currentVersion);
        registerChainedTransformer(registry, builder, VERSION_1_2, VERSION_1_3, VERSION_1_4);

        registerChainedTransformer(registry, builder, VERSION_2_0, VERSION_2_1);
    }

    private static void registerChainedSystemPropertyTransformers(TransformerRegistry registry, ModelVersion currentVersion) {
        ChainedTransformationDescriptionBuilder builder = SystemPropertyTransformers.buildTransformerChain(currentVersion);
        registerChainedTransformer(registry, builder, VERSION_1_2, VERSION_1_3);
    }

    private static void registerChainedInterfaceTransformers(TransformerRegistry registry, ModelVersion currentVersion) {
        ChainedTransformationDescriptionBuilder builder = TransformationDescriptionBuilder.Factory.createChainedInstance(PathElement.pathElement(INTERFACE), currentVersion);
        builder.createBuilder(currentVersion, DomainTransformers.VERSION_1_3)
            .getAttributeBuilder()
               .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, PathResourceDefinition.PATH)
               .setValueConverter(AttributeConverter.NAME_FROM_ADDRESS, ModelDescriptionConstants.NAME)
               .end()
           .addOperationTransformationOverride(ADD)
               .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, PathResourceDefinition.PATH)
               .end();

        registerChainedTransformer(registry, builder, VERSION_1_2, VERSION_1_3);
    }

    private static void registerChainedSocketBindingGroupTransformers(TransformerRegistry registry, ModelVersion currentVersion) {
        ChainedTransformationDescriptionBuilder builder = SocketBindingGroupTransformers.buildTransformerChain(currentVersion);
        registerChainedTransformer(registry, builder, VERSION_1_2, VERSION_1_3);
    }

    private static void registerChainedTransformer(TransformerRegistry registry, ChainedTransformationDescriptionBuilder builder , ModelVersion...versions) {
        for (Map.Entry<ModelVersion, TransformationDescription> entry : builder.build(versions).entrySet()) {
            TransformersSubRegistration domain = registry.getDomainRegistration(entry.getKey());
            TransformationDescription.Tools.register(entry.getValue(), domain);
        }
    }

    private static void registerJsfTransformers(TransformerRegistry registry, ModelVersion...versions) {
        //This is not using a chained transformer, not sure it is worth the hassle of figuring out what is going on :-)
        for (ModelVersion version : versions) {
            TransformersSubRegistration domain = registry.getDomainRegistration(version);
            // Discard all operations to the newly introduced jsf extension
            domain.registerSubResource(JSF_EXTENSION, IGNORED_EXTENSIONS);
            JSFSubsystemTransformers.registerTransformers120(registry, domain);
        }
    }

    private static final ResourceTransformer IGNORED_EXTENSIONS = new IgnoreExtensionResourceTransformer();

    /**
     * Special resource transformer automatically ignoring all subsystems registered by an extension.
     */
    private static class IgnoreExtensionResourceTransformer implements ResourceTransformer {

        @Override
        public void transformResource(final ResourceTransformationContext context, final PathAddress address, final Resource resource) throws OperationFailedException {
            // we just ignore this resource  - so don't add it: context.addTransformedResource(...)
            final PathElement element = address.getLastElement();

            final TransformationTarget target = context.getTarget();
            final ExtensionRegistry registry = target.getExtensionRegistry();

            final Map<String, SubsystemInformation> subsystems = registry.getAvailableSubsystems(element.getValue());
            if(subsystems != null) {
                for(final Map.Entry<String, SubsystemInformation> subsystem : subsystems.entrySet()) {
                    final String name = subsystem.getKey();
                    target.addSubsystemVersion(name, IGNORED_SUBSYSTEMS);
                }
            }
        }
    }
}
