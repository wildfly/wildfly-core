/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.version.Stability;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
abstract class AttributeTransformationDescriptionBuilderImpl<T extends BaseAttributeTransformationDescriptionBuilder<?>> implements BaseAttributeTransformationDescriptionBuilder<T> {

    private AttributeTransformationDescriptionBuilderRegistry registry;
    private ResourceTransformationDescriptionBuilder builder;

    AttributeTransformationDescriptionBuilderImpl(final ResourceTransformationDescriptionBuilder builder, final AttributeTransformationDescriptionBuilderRegistry registry) {
        this.builder = builder;
        this.registry = registry;
    }

    @Override
    public Stability getStability() {
        return this.builder.getStability();
    }

    @Override
    public ResourceTransformationDescriptionBuilder end() {
        return builder;
    }

    @Override
    public T setDiscard(DiscardAttributeChecker discardChecker, Collection<AttributeDefinition> discardedAttributes) {
        for (AttributeDefinition discardedAttribute : discardedAttributes) {
            if (this.enables(discardedAttribute)) {
                this.registry.setDiscardedAttribute(discardChecker, discardedAttribute.getName());
            }
        }
        return this.thisBuilder();
    }

    @Override
    public T setDiscard(DiscardAttributeChecker discardChecker, String... discardedAttributes) {
        for (String discardedAttribute : discardedAttributes) {
            this.registry.setDiscardedAttribute(discardChecker, discardedAttribute);
        }
        return thisBuilder();
    }

    @Override
    public T addRejectCheck(final RejectAttributeChecker checker, final AttributeDefinition... rejectedAttributes){
        for (AttributeDefinition rejectedAttribute : rejectedAttributes) {
            if (this.enables(rejectedAttribute)) {
                this.registry.addAttributeCheck(rejectedAttribute.getName(), checker);
            }
        }
        return this.thisBuilder();
    }

    @Override
    public T addRejectCheck(RejectAttributeChecker rejectChecker, String... rejectedAttributes) {
        for (String rejectedAttribute : rejectedAttributes) {
            this.registry.addAttributeCheck(rejectedAttribute, rejectChecker);
        }
        return this.thisBuilder();
    }

    @Override
    public T addRejectChecks(List<RejectAttributeChecker> rejectCheckers, AttributeDefinition...rejectedAttributes) {
        for (RejectAttributeChecker rejectChecker : rejectCheckers) {
            this.addRejectCheck(rejectChecker, rejectedAttributes);
        }
        return thisBuilder();
    }

    @Override
    public T addRejectChecks(List<RejectAttributeChecker> rejectCheckers, String... rejectedAttributes) {
        for (RejectAttributeChecker rejectChecker : rejectCheckers) {
            this.addRejectCheck(rejectChecker, rejectedAttributes);
        }
        return thisBuilder();
    }

    @Override
    public T addRename(AttributeDefinition attribute, String newName) {
        if (this.enables(attribute)) {
            this.addRename(attribute.getName(), newName);
        }
        return thisBuilder();
    }

    @Override
    public T addRename(String attributeName, String newName) {
        registry.addRenamedAttribute(attributeName, newName);
        return thisBuilder();
    }

    @Override
    public T addRenames(Map<String, String> renames) {
        for (Map.Entry<String, String> rename : renames.entrySet()) {
            this.addRename(rename.getKey(), rename.getValue());
        }
        return thisBuilder();
    }


    @Override
    public T setValueConverter(AttributeConverter attributeConverter, AttributeDefinition...convertedAttributes) {
        for (AttributeDefinition convertedAttribute : convertedAttributes) {
            if (this.enables(convertedAttribute)) {
                this.registry.addAttributeConverter(convertedAttribute.getName(), attributeConverter);
            }
        }
        return thisBuilder();
    }

    @Override
    public T setValueConverter(AttributeConverter attributeConverter, String... convertedAttributes) {
        for (String attribute : convertedAttributes) {
            registry.addAttributeConverter(attribute, attributeConverter);
        }
        return thisBuilder();
    }

    protected AttributeTransformationDescriptionBuilderRegistry getLocalRegistry() {
        return registry;
    }

    static class AttributeTransformationDescriptionBuilderRegistry {
        private final Set<String> allAttributes = new HashSet<String>();
        private final Map<String, List<RejectAttributeChecker>> attributeRestrictions = new HashMap<String, List<RejectAttributeChecker>>();
        private final Map<String, DiscardAttributeChecker> discardedAttributes = new HashMap<String, DiscardAttributeChecker>();
        private final Map<String, String> renamedAttributes = new HashMap<String, String>();
        private final Map<String, AttributeConverter> convertedAttributes = new HashMap<String, AttributeConverter>();


        void addToAllAttributes(String attributeName) {
            if (!allAttributes.contains(attributeName)) {
                allAttributes.add(attributeName);
            }
        }

        void addAttributeCheck(final String attributeName, final RejectAttributeChecker checker) {
            addToAllAttributes(attributeName);
            List<RejectAttributeChecker> checkers = attributeRestrictions.get(attributeName);
            if(checkers == null) {
                checkers = new ArrayList<RejectAttributeChecker>();
                attributeRestrictions.put(attributeName, checkers);
            }
            checkers.add(checker);
        }

        void setDiscardedAttribute(DiscardAttributeChecker discardChecker, String attributeName) {
            assert discardChecker != null : "Null discard checker";
            assert !discardedAttributes.containsKey(attributeName) : "Discard already set";
            addToAllAttributes(attributeName);
            discardedAttributes.put(attributeName, discardChecker);
        }

        void addRenamedAttribute(String attributeName, String newName) {
            assert !renamedAttributes.containsKey(attributeName) : "Rename already set";
            addToAllAttributes(attributeName);
            renamedAttributes.put(attributeName, newName);
        }

        void addAttributeConverter(String attributeName, AttributeConverter attributeConverter) {
            addToAllAttributes(attributeName);
            convertedAttributes.put(attributeName, attributeConverter);
        }

        Map<String, AttributeTransformationDescription> buildAttributes(){
            Map<String, AttributeTransformationDescription> attributes = new HashMap<String, AttributeTransformationDescription>();
            for (String name : allAttributes) {
                List<RejectAttributeChecker> checkers = attributeRestrictions.get(name);
                String newName = renamedAttributes.get(name);
                DiscardAttributeChecker discardChecker = discardedAttributes.get(name);
                attributes.put(name, new AttributeTransformationDescription(name, checkers, newName, discardChecker, convertedAttributes.get(name)));
            }
            return attributes;
        }
    }

    protected static AttributeTransformationDescriptionBuilderRegistry mergeRegistries(AttributeTransformationDescriptionBuilderRegistry one, AttributeTransformationDescriptionBuilderRegistry two) {
        final AttributeTransformationDescriptionBuilderRegistry result = new AttributeTransformationDescriptionBuilderRegistry();

        result.allAttributes.addAll(one.allAttributes);
        result.allAttributes.addAll(two.allAttributes);
        result.attributeRestrictions.putAll(one.attributeRestrictions);
        result.attributeRestrictions.putAll(two.attributeRestrictions);
        result.discardedAttributes.putAll(one.discardedAttributes);
        result.discardedAttributes.putAll(two.discardedAttributes);
        result.renamedAttributes.putAll(one.renamedAttributes);
        result.renamedAttributes.putAll(two.renamedAttributes);
        result.convertedAttributes.putAll(one.convertedAttributes);
        result.convertedAttributes.putAll(two.convertedAttributes);

        return result;
    }

    /**
     * @return this builder
     */
    protected abstract T thisBuilder();

}
