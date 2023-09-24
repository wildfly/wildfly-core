/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.rbac;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import org.jboss.as.controller.access.Action;

/**
 * The standard roles in the WildFly management access control mechanism.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public enum StandardRole {

    MONITOR("Monitor", Action.ActionEffect.READ_CONFIG, Action.ActionEffect.READ_RUNTIME),
    //CONFIGURATOR,
    OPERATOR("Operator", Action.ActionEffect.READ_CONFIG, Action.ActionEffect.READ_RUNTIME, Action.ActionEffect.WRITE_RUNTIME),
    MAINTAINER("Maintainer"),
    DEPLOYER("Deployer"),
    ADMINISTRATOR("Administrator"),
    AUDITOR("Auditor"),
    SUPERUSER("SuperUser");

    private final String name;
    private final Set<Action.ActionEffect> allowedActions;

    private StandardRole(String name) {
        this(name, Action.ActionEffect.values());
    }

    private StandardRole(String name, Action.ActionEffect... allowedExcludingAccess) {
        this(name, EnumSet.of(Action.ActionEffect.ADDRESS, allowedExcludingAccess));
    }

    private StandardRole(String name, Set<Action.ActionEffect> allowedActions) {
        this.name = name;
        this.allowedActions = allowedActions;
    }

    public boolean isActionEffectAllowed(Action.ActionEffect actionEffect) {
        return allowedActions.contains(actionEffect);
    }

    public String getFormalName() {
        return name;
    }

    public String getOfficialForm() {
        return toString().toUpperCase(Locale.ENGLISH);
    }

}
