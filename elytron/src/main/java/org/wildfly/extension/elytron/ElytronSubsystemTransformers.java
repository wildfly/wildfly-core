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

import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NAME;

import java.util.Collections;

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

/**
 * Registers transformers for the elytron subsystem.
 *
 * @author Brian Stansberry
 */
public final class ElytronSubsystemTransformers implements ExtensionTransformerRegistration {
    static final ModelVersion ELYTRON_1_0_0 = ModelVersion.create(1);
    private static final ModelVersion ELYTRON_1_1_0 = ModelVersion.create(1, 1);

    @Override
    public String getSubsystemName() {
        return ElytronExtension.SUBSYSTEM_NAME;
    }

    @Override
    public void registerTransformers(SubsystemTransformerRegistration registration) {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedSubystemInstance(registration.getCurrentSubsystemVersion());

        // 1.1.0 to 1.0.0, aka WildFly Core 3.0.0/3.0.1
        buildTransformers_1_0(chainedBuilder.createBuilder(ELYTRON_1_1_0, ELYTRON_1_0_0));
        // Current 2.0.0 to 1.1.0 aka WildFly Core 3.0.2
        buildTransformers_1_1(chainedBuilder.createBuilder(registration.getCurrentSubsystemVersion(), ELYTRON_1_1_0));
        chainedBuilder.buildAndRegister(registration, new ModelVersion[]{ELYTRON_1_1_0, ELYTRON_1_0_0});
    }

    private void buildTransformers_1_1(ResourceTransformationDescriptionBuilder builder) {
        // Pack policies into lists (jacc-policy and custom-policy was made non-lists)
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.POLICY))
            .getAttributeBuilder()
                .setValueConverter(new AttributeConverter.DefaultAttributeConverter() {
                    @Override
                    protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
                        if (attributeValue.isDefined()) {
                            ModelNode element = attributeValue.clone();
                            element.get(NAME).set(address.getLastElement().getValue());
                            attributeValue.setEmptyList();
                            attributeValue.add(element);
                        }
                    }
                }, PolicyDefinitions.JaccPolicyDefinition.POLICY, PolicyDefinitions.CustomPolicyDefinition.POLICY);

        // Discard new "fail-cache" if it's undefined or has a value same as old unconfigurable behavior; reject otherwise
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY))
                .getAttributeBuilder()
                .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(new ModelNode(0)), KerberosSecurityFactoryDefinition.FAIL_CACHE)
                .addRejectCheck(RejectAttributeChecker.DEFINED, KerberosSecurityFactoryDefinition.FAIL_CACHE);
    }

    private void buildTransformers_1_0(ResourceTransformationDescriptionBuilder builder) {

        // Reject new "match-all" field if it is defined with any other value than 'false'. If it is is present
        // but undefined or false, remove it as ObjectTypeAttributeDefinition will reject unknown fields
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.SIMPLE_PERMISSION_MAPPER))
                .getAttributeBuilder()
                .setValueConverter(new AttributeConverter.DefaultAttributeConverter() {
                    @Override
                    protected void convertAttribute(PathAddress address, String attributeName, ModelNode attributeValue, TransformationContext context) {
                        if (attributeValue.isDefined()) {
                            String name = PermissionMapperDefinitions.MATCH_ALL.getName();
                            for (ModelNode element : attributeValue.asList()) {
                                if (element.has(name)
                                        && (!element.hasDefined(name) || "false".equals(element.get(name).asString()))) {
                                    element.remove(name);
                                }
                            }
                        }
                    }
                }, PermissionMapperDefinitions.PERMISSION_MAPPINGS)
                .addRejectCheck(
                        new RejectAttributeChecker.ListRejectAttributeChecker(
                                new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(
                                        Collections.singletonMap(PermissionMapperDefinitions.MATCH_ALL.getName(), RejectAttributeChecker.DEFINED))),
                        PermissionMapperDefinitions.PERMISSION_MAPPINGS);

        // Discard new "forwarding-mode" if it's undefined or has a value same as old unconfigurable behavior; reject otherwise
        builder.addChildResource(PathElement.pathElement(ElytronDescriptionConstants.AUTHENTICATION_CONFIGURATION))
                .getAttributeBuilder()
                .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(new ModelNode(ElytronDescriptionConstants.AUTHENTICATION)), AuthenticationClientDefinitions.FORWARDING_MODE)
                .addRejectCheck(RejectAttributeChecker.DEFINED, AuthenticationClientDefinitions.FORWARDING_MODE);

    }
}
