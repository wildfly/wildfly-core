/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd.security.auth;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand;
import static org.jboss.as.cli.impl.aesh.cmd.security.SecurityCommand.OPT_MECHANISM;
import org.jboss.as.cli.impl.aesh.cmd.security.model.AuthFactorySpec;

/**
 *
 * @author jdenise@redhat.com
 */
@CommandDefinition(name = "abstract-management-auth-enable", description = "")
public abstract class AbstractMgmtEnableAuthenticationCommand extends AbstractEnableAuthenticationCommand {

    @Option(name = OPT_MECHANISM,
            completer = SecurityCommand.OptionCompleters.MechanismCompleter.class)
    String mechanism;

    public AbstractMgmtEnableAuthenticationCommand(AuthFactorySpec factorySpec) {
        super(factorySpec);
    }

    @Override
    protected String getMechanism() {
        return mechanism;
    }

}
