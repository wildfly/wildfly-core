/*
 * Copyright 2017 Red Hat, Inc.
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

package org.wildfly.test.security.common.elytron;

import java.util.Objects;

/**
 * Abstract parent for {@link ConfigurableElement} implementations. It just holds common fields and provides parent for
 * builders.
 *
 * @author Josef Cacek
 */
public abstract class AbstractConfigurableElement implements ConfigurableElement {

    protected final String name;

    protected AbstractConfigurableElement(Builder<?> builder) {
        this.name = Objects.requireNonNull(builder.name, "Configuration name must not be null");
    }

    @Override
    public final String getName() {
        return name;
    }

    /**
     * Builder to build {@link AbstractConfigurableElement}.
     */
    public abstract static class Builder<T extends Builder<T>> {
        private String name;

        protected Builder() {
        }

        protected abstract T self();

        public final T withName(String name) {
            this.name = name;
            return self();
        }

    }

}
