/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Alexey Loubyansky
 */
public class DefaultCharacterHandlerMap implements CharacterHandlerMap {

    private Map<Character, CharacterHandler> handlers = Collections.emptyMap();

    @Override
    public CharacterHandler getHandler(char ch) {
        return handlers.get(ch);
    }

    @Override
    public void putHandler(char ch, CharacterHandler handler) {
        if(handlers.isEmpty()) {
            handlers = new HashMap<Character, CharacterHandler>();
        }
        handlers.put(ch, handler);
    }

    @Override
    public void removeHandler(char ch) {
        handlers.remove(ch);
    }

}
