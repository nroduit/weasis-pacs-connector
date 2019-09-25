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

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.LangUtil;
import org.weasis.dicom.mf.ArcQuery;
import org.weasis.dicom.mf.QueryResult;
import org.weasis.dicom.mf.ViewerMessage;
import org.weasis.dicom.mf.XmlManifest;
import org.weasis.query.CommonQueryParams;
import org.weasis.servlet.ServletUtil;

public class ManifestBuilder implements Callable<XmlManifest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManifestBuilder.class);

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final int requestId;
    private final long startTimeMillis;
    private final CommonQueryParams params;
    private final XmlManifest xml;
    private Future<XmlManifest> future;

    public ManifestBuilder(CommonQueryParams params) {
        if (params == null) {
            throw new IllegalArgumentException();
        }
        this.params = params;
        this.xml = null;
        this.requestId = COUNTER.incrementAndGet();
        this.startTimeMillis = System.currentTimeMillis();
    }

    public ManifestBuilder(XmlManifest xml) {
        if (xml == null) {
            throw new IllegalArgumentException();
        }
        this.params = null;
        this.xml = xml;
        this.requestId = COUNTER.incrementAndGet();
        this.startTimeMillis = System.currentTimeMillis();
    }

    public final long getStartTimeMillis() {
        return startTimeMillis;
    }

    public final int getRequestId() {
        return requestId;
    }

    public void submit(ExecutorService executor) {
        future = executor.submit(this);
    }

    public Future<XmlManifest> getFuture() {
        return future;
    }

    @Override
    public XmlManifest call() throws Exception {
        if (xml == null) {
            long startTime = System.currentTimeMillis();

            ServletUtil.fillPatientList(params);

            if (!params.hasPatients()) {
                LOGGER.warn("Empty patient list");
                if (!params.hasGeneralViewerMessage() && !params.isAcceptNoImage()) {
                    params.addGeneralViewerMessage(new ViewerMessage("Empty Patient List",
                        "No images have been found with given parameters ", ViewerMessage.eLevel.WARN));
                }
            }

            ArcQuery wadoQuery = new ArcQuery(LangUtil.convertCollectionType(params.getArchiveList(),
                new ArrayList<QueryResult>(), QueryResult.class));

            LOGGER.info("Build Manifest in {} ms [id={}]", System.currentTimeMillis() - startTime, requestId);
            return wadoQuery;
        } else {
            return xml;
        }
    }

}
