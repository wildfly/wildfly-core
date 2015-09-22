package org.jboss.as.server.loaders;

import org.jboss.modules.IterableResourceLoader;

import java.io.File;

/**
 * Resource loader.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface ResourceLoader extends IterableResourceLoader {

    ResourceLoader getParent();

    void addOverlay(String path, File content);

}
