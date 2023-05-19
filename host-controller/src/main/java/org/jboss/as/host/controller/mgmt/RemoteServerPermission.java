/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.host.controller.mgmt;

import org.wildfly.security.permission.AbstractBooleanPermission;

/**
 * Represents the permission required for a domain server to invoke the server
 * to host handlers.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class RemoteServerPermission extends AbstractBooleanPermission<RemoteServerPermission> {

    public RemoteServerPermission() {
        super();
    }

    /**
     * Construct a new instance.
     *
     * @param name ignored
     */
    public RemoteServerPermission(@SuppressWarnings("unused") final String name) {
    }

    /**
     * Construct a new instance.
     *
     * @param name ignored
     * @param actions ignored
     */
    public RemoteServerPermission(@SuppressWarnings("unused") final String name, @SuppressWarnings("unused") final String actions) {
    }

    private static final RemoteServerPermission INSTANCE = new RemoteServerPermission();

    /**
     * Get the instance of this class.
     *
     * @return the instance of this class
     */
    public static RemoteServerPermission getInstance() {
        return INSTANCE;
    }

}
