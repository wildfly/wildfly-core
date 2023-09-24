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
@CommandDefinition(name = "abstract-management-auth-disable", description = "")
public abstract class AbstractMgmtDisableAuthenticationCommand extends AbstractDisableAuthenticationCommand {

    @Option(name = OPT_MECHANISM,
            completer = SecurityCommand.OptionCompleters.MechanismDisableCompleter.class)
    String mechanism;

    public AbstractMgmtDisableAuthenticationCommand(AuthFactorySpec factorySpec) {
        super(factorySpec);
    }

    @Override
    protected String getMechanism() {
        return mechanism;
    }

}
