/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
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

import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.msc.service.ServiceName;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.extension.elytron.capabilities.PrincipalTransformer;
import org.wildfly.security.auth.principal.NamePrincipal;

import java.io.IOException;

/**
 * @author <a href="mailto:jkalina@redhat.com">Jan Kalina</a>
 */
public class MappersTestCase extends AbstractSubsystemBaseTest {
    public MappersTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("mappers-test.xml");
    }

    /* principal-transformers test - rewriting e-mail addresses by server part */
    @Test
    public void testPrincipalTransformerTree() throws Exception {
        KernelServices services = super.createKernelServicesBuilder(new TestEnvironment()).setSubsystemXmlResource("mappers-test.xml").build();
        if (!services.isSuccessfulBoot()) {
            Assert.fail(services.getBootError().toString());
        }

        ServiceName serviceName = Capabilities.PRINCIPAL_TRANSFORMER_RUNTIME_CAPABILITY.getCapabilityServiceName("tree");
        PrincipalTransformer transformer = (PrincipalTransformer) services.getContainer().getService(serviceName).getValue();
        Assert.assertNotNull(transformer);

        Assert.assertEquals("alpha@jboss.org", transformer.apply(new NamePrincipal("alpha@jboss.com")).getName()); // com to org
        Assert.assertEquals("beta", transformer.apply(new NamePrincipal("beta@wildfly.org")).getName()); // remove server part
        Assert.assertEquals("gamma@example.com", transformer.apply(new NamePrincipal("gamma@example.com")).getName()); // keep
        Assert.assertEquals(null, transformer.apply(new NamePrincipal("invalid"))); // not an e-mail address
        Assert.assertEquals(null, transformer.apply(new NamePrincipal(null)));
        Assert.assertEquals(null, transformer.apply(null));
    }
}
