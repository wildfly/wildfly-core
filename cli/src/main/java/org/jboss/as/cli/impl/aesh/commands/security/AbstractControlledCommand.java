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
package org.jboss.as.cli.impl.aesh.commands.security;

import java.util.Iterator;
import java.util.function.Function;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.accesscontrol.AccessRequirement;
import org.jboss.as.cli.operation.CommandLineParser;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.as.cli.parsing.ParserUtil;

/**
 *
 * @author jdenise@redhat.com
 */
public abstract class AbstractControlledCommand implements ControlledCommand {

    private AccessRequirement accessRequirement;
    private OperationRequestAddress requiredAddress;
    private boolean dependsOnProfile;
    private String requiredType;
    private final CommandContext ctx;
    private final Function<CommandContext, AccessRequirement> acBuilder;

    protected AbstractControlledCommand(CommandContext ctx, Function<CommandContext, AccessRequirement> acBuilder) {
        this.ctx = ctx;
        this.acBuilder = acBuilder;
    }

    public CommandContext getCommandContext() {
        return ctx;
    }

    protected final void addRequiredPath(String requiredPath) {
        if (requiredPath == null) {
            throw new IllegalArgumentException("Required path can't be null.");
        }
        DefaultOperationRequestAddress requiredAddress = new DefaultOperationRequestAddress();
        CommandLineParser.CallbackHandler handler = new DefaultCallbackHandler(requiredAddress);
        try {
            ParserUtil.parseOperationRequest(requiredPath, handler);
        } catch (CommandFormatException e) {
            throw new IllegalArgumentException("Failed to parse nodeType: " + e.getMessage());
        }
        addRequiredPath(requiredAddress);
    }

    /**
     * Adds a node path which is required to exist before the command can be
     * used.
     *
     * @param requiredPath node path which is required to exist before the
     * command can be used.
     */
    protected void addRequiredPath(OperationRequestAddress requiredPath) {
        if (requiredPath == null) {
            throw new IllegalArgumentException("Required path can't be null.");
        }
        // there perhaps could be more but for now only one is allowed
        if (requiredAddress != null) {
            throw new IllegalStateException("Only one required address is allowed, atm.");
        }
        requiredAddress = requiredPath;

        final Iterator<OperationRequestAddress.Node> iterator = requiredAddress.iterator();
        if (iterator.hasNext()) {
            final String firstType = iterator.next().getType();
            dependsOnProfile = Util.SUBSYSTEM.equals(firstType) || Util.PROFILE.equals(firstType);
        }
        if (requiredAddress.endsOnType()) {
            requiredType = requiredAddress.toParentNode().getType();
        }
    }

    @Override
    public OperationRequestAddress getRequiredAddress() {
        return requiredAddress;
    }

    @Override
    public boolean isDependsOnProfile() {
        return dependsOnProfile;
    }

    @Override
    public AccessRequirement getAccessRequirement() {
        if (accessRequirement == null) {
            accessRequirement = acBuilder.apply(ctx);
        }
        return accessRequirement;
    }

    @Override
    public String getRequiredType() {
        return requiredType;
    }
}
