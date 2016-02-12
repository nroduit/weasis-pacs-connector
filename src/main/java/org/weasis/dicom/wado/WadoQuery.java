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

    private final List<AbstractQueryConfiguration> archiveList;
    private final StringBuilder manifest;

    public WadoQuery(List<AbstractQueryConfiguration> list) {
        if (list == null) {
            throw new IllegalArgumentException();
        }
        this.archiveList = list;
        this.manifest = new StringBuilder();
    }

    @Override
    public String getCharsetEncoding() {
        return "UTF-8";
    }

    @Override
    public String xmlManifest() {
        manifest.append("<?xml version=\"1.0\" encoding=\"" + getCharsetEncoding() + "\" ?>");
        manifest.append("\n<");
        manifest.append(WadoParameters.TAG_DOCUMENT_ROOT);
        manifest.append(" ");
        manifest.append(WadoParameters.TAG_SCHEMA);
        manifest.append(">");

        for (AbstractQueryConfiguration archive : archiveList) {
            if (archive.getPatients().isEmpty() && archive.getWadoMessages().isEmpty()) {
                continue;
            }
            WadoParameters wadoParameters = archive.getWadoParameters();
            manifest.append("\n<");
            manifest.append(WadoParameters.TAG_WADO_QUERY);
            manifest.append(" ");

            TagUtil.addXmlAttribute(WadoParameters.TAG_WADO_ARCHIVE_ID, wadoParameters.getArchiveID(), manifest);
            TagUtil.addXmlAttribute(WadoParameters.TAG_WADO_URL, wadoParameters.getWadoURL(), manifest);
            TagUtil.addXmlAttribute(WadoParameters.TAG_WADO_WEB_LOGIN, wadoParameters.getWebLogin(), manifest);
            TagUtil.addXmlAttribute(WadoParameters.TAG_WADO_ONLY_SOP_UID, wadoParameters.isRequireOnlySOPInstanceUID(),
                manifest);
            TagUtil.addXmlAttribute(WadoParameters.TAG_WADO_ADDITIONNAL_PARAMETERS,
                wadoParameters.getAdditionnalParameters(), manifest);
            TagUtil.addXmlAttribute(WadoParameters.TAG_WADO_OVERRIDE_TAGS, wadoParameters.getOverrideDicomTagsList(),
                manifest);
            manifest.append(">");
            if (wadoParameters.getHttpTaglist() != null) {
                for (WadoParameters.HttpTag tag : wadoParameters.getHttpTaglist()) {
                    manifest.append("\n<");
                    manifest.append(WadoParameters.TAG_HTTP_TAG);
                    manifest.append(" key=\"");
                    manifest.append(tag.getKey());
                    manifest.append("\" value=\"");
                    manifest.append(tag.getValue());
                    manifest.append("\" />");
                }
            }

            for (WadoMessage wadoMessage : archive.getWadoMessages()) {
                manifest.append("\n<");
                manifest.append(TAG_DOCUMENT_MSG);
                manifest.append(" ");
                TagUtil.addXmlAttribute(TAG_MSG_ATTRIBUTE_TITLE, wadoMessage.title, manifest);
                TagUtil.addXmlAttribute(TAG_MSG_ATTRIBUTE_DESC, wadoMessage.message, manifest);
                TagUtil.addXmlAttribute(TAG_MSG_ATTRIBUTE_LEVEL, wadoMessage.level.name(), manifest);
                manifest.append("/>");
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
                    manifest.append(patient.toXml());
                }
            }

            manifest.append("\n</");
            manifest.append(WadoParameters.TAG_WADO_QUERY);
            manifest.append(">");
        }

        manifest.append("\n</");
        manifest.append(WadoParameters.TAG_DOCUMENT_ROOT);
        manifest.append(">");

        return manifest.toString();
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
