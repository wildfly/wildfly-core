/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.resources;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_EXCLUDE;
import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.transformers.KernelAPIVersion;
import org.jboss.as.host.controller.mgmt.DomainHostExcludeRegistry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for
 * the {@code host-exclude} resources in the domain wide model.
 *
 * @author Brian Stansberry
 */
public class HostExcludeResourceDefinition extends SimpleResourceDefinition {

    public static final PathElement PATH_ELEMENT = PathElement.pathElement(HOST_EXCLUDE);

    private enum KnownRelease {
        EAP62("EAP6.2", KernelAPIVersion.VERSION_1_5),
        EAP63("EAP6.3", KernelAPIVersion.VERSION_1_6),
        EAP64("EAP6.4", KernelAPIVersion.VERSION_1_7),
        EAP70("EAP7.0", KernelAPIVersion.VERSION_4_1),
        EAP71("EAP7.1", KernelAPIVersion.VERSION_5_0),
        EAP72("EAP7.2", KernelAPIVersion.VERSION_8_0),
        EAP73("EAP7.3", KernelAPIVersion.VERSION_10_0),
        EAP74("EAP7.4", KernelAPIVersion.VERSION_16_0),
        EAP80("EAP8.0", KernelAPIVersion.VERSION_21_0),
        WILDFLY10("WildFly10.0", KernelAPIVersion.VERSION_4_0),
        WILDFLY10_1("WildFly10.1", KernelAPIVersion.VERSION_4_2),
        WILDFLY11("WildFly11.0", KernelAPIVersion.VERSION_5_0),
        WILDFLY12("WildFly12.0", KernelAPIVersion.VERSION_6_0),
        WILDFLY13("WildFly13.0", KernelAPIVersion.VERSION_7_0),
        WILDFLY14("WildFly14.0", KernelAPIVersion.VERSION_8_0),
        WILDFLY15("WildFly15.0", KernelAPIVersion.VERSION_9_0),
        WILDFLY16("WildFly16.0", KernelAPIVersion.VERSION_10_0),
        WILDFLY17("WildFly17.0", KernelAPIVersion.VERSION_10_0),
        WILDFLY18("WildFly18.0", KernelAPIVersion.VERSION_10_0),
        WILDFLY19("WildFly19.0", KernelAPIVersion.VERSION_12_0),
        WILDFLY20("WildFly20.0", KernelAPIVersion.VERSION_13_0),
        WILDFLY21("WildFly21.0", KernelAPIVersion.VERSION_14_0),
        WILDFLY22("WildFly22.0", KernelAPIVersion.VERSION_15_0),
        WILDFLY23("WildFly23.0", KernelAPIVersion.VERSION_16_0),
        WILDFLY24("WildFly24.0", KernelAPIVersion.VERSION_17_0),
        WILDFLY25("WildFly25.0", KernelAPIVersion.VERSION_18_0),
        WILDFLY26("WildFly26.0", KernelAPIVersion.VERSION_19_0),
        WILDFLY27("WildFly27.0", KernelAPIVersion.VERSION_20_0),
        WILDFLY28("WildFly28.0", KernelAPIVersion.VERSION_21_0),
        WILDFLY29("WildFly29.0", KernelAPIVersion.VERSION_22_0),
        WILDFLY30("WildFly30.0", KernelAPIVersion.VERSION_23_0),
        WILDFLY31("WildFly31.0", KernelAPIVersion.VERSION_24_0),
        WILDFLY32("WildFly32.0", KernelAPIVersion.VERSION_25_0);

        private static final Map<String, KnownRelease> map = new HashMap<>();
        static {
            for (KnownRelease kr : values()) {
                map.put(kr.toString().toUpperCase(Locale.ENGLISH), kr);
            }
        }

        private static KnownRelease fromName(String name) {
            checkNotNullParam("name", name);
            return checkNotNullParam(name, map.get(name.toUpperCase(Locale.ENGLISH)));
        }

        private final String name;
        private final KernelAPIVersion kernelAPIVersion;

        KnownRelease(String name, KernelAPIVersion kernelAPIVersion) {
            this.name = name;
            this.kernelAPIVersion = kernelAPIVersion;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static final SimpleAttributeDefinition HOST_RELEASE =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.HOST_RELEASE, ModelType.STRING, false)
                    .setXmlName("id")
                    .setAlternatives(ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION)
                    .setValidator(EnumValidator.create(KnownRelease.class))
                    .build();

    public static final SimpleAttributeDefinition MANAGEMENT_MAJOR_VERSION =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION, ModelType.INT, false)
                    .setXmlName("major-version")
                    .setAlternatives(ModelDescriptionConstants.HOST_RELEASE)
                    .setRequires(ModelDescriptionConstants.MANAGEMENT_MINOR_VERSION)
                    .build();

    public static final SimpleAttributeDefinition MANAGEMENT_MINOR_VERSION =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.MANAGEMENT_MINOR_VERSION, ModelType.INT)
                    .setRequired(false)
                    .setXmlName("minor-version")
                    .setAlternatives(ModelDescriptionConstants.HOST_RELEASE)
                    .setRequires(ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION)
                    .build();

    public static final SimpleAttributeDefinition MANAGEMENT_MICRO_VERSION =
            SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.MANAGEMENT_MICRO_VERSION, ModelType.INT)
                    .setRequired(false)
                    .setXmlName("micro-version")
                    .setAlternatives(ModelDescriptionConstants.HOST_RELEASE)
                    .setRequires(ModelDescriptionConstants.MANAGEMENT_MAJOR_VERSION, ModelDescriptionConstants.MANAGEMENT_MINOR_VERSION)
                    .build();

    public static final AttributeDefinition EXCLUDED_EXTENSIONS = new StringListAttributeDefinition.Builder(ModelDescriptionConstants.EXCLUDED_EXTENSIONS)
            .setRequired(false)
            .build();

    public static final AttributeDefinition ACTIVE_SERVER_GROUPS = new StringListAttributeDefinition.Builder(ModelDescriptionConstants.ACTIVE_SERVER_GROUPS)
            .setRequired(false)
            .build();

    public static final AttributeDefinition ACTIVE_SOCKET_BINDING_GROUPS = new StringListAttributeDefinition.Builder(ModelDescriptionConstants.ACTIVE_SOCKET_BINDING_GROUPS)
            .setRequired(false)
            .build();

    private static final AttributeDefinition[] attributes = { MANAGEMENT_MAJOR_VERSION, MANAGEMENT_MINOR_VERSION,
            MANAGEMENT_MICRO_VERSION, HOST_RELEASE, EXCLUDED_EXTENSIONS, ACTIVE_SERVER_GROUPS, ACTIVE_SOCKET_BINDING_GROUPS};

    private final DomainHostExcludeRegistry domainHostExcludeRegistry;

    public HostExcludeResourceDefinition(DomainHostExcludeRegistry domainHostExcludeRegistry) {
        super(PATH_ELEMENT, DomainResolver.getResolver(HOST_EXCLUDE, false),
                new AddHandler(domainHostExcludeRegistry), new RemoveHandler(domainHostExcludeRegistry));
        this.domainHostExcludeRegistry = domainHostExcludeRegistry;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        OperationStepHandler handler = new WriteHandler(domainHostExcludeRegistry);
        for (AttributeDefinition ad : attributes) {
            resourceRegistration.registerReadWriteAttribute(ad, null, handler);
        }
    }

    private static void registerHostExcludes(OperationContext context, ModelNode model, DomainHostExcludeRegistry registry) throws OperationFailedException {
        DomainHostExcludeRegistry.VersionKey versionKey = getVersionKey(context, model);
        Set<String> ignoredExtensions = null;
        Set<String> activeServerGroups = null;
        Set<String> activeSocketBindingGroups = null;

        ModelNode ie = EXCLUDED_EXTENSIONS.resolveModelAttribute(context, model);
        if (ie.isDefined() && ie.asInt() > 0) {
            ignoredExtensions = new HashSet<>();
            for (ModelNode node : ie.asList()) {
                ignoredExtensions.add(node.asString());
            }
        }

        ModelNode asg = ACTIVE_SERVER_GROUPS.resolveModelAttribute(context, model);
        if (asg.isDefined() && asg.asInt() > 0) {
            activeServerGroups = new HashSet<>();
            for (ModelNode node : asg.asList()) {
                activeServerGroups.add(node.asString());
            }
        }

        ModelNode asbg = ACTIVE_SOCKET_BINDING_GROUPS.resolveModelAttribute(context, model);
        if (asbg.isDefined() && asbg.asInt() > 0) {
            activeSocketBindingGroups = new HashSet<>();
            for (ModelNode node : asbg.asList()) {
                activeSocketBindingGroups.add(node.asString());
            }
        }

        registry.recordVersionExcludeData(versionKey, ignoredExtensions, activeServerGroups, activeSocketBindingGroups);
    }

    private static DomainHostExcludeRegistry.VersionKey getVersionKey(OperationContext context, ModelNode model) throws OperationFailedException {
        ModelNode release = HOST_RELEASE.resolveModelAttribute(context, model);
        if (release.isDefined()) {
            KnownRelease kr = KnownRelease.fromName(release.asString());
            ModelVersion modelVersion = kr.kernelAPIVersion.getModelVersion();
            return new DomainHostExcludeRegistry.VersionKey(modelVersion.getMajor(), modelVersion.getMinor(), modelVersion.getMicro());
        } else {
            int major = MANAGEMENT_MAJOR_VERSION.resolveModelAttribute(context, model).asInt();
            int minor = MANAGEMENT_MINOR_VERSION.resolveModelAttribute(context, model).asInt();
            ModelNode micro = MANAGEMENT_MICRO_VERSION.resolveModelAttribute(context, model);
            return new DomainHostExcludeRegistry.VersionKey(major, minor, micro.asIntOrNull());
        }
    }

    private static final class AddHandler extends AbstractAddStepHandler {

        private final DomainHostExcludeRegistry domainHostExcludeRegistry;

        private AddHandler(DomainHostExcludeRegistry domainHostExcludeRegistry) {
            super(HostExcludeResourceDefinition.attributes);
            this.domainHostExcludeRegistry = domainHostExcludeRegistry;
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
            registerHostExcludes(context, resource.getModel(), domainHostExcludeRegistry);
        }

        @Override
        protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {
            try {
                DomainHostExcludeRegistry.VersionKey versionKey = getVersionKey(context, resource.getModel());
                domainHostExcludeRegistry.removeVersionExcludeData(versionKey);
            } catch (OperationFailedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final class RemoveHandler extends AbstractRemoveStepHandler {

        private final DomainHostExcludeRegistry domainHostExcludeRegistry;

        private RemoveHandler(DomainHostExcludeRegistry domainHostExcludeRegistry) {
            this.domainHostExcludeRegistry = domainHostExcludeRegistry;
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            DomainHostExcludeRegistry.VersionKey versionKey = getVersionKey(context, model);
            domainHostExcludeRegistry.removeVersionExcludeData(versionKey);
        }

        @Override
        protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            registerHostExcludes(context, model, domainHostExcludeRegistry);
        }
    }

    private static final class WriteHandler extends AbstractWriteAttributeHandler<Void> {

        private final DomainHostExcludeRegistry domainHostExcludeRegistry;

        private WriteHandler(DomainHostExcludeRegistry domainHostExcludeRegistry) {
            this.domainHostExcludeRegistry = domainHostExcludeRegistry;
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }

        @Override
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> handbackHolder) throws OperationFailedException {
            registerHostExcludes(context, context.readResource(PathAddress.EMPTY_ADDRESS).getModel(), domainHostExcludeRegistry);
            return false;
        }

        @Override
        protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {
            registerHostExcludes(context, context.readResource(PathAddress.EMPTY_ADDRESS).getModel(), domainHostExcludeRegistry);
        }
    }
}
