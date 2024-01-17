/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.descriptions;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CHILDREN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DYNAMIC;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DYNAMIC_ELEMENTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MAX_OCCURS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MIN_OCCURS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODEL_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NOTIFICATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REQUIRED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STABILITY;
import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityReferenceRecorder;
import org.jboss.as.controller.DeprecationData;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.access.management.AccessConstraintDescriptionProviderUtil;
import org.jboss.as.controller.capability.Capability;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.dmr.ModelNode;

/**
 * Provides a default description of a resource by analyzing the registry metadata.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DefaultResourceDescriptionProvider implements DescriptionProvider {

    private final ImmutableManagementResourceRegistration registration;
    private final ResourceDescriptionResolver descriptionResolver;
    private final DeprecationData deprecationData;

    public DefaultResourceDescriptionProvider(final ImmutableManagementResourceRegistration registration,
                                              final ResourceDescriptionResolver descriptionResolver) {
        this(registration, descriptionResolver, null);
    }

    public DefaultResourceDescriptionProvider(final ImmutableManagementResourceRegistration registration,
                                              final ResourceDescriptionResolver descriptionResolver,
                                              final DeprecationData deprecationData) {
        this.registration = registration;
        this.descriptionResolver = descriptionResolver;
        this.deprecationData = deprecationData;
    }

    @Override
    public ModelNode getModelDescription(Locale locale) {
        ModelNode result = new ModelNode();

        final ResourceBundle bundle = descriptionResolver.getResourceBundle(locale);
        result.get(DESCRIPTION).set(descriptionResolver.getResourceDescription(locale, bundle));

        // Output min and max occurs if they are non-default values
        int minOccurs = registration.getMinOccurs();
        if (minOccurs > 0) {
            result.get(MIN_OCCURS).set(minOccurs);
        }
        int maxOccurs = registration.getMaxOccurs();
        PathAddress pa = checkNotNullParam("pa", registration.getPathAddress());
        if (pa.size() == 0) {
            // Root node has no documented 'default'
            result.get(MAX_OCCURS).set(maxOccurs);
        } else {
            int defaultMax = pa.getLastElement().isWildcard() ? Integer.MAX_VALUE : 1;
            if (maxOccurs != defaultMax) {
                result.get(MAX_OCCURS).set(maxOccurs);
            }
        }
        result.get(STABILITY).set(this.registration.getStability().toString());

        Set<? extends Capability> capabilities = registration.getCapabilities();
        if (capabilities !=null && !capabilities.isEmpty()){
            for (Capability capability: capabilities) {
                ModelNode cap = result.get(ModelDescriptionConstants.CAPABILITIES).add();
                cap.get(NAME).set(capability.getName());
                cap.get(DYNAMIC).set(capability.isDynamicallyNamed());
                if (capability.isDynamicallyNamed()) {
                    PathAddress aliasAddress = createAliasPathAddress(pa);
                    if(aliasAddress.size() > 0) {
                        String[] elements = capability.getDynamicName(aliasAddress).split("\\.\\$");
                        String[] capabilityPatternElements = Arrays.copyOfRange(elements, 1, elements.length);
                        if (capabilityPatternElements.length > 0) {
                            for (String patternElement : capabilityPatternElements) {
                                cap.get(DYNAMIC_ELEMENTS).add(patternElement);
                            }
                        }
                    }
                }
                cap.get(STABILITY).set(capability.getStability().toString());
            }
        }

        for (CapabilityReferenceRecorder requirement : registration.getRequirements()) {
            ModelNode cap = result.get(ModelDescriptionConstants.CAPABILITIES).add();
            cap.get(REQUIRED).set(true);
            cap.get(NAME).set(requirement.getBaseRequirementName());
            String[] segments = requirement.getRequirementPatternSegments(null, createAliasPathAddress(pa));
            if (segments != null && segments.length > 0) {
                cap.get(DYNAMIC).set(true);
                for (String segment : segments) {
                    String elt = segment;
                    if (segment.charAt(0) == '$') {
                        elt = segment.substring(1);
                    }
                    cap.get(DYNAMIC_ELEMENTS).add(elt);
                }
            } else {
                cap.get(DYNAMIC).set(false);
            }
        }

        if (deprecationData != null) {
            ModelNode deprecated = addDeprecatedInfo(result);
            deprecated.get(ModelDescriptionConstants.REASON).set(descriptionResolver.getResourceDeprecatedDescription(locale, bundle));
        }
        if (registration.isRuntimeOnly()){
            result.get(ModelDescriptionConstants.STORAGE).set(ModelDescriptionConstants.RUNTIME_ONLY);
        }
        AccessConstraintDescriptionProviderUtil.addAccessConstraints(result, registration.getAccessConstraints(), locale);

        // Sort the attribute descriptions based on attribute group and then attribute name
        Set<String> attributeNames = registration.getAttributeNames(PathAddress.EMPTY_ADDRESS);

        //fix of WFCORE-1985 - attributes have to be sorted firstly by name
        //case that two attributes of the same name are allowed is not possible, see error WFLYCTL0043
        Comparator<AttributeDefinition.NameAndGroup> comparator = new Comparator<AttributeDefinition.NameAndGroup>() {
            @Override
            public int compare(AttributeDefinition.NameAndGroup o1, AttributeDefinition.NameAndGroup o2) {
                int attrCompare = o1.getName().compareTo(o2.getName());
                if (attrCompare != 0) {
                    return attrCompare;
                }
                //if attr compare is the same, use default compare which involves groups
                return o1.compareTo(o2);
            }
        };
        Map<AttributeDefinition.NameAndGroup, ModelNode> sortedDescriptions = new TreeMap<>(comparator);
        for (String attr : attributeNames)  {
            AttributeAccess attributeAccess = registration.getAttributeAccess(PathAddress.EMPTY_ADDRESS, attr);
            AttributeDefinition def = attributeAccess.getAttributeDefinition();
            if (def != null) {
                ModelNode attrDesc = new ModelNode();
                // def will add the description to attrDesc under "attributes" => { attr
                def.addResourceAttributeDescription(attrDesc, descriptionResolver, locale, bundle);
                sortedDescriptions.put(new AttributeDefinition.NameAndGroup(def), attrDesc.get(ATTRIBUTES, attr));
            } else {
                // Just store a placeholder
                sortedDescriptions.put(new AttributeDefinition.NameAndGroup(attr), new ModelNode());
            }
        }

        // Store the sorted descriptions into the overall result
        final ModelNode attributes = result.get(ATTRIBUTES).setEmptyObject();
        for (Map.Entry<AttributeDefinition.NameAndGroup, ModelNode> entry : sortedDescriptions.entrySet()) {
            attributes.get(entry.getKey().getName()).set(entry.getValue());
        }

        result.get(OPERATIONS); // placeholder

        result.get(NOTIFICATIONS); // placeholder

        final ModelNode children = result.get(CHILDREN).setEmptyObject();

        Set<PathElement> childAddresses = registration.getChildAddresses(PathAddress.EMPTY_ADDRESS);
        Set<String> childTypes = new HashSet<String>();
        for (PathElement childAddress : childAddresses) {
            String key = childAddress.getKey();
            if (childTypes.add(key)) {
                final ModelNode childNode = children.get(key);
                childNode.get(DESCRIPTION).set(descriptionResolver.getChildTypeDescription(key, locale, bundle));
                childNode.get(MODEL_DESCRIPTION); // placeholder
            }
        }

        return result;
    }

    private ModelNode addDeprecatedInfo(final ModelNode model) {
        ModelNode deprecated = model.get(ModelDescriptionConstants.DEPRECATED);
        deprecated.get(ModelDescriptionConstants.SINCE).set(deprecationData.getSince().toString());
        deprecated.get(ModelDescriptionConstants.REASON);
        return deprecated;
    }

    /**
     * Creates an alias address by replacing all wildcard values by $ + key so that the address can be used to obtain a capability pattern.
     * @param pa the registration address to be aliased.
     * @return  the aliased address.
     */
    private PathAddress createAliasPathAddress(PathAddress pa) {
        ImmutableManagementResourceRegistration registry = registration.getParent();
        List<PathElement> elements = new ArrayList<>();
        for(int i = pa.size() - 1; i >=0; i--) {
            PathElement elt = pa.getElement(i);
            ImmutableManagementResourceRegistration childRegistration = registry.getSubModel(PathAddress.pathAddress(PathElement.pathElement(elt.getKey())));
            if(childRegistration == null) {
                elements.add(elt);
            } else {
                elements.add(PathElement.pathElement(elt.getKey(), "$" + elt.getKey()));
            }
            registry = registry.getParent();
        }
        Collections.reverse(elements);
        return PathAddress.pathAddress(elements.toArray(new PathElement[elements.size()]));
    }
}
