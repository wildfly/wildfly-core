/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

import static org.wildfly.extension.elytron.SecurityActions.doPrivileged;

import java.lang.reflect.Field;
import java.security.PrivilegedAction;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * A {@link Service} responsible for the runtime changes for security properties.
 *
 * Only a single instance of this service should be registered but we make use of a registered service so that we can respond to
 * lifecycle events and clean up during a restart.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class SecurityPropertyService implements Service<Void> {

    static final ServiceName SERVICE_NAME = ElytronExtension.BASE_SERVICE_NAME.append(ElytronDescriptionConstants.SECURITY_PROPERTIES);

    private volatile boolean started = false;

    /**
     * A {@link Map} of all properties set by this service, the value is the value to restore when the property is being
     * removed.
     */
    private final Map<String, SetState> valuesSet = new HashMap<String, SetState>();

    /**
     * A {@link Map} of values still to be set by this service, if values are added to this service when it has not been started
     * they are held here until the service starts.
     */
    private final Map<String, String> toSet = new HashMap<String, String>();

    SecurityPropertyService(Map<String,String> propertiesToBeSet) {
        toSet.putAll(propertiesToBeSet);
    }

    @Override
    public synchronized void start(StartContext context) throws StartException {
        doPrivileged((PrivilegedAction<Void>) () -> {
            toSet.forEach((String name, String value) -> setPropertyImmediate(name, value));
            return null;
        });
        toSet.clear();
        started = true;
    }

    @Override
    public synchronized void stop(StopContext context) {
        doPrivileged((PrivilegedAction<Void>) () -> {
            valuesSet.forEach((String name, SetState state) -> {
                restoreProperty(name, state.getPreviousValue());
                toSet.put(name, state.getNewValue());
            });
            return null;
        });
        valuesSet.clear();
        started = false;
    }

    @Override
    public Void getValue() throws IllegalStateException, IllegalArgumentException {
        return null;
    }

    private void restoreProperty(String name, String restorationValue) {
        if (restorationValue == null) {
            try {
                Field f = Security.class.getDeclaredField("props");
                f.setAccessible(true);
                Properties props = (Properties) f.get(null);
                props.remove(name);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
            }
        } else {
            Security.setProperty(name, restorationValue);
        }
    }

    private void setPropertyImmediate(String name, String value) {
        String original = valuesSet.containsKey(name) ? valuesSet.get(name).previousValue : Security.getProperty(name);
        Security.setProperty(name, value);
        valuesSet.put(name, new SetState(original, value));
    }

    synchronized void setProperty(String name, String value) {
        if (started) {
            doPrivileged((PrivilegedAction<Void>) () -> {
                setPropertyImmediate(name, value);
                return null;
            });
        } else {
            toSet.put(name, value);
        }
    }

    synchronized void removeProperty(String name) {
        if (started) {
            SetState state = valuesSet.remove(name);
            doPrivileged((PrivilegedAction<Void>) () -> {
                restoreProperty(name, state.getPreviousValue());
                return null;
            });
        } else {
            toSet.remove(name);
        }
    }

    private static class SetState {
        private final String previousValue;
        private final String newValue;

        private SetState(String previousValue, String newValue) {
            this.previousValue = previousValue;
            this.newValue = newValue;
        }

        private String getPreviousValue() {
            return previousValue;
        }
        private String getNewValue() {
            return newValue;
        }

    }

}
