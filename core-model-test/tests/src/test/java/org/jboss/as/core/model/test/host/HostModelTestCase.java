package org.jboss.as.core.model.test.host;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.core.model.test.util.ServerConfigInitializers;
import org.jboss.as.model.test.ModelTestUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 */
public class HostModelTestCase extends AbstractCoreModelTest {

    @Test
    public void testDefaultHostXml() throws Exception {
        doHostXml("host.xml");
    }

    @Test
    public void testDefaultHostXmlWithExpressions() throws Exception {
        doHostXml("host-with-expressions.xml");
    }

    @Test
    public void testWFLY2870() throws Exception {
        doHostXml("host-with-secure-interface.xml");
    }

    @Test
    public void testSocketBindingDefaultInterface() throws Exception {
        doHostXml("host-with-default-interface.xml");
    }

    @Test
    public void testWFLY75() throws Exception {
        doRemoteHostXml("host-remote-domain-manager.xml");
    }

    @Test
    public void testRemoteWithAuthenticationContext() throws Exception {
        doRemoteHostXml("host-http-remoting-domain-manager-ac.xml");
    }

    @Test
    public void testWFLY75HttpRemoting() throws Exception {
        doRemoteHostXml("host-http-remoting-domain-manager.xml");
    }

    @Test
    public void testHostXmlWithServerSSL() throws Exception {
        doHostXml("host-with-server-ssl.xml");
    }

    private void doHostXml(String hostXmlFile) throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder(TestModelType.HOST)
                .setXmlResource(hostXmlFile)
                .setModelInitializer(ServerConfigInitializers.XML_MODEL_INITIALIZER, null)
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());
        String xml = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), hostXmlFile), xml);
        ModelTestUtils.validateModelDescriptions(PathAddress.EMPTY_ADDRESS, kernelServices.getRootRegistration());
    }

    private void doRemoteHostXml(String hostXmlFile) throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder(TestModelType.HOST)
                .setXmlResource(hostXmlFile)
                .setModelInitializer(ServerConfigInitializers.XML_MODEL_INITIALIZER, null)
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());
        String xml = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), hostXmlFile), xml);
        ModelTestUtils.validateModelDescriptions(PathAddress.EMPTY_ADDRESS, kernelServices.getRootRegistration());
    }
}
