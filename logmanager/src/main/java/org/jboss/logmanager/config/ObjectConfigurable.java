/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.logmanager.config;

/**
 * A configurable object with a specific class name.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ObjectConfigurable<T> {

    /**
     * Get the module name for this object's configuration, if any.  If JBoss Modules is not present
     * on the class path, only {@code null} values are accepted.
     *
     * @return the module name, or {@code null} if none is configured
     */
    String getModuleName();

    /**
     * Get the class name for this object's configuration.
     *
     * @return the class name
     */
    String getClassName();

    /**
     * Returns the instance associated with this configuration or {@code null} if no instance has yet been created.
     * <p>
     * Any changes to the instance will not be recognized by the configuration API.
     * </p>
     *
     * @return the instance associated with this configuration or {@code null}
     */
    T getInstance();
}
