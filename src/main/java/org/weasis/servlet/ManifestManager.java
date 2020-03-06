/*******************************************************************************
 * Copyright (c) 2014 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.ClosableURLConnection;
import org.weasis.core.api.util.NetworkUtil;
import org.weasis.core.api.util.URLParameters;
import org.weasis.dicom.mf.thread.ManifestBuilder;
import org.weasis.dicom.mf.thread.ManifestManagerThread;

@WebListener
public class ManifestManager implements ServletContextListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManifestManager.class);

    public static final String DEFAULT_TEMPLATE = "weasis.jnlp";

    private final ConcurrentHashMap<Integer, ManifestBuilder> manifestBuilderMap = new ConcurrentHashMap<>();
    private final ManifestManagerThread manifestManagerThread = new ManifestManagerThread(manifestBuilderMap);
    private final Map<URI, Element> jnlpTemplates = ManifestManager.<URI, Element> createLRUMap(20);

    @Override
    public void contextInitialized(ServletContextEvent context) {
        LOGGER.info("Start the manifest manager running as a background process");
        ServletContext sc = context.getServletContext();
        if (sc.getAttribute("manifestBuilderMap") != null) {
            LOGGER.error(
                "A manifest manager thread is already running in the servlet context! The new one won't be started.");
        } else {
            LOGGER.info("Server info: {} ", sc.getServerInfo());
            LOGGER.debug("Real path: {}", sc.getRealPath("/"));

            final ConnectorProperties properties = new ConnectorProperties();
            try {
                String configDir = System.getProperty("jboss.server.config.dir", "");

                URL configUrl = readConfigURL(configDir, "weasis-pacs-connector.properties");
                LOGGER.info("Path of weasis-pacs-connector configuration: {}", configUrl);
                
                configDir += "/";

                if (configUrl != null) {
                    String baseConfigDir = getBaseConfigURL(configUrl);
                    properties.load(configUrl.openStream());

                    String requests = properties.getProperty("request.ids");
                    String requestIID = properties.getProperty("request.IID.level");
                    if (requests == null) {
                        LOGGER.error("No request ID is allowed for the web services!");
                    } else {
                        for (String id : requests.split(",")) {
                            properties.put(id.trim(), "true");
                        }
                    }
                    if (requestIID == null) {
                        LOGGER.error("No request level is allowed for the web context /IHEInvokeImageDisplay!");
                    } else {
                        for (String id : requestIID.split(",")) {
                            properties.put(id.trim(), "true");
                        }
                    }

                    properties.setProperty(ConnectorProperties.CONFIG_FILENAME, "default");
                    String arcConfigList = properties.getProperty("arc.config.list");
                    if (arcConfigList != null) {
                        for (String arc : arcConfigList.split(",")) {
                            properties.addArchiveProperties(readArchiveURL(baseConfigDir, configDir, arc.trim()));
                        }
                    }

                    initJnlpTemplate(sc, configDir, properties.getProperty("jnlp.default.name", DEFAULT_TEMPLATE),
                        "weasis.default.jnlp", properties);
                } else {
                    LOGGER.error("Cannot find  a configuration file for weasis-pacs-connector");
                }

            } catch (Exception e) {
                LOGGER.error("Error on initialization of ManifestManager", e);
            }
            sc.setAttribute("componentProperties", properties);
            sc.setAttribute("jnlpTemplates", jnlpTemplates);

            manifestManagerThread.setCleanFrequency(ServletUtil.getLongProperty(properties, "thread.clean.frequency",
                ManifestManagerThread.CLEAN_FREQUENCY));
            manifestManagerThread.setMaxLifeCycle(
                ServletUtil.getLongProperty(properties, "thread.max.life.clyle", ManifestManagerThread.MAX_LIFE_CYCLE));
            LOGGER.info("ManifestManagerThread configuration (maxLifeCycle={}s, cleanFrequency={}s)",
                TimeUnit.MILLISECONDS.toSeconds(manifestManagerThread.getMaxLifeCycle()),
                TimeUnit.MILLISECONDS.toSeconds(manifestManagerThread.getCleanFrequency()));

            sc.setAttribute("manifestExecutor",
                Executors.newFixedThreadPool(ServletUtil.getIntProperty(properties, "thread.manifest.concurrency", 5)));
            sc.setAttribute("manifestBuilderMap", manifestBuilderMap);
            manifestManagerThread.start();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent context) {
        LOGGER.info("Stop the manifest manager servlet");

        manifestManagerThread.interrupt();
    }

    private static String getBaseConfigURL(URL config) {
        String val = config.toString();
        return val.substring(0, val.lastIndexOf('/') + 1);
    }

    private URL readConfigURL(String configDir, String name) throws IOException {
        try {
            File file = new File(configDir, name);
            if (file.canRead()) {
                return file.toURI().toURL();
            }
        } catch (Exception e) {
            LOGGER.error("Get url of {}", name, e);
        }
        return this.getClass().getResource("/" + name);
    }

    private Properties readArchiveURL(String baseConfigDir, String configDir, String name) throws IOException {
        Properties archiveProps = new Properties();
        try {
            URL url = new URL(baseConfigDir + name);
            try (ClosableURLConnection c = NetworkUtil.getUrlConnection(url, new URLParameters())) {
                archiveProps.load(c.getInputStream());
                LOGGER.info("Archive configuration: {}", url);
            }
        } catch (Exception e) {
            LOGGER.debug("Get template", e);
            URL arcConfigFile = this.getClass().getResource(configDir + name);
            if (arcConfigFile != null) {
                archiveProps.load(arcConfigFile.openStream());
                LOGGER.info("Archive configuration: {}", arcConfigFile);
            } else {
                throw new IOException("Cannot find archive configuration");
            }
        }
        archiveProps.setProperty(ConnectorProperties.CONFIG_FILENAME, name);
        return archiveProps;
    }

    private void initJnlpTemplate(ServletContext sc, String configDir, String jnlpName, String property,
        Hashtable<Object, Object> properties) throws IOException {

        try {
            URL url = readConfigURL(configDir, jnlpName);
            try (ClosableURLConnection ulrConnection = NetworkUtil.getUrlConnection(url, new URLParameters())) {
                try (InputStream in = ulrConnection.getInputStream()) {
                    // check if resource exist like with JarURLConnection
                }
                properties.put(property, url.toString());
                LOGGER.info("Default jnlp template : {}", url);
            }
        } catch (Exception e) {
            URL jnlpTemplate = this.getClass().getResource(configDir + jnlpName);
            if (jnlpTemplate == null) {
                try {
                    jnlpTemplate = sc.getResource("/" + jnlpName);
                } catch (MalformedURLException ex) {
                    LOGGER.error("Error on getting template", ex);
                }
            }

            if (jnlpTemplate != null) {
                properties.put(property, jnlpTemplate.toString());
                LOGGER.info("Default jnlp template : {}", jnlpTemplate);
            } else {
                LOGGER.error("Error on getting JNLP template : {}", e.getLocalizedMessage());
                throw new IOException("Cannot find template configuration");
            }
        }
    }

    // Get map where the oldest entry when the limit size is reached
    public static <K, V> Map<K, V> createLRUMap(final int maxEntries) {
        return new LinkedHashMap<K, V>(maxEntries * 3 / 2, 0.7f, true) {

            private static final long serialVersionUID = 6516827063164041400L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxEntries;
            }
        };
    }

}