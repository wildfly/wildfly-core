/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.arguments;

import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.ParsingContext;

/**
 *
 * @author jdenise@redhat.com
 */
public class BytesValueState extends DefaultParsingState {

    public static final String ID = "BYTES_VALUE";

    public static final BytesValueState INSTANCE = new BytesValueState();

    public BytesValueState() {
        super(ID);
        setDefaultHandler((ParsingContext ctx) -> {
            final char c = ctx.getCharacter();
            if (c == '}') {
                ctx.leaveState();
            } else {
                ctx.getCallbackHandler().character(ctx);
            }
        });
    }
}
