/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing;

/**
 *
 * @author Alexey Loubyansky
 */
abstract class BaseParsingState implements ParsingState {

    private CharacterHandler enterHandler = GlobalCharacterHandlers.NOOP_CHARACTER_HANDLER;
    private CharacterHandler leaveHandler = GlobalCharacterHandlers.NOOP_CHARACTER_HANDLER;
    private CharacterHandler returnHandler = GlobalCharacterHandlers.NOOP_CHARACTER_HANDLER;
    private CharacterHandler eoc = GlobalCharacterHandlers.NOOP_CHARACTER_HANDLER;
    private final String id;

    BaseParsingState(String id) {
        this.id = id;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.operation.parsing.ParsingState#getId()
     */
    @Override
    public String getId() {
        return id;
    }

    @Override
    public CharacterHandler getEndContentHandler() {
        return eoc;
    }

    public void setEndContentHandler(CharacterHandler handler) {
        if(handler == null) {
            throw new IllegalStateException("The handler can't be null");
        }
        eoc = handler;
    }

    @Override
    public CharacterHandler getReturnHandler() {
        return returnHandler;
    }

    public void setReturnHandler(CharacterHandler handler) {
        if(handler == null) {
            throw new IllegalStateException("The handler can't be null");
        }
        returnHandler = handler;
    }

    @Override
    public CharacterHandler getEnterHandler() {
        return enterHandler;
    }

    public void setEnterHandler(CharacterHandler handler) {
        if(handler == null) {
            throw new IllegalStateException("The handler can't be null");
        }
        enterHandler = handler;
    }

    @Override
    public CharacterHandler getLeaveHandler() {
        return leaveHandler;
    }

    public void setLeaveHandler(CharacterHandler handler) {
        if(handler == null) {
            throw new IllegalStateException("The handler can't be null");
        }
        leaveHandler = handler;
    }

    @Override
    public boolean updateValueIndex() {
        return true;
    }

    @Override
    public boolean lockValueIndex() {
        return false;
    }
}
