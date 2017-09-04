/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.wildfly.core.cli.command.aesh;

import java.util.Collection;
import java.util.List;
import org.aesh.readline.AeshContext;
import org.aesh.command.Command;
import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.registry.CommandRegistry;
import org.aesh.readline.terminal.formatting.TerminalString;
import org.jboss.as.cli.CommandContext;

/**
 *
 * @author jdenise@redhat.com
 */
public class CLICompleterInvocation implements CompleterInvocation {

    private final CompleterInvocation delegate;
    private final CommandContext commandContext;
    private final CommandRegistry registry;

    public CLICompleterInvocation(CompleterInvocation delegate, CommandContext ctx,
            CommandRegistry registry) {
        this.delegate = delegate;
        this.commandContext = ctx;
        this.registry = registry;
    }

    public CommandRegistry getCommandRegistry() {
        return registry;
    }

    @Override
    public String getGivenCompleteValue() {
        return delegate.getGivenCompleteValue();
    }

    @Override
    public Command getCommand() {
        return delegate.getCommand();
    }

    @Override
    public List<TerminalString> getCompleterValues() {
        return delegate.getCompleterValues();
    }

    @Override
    public void setCompleterValuesTerminalString(List<TerminalString> terminalStrings) {
        delegate.setCompleterValuesTerminalString(terminalStrings);
    }

    @Override
    public void clearCompleterValues() {
        delegate.clearCompleterValues();
    }

    @Override
    public void addCompleterValue(String s) {
        delegate.addCompleterValue(s);
    }

    @Override
    public void addCompleterValueTerminalString(TerminalString terminalString) {
        delegate.addCompleterValueTerminalString(terminalString);
    }

    @Override
    public boolean isAppendSpace() {
        return delegate.isAppendSpace();
    }

    @Override
    public void setAppendSpace(boolean b) {
        delegate.setAppendSpace(b);
    }

    @Override
    public void setIgnoreOffset(boolean ignoreOffset) {
        delegate.setIgnoreOffset(ignoreOffset);
    }

    @Override
    public boolean doIgnoreOffset() {
        return delegate.doIgnoreOffset();
    }

    @Override
    public void setOffset(int offset) {
        delegate.setOffset(offset);
    }

    @Override
    public int getOffset() {
        return delegate.getOffset();
    }

    @Override
    public void setIgnoreStartsWith(boolean ignoreStartsWith) {
        delegate.setIgnoreStartsWith(ignoreStartsWith);
    }

    @Override
    public boolean isIgnoreStartsWith() {
        return delegate.isIgnoreStartsWith();
    }

    @Override
    public AeshContext getAeshContext() {
        return delegate.getAeshContext();
    }

    public CommandContext getCommandContext() {
        return commandContext;
    }

    @Override
    public void setCompleterValues(Collection<String> completerValues) {
        delegate.setCompleterValues(completerValues);
    }

    @Override
    public void addAllCompleterValues(Collection<String> completerValues) {
        delegate.addAllCompleterValues(completerValues);
    }
}
