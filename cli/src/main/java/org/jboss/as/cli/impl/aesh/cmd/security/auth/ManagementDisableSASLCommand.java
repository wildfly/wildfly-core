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

import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthFactorySpec;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.jboss.as.cli.CommandContext;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_MANAGEMENT_INTERFACE;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OptionCompleters;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommandActivator;
import org.jboss.as.cli.impl.aesh.cmd.security.model.DefaultResourceNames;
import org.jboss.as.cli.impl.aesh.cmd.security.model.ManagementInterfaces;
import org.jboss.dmr.ModelNode;

/**
 * Disable SASL authentication applied to a management interface.
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "disable-sasl-management", description = "", activator = SecurityCommandActivator.class)
public class ManagementDisableSASLCommand extends AbstractMgmtDisableAuthenticationCommand {

    @Option(name = OPT_MANAGEMENT_INTERFACE, hasValue = true,
            completer = OptionCompleters.ManagementInterfaceCompleter.class)
    String managementInterface;

    public ManagementDisableSASLCommand() {
        super(AuthFactorySpec.SASL);
    }

    @Override
    public String getEnabledFactory(CommandContext ctx) throws Exception {
        return ManagementInterfaces.getManagementInterfaceSaslFactoryName(managementInterface, ctx);
    }

    @Override
    protected ModelNode disableFactory(CommandContext context) throws Exception {
        if (managementInterface == null) {
            managementInterface = DefaultResourceNames.getDefaultManagementInterfaceName(context);
        }
        ModelNode request = ManagementInterfaces.disableSASL(context, managementInterface);
        return request;
    }

    @Override
    protected String getSecuredEndpoint(CommandContext ctx) {
        if (managementInterface == null) {
            managementInterface = DefaultResourceNames.getDefaultManagementInterfaceName(ctx);
        }
        return "management " + managementInterface;
    }

}
