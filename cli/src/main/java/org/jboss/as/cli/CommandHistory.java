/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli;

import java.util.List;

/**
 * Represents the history of commands and operations.
 *
 * @author Alexey Loubyansky
 */
public interface CommandHistory {

    /**
     * Returns the history as a list of strings.
     * @return history as a list of strings.
     */
    List<String> asList();

    /**
     * Returns a boolean indicating whether the history is enabled or not.
     * @return  true in case the history is enabled, false otherwise.
     */
    boolean isUseHistory();

    /**
     * Enables or disables history.
     * @param useHistory true enables history, false disables it.
     */
    void setUseHistory(boolean useHistory);

    /**
     * Clears history.
     */
    void clear();

    /**
     * The maximum length of the history log.
     *
     * @return maximum length of the history log
     */
    int getMaxSize();
}
