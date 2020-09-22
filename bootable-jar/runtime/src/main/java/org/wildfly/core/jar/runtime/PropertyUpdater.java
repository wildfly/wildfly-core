/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2020 Red Hat, Inc., and individual contributors
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

package org.wildfly.core.jar.runtime;

/**
 * A simple interface for setting properties.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@FunctionalInterface
interface PropertyUpdater {

    /**
     * Sets the property.
     *
     * @param name  the name of the property
     * @param value the value of the property
     *
     * @return the previous value if the property existed or {@code null} if the property did not already exist
     */
    String setProperty(String name, String value);
}
