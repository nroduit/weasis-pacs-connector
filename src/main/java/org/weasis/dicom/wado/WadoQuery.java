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
package org.weasis.dicom.wado;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.data.Patient;
import org.weasis.dicom.data.xml.TagUtil;
import org.weasis.dicom.util.FileUtil;
import org.weasis.dicom.util.StringUtil;

public class WadoQuery {

    private static final Logger LOGGER = LoggerFactory.getLogger(WadoQuery.class);

    public static final String FILE_PREFIX = "wado_query";
    public static final String FILE_EXTENSION = ".xml.gz";

    public static final String TAG_DOCUMENT_MSG = "Message";
    public static final String TAG_MSG_ATTRIBUTE_TITLE = "title";
    public static final String TAG_MSG_ATTRIBUTE_DESC = "description";
    public static final String TAG_MSG_ATTRIBUTE_LEVEL = "severity";

    private final StringBuilder wadoQuery = new StringBuilder();
    private final List<Patient> patientList;
    private final String charsetEncoding;
    private WadoMessage wadoMessage;

    /**
     * Creates a wado query with the given patients list.
     * 
     * @param patients
     *            a list of patients
     * @param wadoParameters
     *            the wado parameters
     * @param charsetEncoding
     *            the encoding of the response
     * @param acceptNoImage
     *            if true the manifest will be created with no data
     * @throws WadoQueryException
     */
    public WadoQuery(List<Patient> patients, WadoParameters wadoParameters, String charsetEncoding,
        boolean acceptNoImage) throws WadoQueryException {
        if ((patients == null || patients.size() == 0) && !acceptNoImage) {
            throw new WadoQueryException(WadoQueryException.NO_PATIENTS_LIST);
        } else if (wadoParameters == null || !StringUtil.hasText(charsetEncoding)) {
                throw new IllegalArgumentException();
        } else {
            this.patientList = patients;
            this.charsetEncoding = charsetEncoding;
            wadoQuery.append("<?xml version=\"1.0\" encoding=\"" + charsetEncoding + "\" ?>");
            wadoQuery.append("\n<");
            wadoQuery.append(WadoParameters.TAG_DOCUMENT_ROOT);
            wadoQuery.append(WadoParameters.TAG_SCHEMA);
            wadoQuery.append(" ");
            TagUtil.addXmlAttribute(WadoParameters.TAG_WADO_URL, wadoParameters.getWadoURL(), wadoQuery);
            TagUtil.addXmlAttribute(WadoParameters.TAG_WADO_WEB_LOGIN, wadoParameters.getWebLogin(), wadoQuery);
            TagUtil.addXmlAttribute(WadoParameters.TAG_WADO_ONLY_SOP_UID, wadoParameters.isRequireOnlySOPInstanceUID(),
                wadoQuery);
            TagUtil.addXmlAttribute(WadoParameters.TAG_WADO_ADDITIONNAL_PARAMETERS,
                wadoParameters.getAdditionnalParameters(), wadoQuery);
            TagUtil.addXmlAttribute(WadoParameters.TAG_WADO_OVERRIDE_TAGS, wadoParameters.getOverrideDicomTagsList(),
                wadoQuery);
            wadoQuery.append(">");
            if (wadoParameters.getHttpTaglist() != null) {
                for (WadoParameters.HttpTag tag : wadoParameters.getHttpTaglist()) {
                    wadoQuery.append("\n<");
                    wadoQuery.append(WadoParameters.TAG_HTTP_TAG);
                    wadoQuery.append(" key=\"");
                    wadoQuery.append(tag.getKey());
                    wadoQuery.append("\" value=\"");
                    wadoQuery.append(tag.getValue());
                    wadoQuery.append("\" />");
                }
            }
        }
    }

    /**
     * Returns current wado query in a string
     * 
     * @return current wado query in a string
     */
    @Override
    public String toString() {
        if (wadoMessage != null) {
            wadoQuery.append("\n<");
            wadoQuery.append(TAG_DOCUMENT_MSG);
            wadoQuery.append(" ");
            TagUtil.addXmlAttribute(TAG_MSG_ATTRIBUTE_TITLE, wadoMessage.title, wadoQuery);
            TagUtil.addXmlAttribute(TAG_MSG_ATTRIBUTE_DESC, wadoMessage.message, wadoQuery);
            TagUtil.addXmlAttribute(TAG_MSG_ATTRIBUTE_LEVEL, wadoMessage.level.name(), wadoQuery);
            wadoQuery.append("/>");
        }

        if (patientList != null) {
            Collections.sort(patientList, new Comparator<Patient>() {

                @Override
                public int compare(Patient o1, Patient o2) {
                    return o1.getPatientName().compareTo(o2.getPatientName());
                }
            });

            for (Patient patient : patientList) {
                wadoQuery.append(patient.toXml());
            }
        }

        wadoQuery.append("\n</");
        wadoQuery.append(WadoParameters.TAG_DOCUMENT_ROOT);
        wadoQuery.append(">");
        return wadoQuery.toString();
    }

    public String getCharsetEncoding() {
        return charsetEncoding;
    }

    public WadoMessage getWadoMessage() {
        return wadoMessage;
    }

    public void setWadoMessage(WadoMessage wadoMessage) {
        this.wadoMessage = wadoMessage;
    }

    /**
     * Save current Wado Query to a temporary file and returns the name of the created file.
     * 
     * @param path
     *            path of the temporary file to create
     * @return the name of the created temporary file
     * @throws WadoQueryException
     *             if an error occurs
     */
    public String saveToTmpFile(String path) throws WadoQueryException {
        File tmpFile = null;

        try {
            File folderTemp = new File(path);
            if (!folderTemp.exists()) {
                if (!folderTemp.mkdirs()) {
                    LOGGER.error("Cannot make folder : " + folderTemp);
                    throw new WadoQueryException(WadoQueryException.CANNOT_CREATE_TEMP_FILE);
                }
            }
            tmpFile = File.createTempFile(FILE_PREFIX, FILE_EXTENSION, folderTemp);
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
            throw new WadoQueryException(WadoQueryException.CANNOT_CREATE_TEMP_FILE);
        }

        gzipCompress(new ByteArrayInputStream(toString().getBytes()), tmpFile);
        LOGGER.info("Wado Query saved to temporary file: {}" + tmpFile);
        return tmpFile.getName();
    }

    public static boolean gzipCompress(InputStream in, File gzipFilename) throws WadoQueryException {
        GZIPOutputStream gzipOut = null;
        try {
            gzipOut = new GZIPOutputStream(new FileOutputStream(gzipFilename));
            byte[] buf = new byte[1024];
            int offset;
            while ((offset = in.read(buf)) > 0) {
                gzipOut.write(buf, 0, offset);
            }
            // Finishes writing compressed data
            gzipOut.finish();
            return true;
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
            throw new WadoQueryException(WadoQueryException.CANNOT_WRITE_TO_TEMP_FILE);
        } finally {
            FileUtil.safeClose(in);
            FileUtil.safeClose(gzipOut);
        }
    }

    public static boolean gzipCompress(InputStream in, OutputStream out) throws WadoQueryException {
        GZIPOutputStream gzipOut = null;
        try {
            gzipOut = new GZIPOutputStream(out);
            byte[] buf = new byte[1024];
            int offset;
            while ((offset = in.read(buf)) > 0) {
                gzipOut.write(buf, 0, offset);
            }
            // Finishes writing compressed data
            gzipOut.finish();
            return true;
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
            throw new WadoQueryException(WadoQueryException.CANNOT_WRITE_TO_TEMP_FILE);
        } finally {
            FileUtil.safeClose(in);
            FileUtil.safeClose(gzipOut);
        }
    }

    public static class WadoMessage {
        public enum eLevel {
            INFO, WARN, ERROR;
        }

        private final String message;
        private final String title;
        private final eLevel level;

        public WadoMessage(String title, String message, eLevel level) {
            this.title = title;
            this.message = message;
            this.level = level;
        }

        public String getMessage() {
            return message;
        }

        public String getTitle() {
            return title;
        }

        public eLevel getLevel() {
            return level;
        }
    }
}
