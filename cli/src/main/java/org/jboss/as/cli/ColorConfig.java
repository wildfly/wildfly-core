/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli;

import org.aesh.readline.terminal.formatting.Color;

/**
 * A representation of the CLI color configuration
 * @author <a href="ingo@redhat.com">Ingo Weiss</a>
 */
public interface ColorConfig {
    /**
     * @return If color output if desired
     */
    boolean isEnabled();

    /**
     * @return The color of error messages
     */
    Color getErrorColor();

    /**
     * @return The color of warning messages
     */
    Color getWarnColor();

    /**
     * @return The color of success messages
     */
    Color getSuccessColor();

    /**
     * @return The color of required autocompletion parameters
     */
    Color getRequiredColor();

    /**
     * @return The color of batch and workflow prompts
     */
    Color getWorkflowColor();

    /**
     * @return The color of the prompt
     */
    Color getPromptColor();
}
