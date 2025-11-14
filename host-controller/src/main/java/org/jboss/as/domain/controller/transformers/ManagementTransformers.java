/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.transformers;

import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.createBuilder;
import static org.jboss.as.domain.controller.transformers.KernelAPIVersion.createBuilderFromCurrent;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.access.constraint.SensitivityClassification;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilderFactory;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.domain.management.CoreManagementResourceDefinition;
import org.jboss.as.domain.management.access.AccessAuthorizationResourceDefinition;
import org.jboss.as.domain.management.access.AccessConstraintResources;
import org.jboss.as.domain.management.access.SensitivityClassificationTypeResourceDefinition;
import org.jboss.as.domain.management.access.SensitivityResourceDefinition;

/**
 * Transformers for the domain-wide management configuration.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 * @author Tomaz Cerar
 */
class ManagementTransformers {

    private ManagementTransformers() {
        // prevent instantiation
    }

    static ChainedTransformationDescriptionBuilder buildTransformerChain(ChainedTransformationDescriptionBuilderFactory factory) {
        // Discard the domain level core-service=management resource and its children unless RBAC is enabled
        // Configuring rbac details is OK (i.e. discarable), so long as the provider is not enabled
        ChainedTransformationDescriptionBuilder chainedBuilder = factory.createChainedTransformationDescriptionBuilder(CoreManagementResourceDefinition.PATH_ELEMENT);

        ResourceTransformationDescriptionBuilder builder18To17 = createBuilder(chainedBuilder, KernelAPIVersion.VERSION_1_8, KernelAPIVersion.VERSION_1_7);
        builder18To17.addChildResource(AccessAuthorizationResourceDefinition.PATH_ELEMENT)
                .addChildResource(AccessConstraintResources.SENSITIVITY_PATH_ELEMENT)
                .addChildResource(SensitivityClassificationTypeResourceDefinition.PATH_ELEMENT)
                .discardChildResource(PathElement.pathElement(SensitivityResourceDefinition.PATH_ELEMENT.getKey(), SensitivityClassification.SERVER_SSL.getName()))
                .build();

        ResourceTransformationDescriptionBuilder builderCurrentTo41 = createBuilderFromCurrent(chainedBuilder, KernelAPIVersion.VERSION_4_1);
        ResourceTransformationDescriptionBuilder childResource = builderCurrentTo41.addChildResource(AccessAuthorizationResourceDefinition.PATH_ELEMENT)
                .addChildResource(AccessConstraintResources.SENSITIVITY_PATH_ELEMENT)
                .addChildResource(SensitivityClassificationTypeResourceDefinition.PATH_ELEMENT);

        childResource.discardChildResource(PathElement.pathElement(SensitivityResourceDefinition.PATH_ELEMENT.getKey(), SensitivityClassification.AUTHENTICATION_CLIENT_REF.getName())).build();
        childResource.discardChildResource(PathElement.pathElement(SensitivityResourceDefinition.PATH_ELEMENT.getKey(), SensitivityClassification.AUTHENTICATION_FACTORY_REF.getName())).build();
        childResource.discardChildResource(PathElement.pathElement(SensitivityResourceDefinition.PATH_ELEMENT.getKey(), SensitivityClassification.ELYTRON_SECURITY_DOMAIN_REF.getName())).build();
        childResource.discardChildResource(PathElement.pathElement(SensitivityResourceDefinition.PATH_ELEMENT.getKey(), SensitivityClassification.SSL_REF.getName())).build();

        return chainedBuilder;
    }
}
