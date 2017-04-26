/*
 * Copyright 2017 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.controller.client;

import java.security.BasicPermission;

/**
 * Permission to execute remote management calls on a server.
 * <p>
 * A permission contains a name (also referred to as a "target name") but
 * no actions list; you either have the named permission or you don't.
 * </p>
 *
 * <p>
 * The target name is the name of the permission. The following table lists all the possible {@link org.jboss.as.controller.client.ModelControllerClientPermission} target names,
 * and for each provides a description of what the permission allows.
 * <ul>
 * <li>performRemoteCall: permission to perform a remote call on the server model controller</li>
 * </ul>
 * </p>
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class ModelControllerClientPermission extends BasicPermission {

    public static final String CONNECT_NAME = "connect";
    public static final String PERFORM_REMOTE_CALL_NAME = "performRemoteCall";
    public static final String PERFORM_IN_VM_CALL_NAME = "performInVmCall";
    private static final String WILDCARD_NAME = "*";

    public static final ModelControllerClientPermission CONNECT = new ModelControllerClientPermission(CONNECT_NAME);
    public static final ModelControllerClientPermission PERFORM_REMOTE_CALL = new ModelControllerClientPermission(PERFORM_REMOTE_CALL_NAME);
    public static final ModelControllerClientPermission PERFORM_IN_VM_CALL = new ModelControllerClientPermission(PERFORM_IN_VM_CALL_NAME);

    private static final long serialVersionUID = 1L;

    public ModelControllerClientPermission(String name) {
        super(name);
    }

    public ModelControllerClientPermission(String name, String actions) {
        super(validatePermissionName(name));
        assert actions == null;
    }

    private static String validatePermissionName(String name) throws IllegalArgumentException {
        switch (name) {
            case CONNECT_NAME:
            case PERFORM_REMOTE_CALL_NAME:
            case PERFORM_IN_VM_CALL_NAME:
            case WILDCARD_NAME:
                return name;
            default:
                throw new IllegalArgumentException(name);
        }
    }
}
