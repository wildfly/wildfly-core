/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.cli.command.aesh.activator;

import org.aesh.command.impl.internal.ParsedCommand;

/**
 *
 * Activator that hides option from completion and help.
 *
 * @author jdenise@redhat.com
 */
public class HideOptionActivator extends AbstractOptionActivator {

    @Override
    public boolean isActivated(ParsedCommand processedCommand) {
        return false;
    }

}
