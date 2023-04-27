/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.ElytronCommonConstants.POLICY;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;

/**
 * The subsystem parser, which uses stax to read and write to and from xml
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a> *
 * @author Tomaz Cerar
 */
class ElytronSubsystemParser1_2 extends ElytronSubsystemParser1_1 {

    @Override
    String getNameSpace() {
        return ElytronExtension.NAMESPACE_1_2;
    }

    @Override
    PersistentResourceXMLDescription getPolicyParser() {
        return PersistentResourceXMLDescription.builder(PathElement.pathElement(POLICY))
                .addAttribute(PolicyDefinitions.JaccPolicyDefinition.POLICY)
                .addAttribute(PolicyDefinitions.CustomPolicyDefinition.POLICY)
                .build();
    }
}
