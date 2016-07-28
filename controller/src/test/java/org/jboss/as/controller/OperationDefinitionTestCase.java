/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPRECATED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REASON;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REPLY_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REQUEST_PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SINCE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE_TYPE;

import java.util.Locale;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.jboss.as.controller.SimpleAttributeDefinitionBuilder.create;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.MissingResourceException;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;

/**
 *
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class OperationDefinitionTestCase {

    @Test
    public void testSimpleOperationDefinition() {
        SimpleOperationDefinitionBuilder builder = new SimpleOperationDefinitionBuilder("operation",
                new StandardResourceDescriptionResolver("simple", OperationDefinitionTestCase.class.getName(), Thread.currentThread().getContextClassLoader()));
        builder.addParameter(create("parameter", ModelType.STRING, true).setAllowExpression(true).build());
        builder.addParameter(create("deprecated-parameter", ModelType.STRING, true).setDeprecated(ModelVersion.CURRENT).setAllowExpression(true).build());
        builder.addParameter(create("duplicated", ModelType.STRING, true).setAllowExpression(true).build());
        builder.setReplyParameters(create("duplicated", ModelType.STRING, true).setAllowExpression(true).build(),
                create("deprecated-parameter", ModelType.STRING, true).build());
        ModelNode description = builder.build().getDescriptionProvider().getModelDescription(Locale.ROOT);
        assertThat(description.get(OPERATION_NAME).asString(), is("operation"));
        assertThat(description.get(DESCRIPTION).asString(), is("Simple operation test"));
        assertTrue(description.hasDefined(REQUEST_PROPERTIES));
        ModelNode parameters = description.get(REQUEST_PROPERTIES);
        assertTrue(parameters.hasDefined("parameter"));
        assertThat(parameters.get("parameter", DESCRIPTION).asString(), is("Simple operation parameter"));
        assertTrue(parameters.hasDefined("deprecated-parameter"));
        assertThat(parameters.get("deprecated-parameter", DESCRIPTION).asString(), is("Simple operation deprecated parameter"));
        assertThat(parameters.get("deprecated-parameter", DEPRECATED, SINCE).asString(), is(ModelVersion.CURRENT.toString()));
        assertThat(parameters.get("deprecated-parameter", DEPRECATED, REASON).asString(), is("The simple operation deprecated parameter is deprecated and may be removed in the near future."));
        assertTrue(parameters.hasDefined("duplicated"));
        assertThat(parameters.get("duplicated", DESCRIPTION).asString(), is("Simple operation parameter"));
        assertTrue(description.hasDefined(REPLY_PROPERTIES));
        ModelNode replies = description.get(REPLY_PROPERTIES, VALUE_TYPE);
        assertTrue(replies.hasDefined("deprecated-parameter"));
        assertThat(replies.get("deprecated-parameter", DESCRIPTION).asString(), is("Simple operation reply parameter not deprecated"));
        assertFalse(replies.hasDefined("deprecated-parameter", DEPRECATED));
        assertTrue(replies.hasDefined("duplicated"));
        assertThat(replies.get("duplicated", DESCRIPTION).asString(), is("Simple operation reply parameter"));
    }

    @Test
    public void testReplyValueTypeOperationDefinition() {
        SimpleOperationDefinitionBuilder builder = new SimpleOperationDefinitionBuilder("operation",
                new StandardResourceDescriptionResolver("simple", OperationDefinitionTestCase.class.getName(), Thread.currentThread().getContextClassLoader()));
        builder.addParameter(create("parameter", ModelType.STRING, true).setAllowExpression(true).build());
        builder.addParameter(create("deprecated-parameter", ModelType.STRING, true).setDeprecated(ModelVersion.CURRENT).setAllowExpression(true).build());
        builder.addParameter(create("duplicated", ModelType.STRING, true).setAllowExpression(true).build());
        builder.setReplyType(ModelType.LIST);
        builder.setReplyValueType(ModelType.STRING);
        ModelNode description = builder.build().getDescriptionProvider().getModelDescription(Locale.ROOT);
        assertThat(description.get(OPERATION_NAME).asString(), is("operation"));
        assertThat(description.get(DESCRIPTION).asString(), is("Simple operation test"));
        assertTrue(description.hasDefined(REQUEST_PROPERTIES));
        ModelNode parameters = description.get(REQUEST_PROPERTIES);
        assertTrue(parameters.hasDefined("parameter"));
        assertThat(parameters.get("parameter", DESCRIPTION).asString(), is("Simple operation parameter"));
        assertTrue(parameters.hasDefined("deprecated-parameter"));
        assertThat(parameters.get("deprecated-parameter", DESCRIPTION).asString(), is("Simple operation deprecated parameter"));
        assertThat(parameters.get("deprecated-parameter", DEPRECATED, SINCE).asString(), is(ModelVersion.CURRENT.toString()));
        assertThat(parameters.get("deprecated-parameter", DEPRECATED, REASON).asString(), is("The simple operation deprecated parameter is deprecated and may be removed in the near future."));
        assertTrue(parameters.hasDefined("duplicated"));
        assertThat(parameters.get("duplicated", DESCRIPTION).asString(), is("Simple operation parameter"));
        assertTrue(description.hasDefined(REPLY_PROPERTIES, TYPE));
        assertTrue(description.hasDefined(REPLY_PROPERTIES, VALUE_TYPE));
    }

    @Test
    public void testListOperationDefinition() {
        SimpleOperationDefinitionBuilder builder = new SimpleOperationDefinitionBuilder("operation",
                new StandardResourceDescriptionResolver("simple", OperationDefinitionTestCase.class.getName(), Thread.currentThread().getContextClassLoader()));
        builder.addParameter(create("parameter", ModelType.STRING, true).setAllowExpression(true).build());
        builder.addParameter(create("deprecated-parameter", ModelType.STRING, true).setDeprecated(ModelVersion.CURRENT).setAllowExpression(true).build());
        builder.addParameter(create("duplicated", ModelType.STRING, true).setAllowExpression(true).build());
        builder.setReplyType(ModelType.LIST);
        builder.setReplyParameters(new SimpleListAttributeDefinition.Builder("reply-list",
                create("parameter", ModelType.STRING, true).setAllowExpression(true).build()).build());
        ModelNode description = builder.build().getDescriptionProvider().getModelDescription(Locale.ROOT);
        assertThat(description.get(OPERATION_NAME).asString(), is("operation"));
        assertThat(description.get(DESCRIPTION).asString(), is("Simple operation test"));
        assertTrue(description.hasDefined(REQUEST_PROPERTIES));
        ModelNode parameters = description.get(REQUEST_PROPERTIES);
        assertTrue(parameters.hasDefined("parameter"));
        assertThat(parameters.get("parameter", DESCRIPTION).asString(), is("Simple operation parameter"));
        assertTrue(parameters.hasDefined("deprecated-parameter"));
        assertThat(parameters.get("deprecated-parameter", DESCRIPTION).asString(), is("Simple operation deprecated parameter"));
        assertThat(parameters.get("deprecated-parameter", DEPRECATED, SINCE).asString(), is(ModelVersion.CURRENT.toString()));
        assertThat(parameters.get("deprecated-parameter", DEPRECATED, REASON).asString(), is("The simple operation deprecated parameter is deprecated and may be removed in the near future."));
        assertTrue(parameters.hasDefined("duplicated"));
        assertThat(parameters.get("duplicated", DESCRIPTION).asString(), is("Simple operation parameter"));
        assertTrue(description.hasDefined(REPLY_PROPERTIES, TYPE));
        assertTrue(description.hasDefined(REPLY_PROPERTIES, VALUE_TYPE, "reply-list", DESCRIPTION));
        assertThat(description.get(REPLY_PROPERTIES, VALUE_TYPE, "reply-list", DESCRIPTION).asString(), is("Simple operation reply list of parameters"));
    }

    @Test
    public void testObjectListOperationDefinition() {
        SimpleOperationDefinitionBuilder builder = new SimpleOperationDefinitionBuilder("operation",
                new StandardResourceDescriptionResolver("simple", OperationDefinitionTestCase.class.getName(), Thread.currentThread().getContextClassLoader()));
        builder.addParameter(create("parameter", ModelType.STRING, true).setAllowExpression(true).build());
        builder.addParameter(create("deprecated-parameter", ModelType.STRING, true).setDeprecated(ModelVersion.CURRENT).setAllowExpression(true).build());
        builder.addParameter(create("duplicated", ModelType.STRING, true).setAllowExpression(true).build());
        builder.setReplyType(ModelType.LIST);
        builder.setReplyParameters(new ObjectListAttributeDefinition.Builder("object-list",
                ObjectTypeAttributeDefinition.Builder.of("object",
                        create("attribute", ModelType.STRING, true).setAllowExpression(true).build())
                        .build()).build());
        ModelNode description = builder.build().getDescriptionProvider().getModelDescription(Locale.ROOT);
        assertThat(description.get(OPERATION_NAME).asString(), is("operation"));
        assertThat(description.get(DESCRIPTION).asString(), is("Simple operation test"));
        assertTrue(description.hasDefined(REQUEST_PROPERTIES));
        ModelNode parameters = description.get(REQUEST_PROPERTIES);
        assertTrue(parameters.hasDefined("parameter"));
        assertThat(parameters.get("parameter", DESCRIPTION).asString(), is("Simple operation parameter"));
        assertTrue(parameters.hasDefined("deprecated-parameter"));
        assertThat(parameters.get("deprecated-parameter", DESCRIPTION).asString(), is("Simple operation deprecated parameter"));
        assertThat(parameters.get("deprecated-parameter", DEPRECATED, SINCE).asString(), is(ModelVersion.CURRENT.toString()));
        assertThat(parameters.get("deprecated-parameter", DEPRECATED, REASON).asString(), is("The simple operation deprecated parameter is deprecated and may be removed in the near future."));
        assertTrue(parameters.hasDefined("duplicated"));
        assertThat(parameters.get("duplicated", DESCRIPTION).asString(), is("Simple operation parameter"));
        assertTrue(description.hasDefined(REPLY_PROPERTIES, TYPE));
        assertTrue(description.hasDefined(REPLY_PROPERTIES, VALUE_TYPE, "object-list", DESCRIPTION));
        assertThat(description.get(REPLY_PROPERTIES, VALUE_TYPE, "object-list", DESCRIPTION).asString(), is("Simple operation reply list of objects"));
    }

    @Test
    public void testSimpleOperationDefinitionFallBack() {
        SimpleOperationDefinitionBuilder builder = new SimpleOperationDefinitionBuilder("operation",
                new StandardResourceDescriptionResolver("simple", OperationDefinitionTestCase.class.getName(), Thread.currentThread().getContextClassLoader()));
        builder.addParameter(create("parameter", ModelType.STRING, true).setAllowExpression(true).build());
        builder.addParameter(create("deprecated-parameter", ModelType.STRING, true).setDeprecated(ModelVersion.CURRENT).setAllowExpression(true).build());
        builder.addParameter(create("duplicated", ModelType.STRING, true).setAllowExpression(true).build());
        builder.setReplyParameters(create("parameter", ModelType.STRING, true).setAllowExpression(true).build());
        ModelNode description = builder.build().getDescriptionProvider().getModelDescription(Locale.ROOT);
        assertThat(description.get(OPERATION_NAME).asString(), is("operation"));
        assertThat(description.get(DESCRIPTION).asString(), is("Simple operation test"));
        assertTrue(description.hasDefined(REQUEST_PROPERTIES));
        ModelNode parameters = description.get(REQUEST_PROPERTIES);
        assertTrue(parameters.hasDefined("parameter"));
        assertThat(parameters.get("parameter", DESCRIPTION).asString(), is("Simple operation parameter"));
        assertTrue(parameters.hasDefined("deprecated-parameter"));
        assertThat(parameters.get("deprecated-parameter", DESCRIPTION).asString(), is("Simple operation deprecated parameter"));
        assertThat(parameters.get("deprecated-parameter", DEPRECATED, SINCE).asString(), is(ModelVersion.CURRENT.toString()));
        assertThat(parameters.get("deprecated-parameter", DEPRECATED, REASON).asString(), is("The simple operation deprecated parameter is deprecated and may be removed in the near future."));
        assertTrue(parameters.hasDefined("duplicated"));
        assertThat(parameters.get("duplicated", DESCRIPTION).asString(), is("Simple operation parameter"));
        assertTrue(description.hasDefined(REPLY_PROPERTIES));
        ModelNode replies = description.get(REPLY_PROPERTIES);
        assertTrue(replies.hasDefined(DESCRIPTION));
        assertThat(replies.get(DESCRIPTION).asString(), is("Simple operation parameter"));
    }

    @Test
    public void testNotDefinedSimpleOperationDefinition() {
        SimpleOperationDefinitionBuilder builder = new SimpleOperationDefinitionBuilder("operation",
                new StandardResourceDescriptionResolver("simple", OperationDefinitionTestCase.class.getName(), Thread.currentThread().getContextClassLoader()));
        builder.addParameter(create("parameter", ModelType.STRING, true).setAllowExpression(true).build());
        builder.addParameter(create("deprecated-parameter", ModelType.STRING, true).setDeprecated(ModelVersion.CURRENT).setAllowExpression(true).build());
        builder.addParameter(create("duplicated", ModelType.STRING, true).setAllowExpression(true).build());
        builder.setReplyParameters(create("not-defined-parameter", ModelType.STRING, true).setAllowExpression(true).build());
        try {
            builder.build().getDescriptionProvider().getModelDescription(Locale.ROOT);
            Assert.fail("The description for \"not-defined-parameter\" isn't existing, htis should have failed");
        } catch(MissingResourceException e) {
            assertThat(e.getMessage(), CoreMatchers.containsString("simple.operation.reply.not-defined-parameter"));
        }
    }
}
