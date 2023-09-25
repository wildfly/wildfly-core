/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.access.management;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;

import java.util.List;
import java.util.Locale;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;

/**
 * Utility for adding access constraint descriptive metadata to other metadata.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class AccessConstraintDescriptionProviderUtil {
    public static void addAccessConstraints(ModelNode result, List<AccessConstraintDefinition> accessConstraints, Locale locale) {
        if (!accessConstraints.isEmpty()) {
            ModelNode constraints = new ModelNode();
            for (AccessConstraintDefinition constraint : accessConstraints) {
                ModelNode constraintDesc = constraints.get(constraint.getType(), constraint.getName());
                constraintDesc.get(TYPE).set(constraint.isCore() ? CORE : constraint.getSubsystemName());
                String textDesc = constraint.getDescription(locale);
                if (textDesc != null) {
                    constraintDesc.get(DESCRIPTION).set(textDesc);
                }
                ModelNode details = constraint.getModelDescriptionDetails(locale);
                if (details != null && details.isDefined()) {
                    constraintDesc.get(ModelDescriptionConstants.DETAILS).set(details);
                }
            }
            result.get(ModelDescriptionConstants.ACCESS_CONSTRAINTS).set(constraints);
        }
    }

}
