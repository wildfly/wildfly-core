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
import java.util.Optional;
import java.util.ResourceBundle;

import org.jboss.as.controller.PathElement;

/**
 * Generates resource descriptions for a child resource of a subsystem.
 * @author Paul Ferraro
 */
public class ChildResourceDescriptionResolver implements ResourceDescriptionResolver {

    private final ResourceDescriptionResolver parent;
    private final String prefix;
    private final List<PathElement> paths;

    ChildResourceDescriptionResolver(ResourceDescriptionResolver parent, String prefix, List<PathElement> paths) {
        this.parent = parent;
        this.prefix = prefix;
        this.paths = paths;
    }

    @Override
    public ResourceBundle getResourceBundle(Locale locale) {
        return this.parent.getResourceBundle(locale);
    }

    @Override
    public String getResourceAttributeDescription(String attributeName, Locale locale, ResourceBundle bundle) {
        Optional<MissingResourceException> exception = Optional.empty();
        for (PathElement path : this.paths) {
            String key = this.getBundleKey(path, attributeName);
            try {
                return bundle.getString(key);
            } catch (MissingResourceException e) {
                if (exception.isEmpty()) {
                    exception = Optional.of(e);
                }
            }
        }
        try {
            return this.parent.getResourceAttributeDescription(attributeName, locale, bundle);
        } catch (MissingResourceException e) {
            throw exception.orElse(e);
        }
    }

    @Override
    public String getResourceAttributeValueTypeDescription(String attributeName, Locale locale, ResourceBundle bundle, String... suffixes) {
        Optional<MissingResourceException> exception = Optional.empty();
        for (PathElement path : this.paths) {
            String key = this.getBundleKey(path, attributeName, suffixes);
            try {
                return bundle.getString(key);
            } catch (MissingResourceException e) {
                if (exception.isEmpty()) {
                    exception = Optional.of(e);
                }
            }
        }
        try {
            return this.parent.getResourceAttributeValueTypeDescription(attributeName, locale, bundle, suffixes);
        } catch (MissingResourceException e) {
            throw exception.orElse(e);
        }
    }

    @Override
    public String getOperationDescription(String operationName, Locale locale, ResourceBundle bundle) {
        Optional<MissingResourceException> exception = Optional.empty();
        for (PathElement path : this.paths) {
            String key = this.getBundleKey(path, operationName);
            try {
                return bundle.getString(key);
            } catch (MissingResourceException e) {
                if (exception.isEmpty()) {
                    exception = Optional.of(e);
                }
            }
        }
        try {
            return this.parent.getOperationDescription(operationName, locale, bundle);
        } catch (MissingResourceException e) {
            throw exception.orElse(e);
        }
    }

    @Override
    public String getOperationParameterDescription(String operationName, String paramName, Locale locale, ResourceBundle bundle) {
        Optional<MissingResourceException> exception = Optional.empty();
        for (PathElement path : this.paths) {
            String key = this.getBundleKey(path, operationName.equals(ModelDescriptionConstants.ADD) ? List.of(paramName) : List.of(operationName, paramName));
            try {
                return bundle.getString(key);
            } catch (MissingResourceException e) {
                if (exception.isEmpty()) {
                    exception = Optional.of(e);
                }
            }
        }
        try {
            return this.parent.getOperationParameterDescription(operationName, paramName, locale, bundle);
        } catch (MissingResourceException e) {
            throw exception.orElse(e);
        }
    }

    @Override
    public String getOperationParameterValueTypeDescription(String operationName, String paramName, Locale locale, ResourceBundle bundle, String... suffixes) {
        Optional<MissingResourceException> exception = Optional.empty();
        for (PathElement path : this.paths) {
            String key = this.getBundleKey(path, operationName.equals(ModelDescriptionConstants.ADD) ? List.of(paramName) : List.of(operationName, paramName), suffixes);
            try {
                return bundle.getString(key);
            } catch (MissingResourceException e) {
                if (exception.isEmpty()) {
                    exception = Optional.of(e);
                }
            }
        }
        try {
            return this.parent.getOperationParameterValueTypeDescription(operationName, paramName, locale, bundle, suffixes);
        } catch (MissingResourceException e) {
            throw exception.orElse(e);
        }
    }

    @Override
    public String getOperationReplyDescription(String operationName, Locale locale, ResourceBundle bundle) {
        Optional<MissingResourceException> exception = Optional.empty();
        for (PathElement path : this.paths) {
            String key = this.getBundleKey(path, List.of(operationName, StandardResourceDescriptionResolver.REPLY));
            try {
                return bundle.getString(key);
            } catch (MissingResourceException e) {
                if (exception.isEmpty()) {
                    exception = Optional.of(e);
                }
            }
        }
        try {
            return this.parent.getOperationReplyDescription(operationName, locale, bundle);
        } catch (MissingResourceException e) {
            throw exception.orElse(e);
        }
    }

    @Override
    public String getOperationReplyValueTypeDescription(String operationName, Locale locale, ResourceBundle bundle, String... suffixes) {
        Optional<MissingResourceException> exception = Optional.empty();
        for (PathElement path : this.paths) {
            String key = this.getBundleKey(path, List.of(operationName, StandardResourceDescriptionResolver.REPLY), suffixes);
            try {
                return bundle.getString(key);
            } catch (MissingResourceException e) {
                if (exception.isEmpty()) {
                    exception = Optional.of(e);
                }
            }
        }
        try {
            return this.parent.getOperationReplyValueTypeDescription(operationName, locale, bundle, suffixes);
        } catch (MissingResourceException e) {
            throw exception.orElse(e);
        }
    }

    @Override
    public String getNotificationDescription(String notificationType, Locale locale, ResourceBundle bundle) {
        Optional<MissingResourceException> exception = Optional.empty();
        for (PathElement path : this.paths) {
            String key = this.getBundleKey(path, notificationType);
            try {
                return bundle.getString(key);
            } catch (MissingResourceException e) {
                if (exception.isEmpty()) {
                    exception = Optional.of(e);
                }
            }
        }
        try {
            return this.parent.getNotificationDescription(notificationType, locale, bundle);
        } catch (MissingResourceException e) {
            throw exception.orElse(e);
        }
    }

    @Override
    public String getChildTypeDescription(String childType, Locale locale, ResourceBundle bundle) {
        Optional<MissingResourceException> exception = Optional.empty();
        for (PathElement path : this.paths) {
            String key = this.getBundleKey(path, childType);
            try {
                return bundle.getString(key);
            } catch (MissingResourceException e) {
                if (exception.isEmpty()) {
                    exception = Optional.of(e);
                }
            }
        }
        try {
            return this.parent.getChildTypeDescription(childType, locale, bundle);
        } catch (MissingResourceException e) {
            throw exception.orElse(e);
        }
    }

    @Override
    public String getResourceDescription(Locale locale, ResourceBundle bundle) {
        Optional<MissingResourceException> exception = Optional.empty();
        for (PathElement path : this.paths) {
            String key = this.getBundleKey(path);
            try {
                return bundle.getString(key);
            } catch (MissingResourceException e) {
                if (exception.isEmpty()) {
                    exception = Optional.of(e);
                }
            }
        }
        try {
            return this.parent.getResourceDescription(locale, bundle);
        } catch (MissingResourceException e) {
            throw exception.orElse(e);
        }
    }

    @Override
    public String getResourceDeprecatedDescription(Locale locale, ResourceBundle bundle) {
        Optional<MissingResourceException> exception = Optional.empty();
        for (PathElement path : this.paths) {
            String key = this.getBundleKey(path, List.of(ModelDescriptionConstants.DEPRECATED));
            try {
                return bundle.getString(key);
            } catch (MissingResourceException e) {
                if (exception.isEmpty()) {
                    exception = Optional.of(e);
                }
            }
        }
        try {
            return this.parent.getResourceDeprecatedDescription(locale, bundle);
        } catch (MissingResourceException e) {
            throw exception.orElse(e);
        }
    }

    @Override
    public String getResourceAttributeDeprecatedDescription(String attributeName, Locale locale, ResourceBundle bundle) {
        Optional<MissingResourceException> exception = Optional.empty();
        for (PathElement path : this.paths) {
            String key = this.getBundleKey(path, List.of(attributeName, ModelDescriptionConstants.DEPRECATED));
            try {
                return bundle.getString(key);
            } catch (MissingResourceException e) {
                if (exception.isEmpty()) {
                    exception = Optional.of(e);
                }
            }
        }
        try {
            return this.parent.getResourceAttributeDeprecatedDescription(attributeName, locale, bundle);
        } catch (MissingResourceException e) {
            throw exception.orElse(e);
        }
    }

    @Override
    public String getOperationDeprecatedDescription(String operationName, Locale locale, ResourceBundle bundle) {
        Optional<MissingResourceException> exception = Optional.empty();
        for (PathElement path : this.paths) {
            String key = this.getBundleKey(path, List.of(operationName, ModelDescriptionConstants.DEPRECATED));
            try {
                return bundle.getString(key);
            } catch (MissingResourceException e) {
                if (exception.isEmpty()) {
                    exception = Optional.of(e);
                }
            }
        }
        try {
            return this.parent.getOperationDeprecatedDescription(operationName, locale, bundle);
        } catch (MissingResourceException e) {
            throw exception.orElse(e);
        }
    }

    @Override
    public String getOperationParameterDeprecatedDescription(String operationName, String paramName, Locale locale, ResourceBundle bundle) {
        Optional<MissingResourceException> exception = Optional.empty();
        for (PathElement path : this.paths) {
            String key = this.getBundleKey(path, operationName.equals(ModelDescriptionConstants.ADD) ? List.of(paramName, ModelDescriptionConstants.DEPRECATED) : List.of(operationName, paramName, ModelDescriptionConstants.DEPRECATED));
            try {
                return bundle.getString(key);
            } catch (MissingResourceException e) {
                if (exception.isEmpty()) {
                    exception = Optional.of(e);
                }
            }
        }
        try {
            return this.parent.getOperationParameterDeprecatedDescription(operationName, paramName, locale, bundle);
        } catch (MissingResourceException e) {
            throw exception.orElse(e);
        }
    }

    private String getBundleKey(PathElement path) {
        return this.getBundleKey(path, List.of());
    }

    private String getBundleKey(PathElement path, String key) {
        return this.getBundleKey(path, List.of(key));
    }

    private String getBundleKey(PathElement path, String key, String... suffixes) {
        return this.getBundleKey(path, List.of(key), suffixes);
    }

    private String getBundleKey(PathElement path, List<String> keys) {
        return this.getBundleKey(path, keys, List.of());
    }

    private String getBundleKey(PathElement path, List<String> keys, String... suffixes) {
        return this.getBundleKey(path, keys, List.of(suffixes));
    }

    private String getBundleKey(PathElement path, List<String> keys, List<String> suffixes) {
        StringBuilder builder = new StringBuilder(this.prefix);
        for (String value : path.isWildcard() ? List.of(path.getKey()) : List.of(path.getKey(), path.getValue())) {
            builder.append('.').append(value);
        }
        for (String key : keys) {
            builder.append('.').append(key);
        }
        for (String suffix : suffixes) {
            builder.append('.').append(suffix);
        }
        return builder.toString();
    }
}
