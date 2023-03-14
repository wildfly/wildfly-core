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

import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.function.Function;

import org.jboss.as.controller.PathElement;

/**
 * Generates resource descriptions for a child resource of a subsystem.
 * @author Paul Ferraro
 */
public class ChildResourceDescriptionResolver implements ParentResourceDescriptionResolver {

    private final ParentResourceDescriptionResolver parent;
    private final ParentResourceDescriptionResolver resolver;
    private final Iterable<ResourceDescriptionResolver> alternates;

    ChildResourceDescriptionResolver(ParentResourceDescriptionResolver parent, ParentResourceDescriptionResolver resolver, Iterable<ResourceDescriptionResolver> alternates) {
        this.parent = parent;
        this.resolver = resolver;
        this.alternates = alternates;
    }

    @Override
    public ParentResourceDescriptionResolver createChildResolver(PathElement path, List<PathElement> alternatePaths) {
        return this.resolver.createChildResolver(path, alternatePaths);
    }

    @Override
    public ResourceBundle getResourceBundle(Locale locale) {
        return this.resolver.getResourceBundle(locale);
    }

    @Override
    public String getResourceDescription(Locale locale, ResourceBundle bundle) {
        return this.getDescription(resolver -> resolver.getResourceDescription(locale, bundle));
    }

    @Override
    public String getResourceAttributeDescription(String attributeName, Locale locale, ResourceBundle bundle) {
        return this.getDescription(resolver -> resolver.getResourceAttributeDescription(attributeName, locale, bundle));
    }

    @Override
    public String getResourceAttributeValueTypeDescription(String attributeName, Locale locale, ResourceBundle bundle, String... suffixes) {
        return this.getDescription(resolver -> resolver.getResourceAttributeValueTypeDescription(attributeName, locale, bundle, suffixes));
    }

    @Override
    public String getOperationDescription(String operationName, Locale locale, ResourceBundle bundle) {
        return this.getDescription(resolver -> resolver.getOperationDescription(operationName, locale, bundle));
    }

    @Override
    public String getOperationParameterDescription(String operationName, String paramName, Locale locale, ResourceBundle bundle) {
        return this.getDescription(resolver -> resolver.getOperationParameterDescription(operationName, paramName, locale, bundle));
    }

    @Override
    public String getOperationParameterValueTypeDescription(String operationName, String paramName, Locale locale, ResourceBundle bundle, String... suffixes) {
        return this.getDescription(resolver -> resolver.getOperationParameterValueTypeDescription(operationName, paramName, locale, bundle, suffixes));
    }

    @Override
    public String getOperationReplyDescription(String operationName, Locale locale, ResourceBundle bundle) {
        // This method returns null, rather than throwing an exception if the key was not found
        String result = this.resolver.getOperationReplyDescription(operationName, locale, bundle);
        for (ResourceDescriptionResolver alternate : this.alternates) {
            if (result == null) {
                result = alternate.getOperationReplyDescription(operationName, locale, bundle);
            }
        }
        return (result != null) ? result : this.parent.getOperationReplyDescription(operationName, locale, bundle);
    }

    @Override
    public String getOperationReplyValueTypeDescription(String operationName, Locale locale, ResourceBundle bundle, String... suffixes) {
        return this.getDescription(resolver -> resolver.getOperationReplyValueTypeDescription(operationName, locale, bundle, suffixes));
    }

    @Override
    public String getNotificationDescription(String notificationType, Locale locale, ResourceBundle bundle) {
        return this.getDescription(resolver -> resolver.getNotificationDescription(notificationType, locale, bundle));
    }

    @Override
    public String getChildTypeDescription(String childType, Locale locale, ResourceBundle bundle) {
        return this.getDescription(resolver -> resolver.getChildTypeDescription(childType, locale, bundle));
    }

    @Override
    public String getResourceDeprecatedDescription(Locale locale, ResourceBundle bundle) {
        return this.getDescription(resolver -> resolver.getResourceDeprecatedDescription(locale, bundle));
    }

    @Override
    public String getResourceAttributeDeprecatedDescription(String attributeName, Locale locale, ResourceBundle bundle) {
        return this.getDescription(resolver -> resolver.getResourceAttributeDeprecatedDescription(attributeName, locale, bundle));
    }

    @Override
    public String getOperationDeprecatedDescription(String operationName, Locale locale, ResourceBundle bundle) {
        return this.getDescription(resolver -> resolver.getOperationDeprecatedDescription(operationName, locale, bundle));
    }

    @Override
    public String getOperationParameterDeprecatedDescription(String operationName, String paramName, Locale locale, ResourceBundle bundle) {
        return this.getDescription(resolver -> resolver.getOperationParameterDeprecatedDescription(operationName, paramName, locale, bundle));
    }

    private String getDescription(Function<ResourceDescriptionResolver, String> description) {
        try {
            return description.apply(this.resolver);
        } catch (MissingResourceException e) {
            for (ResourceDescriptionResolver alternate : this.alternates) {
                try {
                    return description.apply(alternate);
                } catch (MissingResourceException ignored) {
                    // Ignore
                }
            }
            try {
                return description.apply(this.parent);
            } catch (MissingResourceException ignored) {
                // Throw original exception
                throw e;
            }
        }
    }
}
