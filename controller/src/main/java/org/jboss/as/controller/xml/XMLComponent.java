/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

import org.jboss.as.controller.Feature;

/**
 * Encapsulates an XML component, e.g. particle/attribute.
 */
public interface XMLComponent<RC, WC> extends Feature {

    /**
     * Returns the reader of this XML component.
     * @return the reader of this XML component.
     */
    XMLComponentReader<RC> getReader();

    /**
     * Returns the writer of this XML component.
     * @return the writer of this XML component.
     */
    XMLContentWriter<WC> getWriter();
}
