/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

/**
 * Defines the usage of a an XML component.
 * @author Paul Ferraro
 */
public interface XMLUsage {

    /**
     * Indicates whether or not the associated XML component is required.
     * @return true, if the associated XML component is required, false otherwise.
     */
    boolean isRequired();

    /**
     * Indicates whether or not the associated XML component is enabled.
     * @return true, if the associated XML component is enabled, false otherwise.
     */
    boolean isEnabled();
}
