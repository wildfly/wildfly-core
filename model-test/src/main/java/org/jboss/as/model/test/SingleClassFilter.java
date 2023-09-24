/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.model.test;

import org.jboss.modules.filter.ClassFilter;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2013 Red Hat, inc.
 */
public class SingleClassFilter implements ClassFilter {

    private final String filteredClassName;

    SingleClassFilter(String filteredClassName) {
        this.filteredClassName = filteredClassName;
    }

    @Override
    public boolean accept(String className) {
        return className != null && className.startsWith(this.filteredClassName);
    }

    public static ClassFilter createFilter(Class<?> filteredClass) {
        return new SingleClassFilter(filteredClass.getName());
    }

}
