/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.parsing.command;

import org.jboss.as.cli.CommandFormatException;

/**
 *
 * @author Bartosz Spyrko-Smietanko
 */
public class ArgumentValueNotFinishedException extends CommandFormatException {
    public ArgumentValueNotFinishedException(String message) {
        super(message);
    }
}
