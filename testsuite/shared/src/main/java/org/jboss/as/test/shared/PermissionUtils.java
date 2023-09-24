/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.shared;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Permission;

import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.StringAsset;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class PermissionUtils {
    public static Asset createPermissionsXmlAsset(Permission... permissions) {
        return new StringAsset(new String(createPermissionsXml(permissions), StandardCharsets.UTF_8));
    }

    public static byte[] createPermissionsXml(Permission... permissions) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            XMLOutputFactory output = XMLOutputFactory.newInstance();
            XMLStreamWriter xmlWriter = output.createXMLStreamWriter(stream, StandardCharsets.UTF_8.name());

            xmlWriter.writeStartDocument("UTF-8", "1.0");
            xmlWriter.writeStartElement( "permissions");
            xmlWriter.writeDefaultNamespace("http://xmlns.jcp.org/xml/ns/javaee");
            xmlWriter.writeAttribute("version", "7");

            for (Permission permission : permissions) {
                xmlWriter.writeStartElement("permission");

                xmlWriter.writeStartElement("class-name");
                xmlWriter.writeCharacters(permission.getClass().getName());
                xmlWriter.writeEndElement();

                xmlWriter.writeStartElement("name");
                xmlWriter.writeCharacters(permission.getName());
                xmlWriter.writeEndElement();

                final String actions = permission.getActions();
                if (actions != null && ! actions.isEmpty()) {
                    xmlWriter.writeStartElement("actions");
                    xmlWriter.writeCharacters(actions);
                    xmlWriter.writeEndElement();
                }
                xmlWriter.writeEndElement();
            }

            xmlWriter.writeEndElement();
            xmlWriter.writeEndDocument();
            xmlWriter.flush();
            xmlWriter.close();

            return stream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Generating permissions.xml failed", e);
        }
    }
}
