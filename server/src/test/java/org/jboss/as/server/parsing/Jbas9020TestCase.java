/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.parsing;

import org.jboss.as.controller.parsing.Namespace;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLMapper;
import org.junit.Test;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class Jbas9020TestCase {
    private static final String namespace = Namespace.DOMAIN_1_0.getUriString();

    @Test
    public void testContent() throws Exception {
        final String xml = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<server name=\"example\" xmlns=\"urn:jboss:domain:1.0\">" +
                "    <deployments>" +
                "        <deployment name=\"test.war\" runtime-name=\"test-run.war\">" +
                "            <content sha1=\"1234\"/>" +
                "        </deployment>" +
                "    </deployments>" +
                "</server>";
        final XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(xml));
        final StandaloneXml parser = new StandaloneXml(null, null, null, null);
        final List<ModelNode> operationList = new ArrayList<ModelNode>();
        final XMLMapper mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(new QName(namespace, "server"), parser);
        mapper.parseDocument(operationList, reader);
        final ModelNode content = operationList.get(1).get("content");
        assertArrayEquals(new byte[] { 0x12, 0x34 }, content.get(0).get("hash").asBytes());
    }

    @Test
    public void testFSArchive() throws Exception {
        final String xml = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<server name=\"example\" xmlns=\"urn:jboss:domain:1.0\">" +
                "    <deployments>" +
                "        <deployment name=\"test.war\" runtime-name=\"test-run.war\">" +
                "            <fs-archive path=\"${jboss.home}/content/welcome.jar\"/>" +
                "        </deployment>" +
                "    </deployments>" +
                "</server>";
        final XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(xml));
        final StandaloneXml parser = new StandaloneXml(null, null, null, null);
        final List<ModelNode> operationList = new ArrayList<ModelNode>();
        final XMLMapper mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(new QName(namespace, "server"), parser);
        mapper.parseDocument(operationList, reader);
        System.out.println(operationList.get(1));
        final ModelNode content = operationList.get(1).get("content");
        assertEquals(true, content.get(0).get("archive").asBoolean());
        assertEquals("${jboss.home}/content/welcome.jar", content.get(0).get("path").asString());
    }

    @Test
    public void testFSExploded() throws Exception {
        final String xml = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<server name=\"example\" xmlns=\"urn:jboss:domain:1.0\">" +
                "    <deployments>" +
                "        <deployment name=\"test.war\" runtime-name=\"test-run.war\">" +
                "            <fs-exploded path=\"deployments/test.jar\" relative-to=\"jboss.server.base.dir\"/>" +
                "        </deployment>" +
                "    </deployments>" +
                "</server>";
        final XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(xml));
        final StandaloneXml parser = new StandaloneXml(null, null, null, null);
        final List<ModelNode> operationList = new ArrayList<ModelNode>();
        final XMLMapper mapper = XMLMapper.Factory.create();
        mapper.registerRootElement(new QName(namespace, "server"), parser);
        mapper.parseDocument(operationList, reader);
        final ModelNode content = operationList.get(1).get("content");
        assertEquals(false, content.get(0).get("archive").asBoolean());
        assertEquals("deployments/test.jar", content.get(0).get("path").asString());
        assertEquals("jboss.server.base.dir", content.get(0).get("relative-to").asString());
    }
}
