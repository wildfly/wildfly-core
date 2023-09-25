/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.management.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.APPLICATION_CLASSIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONSTRAINT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SENSITIVITY_CLASSIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VAULT_EXPRESSION;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.access.constraint.ApplicationTypeConfig;
import org.jboss.as.controller.access.constraint.ApplicationTypeConstraint;
import org.jboss.as.controller.access.constraint.SensitiveTargetConstraint;
import org.jboss.as.controller.access.constraint.SensitivityClassification;
import org.jboss.as.controller.access.management.AccessConstraintUtilizationRegistry;
import org.jboss.as.controller.registry.Resource;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class AccessConstraintResources {

    // Application Classification Resource
    public static final PathElement APPLICATION_PATH_ELEMENT = PathElement.pathElement(CONSTRAINT, APPLICATION_CLASSIFICATION);
    public static Resource getApplicationConfigResource(AccessConstraintUtilizationRegistry registry) {
        return new ApplicationClassificationResource(registry);
    }
    // Sensitivity Classification Resource
    public static final PathElement SENSITIVITY_PATH_ELEMENT = PathElement.pathElement(CONSTRAINT, SENSITIVITY_CLASSIFICATION);
    public static Resource getSensitivityResource(AccessConstraintUtilizationRegistry registry) {
        return new SensitivityClassificationResource(registry);
    }

    // Vault Expression Resource
    public static final PathElement VAULT_PATH_ELEMENT = PathElement.pathElement(CONSTRAINT, VAULT_EXPRESSION);
    public static final Resource VAULT_RESOURCE = SensitivityResourceDefinition.createVaultExpressionResource();

    private static volatile Map<String, Map<String, SensitivityClassification>> classifications;
    private static volatile Map<String, Map<String, ApplicationTypeConfig>> applicationTypes;

    private static Map<String, Map<String, SensitivityClassification>> getSensitivityClassifications(){
        Collection<SensitivityClassification> current = SensitiveTargetConstraint.FACTORY.getSensitivities();
        if (classifications == null || classifications.size() != current.size()) {
            Map<String, Map<String, SensitivityClassification>> classificationsMap = new TreeMap<String, Map<String,SensitivityClassification>>();
            for (SensitivityClassification classification : current) {
                final String type = classification.isCore() ? CORE : classification.getSubsystem();
                Map<String, SensitivityClassification> byName = classificationsMap.get(type);
                if (byName == null) {
                    byName = new TreeMap<String, SensitivityClassification>();
                    classificationsMap.put(type, byName);
                }

                byName.put(classification.getName(), classification);
            }
            classifications = classificationsMap;
        }
        return classifications;
    }

    private static Map<String, Map<String, ApplicationTypeConfig>> getApplicationClassifications(){
        Collection<ApplicationTypeConfig> current = ApplicationTypeConstraint.FACTORY.getApplicationTypeConfigs();
        if (applicationTypes == null || applicationTypes.size() != current.size()) {
            Map<String, Map<String, ApplicationTypeConfig>> classificationsMap = new TreeMap<String, Map<String,ApplicationTypeConfig>>();
            for (ApplicationTypeConfig classification : current) {
                final String type = classification.isCore() ? CORE : classification.getSubsystem();
                Map<String, ApplicationTypeConfig> byName = classificationsMap.get(type);
                if (byName == null) {
                    byName = new TreeMap<String, ApplicationTypeConfig>();
                    classificationsMap.put(type, byName);
                }

                byName.put(classification.getName(), classification);
            }
            applicationTypes = classificationsMap;
        }
        return applicationTypes;
    }


    private static class ApplicationClassificationResource extends AbstractClassificationResource {

        private static final Set<String> CHILD_TYPES = Collections.singleton(TYPE);

        private final AccessConstraintUtilizationRegistry registry;

        private ApplicationClassificationResource(AccessConstraintUtilizationRegistry registry) {
          super(APPLICATION_PATH_ELEMENT);
            this.registry = registry;
        }

        @Override
        public Set<String> getChildTypes() {
            return CHILD_TYPES;
        }

        @Override
        ResourceEntry getChildEntry(String type, String name) {
            if (TYPE.equals(type)) {
                Map<String, Map<String, ApplicationTypeConfig>> applicationTypes = getApplicationClassifications();
                Map<String, ApplicationTypeConfig> byName = applicationTypes.get(name);
                if (byName != null) {
                    return ApplicationClassificationTypeResourceDefinition.createResource(byName, name, registry);
                }
            }
            return null;
        }

        @Override
        public Set<String> getChildrenNames(String type) {
            if (TYPE.equals(type)) {
                Map<String, Map<String, ApplicationTypeConfig>> configs = getApplicationClassifications();
                return configs.keySet();
            }
            return Collections.emptySet();
        }

        @Override
        public Set<ResourceEntry> getChildren(String childType) {
            if (TYPE.equals(childType)) {
                Map<String, Map<String, ApplicationTypeConfig>> applicationTypes = getApplicationClassifications();
                Set<ResourceEntry> children = new LinkedHashSet<ResourceEntry>();
                for (Map.Entry<String, Map<String, ApplicationTypeConfig>> entry : applicationTypes.entrySet()) {
                    children.add(ApplicationClassificationTypeResourceDefinition.createResource(entry.getValue(),
                            entry.getKey(), registry));
                }
                return children;
            }
            return Collections.emptySet();
        }
    }

    private static class SensitivityClassificationResource extends AbstractClassificationResource {

        private static final Set<String> CHILD_TYPES = Collections.singleton(TYPE);
        private final AccessConstraintUtilizationRegistry registry;

        private SensitivityClassificationResource(AccessConstraintUtilizationRegistry registry) {
            super(SENSITIVITY_PATH_ELEMENT);
            this.registry = registry;
        }

        @Override
        public Set<String> getChildTypes() {
            return CHILD_TYPES;
        }

        @Override
        ResourceEntry getChildEntry(String type, String name) {
            if (TYPE.equals(type)) {
                Map<String, Map<String, SensitivityClassification>> classifications = getSensitivityClassifications();
                Map<String, SensitivityClassification> byName = classifications.get(name);
                if (byName != null) {
                    return SensitivityClassificationTypeResourceDefinition.createResource(byName, type, name, registry);
                }
            }
            return null;
        }

        @Override
        public Set<String> getChildrenNames(String type) {
            if (TYPE.equals(type)) {
                Map<String, Map<String, SensitivityClassification>> classifications = getSensitivityClassifications();
                return classifications.keySet();
            }
            return Collections.emptySet();
        }

        @Override
        public Set<ResourceEntry> getChildren(String childType) {
            if (TYPE.equals(childType)) {
                Map<String, Map<String, SensitivityClassification>> classifications = getSensitivityClassifications();
                Set<ResourceEntry> children = new LinkedHashSet<ResourceEntry>();
                for (Map.Entry<String, Map<String, SensitivityClassification>> entry : classifications.entrySet()) {
                    children.add(SensitivityClassificationTypeResourceDefinition.createResource(entry.getValue(), childType,
                            entry.getKey(), registry));
                }
                return children;
            }
            return Collections.emptySet();
        }
    }

}
