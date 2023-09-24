/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.accesscontrol;

import org.jboss.as.cli.CliEvent;
import org.jboss.as.cli.CliEventListener;
import org.jboss.as.cli.CommandContext;
import org.jboss.logging.Logger;


/**
 * @author Alexey Loubyansky
 *
 */
public abstract class BaseAccessRequirement implements AccessRequirement, CliEventListener {

    protected Logger log = Logger.getLogger(getClass());

    private Boolean satisfied;

    @Override
    public boolean isSatisfied(CommandContext ctx) {
        if(satisfied == null) {
            satisfied = checkAccess(ctx);
            log.tracef("%s %s", this, satisfied);
        }
        return satisfied;
    }

    @Override
    public void cliEvent(CliEvent event, CommandContext ctx) {
        if(event == CliEvent.DISCONNECTED) {
            satisfied = null;
            resetState();
        }
    }

    protected void resetState() {
    }

    protected abstract boolean checkAccess(CommandContext ctx);
}
