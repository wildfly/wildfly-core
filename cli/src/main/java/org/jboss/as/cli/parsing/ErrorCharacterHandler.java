/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing;

import org.jboss.as.cli.operation.MissingEndCharacterException;
import org.jboss.as.cli.operation.OperationFormatException;

/**
*
* @author Alexey Loubyansky
*/
class ErrorCharacterHandler implements CharacterHandler {

    private final String msg;

    ErrorCharacterHandler(String msg) {
        this.msg = msg;
    }

    @Override
    public void handle(ParsingContext ctx)
            throws OperationFormatException {
        final MissingEndCharacterException e = new MissingEndCharacterException(msg);
        ctx.setError(e);
        if(ctx.isStrict()) {
            throw e;
        }
    }
}