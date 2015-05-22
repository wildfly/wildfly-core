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

package org.jboss.as.test.integration.security;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.jboss.as.test.module.util.TestModule;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class PicketBoxModuleUtil {

    public static TestModule createTestModule() throws Exception {
        try (InputStream is = PicketBoxModuleUtil.class.getClassLoader().getResourceAsStream("picketbox-module.xml")) {
            Path moduleXml = Files.createTempFile("pl-module", ".xml");
            Files.copy(is, moduleXml, StandardCopyOption.REPLACE_EXISTING);
            TestModule picketLinkModule = new TestModule("org.picketbox", moduleXml.toFile());
            picketLinkModule.create(true);
            Files.delete(moduleXml);
            return picketLinkModule;
        }
    }
}
