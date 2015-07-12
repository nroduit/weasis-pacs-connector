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

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServlet;

import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.util.StringUtil;
import org.weasis.dicom.wado.thread.ManifestBuilder;
import org.weasis.dicom.wado.thread.ManifestManagerThread;

public class ManifestManager extends HttpServlet {

    private static final long serialVersionUID = -3980526826815714220L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ManifestManager.class);

    private final Properties properties = new Properties();
    private final ConcurrentHashMap<Integer, ManifestBuilder> manifestBuilderMap =
        new ConcurrentHashMap<Integer, ManifestBuilder>();

    private final ManifestManagerThread manifestManagerThread = new ManifestManagerThread(manifestBuilderMap);
    private final Map<URL, Element> jnlpTemplates = ManifestManager.<URL, Element> createLRUMap(20);

    @Override
    public void init() {
        LOGGER.info("Start the manifest manager servlet");
        if (this.getServletContext().getAttribute("manifestBuilderMap") != null) {
            LOGGER.error(
                "A manifest manager thread is already running in the servlet context! The new one won't be started.");
        } else {
            LOGGER.debug("init() - getServletContext: {} ", getServletConfig().getServletContext());
            LOGGER.debug("init() - getRealPath: {}", getServletConfig().getServletContext().getRealPath("/"));

            try {
                URL config = this.getClass().getResource("/weasis-pacs-connector.properties");
                if (config == null) {
                    config = this.getClass().getResource("/weasis-connector-default.properties");
                    LOGGER.info("Default configuration file: {}", config);
                } else {
                    LOGGER.info("External weasis-pacs-connector configuration file: {}", config);
                }

                if (config != null) {
                    properties.load(config.openStream());
                    String requests = properties.getProperty("request.ids", null);
                    String requestIID = properties.getProperty("request.IID.level", null);
                    if (requests == null) {
                        LOGGER.error(
                            "No request ID is allowed for the web context /viewer, /viewer-applet and /manifest!");
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

                } else {
                    LOGGER.error("Cannot find  a configuration file for weasis-pacs-connector");
                }

                String jnlpName = properties.getProperty("jnlp.default.name", null);
                if (jnlpName != null) {
                    URL jnlpTemplate = this.getClass().getResource("/" + jnlpName);
                    if (jnlpTemplate != null) {
                        LOGGER.info("External Weasis jnlp template: {}", jnlpTemplate);
                        properties.put("weasis.default.jnlp", jnlpTemplate.toString());
                    }
                }
            } catch (Exception e) {
                StringUtil.logError(LOGGER, e, "Error on initialization");
            }
            this.getServletContext().setAttribute("componentProperties", properties);
            this.getServletContext().setAttribute("jnlpTemplates", jnlpTemplates);

            manifestManagerThread.setCleanFrequency(ServletUtil.getLongProperty(properties, "thread.clean.frequency",
                ManifestManagerThread.CLEAN_FREQUENCY));
            manifestManagerThread.setMaxLifeCycle(
                ServletUtil.getLongProperty(properties, "thread.max.life.clyle", ManifestManagerThread.MAX_LIFE_CYCLE));
            LOGGER.info("ManifestManagerThread configuration (maxLifeCycle="
                + TimeUnit.MILLISECONDS.toSeconds(manifestManagerThread.getMaxLifeCycle()) + "s, cleanFrequency="
                + TimeUnit.MILLISECONDS.toSeconds(manifestManagerThread.getCleanFrequency()) + "s)");

            this.getServletContext().setAttribute("manifestExecutor",
                Executors.newFixedThreadPool(ServletUtil.getIntProperty(properties, "thread.manifest.concurrency", 5)));
            this.getServletContext().setAttribute("manifestBuilderMap", manifestBuilderMap);
            manifestManagerThread.start();
        }
    }

    @Override
    public void destroy() {
        LOGGER.info("Stop the manifest manager servlet");

        manifestManagerThread.interrupt();
    }

    // Get map where the oldest entry when the limit size is reached
    public static <K, V> Map<K, V> createLRUMap(final int maxEntries) {
        return new LinkedHashMap<K, V>(maxEntries * 3 / 2, 0.7f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxEntries;
            }
        };
    }

}