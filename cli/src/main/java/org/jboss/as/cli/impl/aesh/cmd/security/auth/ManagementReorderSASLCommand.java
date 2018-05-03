/*
Copyright 2018 Red Hat, Inc.

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
