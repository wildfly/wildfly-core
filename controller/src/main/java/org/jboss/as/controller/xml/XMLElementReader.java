/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

/**
 * Adds absentee element handling to an {link org.jboss.staxmapper.XMLElementReader}.
 * @author Paul Ferraro
 * @param <C> the reader context type
 */
public interface XMLElementReader<C> extends org.jboss.staxmapper.XMLElementReader<C>, XMLComponentReader<C> {

    @Override
    default void whenAbsent(C context) {
        // Do nothing
    }
}
