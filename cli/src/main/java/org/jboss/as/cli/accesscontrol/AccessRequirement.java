/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.accesscontrol;

import org.jboss.as.cli.CommandContext;

/**
 * @author Alexey Loubyansky
 *
 */
public interface AccessRequirement {

    boolean isSatisfied(CommandContext ctx);

    AccessRequirement NONE = new AccessRequirement() {
        @Override
        public boolean isSatisfied(CommandContext ctx) {
            return true;
        }
        @Override
        public String toString(){
            return "none";
        }
    };
}
