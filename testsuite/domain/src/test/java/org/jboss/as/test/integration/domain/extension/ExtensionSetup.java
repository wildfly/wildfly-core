/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.extension;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.transform.ExtensionTransformerRegistration;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.management.util.DomainTestUtils;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.as.test.integration.management.extension.blocker.BlockerExtension;
import org.jboss.as.test.integration.management.extension.error.ErrorExtension;
import org.jboss.as.test.integration.management.extension.streams.LogStreamExtension;
import org.jboss.as.test.integration.management.util.MgmtOperationException;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.StreamExporter;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

/**
 * @author Emanuel Muckenhuber
 */
public class ExtensionSetup {

    public static void initializeTestExtension(final DomainTestSupport support) throws IOException {
        // Get module.xml, create modules.jar and add to test config
        final InputStream moduleXml = getModuleXml("module.xml");
        StreamExporter exporter = createResourceRoot(TestExtension.class, ExtensionSetup.class.getPackage(), EmptySubsystemParser.class.getPackage());
        Map<String, StreamExporter> content = Collections.singletonMap("test-extension.jar", exporter);
        support.addTestModule(TestExtension.MODULE_NAME, moduleXml, content);
    }

    public static void initializeHostTestExtension(final DomainTestSupport support) throws IOException {
        // Get module.xml, create modules.jar and add to test config
        final InputStream moduleXml = getModuleXml("host-capable-module.xml");
        StreamExporter exporter = createResourceRoot(TestHostCapableExtension.class, ExtensionSetup.class.getPackage(), EmptySubsystemParser.class.getPackage());
        Map<String, StreamExporter> content = Collections.singletonMap("test-extension.jar", exporter);
        support.addTestModule(TestHostCapableExtension.MODULE_NAME, moduleXml, content);
    }

    public static void addExtensionAndSubsystem(final DomainTestSupport support) throws IOException, MgmtOperationException {
        DomainClient primaryClient = support.getDomainPrimaryLifecycleUtil().getDomainClient();

        ModelNode addExtension = Util.createAddOperation(PathAddress.pathAddress("extension", TestExtension.MODULE_NAME));
        DomainTestUtils.executeForResult(addExtension, primaryClient);

        for (String profileName : new String[]{"profile-a", "profile-b", "profile-shared"}) {
            addExtensionAndSubsystem(primaryClient, profileName);
        }
    }


    private static void addExtensionAndSubsystem(final DomainClient primaryClient, String profileName) throws IOException, MgmtOperationException {

        PathAddress profileAddress = PathAddress.pathAddress("profile", profileName);

        PathAddress subsystemAddress = profileAddress.append("subsystem", "1");

        ModelNode addSubsystem = Util.createAddOperation(subsystemAddress);
        addSubsystem.get("name").set("dummy name");
        DomainTestUtils.executeForResult(addSubsystem, primaryClient);

        ModelNode addResource = Util.createAddOperation(subsystemAddress.append("rbac-sensitive","other"));
        DomainTestUtils.executeForResult(addResource, primaryClient);

        addResource = Util.createAddOperation(subsystemAddress.append("rbac-constrained","default"));
        addResource.get("password").set("sa");
        addResource.get("security-domain").set("other");
        DomainTestUtils.executeForResult(addResource, primaryClient);
    }

    public static void initializeTransformersExtension(final DomainTestSupport support) throws IOException {

        // secondary - version1
        InputStream moduleXml = getModuleXml("transformers-module.xml");
        final StreamExporter version1 = createResourceRoot(VersionedExtension1.class, ExtensionSetup.class.getPackage(), EmptySubsystemParser.class.getPackage());
        Map<String, StreamExporter> v1 = Collections.singletonMap("transformers-extension.jar", version1);
        support.addOverrideModule("secondary", VersionedExtensionCommon.EXTENSION_NAME, moduleXml, v1);

        // primary - version2
        moduleXml = getModuleXml("transformers-module.xml");
        final StreamExporter version2 = createResourceRoot(VersionedExtension2.class, VersionedExtension2.TransformerRegistration.class, ExtensionSetup.class.getPackage());
        Map<String, StreamExporter> v2 = Collections.singletonMap("transformers-extension.jar", version2);
        support.addOverrideModule("primary", VersionedExtensionCommon.EXTENSION_NAME, moduleXml, v2);

    }

    public static void initializeBlockerExtension(final DomainTestSupport support) throws IOException {
        // Get module.xml, create modules.jar and add to test config
        final InputStream moduleXml = getModuleXml("blocker-module.xml");
        StreamExporter exporter = createResourceRoot(BlockerExtension.class, EmptySubsystemParser.class.getPackage());
        Map<String, StreamExporter> content = Collections.singletonMap("blocker-extension.jar", exporter);
        support.addTestModule(BlockerExtension.MODULE_NAME, moduleXml, content);
    }

    public static void initializeErrorExtension(final DomainTestSupport support) throws IOException {
        // Get module.xml, create modules.jar and add to test config
        final InputStream moduleXml = getModuleXml("error-module.xml");
        StreamExporter exporter = createResourceRoot(ErrorExtension.class, EmptySubsystemParser.class.getPackage());
        Map<String, StreamExporter> content = Collections.singletonMap("error-extension.jar", exporter);
        support.addTestModule(ErrorExtension.MODULE_NAME, moduleXml, content);
    }

    public static void initializeLogStreamExtension(final DomainTestSupport support) throws IOException {
        // Get module.xml, create modules.jar and add to test config
        final InputStream moduleXml = getModuleXml("log-stream-module.xml");
        StreamExporter exporter = createResourceRoot(LogStreamExtension.class, EmptySubsystemParser.class.getPackage());
        Map<String, StreamExporter> content = Collections.singletonMap("log-stream-extension.jar", exporter);
        support.addTestModule(LogStreamExtension.MODULE_NAME, moduleXml, content);
    }

    public static void initializeOrderedChildResourceExtension(final DomainTestSupport support) throws IOException {
        // Get module.xml, create modules.jar and add to test config
        final InputStream moduleXml = getModuleXml("ordered-child-resource-module.xml");
        StreamExporter exporter = createResourceRoot(OrderedChildResourceExtension.class);
        Map<String, StreamExporter> content = Collections.singletonMap("ordered-child-resource-extension.jar", exporter);
        support.addTestModule(OrderedChildResourceExtension.MODULE_NAME, moduleXml, content);
    }

    public static void initialiseProfileIncludesExtension(final DomainTestSupport support) throws IOException {
        final InputStream moduleXml = getModuleXml("profile-includes-module.xml");
        StreamExporter exporter = createResourceRoot(ProfileIncludesExtension.class, EmptySubsystemParser.class.getPackage());
        Map<String, StreamExporter> content = Collections.singletonMap("profile-includes-extension.jar", exporter);
        support.addTestModule(ProfileIncludesExtension.MODULE_NAME, moduleXml, content);
    }

    public static void initializeTestAliasReadResourceAddressExtension(final DomainTestSupport support) throws IOException {
        final InputStream moduleXml = getModuleXml("test-read-resource-description-alias-address-module.xml");
        StreamExporter exporter = createResourceRoot(TestAliasReadResourceDescriptionAddressExtension.class, EmptySubsystemParser.class.getPackage());
        Map<String, StreamExporter> content = Collections.singletonMap("test-alias-read-resource-description-address.jar", exporter);
        support.addTestModule(TestAliasReadResourceDescriptionAddressExtension.MODULE_NAME, moduleXml, content);
    }

    static StreamExporter createResourceRoot(Class<? extends Extension> extension, Package... additionalPackages) throws IOException {
        return createResourceRoot(extension, null, additionalPackages);
    }

    static StreamExporter createResourceRoot(Class<? extends Extension> extension, Class<? extends ExtensionTransformerRegistration> transformerRegistration,
                                             Package... additionalPackages) throws IOException {
        final JavaArchive archive = ShrinkWrap.create(JavaArchive.class);
        archive.addPackage(extension.getPackage());
        if (additionalPackages != null) {
            for (Package pkg : additionalPackages) {
                archive.addPackage(pkg);
            }
        }
        archive.addAsServiceProvider(Extension.class, extension);
        if (transformerRegistration != null) {
            archive.addAsServiceProvider(ExtensionTransformerRegistration.class, transformerRegistration);
        }
        return archive.as(ZipExporter.class);
    }

    static InputStream getModuleXml(final String name) {
        // Get the module xml
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        return tccl.getResourceAsStream("extension/" + name);
    }

}
