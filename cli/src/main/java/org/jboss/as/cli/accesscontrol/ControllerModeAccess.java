/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.accesscontrol;

import static org.wildfly.common.Assert.checkNotNullParam;

import org.jboss.as.cli.CommandContext;

/**
 * @author Alexey Loubyansky
 *
 */
public class ControllerModeAccess extends BaseAccessRequirement {

    public enum Mode {
        DOMAIN, STANDALONE
    }

    private final Mode mode;
    private AccessRequirement requirement = AccessRequirement.NONE;

    ControllerModeAccess(Mode mode) {
        this.mode = checkNotNullParam("mode", mode);
    }

    public void setRequirement(AccessRequirement requirement) {
        this.requirement = checkNotNullParam("requirement", requirement);
    }

    @Override
    protected boolean checkAccess(CommandContext ctx) {
        if(ctx.isDomainMode()) {
            return mode == Mode.DOMAIN ? requirement.isSatisfied(ctx) : false;
        }
        return mode == Mode.STANDALONE ? requirement.isSatisfied(ctx) : false;
    }

    @Override
    public String toString() {
        return mode + " " + requirement;
    }
}
