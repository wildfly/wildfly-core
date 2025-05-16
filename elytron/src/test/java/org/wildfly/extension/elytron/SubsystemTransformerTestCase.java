/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.model.test.FailedOperationTransformationConfig.REJECTED_RESOURCE;
import static org.jboss.as.model.test.ModelTestControllerVersion.EAP_7_4_0;
import static org.jboss.as.model.test.ModelTestControllerVersion.EAP_8_0_0;
import static org.jboss.as.model.test.ModelTestControllerVersion.EAP_8_1_0;
import static org.jboss.as.model.test.ModelTestControllerVersion.WILDFLY_31_0_0;
import static org.junit.Assert.assertTrue;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.TRUST_MANAGER;

import java.io.IOException;
import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.model.test.FailedOperationTransformationConfig;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests of transformation of the elytron subsystem to previous API versions.
 *
 * @author Brian Stansberry
 * @author Tomaz Cerar
 */
public class SubsystemTransformerTestCase extends AbstractElytronSubsystemBaseTest {

    private static final PathAddress SUBSYSTEM_ADDRESS = PathAddress.pathAddress(ModelDescriptionConstants.SUBSYSTEM, ElytronExtension.SUBSYSTEM_NAME);

    public SubsystemTransformerTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("elytron-transformers-13.0.xml");
    }

    protected String getSubsystemXml(final String subsystemFile) throws IOException {
        return readResource(subsystemFile);
    }

    /**
     * Test case testing resources and attributes are appropriately rejected when transforming to EAP 7.4.
     */
    @Test
    public void testRejectingTransformersEAP740() throws Exception {
        testRejectingTransformers(EAP_7_4_0, "elytron-transformers-13.0-reject.xml", new FailedOperationTransformationConfig()
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.SERVER_SSL_SNI_CONTEXT, "SNIwithCaret")),
                        new FailedOperationTransformationConfig.NewAttributesConfig(ElytronDescriptionConstants.HOST_CONTEXT_MAP)
                )
            .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.DYNAMIC_CLIENT_SSL_CONTEXT, "dcsc")),
                    FailedOperationTransformationConfig.REJECTED_RESOURCE)
            .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.PROPERTIES_REALM, "PropertiesRealmEncodingCharset")),
                    FailedOperationTransformationConfig.REJECTED_RESOURCE)
            .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.FILESYSTEM_REALM, "FilesystemRealmEncodingCharset")),
                    FailedOperationTransformationConfig.REJECTED_RESOURCE)
            .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.FILESYSTEM_REALM, "FilesystemRealmEncrypted")),
                    FailedOperationTransformationConfig.REJECTED_RESOURCE)
            .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.FILESYSTEM_REALM, "FilesystemRealmIntegrity")),
                    FailedOperationTransformationConfig.REJECTED_RESOURCE)
            .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.JDBC_REALM, "JDBCRealmCharset")),
                    FailedOperationTransformationConfig.REJECTED_RESOURCE)
            .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.LDAP_REALM, "LDAPRealmEncodingCharset")),
                    FailedOperationTransformationConfig.REJECTED_RESOURCE)
            .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.DISTRIBUTED_REALM, "DistributedRealmFirstUnavailableIgnoredEventEmitted")),
                    FailedOperationTransformationConfig.REJECTED_RESOURCE)
            .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(TRUST_MANAGER, "TrustManagerCrls")), REJECTED_RESOURCE)
            .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.SERVER_SSL_CONTEXT, "ctxSSLv2Hello")),
                    REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.CLIENT_SSL_CONTEXT, "ClientContextSSLv2Hello")),
                        REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.JAAS_REALM, "myJaasRealm")), REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.VIRTUAL_SECURITY_DOMAIN, "myVirtualDomain")), REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.SECURITY_DOMAIN, "myDomain")), REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.FILE_AUDIT_LOG)),
                    new FailedOperationTransformationConfig.NewAttributesConfig(AuditResourceDefinitions.ENCODING))
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.PERIODIC_ROTATING_FILE_AUDIT_LOG)),
                    new FailedOperationTransformationConfig.NewAttributesConfig(AuditResourceDefinitions.ENCODING))
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.SIZE_ROTATING_FILE_AUDIT_LOG)),
                    new FailedOperationTransformationConfig.NewAttributesConfig(AuditResourceDefinitions.ENCODING))
        );
    }

    /**
     * Test case testing resources and attributes are appropriately transformed when transforming to EAP 7.4.
     */
    @Test
    public void testTransformerEAP740() throws Exception {
        testTransformation(EAP_7_4_0);
    }

    /**
     * Test case testing resources and attributes are appropriately transformed when transforming to EAP 8.0.0.
     */
    @Test
    public void testTransformerEAP800() throws Exception {
        testTransformation(EAP_8_0_0);
    }

    /**
     * Test case testing resources and attributes are appropriately transformed when transforming to EAP 8.1.0.
     */
    @Test
    public void testTransformerEAP810() throws Exception {
        testTransformation(EAP_8_1_0);
    }

    @Test
    public void testTransformerWFLY31() throws Exception {
        testTransformation(WILDFLY_31_0_0);
    }

    private KernelServices buildKernelServices(String xml, ModelTestControllerVersion controllerVersion, ModelVersion version, String... mavenResourceURLs) throws Exception {
        AdditionalInitialization additionalInitialization = AdditionalInitialization.fromModelTestControllerVersion(controllerVersion);

        KernelServicesBuilder builder = this.createKernelServicesBuilder(additionalInitialization).setSubsystemXml(xml);

        builder.createLegacyKernelServicesBuilder(additionalInitialization, controllerVersion, version)
                .addMavenResourceURL(mavenResourceURLs)
                .skipReverseControllerCheck()
                .addParentFirstClassPattern("org.jboss.as.controller.logging.ControllerLogger*")
                .addParentFirstClassPattern("org.jboss.as.controller.PathAddress")
                .addParentFirstClassPattern("org.jboss.as.controller.PathElement")
                .addParentFirstClassPattern("org.jboss.as.server.logging.*")
                .addParentFirstClassPattern("org.jboss.logging.*")
                .addParentFirstClassPattern("org.jboss.dmr.*")
                .dontPersistXml();

        KernelServices services = builder.build();
        Assert.assertTrue(ModelTestControllerVersion.MASTER + " boot failed", services.isSuccessfulBoot());
        Assert.assertTrue(controllerVersion.getMavenGavVersion() + " boot failed", services.getLegacyServices(version).isSuccessfulBoot());
        return services;
    }

    private void testTransformation(final ModelTestControllerVersion controller, final String subsystemXml) throws Exception {
        final ModelVersion version = controller.getSubsystemModelVersion(getMainSubsystemName());

        KernelServices services = this.buildKernelServices(subsystemXml, controller, version,
                controller.getCoreMavenGroupId() + ":wildfly-elytron-integration:" + controller.getCoreVersion());

        // check that both versions of the legacy model are the same and valid
        checkSubsystemModelTransformation(services, version, null, false);

        ModelNode transformed = services.readTransformedModel(version);
        Assert.assertTrue(transformed.isDefined());
    }

    private void testTransformation(final ModelTestControllerVersion controller) throws Exception {
        testTransformation(controller, getSubsystemXml());
    }

    private void testRejectingTransformers(ModelTestControllerVersion controllerVersion, final String subsystemXmlFile, final FailedOperationTransformationConfig config) throws Exception {
        ModelVersion elytronVersion = controllerVersion.getSubsystemModelVersion(getMainSubsystemName());

        //Boot up empty controllers with the resources needed for the ops coming from the xml to work
        KernelServicesBuilder builder = createKernelServicesBuilder(AdditionalInitialization.withCapabilities(controllerVersion.getStability(),
                        RuntimeCapability.buildDynamicCapabilityName(Capabilities.DATA_SOURCE_CAPABILITY_NAME, "ExampleDS")
        ));
        builder.createLegacyKernelServicesBuilder(AdditionalInitialization.MANAGEMENT, controllerVersion, elytronVersion)
                .addMavenResourceURL(controllerVersion.getCoreMavenGroupId() + ":wildfly-elytron-integration:" + controllerVersion.getCoreVersion())
                .addParentFirstClassPattern("org.jboss.as.controller.logging.ControllerLogger*")
                .addParentFirstClassPattern("org.jboss.as.controller.PathAddress")
                .addParentFirstClassPattern("org.jboss.as.controller.PathElement")
                .addParentFirstClassPattern("org.jboss.as.server.logging.*")
                .addParentFirstClassPattern("org.jboss.logging.*")
                .addParentFirstClassPattern("org.jboss.dmr.*")
                .dontPersistXml();

        KernelServices mainServices = builder.build();
        assertTrue(mainServices.isSuccessfulBoot());
        assertTrue(mainServices.getLegacyServices(elytronVersion).isSuccessfulBoot());

        List<ModelNode> ops = builder.parseXmlResource(subsystemXmlFile);
        ModelTestUtils.checkFailedTransformedBootOperations(mainServices, elytronVersion, ops, config);
    }

}
