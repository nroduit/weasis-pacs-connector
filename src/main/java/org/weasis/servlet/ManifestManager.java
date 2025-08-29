/*
 * Copyright (c) 2014-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.servlet;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.mf.thread.ManifestBuilder;
import org.weasis.dicom.mf.thread.ManifestManagerThread;

/**
 * @author Nicolas Roduit
 */
@WebListener
public class ManifestManager implements ServletContextListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(ManifestManager.class);

  private final ConcurrentHashMap<Integer, ManifestBuilder> manifestBuilderMap =
      new ConcurrentHashMap<>();
  private final ManifestManagerThread manifestManagerThread =
      new ManifestManagerThread(manifestBuilderMap);

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

        } else {
          LOGGER.error("Cannot find  a configuration file for weasis-pacs-connector");
        }

      } catch (Exception e) {
        LOGGER.error("Error on initialization of ManifestManager", e);
      }
      sc.setAttribute("componentProperties", properties);

      manifestManagerThread.setCleanFrequency(
          ServletUtil.getLongProperty(
              properties, "thread.clean.frequency", ManifestManagerThread.CLEAN_FREQUENCY));
      manifestManagerThread.setMaxLifeCycle(
          ServletUtil.getLongProperty(
              properties, "thread.max.life.clyle", ManifestManagerThread.MAX_LIFE_CYCLE));
      LOGGER.info(
          "ManifestManagerThread configuration (maxLifeCycle={}s, cleanFrequency={}s)",
          TimeUnit.MILLISECONDS.toSeconds(manifestManagerThread.getMaxLifeCycle()),
          TimeUnit.MILLISECONDS.toSeconds(manifestManagerThread.getCleanFrequency()));

      sc.setAttribute(
          "manifestExecutor",
          Executors.newFixedThreadPool(
              ServletUtil.getIntProperty(properties, "thread.manifest.concurrency", 5)));
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

  private Properties readArchiveURL(String baseConfigDir, String configDir, String name)
      throws IOException {
    Properties archiveProps = new Properties();
    try {
      URL url = new URL(baseConfigDir + name);
      try (InputStream stream = url.openStream()) {
        archiveProps.load(stream);
        LOGGER.info("Archive configuration: {}", url);
      }
    } catch (Exception e) {
      LOGGER.debug("Get template", e);
      URL arcConfigFile = this.getClass().getResource(configDir + name);
      if (arcConfigFile != null) {
        try (InputStream stream = arcConfigFile.openStream()) {
          archiveProps.load(stream);
          LOGGER.info("Archive configuration: {}", arcConfigFile);
        }
      } else {
        throw new IOException("Cannot find archive configuration");
      }
    }
    archiveProps.setProperty(ConnectorProperties.CONFIG_FILENAME, name);
    return archiveProps;
  }
}
