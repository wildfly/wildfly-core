/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.management;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.jboss.as.controller.access.constraint.ConstraintFactory;
import org.jboss.as.controller.access.constraint.SensitiveTargetConstraint;
import org.jboss.as.controller.access.constraint.SensitivityClassification;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;

/**
 * {@link AccessConstraintDefinition} for {@link SensitiveTargetConstraint}.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class SensitiveTargetAccessConstraintDefinition implements AccessConstraintDefinition {

    public static final SensitiveTargetAccessConstraintDefinition ACCESS_CONTROL = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.ACCESS_CONTROL);
    public static final SensitiveTargetAccessConstraintDefinition AUTHENTICATION_CLIENT_REF = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.AUTHENTICATION_CLIENT_REF);
    public static final SensitiveTargetAccessConstraintDefinition AUTHENTICATION_FACTORY_REF = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.AUTHENTICATION_FACTORY_REF);
    public static final SensitiveTargetAccessConstraintDefinition CREDENTIAL = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.CREDENTIAL);
    public static final SensitiveTargetAccessConstraintDefinition DOMAIN_CONTROLLER = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.DOMAIN_CONTROLLER);
    public static final SensitiveTargetAccessConstraintDefinition DOMAIN_NAMES = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.DOMAIN_NAMES);
    public static final SensitiveTargetAccessConstraintDefinition ELYTRON_SECURITY_DOMAIN_REF = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.ELYTRON_SECURITY_DOMAIN_REF);
    public static final SensitiveTargetAccessConstraintDefinition EXTENSIONS = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.EXTENSIONS);
    public static final SensitiveTargetAccessConstraintDefinition JVM = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.JVM);
    public static final SensitiveTargetAccessConstraintDefinition MANAGEMENT_INTERFACES = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.MANAGEMENT_INTERFACES);
    public static final SensitiveTargetAccessConstraintDefinition MODULE_LOADING = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.MODULE_LOADING);
    public static final SensitiveTargetAccessConstraintDefinition PATCHING = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.PATCHING);
    public static final SensitiveTargetAccessConstraintDefinition READ_WHOLE_CONFIG = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.READ_WHOLE_CONFIG);
    public static final SensitiveTargetAccessConstraintDefinition RELOAD_ENHANCED = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.RELOAD_ENHANCED);
    public static final SensitiveTargetAccessConstraintDefinition SECURITY_DOMAIN = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.SECURITY_DOMAIN);
    public static final SensitiveTargetAccessConstraintDefinition SECURITY_DOMAIN_REF = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.SECURITY_DOMAIN_REF);
    public static final SensitiveTargetAccessConstraintDefinition SECURITY_REALM = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.SECURITY_REALM);
    public static final SensitiveTargetAccessConstraintDefinition SECURITY_REALM_REF = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.SECURITY_REALM_REF);
    public static final SensitiveTargetAccessConstraintDefinition SECURITY_VAULT = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.SECURITY_VAULT);
    public static final SensitiveTargetAccessConstraintDefinition SERVER_SSL = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.SERVER_SSL);
    public static final SensitiveTargetAccessConstraintDefinition SERVICE_CONTAINER = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.SERVICE_CONTAINER);
    public static final SensitiveTargetAccessConstraintDefinition SOCKET_BINDING_REF = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.SOCKET_BINDING_REF);
    public static final SensitiveTargetAccessConstraintDefinition SOCKET_CONFIG = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.SOCKET_CONFIG);
    public static final SensitiveTargetAccessConstraintDefinition SNAPSHOTS = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.SNAPSHOTS);
    public static final SensitiveTargetAccessConstraintDefinition SSL_REF = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.SSL_REF);
    public static final SensitiveTargetAccessConstraintDefinition SYSTEM_PROPERTY = new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.SYSTEM_PROPERTY);

    private final SensitivityClassification sensitivity;
    private final AccessConstraintKey key;

    public SensitiveTargetAccessConstraintDefinition(SensitivityClassification sensitivity) {
        // Register this sensitivity, and if a compatible one is already registered, use that instead
        final SensitivityClassification toUse = SensitiveTargetConstraint.FACTORY.addSensitivity(sensitivity);
        this.sensitivity = toUse;
        this.key = new AccessConstraintKey(ModelDescriptionConstants.SENSITIVITY_CLASSIFICATION, toUse.isCore(),
                toUse.getSubsystem(), toUse.getName());
    }

    public SensitivityClassification getSensitivity() {
        return sensitivity;
    }

    @Override
    public ModelNode getModelDescriptionDetails(Locale locale) {
        return null;
    }

    @Override
    public ConstraintFactory getConstraintFactory() {
        return SensitiveTargetConstraint.FACTORY;
    }

    @Override
    public String getName() {
        return sensitivity.getName();
    }

    @Override
    public String getType() {
        return ModelDescriptionConstants.SENSITIVE;
    }

    @Override
    public boolean isCore() {
        return sensitivity.isCore();
    }

    @Override
    public String getSubsystemName() {
        return sensitivity.isCore() ? null : sensitivity.getSubsystem();
    }

    @Override
    public AccessConstraintKey getKey() {
        return key;
    }

    @Override
    public String getDescription(Locale locale) {
        //TODO implement getDescription
        return null;
    }

    @Override
    public int hashCode() {
        return sensitivity.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SensitiveTargetAccessConstraintDefinition
                && sensitivity.equals(((SensitiveTargetAccessConstraintDefinition)obj).sensitivity);
    }

    public List<AccessConstraintDefinition> wrapAsList() {
        return Collections.singletonList((AccessConstraintDefinition) this);
    }
}
