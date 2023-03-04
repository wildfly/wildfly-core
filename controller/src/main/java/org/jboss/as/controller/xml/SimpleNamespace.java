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

import org.jboss.staxmapper.Namespace;

/**
 * Simple {@link Namespace} implementation.
 * @author Paul Ferraro
 */
public class SimpleNamespace implements Namespace {

    private final String uri;

    public SimpleNamespace(String uri) {
        this.uri = uri;
    }

    @Override
    public String getUri() {
        return this.uri;
    }

    @Override
    public int hashCode() {
        return this.uri.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        return (object instanceof Namespace) && this.uri.equals(((Namespace) object).getUri());
    }

    @Override
    public String toString() {
        return this.uri;
    }
}
