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

import java.util.Collection;
import java.util.List;

/**
 * A configurable object which is a container for handlers.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface HandlerContainingConfigurable {

    /**
     * Get the names of the configured handlers.
     *
     * @return the names of the configured handlers
     */
    List<String> getHandlerNames();

    /**
     * Set the names of the configured handlers.
     *
     * @param names the names of the configured handlers
     */
    void setHandlerNames(String... names);

    /**
     * Set the names of the configured handlers.
     *
     * @param names the names of the configured handlers
     */
    void setHandlerNames(Collection<String> names);

    /**
     * Add a handler name to this logger.
     *
     * @param name the handler name
     * @return {@code true} if the name was not already set, {@code false} if it was
     */
    boolean addHandlerName(String name);

    /**
     * Remove a handler name from this logger.
     *
     * @param name the handler name
     * @return {@code true} if the name was removed, {@code false} if it was not present
     */
    boolean removeHandlerName(String name);

}
