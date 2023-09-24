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
public class AllRequiredSet extends AccessRequirementSet {

    /* (non-Javadoc)
     * @see org.jboss.as.cli.accesscontrol.BaseAccessRequirement#checkAccess(org.jboss.as.cli.CommandContext)
     */
    @Override
    protected boolean checkAccess(CommandContext ctx) {
        for(AccessRequirement req : requirements) {
            if(!req.isSatisfied(ctx)) {
                return false;
            }
        }
        return true;
    }
}
