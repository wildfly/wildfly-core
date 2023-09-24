/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.arguments;

import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.EnterStateCharacterHandler;

/**
 *
 * @author Alexey Loubyansky
 */
public class ArgumentValueInitialState extends DefaultParsingState {

    public static final ArgumentValueInitialState INSTANCE = new ArgumentValueInitialState();

    public ArgumentValueInitialState() {
        super("ARG_VALUE_INIT");
        final CompositeState complexValueState = new CompositeState(ArgumentValueState.INSTANCE);
        setDefaultHandler(new EnterStateCharacterHandler(complexValueState));
        enterState('[', complexValueState);
        enterState('{', complexValueState);
    }
}
