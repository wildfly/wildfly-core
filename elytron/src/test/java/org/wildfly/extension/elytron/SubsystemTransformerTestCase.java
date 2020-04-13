/*
Copyright 2017 Red Hat, Inc.

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

package org.wildfly.extension.elytron;

import static org.jboss.as.model.test.FailedOperationTransformationConfig.REJECTED_RESOURCE;
import static org.jboss.as.model.test.ModelTestControllerVersion.EAP_7_1_0;
import static org.jboss.as.model.test.ModelTestControllerVersion.EAP_7_2_0;
import static org.junit.Assert.assertTrue;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.JDBC_REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AGGREGATE_REALM;

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
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
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
public class SubsystemTransformerTestCase extends AbstractSubsystemBaseTest {

    private static final PathAddress SUBSYSTEM_ADDRESS = PathAddress.pathAddress(ModelDescriptionConstants.SUBSYSTEM, ElytronExtension.SUBSYSTEM_NAME);

    public SubsystemTransformerTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("elytron-transformers-4.0.xml");
    }

    protected String getSubsystemXml(final String subsystemFile) throws IOException {
        return readResource(subsystemFile);
    }

    @Test
    public void testRejectingTransformersEAP710() throws Exception {
        testRejectingTransformers(EAP_7_1_0, "elytron-transformers-1.2-reject.xml", new FailedOperationTransformationConfig()
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY)),
                        new FailedOperationTransformationConfig.NewAttributesConfig(KerberosSecurityFactoryDefinition.FAIL_CACHE)
                )
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.JDBC_REALM, "DisallowedScramSha384")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE
                )
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.JDBC_REALM, "DisallowedScramSha512")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE
                )
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.MAPPED_ROLE_MAPPER, "DisallowedMappedRoleMapper")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE
                )
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT)),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE
                )
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.CUSTOM_SECURITY_EVENT_LISTENER)),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE
                ));
    }

    @Test
    public void testRejectingTransformersEAP720() throws Exception {
        testRejectingTransformers(EAP_7_2_0, "elytron-transformers-4.0-reject.xml", new FailedOperationTransformationConfig()
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION, "authWithCredentialReference")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.KEY_STORE, "test.keystore")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.KEY_MANAGER, "key2")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT, "testCAA2")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.CREDENTIAL_STORE, "store2")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.DIR_CONTEXT, "dirContext")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.SERVER_SSL_CONTEXT)),
                        new FailedOperationTransformationConfig.NewAttributesConfig(SSLDefinitions.CIPHER_SUITE_NAMES))
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.CLIENT_SSL_CONTEXT)),
                        new FailedOperationTransformationConfig.NewAttributesConfig(SSLDefinitions.CIPHER_SUITE_NAMES))
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.X500_SUBJECT_EVIDENCE_DECODER, "subjectDecoder")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.X509_SUBJECT_ALT_NAME_EVIDENCE_DECODER, "rfc822Decoder")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.CUSTOM_EVIDENCE_DECODER, "customEvidenceDecoder")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.AGGREGATE_EVIDENCE_DECODER, "aggregateEvidenceDecoder")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.SECURITY_DOMAIN)),
                        new FailedOperationTransformationConfig.NewAttributesConfig(DomainDefinition.EVIDENCE_DECODER))
                .addFailedAttribute(SUBSYSTEM_ADDRESS, new FailedOperationTransformationConfig.NewAttributesConfig(ElytronDefinition.DEFAULT_SSL_CONTEXT))
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.JASPI_CONFIGURATION, "minimal")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.FILE_AUDIT_LOG, "audit1")),
                       new FailedOperationTransformationConfig.NewAttributesConfig(AuditResourceDefinitions.AUTOFLUSH)
                )
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.FILE_AUDIT_LOG, "audit2")),
                        new FailedOperationTransformationConfig.NewAttributesConfig(AuditResourceDefinitions.AUTOFLUSH)
                )
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.PERIODIC_ROTATING_FILE_AUDIT_LOG, "audit3")),
                        new FailedOperationTransformationConfig.NewAttributesConfig(AuditResourceDefinitions.AUTOFLUSH)
                )
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.PERIODIC_ROTATING_FILE_AUDIT_LOG, "audit4")),
                        new FailedOperationTransformationConfig.NewAttributesConfig(AuditResourceDefinitions.AUTOFLUSH)
                )
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.SIZE_ROTATING_FILE_AUDIT_LOG, "audit5")),
                        new FailedOperationTransformationConfig.NewAttributesConfig(AuditResourceDefinitions.AUTOFLUSH)
                )
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.SIZE_ROTATING_FILE_AUDIT_LOG, "audit6")),
                        new FailedOperationTransformationConfig.NewAttributesConfig(AuditResourceDefinitions.AUTOFLUSH)
                )
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.SYSLOG_AUDIT_LOG, "audit7")),
                        new FailedOperationTransformationConfig.NewAttributesConfig(AuditResourceDefinitions.SYSLOG_FORMAT, AuditResourceDefinitions.RECONNECT_ATTEMPTS)
                )
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.SERVER_SSL_SNI_CONTEXT)),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(JDBC_REALM, "JdbcBcryptHashHex")), REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(JDBC_REALM, "JdbcBcryptSaltHex")), REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(JDBC_REALM, "JdbcSaltedSimpleDigestHashHex")), REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(JDBC_REALM, "JdbcSaltedSimpleDigestSaltHex")), REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(JDBC_REALM, "JdbcSimpleDigestHashHex")), REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(JDBC_REALM, "JdbcScramHashHex")), REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(JDBC_REALM, "JdbcScramSaltHex")), REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(JDBC_REALM, "JdbcModularCrypt")), REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.TOKEN_REALM, "SslTokenRealm")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.TOKEN_REALM, "KeyMapTokenRealm")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE
                ).addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.KEY_STORE, "automatic.keystore")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE
                )
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(AGGREGATE_REALM, "AggregateOne")), REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT, "invalidCAA")),
                        new FailedOperationTransformationConfig.NewAttributesConfig(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY)
                )
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY)),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.TRUST_MANAGER, "TestingTrustManager")),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(AGGREGATE_REALM, "AggregateTwo")), REJECTED_RESOURCE)
                .addFailedAttribute(SUBSYSTEM_ADDRESS.append(PathElement.pathElement(ElytronDescriptionConstants.WEBSERVICES)),
                        FailedOperationTransformationConfig.REJECTED_RESOURCE)
                );
    }

    @Test
    public void testTransformerEAP710() throws Exception {
        testTransformation(EAP_7_1_0, getSubsystemXml("elytron-transformers-1.2.xml"));
    }

    @Test
    public void testTransformerEAP720() throws Exception {
        testTransformation(EAP_7_2_0);
    }

    private KernelServices buildKernelServices(String xml, ModelTestControllerVersion controllerVersion, ModelVersion version, String... mavenResourceURLs) throws Exception {
        KernelServicesBuilder builder = this.createKernelServicesBuilder(AdditionalInitialization.MANAGEMENT).setSubsystemXml(xml);

        builder.createLegacyKernelServicesBuilder(AdditionalInitialization.MANAGEMENT, controllerVersion, version)
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
        KernelServicesBuilder builder = createKernelServicesBuilder(AdditionalInitialization.withCapabilities(
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
