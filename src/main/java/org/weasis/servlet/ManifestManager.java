package org.weasis.servlet;

import java.net.URL;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.util.StringUtil;
import org.weasis.dicom.wado.thread.ManifestBuilder;
import org.weasis.dicom.wado.thread.ManifestManagerThread;

public class ManifestManager extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManifestManager.class);

    private final Properties properties = new Properties();
    private final ConcurrentHashMap<Integer, ManifestBuilder> manifestBuilderMap =
        new ConcurrentHashMap<Integer, ManifestBuilder>();

    private final ManifestManagerThread manifestManagerThread = new ManifestManagerThread(manifestBuilderMap);

    @Override
    public void init() {
        LOGGER.info("Start the manifest manager servlet");
        if (this.getServletContext().getAttribute("manifestBuilderMap") != null) {
            LOGGER
                .error("A manifest manager thread is already running in the servlet context! The new one won't be started.");
        } else {
            LOGGER.debug("init() - getServletContext: {} ", getServletConfig().getServletContext());
            LOGGER.debug("init() - getRealPath: {}", getServletConfig().getServletContext().getRealPath("/"));

            try {
                URL config = this.getClass().getResource("/weasis-pacs-connector.properties");
                if (config == null) {
                    config = this.getClass().getResource("/weasis-connector-default.properties");
                    LOGGER.info("Default configuration file: {}", config);
                } else {
                    LOGGER.info("External configuration file: {}", config);
                }

                if (config != null) {
                    properties.load(config.openStream());
                    String requests = properties.getProperty("request.ids", null);
                    if (requests == null) {
                        LOGGER.error("No request ID is allowed!");
                    } else {
                        for (String id : requests.split(",")) {
                            properties.put(id, "true");
                        }
                    }
                } else {
                    LOGGER.error("Cannot find  a configuration file for weasis-pacs-connector");
                }

                URL jnlpTemplate = this.getClass().getResource("/weasis-jnlp.xml");
                if (jnlpTemplate == null) {
                    jnlpTemplate = this.getClass().getResource("/weasis-jnlp-default.xml");
                    LOGGER.info("Default Weasis template: {}", jnlpTemplate);
                    if (jnlpTemplate == null) {
                        LOGGER.error("Cannot find the default JNLP template");
                    }
                } else {
                    LOGGER.info("External Weasis template: {}", jnlpTemplate);
                }
                properties.put("weasis.jnlp", jnlpTemplate.toString());
            } catch (Exception e) {
                StringUtil.logError(LOGGER, e, "Error on initialization");
            }
            this.getServletContext().setAttribute("componentProperties", properties);

            manifestManagerThread.setCleanFrequency(ServletUtil.getLongProperty(properties, "thread.clean.frequency",
                ManifestManagerThread.CLEAN_FREQUENCY));
            manifestManagerThread.setMaxLifeCycle(ServletUtil.getLongProperty(properties, "thread.max.life.clyle",
                ManifestManagerThread.MAX_LIFE_CYCLE));
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
}