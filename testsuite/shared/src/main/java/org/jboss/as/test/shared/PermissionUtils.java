/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
