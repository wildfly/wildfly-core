/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.management;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.jboss.as.controller.access.constraint.ApplicationTypeConfig;
import org.jboss.as.controller.access.constraint.ApplicationTypeConstraint;
import org.jboss.as.controller.access.constraint.ConstraintFactory;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;

/**
 * {@link AccessConstraintDefinition} for {@link ApplicationTypeConstraint}.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class ApplicationTypeAccessConstraintDefinition implements AccessConstraintDefinition {

    public static final ApplicationTypeAccessConstraintDefinition DEPLOYMENT = new ApplicationTypeAccessConstraintDefinition(ApplicationTypeConfig.DEPLOYMENT);

    public static final List<AccessConstraintDefinition> DEPLOYMENT_AS_LIST = DEPLOYMENT.wrapAsList();

    private final ApplicationTypeConfig applicationTypeConfig;
    private final AccessConstraintKey key;

    public ApplicationTypeAccessConstraintDefinition(ApplicationTypeConfig applicationTypeConfig) {
        // Register this applicationTypeConfig, and if a compatible one is already registered, use that instead
        ApplicationTypeConfig toUse = ApplicationTypeConstraint.FACTORY.addApplicationTypeConfig(applicationTypeConfig);
        this.applicationTypeConfig = toUse;
        this.key = new AccessConstraintKey(ModelDescriptionConstants.APPLICATION_CLASSIFICATION, toUse.isCore(),
                toUse.getSubsystem(), toUse.getName());
    }

    @Override
    public ModelNode getModelDescriptionDetails(Locale locale) {
        return null;
    }

    @Override
    public ConstraintFactory getConstraintFactory() {
        return ApplicationTypeConstraint.FACTORY;
    }

    public ApplicationTypeConfig getApplicationTypeConfig() {
        return applicationTypeConfig;
    }

    @Override
    public String getName() {
        return applicationTypeConfig.getName();
    }

    @Override
    public String getType() {
        return ModelDescriptionConstants.APPLICATION;
    }

    @Override
    public boolean isCore() {
        return applicationTypeConfig.isCore();
    }

    @Override
    public String getSubsystemName() {
        return applicationTypeConfig.isCore() ? null : applicationTypeConfig.getSubsystem();
    }

    @Override
    public AccessConstraintKey getKey() {
        return key;
    }

    @Override
    public String getDescription(Locale locale) {
        // TODO
        return null;
    }

    @Override
    public int hashCode() {
        return applicationTypeConfig.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ApplicationTypeAccessConstraintDefinition
                && applicationTypeConfig.equals(((ApplicationTypeAccessConstraintDefinition)obj).applicationTypeConfig);
    }

    public List<AccessConstraintDefinition> wrapAsList() {
        return Collections.singletonList((AccessConstraintDefinition) this);
    }
}
