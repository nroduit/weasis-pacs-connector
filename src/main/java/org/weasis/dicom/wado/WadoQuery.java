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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.weasis.dicom.data.Patient;
import org.weasis.dicom.data.xml.TagUtil;
import org.weasis.query.AbstractQueryConfiguration;

public class WadoQuery implements XmlManifest {

    public static final String FILE_PREFIX = "wado_query";
    public static final String FILE_EXTENSION = ".xml.gz";

    public static final String TAG_DOCUMENT_MSG = "Message";
    public static final String TAG_MSG_ATTRIBUTE_TITLE = "title";
    public static final String TAG_MSG_ATTRIBUTE_DESC = "description";
    public static final String TAG_MSG_ATTRIBUTE_LEVEL = "severity";

    private final StringBuilder wadoQuery = new StringBuilder();
    private final List<AbstractQueryConfiguration> archiveList;

    public WadoQuery(List<AbstractQueryConfiguration> list) {
        if (list == null) {
            throw new IllegalArgumentException();
        }
        this.archiveList = list;
    }

    @Override
    public String getCharsetEncoding() {
        return "UTF-8";
    }

    @Override
    public String xmlManifest() {
        wadoQuery.append("<?xml version=\"1.0\" encoding=\"" + getCharsetEncoding() + "\" ?>");
        wadoQuery.append("\n<");
        wadoQuery.append(WadoParameters.TAG_DOCUMENT_ROOT);
        wadoQuery.append(" ");
        wadoQuery.append(WadoParameters.TAG_SCHEMA);
        wadoQuery.append(">");
        
        for (AbstractQueryConfiguration archive : archiveList) {
            if (archive.getPatients().isEmpty() && archive.getWadoMessages().isEmpty()) {
                continue;
            }
            WadoParameters wadoParameters = archive.getWadoParameters();
            wadoQuery.append("\n<");
            wadoQuery.append(WadoParameters.TAG_WADO_QUERY);
            wadoQuery.append(" ");

            TagUtil.addXmlAttribute(WadoParameters.TAG_WADO_ARCHIVE_ID, wadoParameters.getArchiveID(), wadoQuery);
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

            for (WadoMessage wadoMessage : archive.getWadoMessages()) {
                wadoQuery.append("\n<");
                wadoQuery.append(TAG_DOCUMENT_MSG);
                wadoQuery.append(" ");
                TagUtil.addXmlAttribute(TAG_MSG_ATTRIBUTE_TITLE, wadoMessage.title, wadoQuery);
                TagUtil.addXmlAttribute(TAG_MSG_ATTRIBUTE_DESC, wadoMessage.message, wadoQuery);
                TagUtil.addXmlAttribute(TAG_MSG_ATTRIBUTE_LEVEL, wadoMessage.level.name(), wadoQuery);
                wadoQuery.append("/>");
            }

            List<Patient> patientList = archive.getPatients();
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
            wadoQuery.append(WadoParameters.TAG_WADO_QUERY);
            wadoQuery.append(">");
        }
        
        wadoQuery.append("\n</");
        wadoQuery.append(WadoParameters.TAG_DOCUMENT_ROOT);
        wadoQuery.append(">");

        return wadoQuery.toString();
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
