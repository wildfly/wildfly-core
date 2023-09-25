/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.security.adduser;

import java.io.IOError;
import java.util.IllegalFormatException;

/**
 * Mock the Java console to simulate console commands for
 * test purpose
 *
 * @author <a href="mailto:flemming.harms@gmail.com">Flemming Harms</a>
 */
public class ConsoleMock implements ConsoleWrapper {
    private AssertConsoleBuilder responses;

    public ConsoleMock() {
    }

    public void setResponses(AssertConsoleBuilder responses) {
        this.responses = responses;
    }

    @Override
    public void format(String fmt, Object... args) throws IllegalFormatException {
        responses.assertDisplayText(String.format(fmt,args));
    }

    @Override
    public void printf(String format, Object... args) throws IllegalFormatException {
        responses.assertDisplayText(String.format(format,args));
    }

    @Override
    public String readLine(String fmt, Object... args) throws IOError {
        responses.assertDisplayText(String.format(fmt,args));
        return responses.popAnswer();
    }

    @Override
    public char[] readPassword(String fmt, Object... args) throws IllegalFormatException, IOError {
        responses.assertDisplayText(String.format(fmt,args));
        return responses.popAnswer().toCharArray();
    }

    @Override
    public boolean hasConsole() {
        return true;
    }

}
