/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.cli.impl.aesh.cmd.security;

import java.util.Iterator;
import java.util.function.Function;
import org.aesh.command.Command;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.accesscontrol.AccessRequirement;
import org.jboss.as.cli.operation.CommandLineParser;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestAddress;
import org.jboss.as.cli.parsing.ParserUtil;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 * An abstract command that depends on the presence of a path and permissions.
 *
 * @author jdenise@redhat.com
 */
public abstract class AbstractControlledCommand implements Command<CLICommandInvocation> {

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

    public OperationRequestAddress getRequiredAddress() {
        return requiredAddress;
    }

    public boolean isDependsOnProfile() {
        return dependsOnProfile;
    }

    public AccessRequirement getAccessRequirement() {
        if (accessRequirement == null) {
            accessRequirement = acBuilder.apply(ctx);
        }
        return accessRequirement;
    }

    public String getRequiredType() {
        return requiredType;
    }
}
