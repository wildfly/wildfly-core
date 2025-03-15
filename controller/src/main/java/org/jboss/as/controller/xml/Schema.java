/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.xml;

import javax.xml.namespace.QName;

import org.jboss.staxmapper.Namespace;

/**
 * A namespace qualified XML attribute or element.
 * @author Paul Ferraro
 */
public interface Schema extends QNameResolver {

    /**
     * Returns the local name of this attribute/element.
     * @return the local name of this attribute/element.
     */
    String getLocalName();

    /**
     * Returns the namespace of this attribute/element.
     * @return the namespace of this attribute/element.
     */
    Namespace getNamespace();

    /**
     * Returns the qualified name of this attribute/element.
     * @return the qualified name of this attribute/element.
     */
    default QName getQualifiedName() {
        return this.resolve(this.getLocalName());
    }

    @Override
    default QName resolve(String localName) {
        return new QName(this.getNamespace().getUri(), localName);
    }
}
