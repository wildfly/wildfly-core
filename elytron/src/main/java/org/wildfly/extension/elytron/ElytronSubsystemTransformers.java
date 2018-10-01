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

import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ALGORITHM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AUTOFLUSH;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILE_AUDIT_LOG;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PERIODIC_ROTATING_FILE_AUDIT_LOG;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SCRAM_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SIZE_ROTATING_FILE_AUDIT_LOG;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SYNCHRONIZED;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_1_2_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_2_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_3_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_4_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_5_0_0;
import static org.wildfly.extension.elytron.JdbcRealmDefinition.PrincipalQueryAttributes.PRINCIPAL_QUERIES;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.util.Collections;
import java.util.Map;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.transform.ExtensionTransformerRegistration;
import org.jboss.as.controller.transform.SubsystemTransformerRegistration;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.as.controller.transform.description.AttributeConverter;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.password.interfaces.ScramDigestPassword;

/**
 * Registers transformers for the elytron subsystem.
 *
 * @author Brian Stansberry
 */
public final class ElytronSubsystemTransformers implements ExtensionTransformerRegistration {

    @Override
    public String getSubsystemName() {
        return ElytronExtension.SUBSYSTEM_NAME;
    }

    @Override
    public void registerTransformers(SubsystemTransformerRegistration registration) {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedSubystemInstance(registration.getCurrentSubsystemVersion());

        // 5.0.0 (WildFly 15) to 4.0.0 (WildFly 14)
        from5(chainedBuilder);
        // 4.0.0 (WildFly 14) to 3.0.0 (WildFly 13)
        from4(chainedBuilder);
        // 3.0.0 (WildFly 13) to 2.0.0 (WildFly 12)
        from3(chainedBuilder);
        // 2.0.0 (WildFly 12) to 1.2.0, (WildFly 11 and EAP 7.1.0)
        from2(chainedBuilder);

        chainedBuilder.buildAndRegister(registration, new ModelVersion[] { ELYTRON_4_0_0, ELYTRON_3_0_0, ELYTRON_2_0_0, ELYTRON_1_2_0 });

    }

    private static void from5(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_5_0_0, ELYTRON_4_0_0);
        builder.getAttributeBuilder()
            .setDiscard(DiscardAttributeChecker.ALWAYS, ElytronDefinition.REGISTER_JASPI_FACTORY)
            .end();
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.JASPI_CONFIGURATION));
        transformAutoFlush(builder, FILE_AUDIT_LOG);
        transformAutoFlush(builder, PERIODIC_ROTATING_FILE_AUDIT_LOG);
        transformAutoFlush(builder, SIZE_ROTATING_FILE_AUDIT_LOG);
    }

    private static void transformAutoFlush(ResourceTransformationDescriptionBuilder builder, final String resourceName) {
        builder
            .addChildResource(PathElement.pathElement(resourceName))
            .getAttributeBuilder()
            .setDiscard(DISCARD_IF_EQUALS_SYNCHRONIZED, AuditResourceDefinitions.AUTOFLUSH)
            .addRejectCheck(REJECT_IF_DIFFERENT_FROM_SYNCHRONIZED, AuditResourceDefinitions.AUTOFLUSH)
            .end();
    }

    private static void from4(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_4_0_0, ELYTRON_3_0_0);
        builder
                .addChildResource(PathElement.pathElement(ElytronDescriptionConstants.JDBC_REALM))
                .getAttributeBuilder()
                .addRejectCheck(new RejectAttributeChecker.ListRejectAttributeChecker(
                        new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(Collections.singletonMap(SCRAM_MAPPER,
                                new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(Collections.singletonMap(ALGORITHM,
                                        new RejectAttributeChecker.SimpleRejectAttributeChecker(new ModelNode(ScramDigestPassword.ALGORITHM_SCRAM_SHA_384))
                                ))
                        ))
                ), PRINCIPAL_QUERIES)
                .addRejectCheck(new RejectAttributeChecker.ListRejectAttributeChecker(
                        new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(Collections.singletonMap(SCRAM_MAPPER,
                                new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(Collections.singletonMap(ALGORITHM,
                                        new RejectAttributeChecker.SimpleRejectAttributeChecker(new ModelNode(ScramDigestPassword.ALGORITHM_SCRAM_SHA_512))
                                ))
                        ))
                ), PRINCIPAL_QUERIES)
                .end();
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY_ACCOUNT));
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.MAPPED_ROLE_MAPPER));
        builder.rejectChildResource(PathElement.pathElement(ElytronDescriptionConstants.CUSTOM_SECURITY_EVENT_LISTENER));
    }

    private static void from3(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_3_0_0, ELYTRON_2_0_0);
        builder.discardChildResource(PathElement.pathElement(ElytronDescriptionConstants.PERMISSION_SET));
        builder
                .addChildResource(PathElement.pathElement(ElytronDescriptionConstants.SIMPLE_PERMISSION_MAPPER))
                .getAttributeBuilder()
                .setValueConverter(MAPPING_PERMISSION_SET_CONVERTER, ElytronDescriptionConstants.PERMISSION_MAPPINGS)
                .end();
        builder
                .addChildResource(PathElement.pathElement(ElytronDescriptionConstants.CONSTANT_PERMISSION_MAPPER))
                .getAttributeBuilder()
                .addRename(ElytronDescriptionConstants.PERMISSION_SETS, ElytronDescriptionConstants.PERMISSIONS)
                .setValueConverter(CONSTANT_PERMISSION_SET_CONVERTER, ElytronDescriptionConstants.PERMISSION_SETS)
                .end();
    }

    private static void from2(ChainedTransformationDescriptionBuilder chainedBuilder) {
        ResourceTransformationDescriptionBuilder builder = chainedBuilder.createBuilder(ELYTRON_2_0_0, ELYTRON_1_2_0);

        // Discard new "fail-cache" if it's undefined or has a value same as old unconfigurable behavior; reject otherwise
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY))
            .getAttributeBuilder()
            .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(new ModelNode(0)), KerberosSecurityFactoryDefinition.FAIL_CACHE)
            .addRejectCheck(RejectAttributeChecker.DEFINED, KerberosSecurityFactoryDefinition.FAIL_CACHE);
    }

    // converting permission-set reference back to inline permissions
    private static final AttributeConverter MAPPING_PERMISSION_SET_CONVERTER = new AttributeConverter.DefaultAttributeConverter() {
        @Override
        protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            if (attributeValue.isDefined()) {
                for (ModelNode permissionMapping : attributeValue.asList()) {
                    if (permissionMapping.hasDefined(ElytronDescriptionConstants.PERMISSION_SETS)) {
                        ModelNode permissionSets = permissionMapping.get(ElytronDescriptionConstants.PERMISSION_SETS);
                        for (ModelNode permissionSet : permissionSets.asList()) {
                            ModelNode permissionSetName = permissionSet.get(ElytronDescriptionConstants.PERMISSION_SET);
                            PathAddress permissionSetAddress = address.getParent().append(ElytronDescriptionConstants.PERMISSION_SET, permissionSetName.asString());
                            ModelNode permissions = context.readResourceFromRoot(permissionSetAddress).getModel().get(ElytronDescriptionConstants.PERMISSIONS);
                            for (ModelNode permission: permissions.asList()) {
                                permissionMapping.get(ElytronDescriptionConstants.PERMISSIONS).add(permission);
                            }
                        }
                        permissionMapping.remove(ElytronDescriptionConstants.PERMISSION_SETS);
                    }
                }
            }
        }
    };

    // converting permission-set reference back to inline permissions
    private static final AttributeConverter CONSTANT_PERMISSION_SET_CONVERTER = new AttributeConverter.DefaultAttributeConverter() {
        @Override
        protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            if (attributeValue.isDefined()) {
                ModelNode allPermissions = new ModelNode();
                for (ModelNode permissionSet : attributeValue.asList()) {
                    ModelNode permissionSetName = permissionSet.get(ElytronDescriptionConstants.PERMISSION_SET);
                    PathAddress permissionSetAddress = address.getParent().append(ElytronDescriptionConstants.PERMISSION_SET, permissionSetName.asString());
                    ModelNode permissions = context.readResourceFromRoot(permissionSetAddress).getModel().get(ElytronDescriptionConstants.PERMISSIONS);
                    for (ModelNode permission: permissions.asList()) {
                        allPermissions.add(permission);
                    }
                }
                attributeValue.set(allPermissions);
            }
        }
    };

    private static final RejectAttributeChecker REJECT_IF_DIFFERENT_FROM_SYNCHRONIZED = new RejectAttributeChecker.DefaultRejectAttributeChecker() {
        @Override
        public String getRejectionLogMessage(Map<String, ModelNode> attributes) {
            return ROOT_LOGGER.unableToTransformTornAttribute(AUTOFLUSH, SYNCHRONIZED);
        }

        @Override
        protected boolean rejectAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            if (attributeValue.isDefined()) {
                boolean synced = context.readResourceFromRoot(address).getModel().get(SYNCHRONIZED).asBoolean();
                return synced != attributeValue.asBoolean();
            }
            return false;
        }
    };

    private static final DiscardAttributeChecker DISCARD_IF_EQUALS_SYNCHRONIZED = new DiscardAttributeChecker.DefaultDiscardAttributeChecker() {
        @Override
        protected boolean isValueDiscardable(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
            boolean synced = context.readResourceFromRoot(address).getModel().get(SYNCHRONIZED).asBoolean();
            return synced == attributeValue.asBoolean();
        }
    };
}
