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

  /**
   * The role of the ManifestManagerThread class is to clean the non consumed threads.
   *
   * @param manifestBuilderMap the thread safe hashMap
   */
  public ManifestManagerThread(ConcurrentMap<Integer, ManifestBuilder> manifestBuilderMap) {
    if (manifestBuilderMap == null) {
      throw new IllegalArgumentException();
    }
    this.manifestBuilderMap = manifestBuilderMap;
    this.maxLifeCycle = MAX_LIFE_CYCLE;
    this.cleanFrequency = CLEAN_FREQUENCY;
  }

  public long getMaxLifeCycle() {
    return maxLifeCycle;
  }

  public void setMaxLifeCycle(long maxLifeCycle) {
    this.maxLifeCycle = maxLifeCycle;
  }

  public long getCleanFrequency() {
    return cleanFrequency;
  }

  public void setCleanFrequency(long cleanFrequency) {
    this.cleanFrequency = cleanFrequency;
  }

  @Override
  public void run() {
    while (isAlive() && !isInterrupted()) {
      for (Entry<Integer, ManifestBuilder> entry : manifestBuilderMap.entrySet()) {
        Integer key = entry.getKey();
        ManifestBuilder manifestBuilder = entry.getValue();

        long diff = System.currentTimeMillis() - manifestBuilder.getStartTimeMillis();

        if (diff > MAX_LIFE_CYCLE) {
          Future<XmlManifest> future = manifestBuilder.getFuture();
          if (future != null && !future.isDone()) {
            // If the builder process is still running after 5 minutes, kill it.
            future.cancel(true);
          }

          manifestBuilderMap.remove(key);
          LOGGER.info(
              "Remove ManifestBuilder with key={}, not consumed after {} sec",
              key,
              TimeUnit.MILLISECONDS.toSeconds(diff));
        }
      }
      try {
        Thread.sleep(CLEAN_FREQUENCY);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOGGER.warn(e.getMessage());
      }
    }
  }
}
