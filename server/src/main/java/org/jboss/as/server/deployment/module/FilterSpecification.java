/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import java.io.Serializable;
import java.util.Objects;

import org.jboss.modules.filter.PathFilter;

/**
* @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
*/
public final class FilterSpecification implements Serializable {

    private static final long serialVersionUID = -4233637179300139418L;

    private final PathFilter pathFilter;
    private final boolean include;

    public FilterSpecification(final PathFilter pathFilter, final boolean include) {
        this.pathFilter = pathFilter;
        this.include = include;
    }

    public PathFilter getPathFilter() {
        return pathFilter;
    }

    public boolean isInclude() {
        return include;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FilterSpecification that = (FilterSpecification) o;
        return include == that.include && pathFilter.equals(that.pathFilter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pathFilter, include);
    }
}
