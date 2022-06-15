package org.apache.solr.handler.dataimport;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Code copied from Solr 8.11.1
 */
public class SolrPaths {

    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final Set<String> loggedOnce = new ConcurrentSkipListSet<>();

    /**
     * Finds the solrhome based on looking up the value in one of three places:
     * <ol>
     * <li>JNDI: via java:comp/env/solr/home</li>
     * <li>The system property solr.solr.home</li>
     * <li>Look in the current working directory for a solr/ directory</li>
     * </ol>
     * <p>
     * The return value is normalized.  Normalization essentially means it ends in a trailing slash.
     *
     * @return A normalized solrhome
     */
    public static Path locateSolrHome() {

        String home = null;
        // Try JNDI
        try {
            Context c = new InitialContext();
            home = (String) c.lookup("java:comp/env/solr/home");
            logOnceInfo("home_using_jndi", "Using JNDI solr.home: " + home);
        } catch (NoInitialContextException e) {
            log.debug("JNDI not configured for solr (NoInitialContextEx)");
        } catch (NamingException e) {
            log.debug("No /solr/home in JNDI");
        } catch (RuntimeException ex) {
            log.warn("Odd RuntimeException while testing for JNDI: ", ex);
        }

        // Now try system property
        if (home == null) {
            String prop = "solr.solr.home";
            home = System.getProperty(prop);
            if (home != null) {
                logOnceInfo("home_using_sysprop", "Using system property " + prop + ": " + home);
            }
        }

        // if all else fails, try
        if (home == null) {
            home = "solr/";
            logOnceInfo("home_default", "solr home defaulted to '" + home + "' (could not find system property or JNDI)");
        }
        return Paths.get(home);
    }

    // Logs a message only once per startup
    private static void logOnceInfo(String key, String msg) {
        if (!loggedOnce.contains(key)) {
            loggedOnce.add(key);
            log.info(msg);
        }
    }

}
