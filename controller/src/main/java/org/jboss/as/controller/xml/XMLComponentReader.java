/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

/**
 * Super-interface for XML component readers with absentee handling.
 * @author Paul Ferraro
 * @param <C> the reader context type
 */
public interface XMLComponentReader<C> {
    /**
     * Triggered when the associated XML content is absent from the XML input.
     * @param context a reader context
     */
    void whenAbsent(C context);
}
