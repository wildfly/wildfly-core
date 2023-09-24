/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.auth;

import java.io.IOException;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_MANAGEMENT_INTERFACE;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OptionCompleters;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommandActivator;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ManagementInterfaces;
import org.jboss.as.cli.operation.OperationFormatException;

/**
 * Reorder sasl mechanisms of a sasl factory attached to a management interface.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "reorder-sasl-management", description = "", activator = SecurityCommandActivator.class)
public class ManagementReorderSASLCommand extends AbstractReorderSASLCommand {

    @Option(name = OPT_MANAGEMENT_INTERFACE, hasValue = true,
            completer = OptionCompleters.ManagementInterfaceCompleter.class)
    String managementInterface;

    @Override
    public String getSASLFactoryName(CommandContext ctx) throws IOException, OperationFormatException {
        return ManagementInterfaces.getManagementInterfaceSaslFactoryName(managementInterface, ctx);
    }

}
