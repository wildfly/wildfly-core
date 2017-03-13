package org.wildfly.test.jmx.staticmodule;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.server.CurrentServiceContainer;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceTarget;

/**
 * @author Jan Martiska
 */
public class JMXControlledStateNotificationListenerExtension implements Extension {

    private static final Logger log = Logger.getLogger(JMXControlledStateNotificationListenerExtension.class);

    public static final String SUBSYSTEM_NAME = "jmx-state-notification-listener";
    public static final String EXTENSION_NAME = "jmx-notification-listener";

    public static final String NAMESPACE
            = "urn:wildfly:extension:jmx-controlled-state-notification-listener:1.0";

    private static final EmptySubsystemParser PARSER = new EmptySubsystemParser(NAMESPACE);

    ServiceContainer container;

    /**
     * Install the MSC service which listens for JMX notifications.
     */
    @Override
    public void initialize(ExtensionContext context) {
        log.info("Initializing " + EXTENSION_NAME);
        container = CurrentServiceContainer.getServiceContainer();
        if(container.getService(JMXNotificationsService.SERVICE_NAME) == null) {
            log.info("Installing " + JMXNotificationsService.SERVICE_NAME);
            final ServiceTarget target = container.subTarget();
            JMXNotificationsService service = new JMXNotificationsService();
            target.addService(JMXNotificationsService.SERVICE_NAME, service)
                    .install();
        }
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE, PARSER);
    }

}
