package org.weasis.dicom.wado.thread;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.wado.DicomQueryParams;
import org.weasis.dicom.wado.WadoQuery;
import org.weasis.dicom.wado.WadoQuery.WadoMessage;
import org.weasis.servlet.ServletUtil;

public class ManifestBuilder implements Callable<WadoQuery> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManifestBuilder.class);

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final int requestId;
    private final long startTimeMillis;
    private final DicomQueryParams params;
    private volatile Future<WadoQuery> future;

    public ManifestBuilder(DicomQueryParams params) {
        this.params = params;
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

    public Future<WadoQuery> getFuture() {
        return future;
    }

    @Override
    public WadoQuery call() throws Exception {
        long startTime = System.currentTimeMillis();

        WadoMessage message = ServletUtil.getPatientList(params);

        WadoQuery wadoQuery =
            new WadoQuery(params.getPatients(), params.getWadoParameters(), params.getCharsetEncoding(),
                params.isAcceptNoImage());
        wadoQuery.setWadoMessage(message);

        LOGGER.info("Build Manifest in {} ms [id={}]", (System.currentTimeMillis() - startTime), requestId);
        return wadoQuery;
    }

}
