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


/**
 * Represents an additional module attached to a top level deployment.
 *
 * @author Stuart Douglas
 *
 */
public class AdditionalModuleSpecification extends ModuleSpecification implements Attachable {

    private final String moduleName;

    private final List<ResourceRoot> resourceRoots;

    public AdditionalModuleSpecification(String moduleName, ResourceRoot resourceRoot) {
        this.moduleName = moduleName;
        this.resourceRoots = new ArrayList<>(2);
        this.resourceRoots.add(resourceRoot);
    }

    public AdditionalModuleSpecification(String moduleName, Collection<ResourceRoot> resourceRoots) {
        this.moduleName = moduleName;
        this.resourceRoots = new ArrayList<>(resourceRoots);
    }

    public String getModuleName() {
        return moduleName;
    }


    /** @deprecated unused method will be removed */
    @Deprecated(forRemoval = true)
    public void addResourceRoot(ResourceRoot resourceRoot) {
        this.resourceRoots.add(resourceRoot);
    }


    /** @deprecated unused method will be removed */
    @Deprecated(forRemoval = true)
    public void addResourceRoots(Collection<ResourceRoot> resourceRoots) {
        this.resourceRoots.addAll(resourceRoots);
    }


    public List<ResourceRoot> getResourceRoots() {
        return Collections.unmodifiableList(resourceRoots);
    }
}
