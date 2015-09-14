package org.jboss.as.server.loaders;

import org.jboss.modules.IterableResourceLoader;

/**
 * Resource loader.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface ResourceLoader extends IterableResourceLoader {

    ResourceLoader getParent();

}
