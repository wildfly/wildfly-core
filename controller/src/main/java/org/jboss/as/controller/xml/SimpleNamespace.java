/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import org.jboss.staxmapper.Namespace;

/**
 * Simple {@link Namespace} implementation.
 * @author Paul Ferraro
 */
public class SimpleNamespace implements Namespace {

    private final String uri;

    public SimpleNamespace(String uri) {
        this.uri = uri;
    }

    @Override
    public String getUri() {
        return this.uri;
    }

    @Override
    public int hashCode() {
        return this.uri.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        return (object instanceof Namespace) && this.uri.equals(((Namespace) object).getUri());
    }

    @Override
    public String toString() {
        return this.uri;
    }
}
