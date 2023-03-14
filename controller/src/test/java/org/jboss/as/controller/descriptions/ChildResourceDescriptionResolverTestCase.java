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

package org.jboss.as.controller.descriptions;

import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.jboss.as.controller.PathElement;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for {@link ChildResourceDescriptionResolver}.
 * @author Paul Ferraro
 */
public class ChildResourceDescriptionResolverTestCase {

    @Test
    public void getChildTypeDescription() {
        String subsystem = "subsystem";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);

        ParentResourceDescriptionResolver parent = new SubsystemResourceDescriptionResolver(subsystem, this.getClass());
        ParentResourceDescriptionResolver resolver = parent.createChildResolver(PathElement.pathElement("child", "value"), List.of(PathElement.pathElement("child")));

        Assert.assertThrows(MissingResourceException.class, () -> resolver.getChildTypeDescription("test", locale, bundle));

        properties.put("subsystem.test", "parent");

        Assert.assertEquals("parent", resolver.getChildTypeDescription("test", locale, bundle));

        properties.put("subsystem.child.test", "foo");

        Assert.assertEquals("foo", resolver.getChildTypeDescription("test", locale, bundle));

        properties.put("subsystem.child.value.test", "bar");

        Assert.assertEquals("bar", resolver.getChildTypeDescription("test", locale, bundle));

        ResourceDescriptionResolver child = resolver.createChildResolver(PathElement.pathElement("grandchild"));

        properties.put("subsystem.child.value.grandchild.test", "foo");

        Assert.assertEquals("foo", child.getChildTypeDescription("test", locale, bundle));
    }

    @Test
    public void getResourceBundle() {
        ParentResourceDescriptionResolver parent = mock(ParentResourceDescriptionResolver.class);
        ParentResourceDescriptionResolver child = mock(ParentResourceDescriptionResolver.class);
        ResourceDescriptionResolver resolver = new ChildResourceDescriptionResolver(parent, child, List.of());
        Locale locale = Locale.getDefault();
        ResourceBundle bundle = mock(ResourceBundle.class);

        when(child.getResourceBundle(locale)).thenReturn(bundle);

        Assert.assertSame(bundle, resolver.getResourceBundle(locale));
    }

    @Test
    public void getResourceDescription() {
        String subsystem = "subsystem";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);

        ParentResourceDescriptionResolver parent = new SubsystemResourceDescriptionResolver(subsystem, this.getClass());
        ParentResourceDescriptionResolver resolver = parent.createChildResolver(PathElement.pathElement("child", "value"), List.of(PathElement.pathElement("child")));

        Assert.assertThrows(MissingResourceException.class, () -> resolver.getResourceDescription(locale, bundle));

        properties.put("subsystem", "parent");

        Assert.assertEquals("parent", resolver.getResourceDescription(locale, bundle));

        properties.put("subsystem.child", "foo");

        Assert.assertEquals("foo", resolver.getResourceDescription(locale, bundle));

        properties.put("subsystem.child.value", "bar");

        Assert.assertEquals("bar", resolver.getResourceDescription(locale, bundle));

        ResourceDescriptionResolver child = resolver.createChildResolver(PathElement.pathElement("grandchild"));

        properties.put("subsystem.child.value.grandchild", "foo");

        Assert.assertEquals("foo", child.getResourceDescription(locale, bundle));
    }

    @Test
    public void getResourceAttributeDescription() {
        String subsystem = "subsystem";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);

        ParentResourceDescriptionResolver parent = new SubsystemResourceDescriptionResolver(subsystem, this.getClass());
        ParentResourceDescriptionResolver resolver = parent.createChildResolver(PathElement.pathElement("child", "value"), List.of(PathElement.pathElement("child")));

        Assert.assertThrows(MissingResourceException.class, () -> resolver.getResourceAttributeDescription("test", locale, bundle));

        properties.put("subsystem.test", "parent");

        Assert.assertEquals("parent", resolver.getResourceAttributeDescription("test", locale, bundle));

        properties.put("subsystem.child.test", "foo");

        Assert.assertEquals("foo", resolver.getResourceAttributeDescription("test", locale, bundle));

        properties.put("subsystem.child.value.test", "bar");

        Assert.assertEquals("bar", resolver.getResourceAttributeDescription("test", locale, bundle));

        ResourceDescriptionResolver child = resolver.createChildResolver(PathElement.pathElement("grandchild"));

        properties.put("subsystem.child.value.grandchild.test", "foo");

        Assert.assertEquals("foo", child.getResourceAttributeDescription("test", locale, bundle));
    }

    @Test
    public void getResourceAttributeValueTypeDescription() {
        String subsystem = "subsystem";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);

        ParentResourceDescriptionResolver parent = new SubsystemResourceDescriptionResolver(subsystem, this.getClass());
        ParentResourceDescriptionResolver resolver = parent.createChildResolver(PathElement.pathElement("child", "value"), List.of(PathElement.pathElement("child")));

        Assert.assertThrows(MissingResourceException.class, () -> resolver.getResourceAttributeValueTypeDescription("test", locale, bundle));

        properties.put("subsystem.test", "parent");

        Assert.assertEquals("parent", resolver.getResourceAttributeValueTypeDescription("test", locale, bundle));

        properties.put("subsystem.child.test", "foo");

        Assert.assertEquals("foo", resolver.getResourceAttributeValueTypeDescription("test", locale, bundle));

        properties.put("subsystem.child.value.test", "bar");

        Assert.assertEquals("bar", resolver.getResourceAttributeValueTypeDescription("test", locale, bundle));

        ResourceDescriptionResolver child = resolver.createChildResolver(PathElement.pathElement("grandchild"));

        properties.put("subsystem.child.value.grandchild.test", "foo");

        Assert.assertEquals("foo", child.getResourceAttributeValueTypeDescription("test", locale, bundle));
    }

    @Test
    public void getOperationDescription() {
        String subsystem = "subsystem";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);

        ParentResourceDescriptionResolver parent = new SubsystemResourceDescriptionResolver(subsystem, this.getClass());
        ParentResourceDescriptionResolver resolver = parent.createChildResolver(PathElement.pathElement("child", "value"), List.of(PathElement.pathElement("child")));

        Assert.assertThrows(MissingResourceException.class, () -> resolver.getOperationDescription("test", locale, bundle));

        properties.put("subsystem.test", "parent");

        Assert.assertEquals("parent", resolver.getOperationDescription("test", locale, bundle));

        properties.put("subsystem.child.test", "foo");

        Assert.assertEquals("foo", resolver.getOperationDescription("test", locale, bundle));

        properties.put("subsystem.child.value.test", "bar");

        Assert.assertEquals("bar", resolver.getOperationDescription("test", locale, bundle));

        ResourceDescriptionResolver child = resolver.createChildResolver(PathElement.pathElement("grandchild"));

        properties.put("subsystem.child.value.grandchild.test", "foo");

        Assert.assertEquals("foo", child.getOperationDescription("test", locale, bundle));
    }

    @Test
    public void getOperationParameterDescription() {
        String subsystem = "subsystem";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);

        ParentResourceDescriptionResolver parent = new SubsystemResourceDescriptionResolver(subsystem, this.getClass());
        ParentResourceDescriptionResolver resolver = parent.createChildResolver(PathElement.pathElement("child", "value"), List.of(PathElement.pathElement("child")));

        Assert.assertThrows(MissingResourceException.class, () -> resolver.getOperationParameterDescription("test", "param", locale, bundle));

        properties.put("subsystem.test.param", "parent");

        Assert.assertEquals("parent", resolver.getOperationParameterDescription("test", "param", locale, bundle));

        properties.put("subsystem.child.test.param", "foo");

        Assert.assertEquals("foo", resolver.getOperationParameterDescription("test", "param", locale, bundle));

        properties.put("subsystem.child.value.test.param", "bar");

        Assert.assertEquals("bar", resolver.getOperationParameterDescription("test", "param", locale, bundle));

        ResourceDescriptionResolver child = resolver.createChildResolver(PathElement.pathElement("grandchild"));

        properties.put("subsystem.child.value.grandchild.test.param", "foo");

        Assert.assertEquals("foo", child.getOperationParameterDescription("test", "param", locale, bundle));
    }

    @Test
    public void getOperationParameterValueTypeDescription() {
        String subsystem = "subsystem";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);

        ParentResourceDescriptionResolver parent = new SubsystemResourceDescriptionResolver(subsystem, this.getClass());
        ParentResourceDescriptionResolver resolver = parent.createChildResolver(PathElement.pathElement("child", "value"), List.of(PathElement.pathElement("child")));

        Assert.assertThrows(MissingResourceException.class, () -> resolver.getOperationParameterValueTypeDescription("test", "param", locale, bundle, "type"));

        properties.put("subsystem.test.param.type", "parent");

        Assert.assertEquals("parent", resolver.getOperationParameterValueTypeDescription("test", "param", locale, bundle, "type"));

        properties.put("subsystem.child.test.param.type", "foo");

        Assert.assertEquals("foo", resolver.getOperationParameterValueTypeDescription("test", "param", locale, bundle, "type"));

        properties.put("subsystem.child.value.test.param.type", "bar");

        Assert.assertEquals("bar", resolver.getOperationParameterValueTypeDescription("test", "param", locale, bundle, "type"));

        ResourceDescriptionResolver child = resolver.createChildResolver(PathElement.pathElement("grandchild"));

        properties.put("subsystem.child.value.grandchild.test.param.type", "foo");

        Assert.assertEquals("foo", child.getOperationParameterValueTypeDescription("test", "param", locale, bundle, "type"));
    }

    @Test
    public void getOperationReplyDescription() {
        String subsystem = "subsystem";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);

        ParentResourceDescriptionResolver parent = new SubsystemResourceDescriptionResolver(subsystem, this.getClass());
        ParentResourceDescriptionResolver resolver = parent.createChildResolver(PathElement.pathElement("child", "value"), List.of(PathElement.pathElement("child")));

        Assert.assertNull(resolver.getOperationReplyDescription("test", locale, bundle));

        properties.put("subsystem.test.reply", "parent");

        Assert.assertEquals("parent", resolver.getOperationReplyDescription("test", locale, bundle));

        properties.put("subsystem.child.test.reply", "foo");

        Assert.assertEquals("foo", resolver.getOperationReplyDescription("test", locale, bundle));

        properties.put("subsystem.child.value.test.reply", "bar");

        Assert.assertEquals("bar", resolver.getOperationReplyDescription("test", locale, bundle));

        ResourceDescriptionResolver child = resolver.createChildResolver(PathElement.pathElement("grandchild"));

        properties.put("subsystem.child.value.grandchild.test.reply", "foo");

        Assert.assertEquals("foo", child.getOperationReplyDescription("test", locale, bundle));
    }

    @Test
    public void getOperationReplyValueTypeDescription() {
        String subsystem = "subsystem";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);

        ParentResourceDescriptionResolver parent = new SubsystemResourceDescriptionResolver(subsystem, this.getClass());
        ParentResourceDescriptionResolver resolver = parent.createChildResolver(PathElement.pathElement("child", "value"), List.of(PathElement.pathElement("child")));

        Assert.assertThrows(MissingResourceException.class, () -> resolver.getOperationReplyValueTypeDescription("test", locale, bundle, "type"));

        properties.put("subsystem.test.reply.type", "parent");

        Assert.assertEquals("parent", resolver.getOperationReplyValueTypeDescription("test", locale, bundle, "type"));

        properties.put("subsystem.child.test.reply.type", "foo");

        Assert.assertEquals("foo", resolver.getOperationReplyValueTypeDescription("test", locale, bundle, "type"));

        properties.put("subsystem.child.value.test.reply.type", "bar");

        Assert.assertEquals("bar", resolver.getOperationReplyValueTypeDescription("test", locale, bundle, "type"));

        ResourceDescriptionResolver child = resolver.createChildResolver(PathElement.pathElement("grandchild"));

        properties.put("subsystem.child.value.grandchild.test.reply.type", "foo");

        Assert.assertEquals("foo", child.getOperationReplyValueTypeDescription("test", locale, bundle, "type"));
    }

    @Test
    public void getNotificationDescription() {
        String subsystem = "subsystem";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);

        ParentResourceDescriptionResolver parent = new SubsystemResourceDescriptionResolver(subsystem, this.getClass());
        ParentResourceDescriptionResolver resolver = parent.createChildResolver(PathElement.pathElement("child", "value"), List.of(PathElement.pathElement("child")));

        Assert.assertThrows(MissingResourceException.class, () -> resolver.getNotificationDescription("test", locale, bundle));

        properties.put("subsystem.test", "parent");

        Assert.assertEquals("parent", resolver.getNotificationDescription("test", locale, bundle));

        properties.put("subsystem.child.test", "foo");

        Assert.assertEquals("foo", resolver.getNotificationDescription("test", locale, bundle));

        properties.put("subsystem.child.value.test", "bar");

        Assert.assertEquals("bar", resolver.getNotificationDescription("test", locale, bundle));

        ResourceDescriptionResolver child = resolver.createChildResolver(PathElement.pathElement("grandchild"));

        properties.put("subsystem.child.value.grandchild.test", "foo");

        Assert.assertEquals("foo", child.getNotificationDescription("test", locale, bundle));
    }

    @Test
    public void getResourceDeprecatedDescription() {
        String subsystem = "subsystem";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);

        ParentResourceDescriptionResolver parent = new SubsystemResourceDescriptionResolver(subsystem, this.getClass());
        ParentResourceDescriptionResolver resolver = parent.createChildResolver(PathElement.pathElement("child", "value"), List.of(PathElement.pathElement("child")));

        Assert.assertThrows(MissingResourceException.class, () -> resolver.getResourceDeprecatedDescription(locale, bundle));

        properties.put("subsystem.deprecated", "parent");

        Assert.assertEquals("parent", resolver.getResourceDeprecatedDescription(locale, bundle));

        properties.put("subsystem.child.deprecated", "foo");

        Assert.assertEquals("foo", resolver.getResourceDeprecatedDescription(locale, bundle));

        properties.put("subsystem.child.value.deprecated", "bar");

        Assert.assertEquals("bar", resolver.getResourceDeprecatedDescription(locale, bundle));

        ResourceDescriptionResolver child = resolver.createChildResolver(PathElement.pathElement("grandchild"));

        properties.put("subsystem.child.value.grandchild.deprecated", "foo");

        Assert.assertEquals("foo", child.getResourceDeprecatedDescription(locale, bundle));
    }

    @Test
    public void getResourceAttributeDeprecatedDescription() {
        String subsystem = "subsystem";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);

        ParentResourceDescriptionResolver parent = new SubsystemResourceDescriptionResolver(subsystem, this.getClass());
        ParentResourceDescriptionResolver resolver = parent.createChildResolver(PathElement.pathElement("child", "value"), List.of(PathElement.pathElement("child")));

        Assert.assertThrows(MissingResourceException.class, () -> resolver.getResourceAttributeDeprecatedDescription("test", locale, bundle));

        properties.put("subsystem.test.deprecated", "parent");

        Assert.assertEquals("parent", resolver.getResourceAttributeDeprecatedDescription("test", locale, bundle));

        properties.put("subsystem.child.test.deprecated", "foo");

        Assert.assertEquals("foo", resolver.getResourceAttributeDeprecatedDescription("test", locale, bundle));

        properties.put("subsystem.child.value.test.deprecated", "bar");

        Assert.assertEquals("bar", resolver.getResourceAttributeDeprecatedDescription("test", locale, bundle));

        ResourceDescriptionResolver child = resolver.createChildResolver(PathElement.pathElement("grandchild"));

        properties.put("subsystem.child.value.grandchild.test.deprecated", "foo");

        Assert.assertEquals("foo", child.getResourceAttributeDeprecatedDescription("test", locale, bundle));
    }

    @Test
    public void getOperationDeprecatedDescription() {
        String subsystem = "subsystem";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);

        ParentResourceDescriptionResolver parent = new SubsystemResourceDescriptionResolver(subsystem, this.getClass());
        ParentResourceDescriptionResolver resolver = parent.createChildResolver(PathElement.pathElement("child", "value"), List.of(PathElement.pathElement("child")));

        Assert.assertThrows(MissingResourceException.class, () -> resolver.getOperationDeprecatedDescription("test", locale, bundle));

        properties.put("subsystem.test.deprecated", "parent");

        Assert.assertEquals("parent", resolver.getOperationDeprecatedDescription("test", locale, bundle));

        properties.put("subsystem.child.test.deprecated", "foo");

        Assert.assertEquals("foo", resolver.getOperationDeprecatedDescription("test", locale, bundle));

        properties.put("subsystem.child.value.test.deprecated", "bar");

        Assert.assertEquals("bar", resolver.getOperationDeprecatedDescription("test", locale, bundle));

        ResourceDescriptionResolver child = resolver.createChildResolver(PathElement.pathElement("grandchild"));

        properties.put("subsystem.child.value.grandchild.test.deprecated", "foo");

        Assert.assertEquals("foo", child.getOperationDeprecatedDescription("test", locale, bundle));
    }

    @Test
    public void getOperationParameterDeprecatedDescription() {
        String subsystem = "subsystem";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);

        ParentResourceDescriptionResolver parent = new SubsystemResourceDescriptionResolver(subsystem, this.getClass());
        ParentResourceDescriptionResolver resolver = parent.createChildResolver(PathElement.pathElement("child", "value"), List.of(PathElement.pathElement("child")));

        Assert.assertThrows(MissingResourceException.class, () -> resolver.getOperationParameterDeprecatedDescription("test", "param", locale, bundle));

        properties.put("subsystem.test.param.deprecated", "parent");

        Assert.assertEquals("parent", resolver.getOperationParameterDeprecatedDescription("test", "param", locale, bundle));

        properties.put("subsystem.child.test.param.deprecated", "foo");

        Assert.assertEquals("foo", resolver.getOperationParameterDeprecatedDescription("test", "param", locale, bundle));

        properties.put("subsystem.child.value.test.param.deprecated", "bar");

        Assert.assertEquals("bar", resolver.getOperationParameterDeprecatedDescription("test", "param", locale, bundle));

        ResourceDescriptionResolver child = resolver.createChildResolver(PathElement.pathElement("grandchild"));

        properties.put("subsystem.child.value.grandchild.test.param.deprecated", "foo");

        Assert.assertEquals("foo", child.getOperationParameterDeprecatedDescription("test", "param", locale, bundle));
    }

    private static class PropertiesResourceBundle extends ResourceBundle {

        private final Map<String, String> properties;

        PropertiesResourceBundle(Map<String, String> properties) {
            this.properties = properties;
        }

        @Override
        protected Object handleGetObject(String key) {
            if (!this.properties.containsKey(key)) {
                throw new MissingResourceException(null, "", key);
            }
            return this.properties.get(key);
        }

        @Override
        public Enumeration<String> getKeys() {
            return Collections.enumeration(this.properties.keySet());
        }
    }
}
