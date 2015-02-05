/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
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

package org.jboss.as.test.manualmode.vault.module;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.parsing.ExtensionParsingContext;




/**
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class TestVaultExtension implements Extension {

    public static final String SUBSYSTEM_NAME = "test-custom-vault";

    @Override
    public void initialize(ExtensionContext context) {
        SubsystemRegistration registration = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(1));
        registration.registerSubsystemModel(new TestVaultSubsystemResourceDescription());
        registration.registerXMLElementWriter(new TestVaultParser());
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        //Don't need a parser, just register a dummy writer in the initialize() method
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, TestVaultParser.NAMESPACE, new TestVaultParser());
    }


}
