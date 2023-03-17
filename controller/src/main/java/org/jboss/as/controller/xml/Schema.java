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

import javax.xml.namespace.QName;

import org.jboss.staxmapper.Namespace;

/**
 * A namespace qualified XML attribute or element.
 * @author Paul Ferraro
 */
public interface Schema {

    /**
     * Returns the local name of this attribute/element.
     * @return the local name of this attribute/element.
     */
    String getLocalName();

    /**
     * Returns the namespace of this attribute/element.
     * @return the namespace of this attribute/element.
     */
    Namespace getNamespace();

    /**
     * Returns the qualified name of this attribute/element.
     * @return the qualified name of this attribute/element.
     */
    default QName getQualifiedName() {
        return new QName(this.getNamespace().getUri(), this.getLocalName());
    }
}
