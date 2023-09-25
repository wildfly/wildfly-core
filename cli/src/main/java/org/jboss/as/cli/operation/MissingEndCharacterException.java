/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.operation;


/**
 * @author Alexey Loubyansky
 *
 */
public class MissingEndCharacterException extends OperationFormatException {

    private static final long serialVersionUID = 4801608214419787570L;

    public MissingEndCharacterException(String message) {
        super(message);
    }
}
