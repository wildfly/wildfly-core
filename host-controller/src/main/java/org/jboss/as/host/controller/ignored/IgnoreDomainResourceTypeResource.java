/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.ignored;

import static org.jboss.as.host.controller.ignored.IgnoredDomainTypeResourceDefinition.NAMES;
import static org.jboss.as.host.controller.ignored.IgnoredDomainTypeResourceDefinition.WILDCARD;

import java.util.LinkedHashSet;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.dmr.ModelNode;

/**
 * {@link org.jboss.as.controller.registry.Resource} implementation for a given type of ignored resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class IgnoreDomainResourceTypeResource extends PlaceholderResource.PlaceholderResourceEntry {

    private IgnoredDomainResourceRoot parent;
    private Boolean hasWildcard;
    private final LinkedHashSet<String> model = new LinkedHashSet<String>();

    /**
     * Constructor for use by operation step handlers.
     *
     * @param type the name of the type some of whose resources are to be ignored
     * @param names the specific instances of type that should be ignored. Either {@link org.jboss.dmr.ModelType#LIST}
     *              or {@link org.jboss.dmr.ModelType#UNDEFINED}; cannot be {@code null}
     * @param wildcard {@code true} if all resources of the type should be matched. Use {@code null} to indicate
     *                 this is undefined by the user, meaning {@code false} in practical effect
     */
    public IgnoreDomainResourceTypeResource(String type, final ModelNode names, final Boolean wildcard) {
        super(ModelDescriptionConstants.IGNORED_RESOURCE_TYPE, type);
        setNames(names);
        setWildcard(wildcard);
    }

    private IgnoreDomainResourceTypeResource(IgnoreDomainResourceTypeResource toCopy) {
        super(ModelDescriptionConstants.IGNORED_RESOURCE_TYPE, toCopy.getName());
        synchronized (toCopy.model) {
            model.addAll(toCopy.model);
        }
        this.hasWildcard = toCopy.hasWildcard;
        this.parent = toCopy.parent;
    }

    @Override
    public boolean isRuntime() {
        return false;
    }

    /** {@inheritDoc */
    @Override
    public ModelNode getModel() {
        synchronized (model) {
            // We return what is effectively a copy; force handlers to use writeModel, setWildcard or setNames to modify
            ModelNode result = new ModelNode();
            ModelNode wildcard = result.get(WILDCARD.getName());
            if (hasWildcard != null) {
                wildcard.set(hasWildcard.booleanValue());
            }
            ModelNode names = result.get(NAMES.getName());
            synchronized (model) {
                for (String name : model) {
                    names.add(name);
                }
            }
            return result;
        }
    }

    /** {@inheritDoc */
    @Override
    public void writeModel(ModelNode newModel) {
        synchronized (model) {
            if (newModel.hasDefined(WILDCARD.getName())) {
                setWildcard(newModel.get(WILDCARD.getName()).asBoolean());
            }
            setNames(newModel.get(NAMES.getName()));
        }
    }

    /** {@inheritDoc */
    @Override
    public boolean isModelDefined() {
        return true;
    }

    /** {@inheritDoc */
    @Override
    public IgnoreDomainResourceTypeResource clone() {
        return new IgnoreDomainResourceTypeResource(this);
    }

    void setParent(IgnoredDomainResourceRoot parent) {
        this.parent = parent;
    }

    void setNames(ModelNode names) {
        synchronized (model) {
            model.clear();
            if (names.isDefined()) {
                for (ModelNode name : names.asList()) {
                    String nameStr = name.asString();
                    model.add(nameStr);
                }
            }
        }
    }

    void publish() {
        parent.publish();
    }

    boolean hasName(String name) {
        synchronized (model) {
            return (hasWildcard != null && hasWildcard.booleanValue()) || model.contains(name);
        }
    }

    public void setWildcard(Boolean wildcard) {
        synchronized (model) {
            this.hasWildcard = wildcard;
        }
    }
}
