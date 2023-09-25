/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.controller.descriptions;

/**
 * Various constants used in descriptions of server model resources.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 *
 */
public final class ServerDescriptionConstants {

    private ServerDescriptionConstants() {}

    public static final String PROFILE_NAME = "profile-name";

    public static final String SERVER_ENVIRONMENT = "server-environment";

    // replaces server-state
    public static final String RUNTIME_CONFIGURATION_STATE = "runtime-configuration-state";

    /**
     * @deprecated see {@link #RUNTIME_CONFIGURATION_STATE}
     */
    public static final String PROCESS_STATE = "server-state";

    public static final String PROCESS_TYPE = "process-type";

    public static final String LAUNCH_TYPE = "launch-type";

    public static final String RUNNING_MODE = "running-mode";
}
