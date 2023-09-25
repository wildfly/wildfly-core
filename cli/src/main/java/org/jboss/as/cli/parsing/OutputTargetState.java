/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing;


/**
 *
 * @author Alexey Loubyansky
 */
public class OutputTargetState extends DefaultParsingState {

    public static final String ID = "OUT_REDIRECT";
    public static final OutputTargetState INSTANCE = new OutputTargetState();
    public static final char OUTPUT_REDIRECT_CHAR = '>';

    public OutputTargetState() {
        super(ID);
        setDefaultHandler(GlobalCharacterHandlers.CONTENT_CHARACTER_HANDLER);
    }
}
