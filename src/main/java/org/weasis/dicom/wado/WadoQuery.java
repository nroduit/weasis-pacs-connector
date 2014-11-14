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

public class WadoQuery {

    private static final Logger logger = LoggerFactory.getLogger(WadoQuery.class);

    public static final String FILE_PREFIX = "wado_query";
    public static final String FILE_EXTENSION = ".xml.gz";

    private StringBuffer wadoQuery;

    /**
     * Creates a wado query with the given patients list.
     * 
     * @param patients
     *            a list of patients
     * @param acceptNoImage
     * @throws WadoQueryException
     *             if an error occurs
     */
    public WadoQuery(List<Patient> patients, WadoParameters wadoParameters, String dbCharset, boolean acceptNoImage)
        throws WadoQueryException {
        if ((patients == null || patients.size() == 0) && !acceptNoImage) {
            throw new WadoQueryException(WadoQueryException.NO_PATIENTS_LIST);
        } else {
            if (patients != null) {
                Collections.sort(patients, new Comparator<Patient>() {

                    @Override
                    public int compare(Patient o1, Patient o2) {
                        return o1.getPatientName().compareTo(o2.getPatientName());
                    }
                });
            }
            wadoQuery = new StringBuffer();
            wadoQuery.append("<?xml version=\"1.0\" encoding=\"" + dbCharset + "\" ?>");
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
            logger.debug("Xml header [{}]", wadoQuery.toString());

            if (patients != null) {
                for (int i = 0; i < patients.size(); i++) {
                    wadoQuery.append(patients.get(i).toXml());
                }
            }

            wadoQuery.append("\n</");
            wadoQuery.append(WadoParameters.TAG_DOCUMENT_ROOT);
            wadoQuery.append(">");
        }
    }

    /**
     * Returns current wado query in a string
     * 
     * @return current wado query in a string
     */
    @Override
    public String toString() {
        return wadoQuery.toString();
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
                    logger.error("Cannot make folder : " + folderTemp);
                    throw new WadoQueryException(WadoQueryException.CANNOT_CREATE_TEMP_FILE);
                }
            }
            tmpFile = File.createTempFile(FILE_PREFIX, FILE_EXTENSION, folderTemp);
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new WadoQueryException(WadoQueryException.CANNOT_CREATE_TEMP_FILE);
        }

        gzipCompress(new ByteArrayInputStream(wadoQuery.toString().getBytes()), tmpFile);
        logger.info("Wado Query saved to temporary file: {}" + tmpFile);
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
            logger.error(e.getMessage());
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
            logger.error(e.getMessage());
            throw new WadoQueryException(WadoQueryException.CANNOT_WRITE_TO_TEMP_FILE);
        } finally {
            FileUtil.safeClose(in);
            FileUtil.safeClose(gzipOut);
        }
    }
}
