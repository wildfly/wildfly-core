/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers.ifelse;

import java.util.regex.Pattern;

/**
 *
 * @author Thomas Darimont
 */
public class MatchOperation extends SameTypeOperation {

    static final String SYMBOL = "~=";

    MatchOperation() {
        super(SYMBOL);
    }

    @Override
    protected boolean doCompare(Object left, Object right) {

        if(left == null) {
            return right == null;
        }

        String text = String.valueOf(left);
        String pattern = String.valueOf(right);

        Pattern compiledPattern = Pattern.compile(pattern);

        return compiledPattern.matcher(text).matches();
    }
}
