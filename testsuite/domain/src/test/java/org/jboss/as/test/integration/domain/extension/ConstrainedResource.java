/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.domain.extension;


import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.constraint.ApplicationTypeConfig;
import org.jboss.as.controller.access.constraint.SensitivityClassification;
import org.jboss.as.controller.access.management.ApplicationTypeAccessConstraintDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class ConstrainedResource extends SimpleResourceDefinition {
    static final SensitivityClassification DS_SECURITY = new SensitivityClassification("datasources", "data-source-security", false, true, true);
    static final SensitiveTargetAccessConstraintDefinition DS_SECURITY_DEF = new SensitiveTargetAccessConstraintDefinition(DS_SECURITY);

    private static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder("password", ModelType.STRING)
            .setAllowExpression(true)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.CREDENTIAL)
            .addAccessConstraint(DS_SECURITY_DEF)
            .build();


    static final SimpleAttributeDefinition SECURITY_DOMAIN = new SimpleAttributeDefinitionBuilder("security-domain", ModelType.STRING)
            .setAllowExpression(true)
            .setRequired(false)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SECURITY_DOMAIN_REF)
            .addAccessConstraint(DS_SECURITY_DEF)
            .build();

    static final SimpleAttributeDefinition AUTHENTICATION_INFLOW = new SimpleAttributeDefinitionBuilder("authentication-inflow", ModelType.BOOLEAN)
            .setRequired(false)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setNullSignificant(false)
            .addAccessConstraint(DS_SECURITY_DEF)
            .build();


    public ConstrainedResource(PathElement pathElement) {
        super(new Parameters(pathElement, NonResolvingResourceDescriptionResolver.INSTANCE)
                .setAddHandler(ModelOnlyAddStepHandler.INSTANCE)
                .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE)
                .setAccessConstraints(new ApplicationTypeAccessConstraintDefinition(new ApplicationTypeConfig("datasources", "datasource"))));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadOnlyAttribute(PASSWORD, null);
        resourceRegistration.registerReadOnlyAttribute(SECURITY_DOMAIN, null);
        resourceRegistration.registerReadOnlyAttribute(AUTHENTICATION_INFLOW, null);
    }
}
