/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.core.management;

import org.jboss.as.controller.Feature;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentSubsystemSchema;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.as.version.Stability;
import org.jboss.staxmapper.IntVersion;

import java.util.EnumSet;
import java.util.Map;

/**
 * Parser and Marshaller for the core-management subsystem.
 * <p/>
 * <em>All resources and attributes must be listed explicitly and not through any collections.</em>
 * This ensures that if the resource definitions change in later version (e.g. a new attribute is added),
 * this will have no impact on parsing this specific version of the subsystem.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat inc.
 */
public enum CoreManagementSubsystemSchema implements PersistentSubsystemSchema<CoreManagementSubsystemSchema> {

    VERSION_1_0(1),
    VERSION_1_0_PREVIEW(1, Stability.PREVIEW),
    VERSION_2_0_PREVIEW(2, Stability.PREVIEW);
    static final Map<Stability, CoreManagementSubsystemSchema> CURRENT = Feature.map(EnumSet.of(VERSION_1_0, VERSION_2_0_PREVIEW));

    private final VersionedNamespace<IntVersion, CoreManagementSubsystemSchema> namespace;

    CoreManagementSubsystemSchema(int major) {
        this.namespace = SubsystemSchema.createLegacySubsystemURN(CoreManagementExtension.SUBSYSTEM_NAME, new IntVersion(major, 0));
    }

    CoreManagementSubsystemSchema(int major, Stability stability) {
        this.namespace = SubsystemSchema.createLegacySubsystemURN(CoreManagementExtension.SUBSYSTEM_NAME, stability, new IntVersion(major, 0));
    }

    @Override
    public VersionedNamespace<IntVersion, CoreManagementSubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public PersistentResourceXMLDescription getXMLDescription() {
        PersistentResourceXMLDescription.Factory factory = PersistentResourceXMLDescription.factory(this);
        PersistentResourceXMLDescription.Builder builder =  factory.builder(CoreManagementExtension.SUBSYSTEM_PATH);
        builder.addChild(
                factory.builder(ConfigurationChangeResourceDefinition.PATH)
                        .addAttribute(ConfigurationChangeResourceDefinition.MAX_HISTORY)
                        .build());
        builder.addChild(
                factory.builder(UnstableApiAnnotationResourceDefinition.RESOURCE_REGISTRATION)
                        .addAttribute(UnstableApiAnnotationResourceDefinition.LEVEL)
                        .build());
        builder.addChild(
                factory.builder(CoreManagementExtension.PROCESS_STATE_LISTENER_PATH)

                        .addAttribute(ProcessStateListenerResourceDefinition.LISTENER_CLASS)
                        .addAttribute(ProcessStateListenerResourceDefinition.LISTENER_MODULE)
                        .addAttribute(ProcessStateListenerResourceDefinition.PROPERTIES)
                        .addAttribute(ProcessStateListenerResourceDefinition.TIMEOUT)
                        .build());
        builder.addChild(
                factory.builder(VirtualThreadPinningResourceDefinition.RESOURCE_REGISTRATION)
                        .addAttribute(VirtualThreadPinningResourceDefinition.START_MODE)
                        .addAttribute(VirtualThreadPinningResourceDefinition.LOG_LEVEL)
                        .addAttribute(VirtualThreadPinningResourceDefinition.MAX_STACK_DEPTH)
                        .build()
        );
        return builder.build();
    }
}
