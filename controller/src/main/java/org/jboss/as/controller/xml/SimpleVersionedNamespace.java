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
package org.jboss.as.controller.xml;

import org.jboss.staxmapper.Versioned;

/**
 * Simple {@link VersionedNamespace} implementation.
 * @author Paul Ferraro
 * @param <V> the namespace version
 * @param <N> the namespace type
 */
public class SimpleVersionedNamespace<V extends Comparable<V>, N extends Versioned<V, N>> extends SimpleNamespace implements VersionedNamespace<V, N> {

    private final V version;

    public SimpleVersionedNamespace(String uri, V version) {
        super(uri);
        this.version = version;
    }

    @Override
    public V getVersion() {
        return this.version;
    }
}
