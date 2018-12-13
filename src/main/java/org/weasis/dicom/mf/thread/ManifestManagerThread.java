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
package org.weasis.dicom.mf.thread;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.mf.XmlManifest;

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
     * @param manifestBuilderMap
     *            the thread safe hashMap
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
                    LOGGER.info("Remove ManifestBuilder with key={}, not consumed after {} sec", key,
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
