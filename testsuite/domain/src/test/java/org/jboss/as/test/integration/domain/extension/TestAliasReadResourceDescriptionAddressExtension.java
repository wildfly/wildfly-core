/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.extension;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.AliasEntry;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;

/**
 * Fake extension to use in testing extension management.
 *
 * @author Kabir Khan
 */
public class TestAliasReadResourceDescriptionAddressExtension implements Extension {

    public static final String MODULE_NAME = "org.wildfly.extension.test-alias-read-resource-description-address";
    public static final String SUBSYSTEM_NAME = "test-alias-read-resource-description-address";
    public static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME);
    public static final PathElement THING_PATH = PathElement.pathElement("thing");
    public static final PathElement SINGLETON_PATH = PathElement.pathElement("singleton", "one");
    public static final PathElement SINGLETON_ALIAS_PATH = PathElement.pathElement("singleton-alias", "uno");
    public static final PathElement WILDCARD_PATH = PathElement.pathElement("wildcard");
    public static final PathElement WILDCARD_ALIAS_PATH = PathElement.pathElement("wildcard-alias");

    private static final String NAMESPACE = "urn:jboss:test:extension:test:alias:read:resource:description:address:1.0";
    private static final EmptySubsystemParser PARSER = new EmptySubsystemParser(NAMESPACE);

    @Override
    public void initialize(ExtensionContext context) {
        SubsystemRegistration reg = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(1, 1, 1));
        reg.registerXMLElementWriter(PARSER);
        reg.registerSubsystemModel(new SubsystemResourceDefinition());
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE, PARSER);
    }

    private static class AbstractResourceDefinition extends SimpleResourceDefinition {
        public AbstractResourceDefinition(PathElement pathElement) {
            super(pathElement,
                    NonResolvingResourceDescriptionResolver.INSTANCE,
                    ModelOnlyAddStepHandler.INSTANCE,
                    ModelOnlyRemoveStepHandler.INSTANCE);
        }
    }

    private static class SubsystemResourceDefinition extends AbstractResourceDefinition {
        public SubsystemResourceDefinition() {
            super(SUBSYSTEM_PATH);
        }

        @Override
        public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerSubModel(new ThingResourceDefinition());
        }
    }

    private static class ThingResourceDefinition extends AbstractResourceDefinition {
        public ThingResourceDefinition() {
            super(THING_PATH);
        }

        @Override
        public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            ManagementResourceRegistration singleton = resourceRegistration.registerSubModel(new SingletonResourceDefinition());
            ManagementResourceRegistration wildcard = resourceRegistration.registerSubModel(new WildcardResourceDefinition());

            resourceRegistration.registerAlias(SINGLETON_ALIAS_PATH, new AliasEntry(singleton) {
                @Override
                public PathAddress convertToTargetAddress(PathAddress address, AliasContext aliasContext) {
                    List<PathElement> list = new ArrayList<>();
                    final PathElement alias = getAliasAddress().getLastElement();
                    for (PathElement element : address) {
                        if (element.equals(alias)) {
                            list.add(getTargetAddress().getLastElement());
                        } else {
                            list.add(element);
                        }
                    }
                    return PathAddress.pathAddress(list);
                }
            });

            resourceRegistration.registerAlias(WILDCARD_ALIAS_PATH, new AliasEntry(wildcard) {
                @Override
                public PathAddress convertToTargetAddress(PathAddress address, AliasContext aliasContext) {
                    List<PathElement> list = new ArrayList<>();
                    final PathElement alias = getAliasAddress().getLastElement();
                    for (PathElement element : address) {
                        if (element.getKey().equals(alias.getKey())) {
                            list.add(PathElement.pathElement(getTargetAddress().getLastElement().getKey(), element.getValue()));
                        } else {
                            list.add(element);
                        }
                    }
                    return PathAddress.pathAddress(list);
                }
            });
        }
    }

    private static class SingletonResourceDefinition extends AbstractResourceDefinition {
        public SingletonResourceDefinition() {
            super(SINGLETON_PATH);
        }
    }

    private static class WildcardResourceDefinition extends AbstractResourceDefinition {
        public WildcardResourceDefinition() {
            super(WILDCARD_PATH);
        }
    }
}
