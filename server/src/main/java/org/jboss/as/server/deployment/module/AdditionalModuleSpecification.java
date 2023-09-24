/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jboss.as.server.deployment.Attachable;
import org.jboss.modules.ModuleIdentifier;


/**
 * Represents an additional module attached to a top level deployment.
 *
 * @author Stuart Douglas
 *
 */
public class AdditionalModuleSpecification extends ModuleSpecification implements Attachable {

    private final ModuleIdentifier moduleIdentifier;

    private final List<ResourceRoot> resourceRoots;

    public AdditionalModuleSpecification(ModuleIdentifier moduleIdentifier, ResourceRoot resourceRoot) {
        this.moduleIdentifier = moduleIdentifier;
        this.resourceRoots = new ArrayList<ResourceRoot>();
        this.resourceRoots.add(resourceRoot);
    }

    public AdditionalModuleSpecification(ModuleIdentifier moduleIdentifier, Collection<ResourceRoot> resourceRoots) {
        this.moduleIdentifier = moduleIdentifier;
        this.resourceRoots = new ArrayList<ResourceRoot>(resourceRoots);
    }

    public ModuleIdentifier getModuleIdentifier() {
        return moduleIdentifier;
    }


    public void addResourceRoot(ResourceRoot resourceRoot) {
        this.resourceRoots.add(resourceRoot);
    }


    public void addResourceRoots(Collection<ResourceRoot> resourceRoots) {
        this.resourceRoots.addAll(resourceRoots);
    }


    public List<ResourceRoot> getResourceRoots() {
        return Collections.unmodifiableList(resourceRoots);
    }
}
