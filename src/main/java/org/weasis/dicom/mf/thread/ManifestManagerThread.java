/*
 * Copyright (c) 2014-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.mf.thread;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.mf.XmlManifest;

/**
 * @author Nicolas Roduit
 */
public class ManifestManagerThread extends Thread {

  private static final Logger LOGGER = LoggerFactory.getLogger(ManifestManagerThread.class);

  public static final long MAX_LIFE_CYCLE = 300000L; // in milliseconds => 5 min
  public static final long CLEAN_FREQUENCY = 60000L; // in milliseconds => 1 min

  private final ConcurrentMap<Integer, ManifestBuilder> manifestBuilderMap;

  private volatile long maxLifeCycle = MAX_LIFE_CYCLE;
  private volatile long cleanFrequency = CLEAN_FREQUENCY;
  private volatile boolean running = true;

  /**
   * The role of the ManifestManagerThread class is to clean the non consumed threads.
   *
   * @param manifestBuilderMap the thread safe hashMap
   */
  public ManifestManagerThread(ConcurrentMap<Integer, ManifestBuilder> manifestBuilderMap) {
    super("ManifestManagerThread");
    setDaemon(true);
    if (manifestBuilderMap == null) {
      throw new IllegalArgumentException("manifestBuilderMap cannot be null");
    }
    this.manifestBuilderMap = manifestBuilderMap;
    this.maxLifeCycle = MAX_LIFE_CYCLE;
    this.cleanFrequency = CLEAN_FREQUENCY;
  }

  public long getMaxLifeCycle() {
    return maxLifeCycle;
  }

  public void setMaxLifeCycle(long maxLifeCycle) {
    if (maxLifeCycle <= 0) {
      throw new IllegalArgumentException("maxLifeCycle must be positive");
    }
    this.maxLifeCycle = maxLifeCycle;
  }

  public long getCleanFrequency() {
    return cleanFrequency;
  }

  public void setCleanFrequency(long cleanFrequency) {
    if (cleanFrequency <= 0) {
      throw new IllegalArgumentException("cleanFrequency must be positive");
    }
    this.cleanFrequency = cleanFrequency;
  }

  public void shutdown() {
    running = false;
    interrupt();
  }

  @Override
  public void run() {
    while (running && !isInterrupted()) {
      cleanExpiredManifests();
      try {
        Thread.sleep(cleanFrequency);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOGGER.debug("ManifestManagerThread interrupted", e);
        break;
      }
    }
    LOGGER.info("ManifestManagerThread stopped");
  }

  private void cleanExpiredManifests() {
    long currentTime = System.currentTimeMillis();
    for (Entry<Integer, ManifestBuilder> entry : manifestBuilderMap.entrySet()) {
      Integer key = entry.getKey();
      ManifestBuilder manifestBuilder = entry.getValue();

      long diff = currentTime - manifestBuilder.getStartTimeMillis();

      if (diff > maxLifeCycle) {
        Future<XmlManifest> future = manifestBuilder.getFuture();
        if (future != null && !future.isDone()) {
          future.cancel(true);
          LOGGER.warn(
              "Cancelled running ManifestBuilder with key={} after {} sec",
              key,
              TimeUnit.MILLISECONDS.toSeconds(diff));
        }

        manifestBuilderMap.remove(key);
        LOGGER.info(
            "Removed ManifestBuilder with key={}, not consumed after {} sec",
            key,
            TimeUnit.MILLISECONDS.toSeconds(diff));
      }
    }
  }
}
