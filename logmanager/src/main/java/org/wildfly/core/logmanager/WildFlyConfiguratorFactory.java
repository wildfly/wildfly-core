/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager;

import java.util.logging.Level;

import org.jboss.logmanager.ConfiguratorFactory;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.LogContextConfigurator;
import org.jboss.logmanager.Logger;
import org.wildfly.core.logmanager.config.LogContextConfiguration;
import org.wildfly.core.logmanager.config.PropertyLogContextConfiguration;
import org.jboss.logmanager.configuration.ContextConfiguration;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
// TODO (jrp) do we need this or just the LogContextConfigurator?
// TODO (jrp) we need to consider what happens when we don't execute the runtime stage. Possibly with logging we do the
// TODO (jrp) configuration in the model stage. Or we have a default ConsoleHandler only. This can be handled in the
// TODO (jrp) subsystem though
public class WildFlyConfiguratorFactory implements ConfiguratorFactory {

    @Override
    public LogContextConfigurator create() {
        return (logContext, inputStream) -> {
            // TODO (jrp) testing, this should be thought about
            WildFlyLogContextSelector.getContextSelector();
            final LogContext context = logContext == null ? WildFlyLogContextSelector.getContextSelector()
                    .getLogContext() : logContext;
            final LogContextConfiguration configuration = PropertyLogContextConfiguration.configure(
                    LogContextConfiguration.getInstance(context), inputStream);

            // TODO (jrp) what do we do if there is a logging.properties file? We do need to support this for
            // TODO (jrp) legacy reasons. However, we also need to support adding the DelayedHandler. The
            // TODO (jrp) PropertiesLogContextConfigurator will attempt to load implementations of LogContextConfigurator
            // TODO (jrp) with a service loader.
            // TODO (jrp) I think what we do is check if a ContextConfiguration is attached to the current context.
            // TODO (jrp) if it is, log a warning/error, if not continue.

            // TODO (jrp) we need a way to set the default log level
            //final Level defaultLevel = Level.INFO;

            // Configure a DelayedHandler
            //final WildFlyDelayedHandler delayedHandler = new WildFlyDelayedHandler(logContext);
            //delayedHandler.setLevel(defaultLevel);
            //delayedHandler.setCloseChildren(false);
            // Add the handler to the root logger
            //final Logger root = logContext.getLogger("");
            //root.addHandler(delayedHandler);
            //root.setLevel(defaultLevel);

            // Add this configuration as the context configuration
            logContext.attach(ContextConfiguration.CONTEXT_CONFIGURATION_KEY, configuration);
        };
    }

    @Override
    public int priority() {
        return 0;
    }

    static void reset() {
        final LogContext logContext = LogContext.getLogContext();

        // Add this configuration to a default ContextConfiguration
        var configuration = logContext.detach(ContextConfiguration.CONTEXT_CONFIGURATION_KEY);
        if (configuration != null) {
            try {
                configuration.close();
            } catch (Exception e) {
                // TODO (jrp) what do we actually do here?
                throw new RuntimeException(e);
            }
        }
        final Level defaultLevel = Level.INFO;
        // Configure a DelayedHandler
        final WildFlyDelayedHandler delayedHandler = new WildFlyDelayedHandler(logContext);
        delayedHandler.setLevel(defaultLevel);
        delayedHandler.setCloseChildren(false);
        // Add the handler to the root logger
        final Logger root = logContext.getLogger("");
        root.addHandler(delayedHandler);
        root.setLevel(defaultLevel);
        configuration = LogContextConfiguration.getInstance(logContext);
        logContext.attach(ContextConfiguration.CONTEXT_CONFIGURATION_KEY, configuration);
    }
}
