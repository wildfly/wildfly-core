/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
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
package org.wildfly.extension.elytron;

import java.util.Map;

/**
 * A general purpose interface that can be implemented by custom implementations being plugged into the Elytron subsystem.
 *
 * Where custom components implement this interface they can be dynamically be configured by the subsystem with
 * a {@link Map<String, String>}.
 *
 * @deprecated it is sufficient to implement {@code initialize(Map&lt;String, String&gt;)} method in custom component
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@Deprecated
public interface Configurable {

    /**
     * Initialize the {@link Configurable} class with the specified options.
     *
     * @param configuration
     */
    void initialize(final Map<String, String> configuration);

}
