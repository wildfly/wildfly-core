/*
 * Copyright 2019 Red Hat, Inc.
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

package org.jboss.as.server.parsing;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLMapper;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

/**
 *
 * @author rmartinc
 */
public class SystemPropertiesParsingTestCase {

    private static final String namespace = Namespace.DOMAIN_8_0.getUriString();
    private ControllerLogger mockedLogger;
    private ControllerLogger realLogger;

    @BeforeClass
    public static void workAroundWFLY5637() {
        Assume.assumeTrue("WFCORE-5637 Tests failing if the JDK in use is after 16.", Runtime.version().compareTo(Runtime.Version.parse("17")) < 0);
    }

    @Before
    public void before() throws Exception {
        mockedLogger = Mockito.mock(ControllerLogger.class);
        Field field = ControllerLogger.class.getDeclaredField("ROOT_LOGGER");
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        realLogger = (ControllerLogger) field.get(null);
        field.set(null, mockedLogger);
    }

    @After
    public void after() throws Exception {
        Field field = ControllerLogger.class.getDeclaredField("ROOT_LOGGER");
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(null, realLogger);
    }

    @Test
    public void testSystemPropertyAlreadyExistIsCalled() throws Exception {
        // assign two properties in the system
        System.setProperty("org.jboss.as.server.parsing.test", "test-value");
        System.setProperty("org.jboss.as.server.parsing.secret", "super-secret-value");
        System.setProperty("org.jboss.as.server.parsing.secret-nested", "super-secret-value");

        Mockito.when(mockedLogger.resolverExtensionExpressionsNotAllowed(Mockito.anyString())).thenReturn(new ExpressionResolver.ExpressionResolutionServerException("not allowed"));
        try {
            final String xml = "<?xml version='1.0' encoding='UTF-8'?>"
                    + "<server name=\"example\" xmlns=\"urn:jboss:domain:8.0\">"
                    + "    <system-properties>\n"
                    + "        <property name=\"org.jboss.as.server.parsing.secret\" value=\"${XYZ::vb::password::1}\"/>\n"
                    + "        <property name=\"org.jboss.as.server.parsing.secret-nested\" value=\"${ABC::vb::${not-found:pword}::1}\"/>\n"
                    + "        <property name=\"org.jboss.as.server.parsing.test\" value=\"other-value\"/>\n"
                    + "    </system-properties>\n"
                    + "</server>";
            final XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(xml));
            final ExtensionRegistry extensionRegistry = ExtensionRegistry.builder(ProcessType.STANDALONE_SERVER).build();
            final StandaloneXml parser = new StandaloneXml(null, null, extensionRegistry);
            final List<ModelNode> operationList = new ArrayList<>();
            final XMLMapper mapper = XMLMapper.Factory.create();
            mapper.registerRootElement(new QName(namespace, "server"), parser);
            mapper.parseDocument(operationList, reader);
            // assert the method is called only once for test
            Mockito.verify(mockedLogger, Mockito.times(1)).systemPropertyAlreadyExist(Mockito.anyString());
            Mockito.verify(mockedLogger, Mockito.times(1)).systemPropertyAlreadyExist(Mockito.eq("org.jboss.as.server.parsing.test"));
            // Verify the extension expression resolution failed twice
            //noinspection ThrowableNotThrown
            Mockito.verify(mockedLogger, Mockito.times(2)).resolverExtensionExpressionsNotAllowed(Mockito.anyString());
            //noinspection ThrowableNotThrown
            Mockito.verify(mockedLogger, Mockito.times(1)).resolverExtensionExpressionsNotAllowed(Mockito.eq("${XYZ::vb::password::1}"));
            //noinspection ThrowableNotThrown
            Mockito.verify(mockedLogger, Mockito.times(1)).resolverExtensionExpressionsNotAllowed(Mockito.eq("${ABC::vb::pword::1}"));
        } finally {
            System.clearProperty("org.jboss.as.server.parsing.test");
            System.clearProperty("org.jboss.as.server.parsing.secret");
            System.clearProperty("org.jboss.as.server.parsing.secret-nested");
        }
    }
}
