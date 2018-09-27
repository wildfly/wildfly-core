/*
Copyright 2016 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.domain.controller.resources;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_EXCLUDE;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
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
import org.jboss.as.host.controller.mgmt.DomainHostExcludeRegistry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry.ResourceDefinition} for
 * the {@code host-ignores} resources in the domain wide model.
 *
 * @author Brian Stansberry
 */
public class HostExcludeResourceDefinition extends SimpleResourceDefinition {

    public static final PathElement PATH_ELEMENT = PathElement.pathElement(HOST_EXCLUDE);

    private enum KnownRelease {
        EAP62("EAP6.2", 1, 5),
        EAP63("EAP6.3", 1, 6),
        EAP64("EAP6.4", 1, 7),
        EAP70("EAP7.0", 4, 1),
        EAP71("EAP7.1", 5, 0),
        WILDFLY10("WildFly10.0", 4, 0),
        WILDFLY10_1("WildFly10.1", 4, 2),
        WILDFLY11("WildFly11.0", 5, 0),
        WILDFLY12("WildFly12.0", 6, 0),
        WILDFLY13("WildFly13.0", 7, 0),
        WILDFLY14("WildFly14.0", 8, 0);


        private static final Map<String, KnownRelease> map = new HashMap<>();
        static {
            for (KnownRelease kr : values()) {
                map.put(kr.toString().toUpperCase(Locale.ENGLISH), kr);
            }
        }

        private static KnownRelease fromName(String name) {
            KnownRelease kr = map.get(name.toUpperCase(Locale.ENGLISH));
            if (kr == null) {
                throw new IllegalArgumentException(name);
            }
            return kr;
        }

        private final String name;
        private final int major;
        private final int minor;

        KnownRelease(String name, int major, int minor) {
            this.name = name;
            this.major = major;
            this.minor = minor;
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
                    .setValidator(EnumValidator.create(KnownRelease.class, false, false))
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
        OperationStepHandler handler = new WriteHandler(domainHostExcludeRegistry, attributes);
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
            return new DomainHostExcludeRegistry.VersionKey(kr.major, kr.minor, null);
        } else {
            int major = MANAGEMENT_MAJOR_VERSION.resolveModelAttribute(context, model).asInt();
            int minor = MANAGEMENT_MINOR_VERSION.resolveModelAttribute(context, model).asInt();
            ModelNode micro = MANAGEMENT_MICRO_VERSION.resolveModelAttribute(context, model);
            if (micro.isDefined()) {
                return new DomainHostExcludeRegistry.VersionKey(major, minor, micro.asInt());
            } else {
                return new DomainHostExcludeRegistry.VersionKey(major, minor, null);
            }
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

        private WriteHandler(DomainHostExcludeRegistry domainHostExcludeRegistry, AttributeDefinition... attributes) {
            super(attributes);
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
