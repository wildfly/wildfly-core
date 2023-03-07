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
        ResourceDescriptionResolver parent = mock(ResourceDescriptionResolver.class);
        String prefix = "prefix";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);
        ResourceDescriptionResolver resolver = new ChildResourceDescriptionResolver(parent, prefix, List.of(PathElement.pathElement("child", "value"), PathElement.pathElement("child")));

        when(parent.getChildTypeDescription("test", locale, bundle)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getChildTypeDescription("test", locale, bundle));

        properties.put("prefix.child.test", "foo");

        Assert.assertEquals("foo", resolver.getChildTypeDescription("test", locale, bundle));

        properties.put("prefix.child.value.test", "bar");

        Assert.assertEquals("bar", resolver.getChildTypeDescription("test", locale, bundle));
    }

    @Test
    public void getResourceBundle() {
        ResourceDescriptionResolver parent = mock(ResourceDescriptionResolver.class);
        String prefix = "prefix";
        ResourceDescriptionResolver resolver = new ChildResourceDescriptionResolver(parent, prefix, List.of());
        Locale locale = Locale.getDefault();
        ResourceBundle bundle = mock(ResourceBundle.class);

        when(parent.getResourceBundle(locale)).thenReturn(bundle);

        Assert.assertSame(bundle, resolver.getResourceBundle(locale));
    }

    @Test
    public void getResourceDescription() {
        ResourceDescriptionResolver parent = mock(ResourceDescriptionResolver.class);
        String prefix = "prefix";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);
        ResourceDescriptionResolver resolver = new ChildResourceDescriptionResolver(parent, prefix, List.of(PathElement.pathElement("child", "value"), PathElement.pathElement("child")));

        when(parent.getResourceDescription(locale, bundle)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getResourceDescription(locale, bundle));

        properties.put("prefix.child", "foo");

        Assert.assertEquals("foo", resolver.getResourceDescription(locale, bundle));

        properties.put("prefix.child.value", "bar");

        Assert.assertEquals("bar", resolver.getResourceDescription(locale, bundle));
    }

    @Test
    public void getResourceAttributeDescription() {
        ResourceDescriptionResolver parent = mock(ResourceDescriptionResolver.class);
        String prefix = "prefix";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);
        ResourceDescriptionResolver resolver = new ChildResourceDescriptionResolver(parent, prefix, List.of(PathElement.pathElement("child", "value"), PathElement.pathElement("child")));

        when(parent.getResourceAttributeDescription("test", locale, bundle)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getResourceAttributeDescription("test", locale, bundle));

        properties.put("prefix.child.test", "foo");

        Assert.assertEquals("foo", resolver.getResourceAttributeDescription("test", locale, bundle));

        properties.put("prefix.child.value.test", "bar");

        Assert.assertEquals("bar", resolver.getResourceAttributeDescription("test", locale, bundle));
    }

    @Test
    public void getResourceAttributeValueTypeDescription() {
        ResourceDescriptionResolver parent = mock(ResourceDescriptionResolver.class);
        String prefix = "prefix";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);

        ResourceDescriptionResolver resolver = new ChildResourceDescriptionResolver(parent, prefix, List.of(PathElement.pathElement("child", "value"), PathElement.pathElement("child")));

        when(parent.getResourceAttributeValueTypeDescription("test", locale, bundle)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getResourceAttributeValueTypeDescription("test", locale, bundle));

        properties.put("prefix.child.test", "foo");

        Assert.assertEquals("foo", resolver.getResourceAttributeValueTypeDescription("test", locale, bundle));

        properties.put("prefix.child.value.test", "bar");

        Assert.assertEquals("bar", resolver.getResourceAttributeValueTypeDescription("test", locale, bundle));

        String[] suffixes = new String[] { "suffix1", "suffix2" };

        when(parent.getResourceAttributeValueTypeDescription("test", locale, bundle, suffixes)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getResourceAttributeValueTypeDescription("test", locale, bundle, suffixes));

        properties.put("prefix.child.test.suffix1.suffix2", "foo");

        Assert.assertEquals("foo", resolver.getResourceAttributeValueTypeDescription("test", locale, bundle, suffixes));

        properties.put("prefix.child.value.test.suffix1.suffix2", "bar");

        Assert.assertEquals("bar", resolver.getResourceAttributeValueTypeDescription("test", locale, bundle, suffixes));
    }

    @Test
    public void getOperationDescription() {
        ResourceDescriptionResolver parent = mock(ResourceDescriptionResolver.class);
        String prefix = "prefix";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);
        ResourceDescriptionResolver resolver = new ChildResourceDescriptionResolver(parent, prefix, List.of(PathElement.pathElement("child", "value"), PathElement.pathElement("child")));

        when(parent.getOperationDescription("test", locale, bundle)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getOperationDescription("test", locale, bundle));

        properties.put("prefix.child.test", "foo");

        Assert.assertEquals("foo", resolver.getOperationDescription("test", locale, bundle));

        properties.put("prefix.child.value.test", "bar");

        Assert.assertEquals("bar", resolver.getOperationDescription("test", locale, bundle));
    }

    @Test
    public void getOperationParameterDescription() {
        ResourceDescriptionResolver parent = mock(ResourceDescriptionResolver.class);
        String prefix = "prefix";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);
        ResourceDescriptionResolver resolver = new ChildResourceDescriptionResolver(parent, prefix, List.of(PathElement.pathElement("child", "value"), PathElement.pathElement("child")));

        when(parent.getOperationParameterDescription(ModelDescriptionConstants.ADD, "test", locale, bundle)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getOperationParameterDescription(ModelDescriptionConstants.ADD, "test", locale, bundle));

        properties.put("prefix.child.test", "foo");

        Assert.assertEquals("foo", resolver.getOperationParameterDescription(ModelDescriptionConstants.ADD, "test", locale, bundle));

        properties.put("prefix.child.value.test", "bar");

        Assert.assertEquals("bar", resolver.getOperationParameterDescription(ModelDescriptionConstants.ADD, "test", locale, bundle));

        when(parent.getOperationParameterDescription("operation", "test", locale, bundle)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getOperationParameterDescription("operation", "test", locale, bundle));

        properties.put("prefix.child.operation.test", "foo");

        Assert.assertEquals("foo", resolver.getOperationParameterDescription("operation", "test", locale, bundle));

        properties.put("prefix.child.value.operation.test", "bar");

        Assert.assertEquals("bar", resolver.getOperationParameterDescription("operation", "test", locale, bundle));
    }

    @Test
    public void getOperationParameterValueTypeDescription() {
        ResourceDescriptionResolver parent = mock(ResourceDescriptionResolver.class);
        String prefix = "prefix";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);
        ResourceDescriptionResolver resolver = new ChildResourceDescriptionResolver(parent, prefix, List.of(PathElement.pathElement("child", "value"), PathElement.pathElement("child")));

        when(parent.getOperationParameterValueTypeDescription(ModelDescriptionConstants.ADD, "test", locale, bundle)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getOperationParameterValueTypeDescription(ModelDescriptionConstants.ADD, "test", locale, bundle));

        properties.put("prefix.child.test", "foo");

        Assert.assertEquals("foo", resolver.getOperationParameterValueTypeDescription(ModelDescriptionConstants.ADD, "test", locale, bundle));

        properties.put("prefix.child.value.test", "bar");

        Assert.assertEquals("bar", resolver.getOperationParameterValueTypeDescription(ModelDescriptionConstants.ADD, "test", locale, bundle));

        when(parent.getOperationParameterValueTypeDescription("operation", "test", locale, bundle)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getOperationParameterValueTypeDescription("operation", "test", locale, bundle));

        properties.put("prefix.child.operation.test", "foo");

        Assert.assertEquals("foo", resolver.getOperationParameterValueTypeDescription("operation", "test", locale, bundle));

        properties.put("prefix.child.value.operation.test", "bar");

        Assert.assertEquals("bar", resolver.getOperationParameterValueTypeDescription("operation", "test", locale, bundle));

        String[] suffixes = new String[] { "suffix1", "suffix2" };

        when(parent.getOperationParameterValueTypeDescription(ModelDescriptionConstants.ADD, "test", locale, bundle, suffixes)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getOperationParameterValueTypeDescription(ModelDescriptionConstants.ADD, "test", locale, bundle, suffixes));

        properties.put("prefix.child.test.suffix1.suffix2", "foo");

        Assert.assertEquals("foo", resolver.getOperationParameterValueTypeDescription(ModelDescriptionConstants.ADD, "test", locale, bundle, suffixes));

        properties.put("prefix.child.value.test.suffix1.suffix2", "bar");

        Assert.assertEquals("bar", resolver.getOperationParameterValueTypeDescription(ModelDescriptionConstants.ADD, "test", locale, bundle, suffixes));

        when(parent.getOperationParameterValueTypeDescription("operation", "test", locale, bundle, suffixes)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getOperationParameterValueTypeDescription("operation", "test", locale, bundle, suffixes));

        properties.put("prefix.child.operation.test.suffix1.suffix2", "foo");

        Assert.assertEquals("foo", resolver.getOperationParameterValueTypeDescription("operation", "test", locale, bundle, suffixes));

        properties.put("prefix.child.value.operation.test.suffix1.suffix2", "bar");

        Assert.assertEquals("bar", resolver.getOperationParameterValueTypeDescription("operation", "test", locale, bundle, suffixes));
    }

    @Test
    public void getOperationReplyDescription() {
        ResourceDescriptionResolver parent = mock(ResourceDescriptionResolver.class);
        String prefix = "prefix";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);
        ResourceDescriptionResolver resolver = new ChildResourceDescriptionResolver(parent, prefix, List.of(PathElement.pathElement("child", "value"), PathElement.pathElement("child")));

        when(parent.getOperationReplyDescription("test", locale, bundle)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getOperationReplyDescription("test", locale, bundle));

        properties.put("prefix.child.test.reply", "foo");

        Assert.assertEquals("foo", resolver.getOperationReplyDescription("test", locale, bundle));

        properties.put("prefix.child.value.test.reply", "bar");

        Assert.assertEquals("bar", resolver.getOperationReplyDescription("test", locale, bundle));
    }

    @Test
    public void getOperationReplyValueTypeDescription() {
        ResourceDescriptionResolver parent = mock(ResourceDescriptionResolver.class);
        String prefix = "prefix";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);

        ResourceDescriptionResolver resolver = new ChildResourceDescriptionResolver(parent, prefix, List.of(PathElement.pathElement("child", "value"), PathElement.pathElement("child")));

        when(parent.getOperationReplyValueTypeDescription("test", locale, bundle)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getOperationReplyValueTypeDescription("test", locale, bundle));

        properties.put("prefix.child.test.reply", "foo");

        Assert.assertEquals("foo", resolver.getOperationReplyValueTypeDescription("test", locale, bundle));

        properties.put("prefix.child.value.test.reply", "bar");

        Assert.assertEquals("bar", resolver.getOperationReplyValueTypeDescription("test", locale, bundle));

        String[] suffixes = new String[] { "suffix1", "suffix2" };

        when(parent.getOperationReplyValueTypeDescription("test", locale, bundle, suffixes)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getOperationReplyValueTypeDescription("test", locale, bundle, suffixes));

        properties.put("prefix.child.test.reply.suffix1.suffix2", "foo");

        Assert.assertEquals("foo", resolver.getOperationReplyValueTypeDescription("test", locale, bundle, suffixes));

        properties.put("prefix.child.value.test.reply.suffix1.suffix2", "bar");

        Assert.assertEquals("bar", resolver.getOperationReplyValueTypeDescription("test", locale, bundle, suffixes));
    }

    @Test
    public void getNotificationDescription() {
        ResourceDescriptionResolver parent = mock(ResourceDescriptionResolver.class);
        String prefix = "prefix";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);
        ResourceDescriptionResolver resolver = new ChildResourceDescriptionResolver(parent, prefix, List.of(PathElement.pathElement("child", "value"), PathElement.pathElement("child")));

        when(parent.getNotificationDescription("test", locale, bundle)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getNotificationDescription("test", locale, bundle));

        properties.put("prefix.child.test", "foo");

        Assert.assertEquals("foo", resolver.getNotificationDescription("test", locale, bundle));

        properties.put("prefix.child.value.test", "bar");

        Assert.assertEquals("bar", resolver.getNotificationDescription("test", locale, bundle));
    }

    @Test
    public void getResourceDeprecatedDescription() {
        ResourceDescriptionResolver parent = mock(ResourceDescriptionResolver.class);
        String prefix = "prefix";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);
        ResourceDescriptionResolver resolver = new ChildResourceDescriptionResolver(parent, prefix, List.of(PathElement.pathElement("child", "value"), PathElement.pathElement("child")));

        when(parent.getResourceDeprecatedDescription(locale, bundle)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getResourceDeprecatedDescription(locale, bundle));

        properties.put("prefix.child.deprecated", "foo");

        Assert.assertEquals("foo", resolver.getResourceDeprecatedDescription(locale, bundle));

        properties.put("prefix.child.value.deprecated", "bar");

        Assert.assertEquals("bar", resolver.getResourceDeprecatedDescription(locale, bundle));
    }

    @Test
    public void getResourceAttributeDeprecatedDescription() {
        ResourceDescriptionResolver parent = mock(ResourceDescriptionResolver.class);
        String prefix = "prefix";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);
        ResourceDescriptionResolver resolver = new ChildResourceDescriptionResolver(parent, prefix, List.of(PathElement.pathElement("child", "value"), PathElement.pathElement("child")));

        when(parent.getResourceAttributeDeprecatedDescription("test", locale, bundle)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getResourceAttributeDeprecatedDescription("test", locale, bundle));

        properties.put("prefix.child.test.deprecated", "foo");

        Assert.assertEquals("foo", resolver.getResourceAttributeDeprecatedDescription("test", locale, bundle));

        properties.put("prefix.child.value.test.deprecated", "bar");

        Assert.assertEquals("bar", resolver.getResourceAttributeDeprecatedDescription("test", locale, bundle));
    }

    @Test
    public void getOperationDeprecatedDescription() {
        ResourceDescriptionResolver parent = mock(ResourceDescriptionResolver.class);
        String prefix = "prefix";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);
        ResourceDescriptionResolver resolver = new ChildResourceDescriptionResolver(parent, prefix, List.of(PathElement.pathElement("child", "value"), PathElement.pathElement("child")));

        when(parent.getOperationDeprecatedDescription("test", locale, bundle)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getOperationDeprecatedDescription("test", locale, bundle));

        properties.put("prefix.child.test.deprecated", "foo");

        Assert.assertEquals("foo", resolver.getOperationDeprecatedDescription("test", locale, bundle));

        properties.put("prefix.child.value.test.deprecated", "bar");

        Assert.assertEquals("bar", resolver.getOperationDeprecatedDescription("test", locale, bundle));
    }

    @Test
    public void getOperationParameterDeprecatedDescription() {
        ResourceDescriptionResolver parent = mock(ResourceDescriptionResolver.class);
        String prefix = "prefix";
        Locale locale = Locale.getDefault();
        Map<String, String> properties = new HashMap<>();
        ResourceBundle bundle = new PropertiesResourceBundle(properties);
        ResourceDescriptionResolver resolver = new ChildResourceDescriptionResolver(parent, prefix, List.of(PathElement.pathElement("child", "value"), PathElement.pathElement("child")));

        when(parent.getOperationParameterDeprecatedDescription(ModelDescriptionConstants.ADD, "test", locale, bundle)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getOperationParameterDeprecatedDescription(ModelDescriptionConstants.ADD, "test", locale, bundle));

        properties.put("prefix.child.test.deprecated", "foo");

        Assert.assertEquals("foo", resolver.getOperationParameterDeprecatedDescription(ModelDescriptionConstants.ADD, "test", locale, bundle));

        properties.put("prefix.child.value.test.deprecated", "bar");

        Assert.assertEquals("bar", resolver.getOperationParameterDeprecatedDescription(ModelDescriptionConstants.ADD, "test", locale, bundle));

        when(parent.getOperationParameterDeprecatedDescription("operation", "test", locale, bundle)).thenReturn("parent");

        Assert.assertEquals("parent", resolver.getOperationParameterDeprecatedDescription("operation", "test", locale, bundle));

        properties.put("prefix.child.operation.test.deprecated", "foo");

        Assert.assertEquals("foo", resolver.getOperationParameterDeprecatedDescription("operation", "test", locale, bundle));

        properties.put("prefix.child.value.operation.test.deprecated", "bar");

        Assert.assertEquals("bar", resolver.getOperationParameterDeprecatedDescription("operation", "test", locale, bundle));
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
