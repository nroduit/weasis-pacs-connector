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
import org.weasis.dicom.wado.WadoParameters.HttpTag;
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
    public String xmlManifest(String version) {
        manifest.append("<?xml version=\"1.0\" encoding=\"" + getCharsetEncoding() + "\" ?>");
        
        if(version != null && "1".equals(version.trim())){
            return xmlManifest1();
        }
        
        manifest.append("\n<");
        manifest.append(WadoParameters.TAG_DOCUMENT_ROOT);
        manifest.append(" ");
        manifest.append(WadoParameters.TAG_SCHEMA);
        manifest.append(">");

        for (AbstractQueryConfiguration archive : archiveList) {
            if (archive.getPatients().isEmpty() && archive.getViewerMessage() == null) {
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

            buildHttpTags(wadoParameters.getHttpTaglist());
            buildViewerMessage(archive.getViewerMessage());
            buildPatient(archive);

            manifest.append("\n</");
            manifest.append(WadoParameters.TAG_WADO_QUERY);
            manifest.append(">");
        }

        manifest.append("\n</");
        manifest.append(WadoParameters.TAG_DOCUMENT_ROOT);
        manifest.append(">\n"); // Requires end of line

        return manifest.toString();
    }
    
    public String xmlManifest1() {
        for (AbstractQueryConfiguration archive : archiveList) {
            if (archive.getPatients().isEmpty() && archive.getViewerMessage() == null) {
                continue;
            }
            WadoParameters wadoParameters = archive.getWadoParameters();
            manifest.append("\n<");
            manifest.append(WadoParameters.TAG_WADO_QUERY);
            manifest.append(" xmlns=\"http://www.weasis.org/xsd\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ");

            TagUtil.addXmlAttribute(WadoParameters.TAG_WADO_URL, wadoParameters.getWadoURL(), manifest);
            TagUtil.addXmlAttribute(WadoParameters.TAG_WADO_WEB_LOGIN, wadoParameters.getWebLogin(), manifest);
            TagUtil.addXmlAttribute(WadoParameters.TAG_WADO_ONLY_SOP_UID, wadoParameters.isRequireOnlySOPInstanceUID(),
                manifest);
            TagUtil.addXmlAttribute(WadoParameters.TAG_WADO_ADDITIONNAL_PARAMETERS,
                wadoParameters.getAdditionnalParameters(), manifest);
            TagUtil.addXmlAttribute(WadoParameters.TAG_WADO_OVERRIDE_TAGS, wadoParameters.getOverrideDicomTagsList(),
                manifest);
            manifest.append(">");

            buildHttpTags(wadoParameters.getHttpTaglist());
            buildViewerMessage(archive.getViewerMessage());
            buildPatient(archive);

            manifest.append("\n</");
            manifest.append(WadoParameters.TAG_WADO_QUERY);
            manifest.append(">\n"); // Requires end of line
            
            break; // accept only one element
        }     
        return manifest.toString();
    }
    
    private void buildPatient(AbstractQueryConfiguration archive){
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
    }
    
    private void buildHttpTags(List<HttpTag> list){
        if (list != null) {
            for (WadoParameters.HttpTag tag : list) {
                manifest.append("\n<");
                manifest.append(WadoParameters.TAG_HTTP_TAG);
                manifest.append(" key=\"");
                manifest.append(tag.getKey());
                manifest.append("\" value=\"");
                manifest.append(tag.getValue());
                manifest.append("\" />");
            }
        }
    }
    
    private void buildViewerMessage(ViewerMessage message){
        if (message != null) {
            manifest.append("\n<");
            manifest.append(TAG_DOCUMENT_MSG);
            manifest.append(" ");
            TagUtil.addXmlAttribute(TAG_MSG_ATTRIBUTE_TITLE, message.title, manifest);
            TagUtil.addXmlAttribute(TAG_MSG_ATTRIBUTE_DESC, message.message, manifest);
            TagUtil.addXmlAttribute(TAG_MSG_ATTRIBUTE_LEVEL, message.level.name(), manifest);
            manifest.append("/>");
        }
    }

    public static class ViewerMessage {
        public enum eLevel {
            INFO, WARN, ERROR;
        }

        private final String message;
        private final String title;
        private final eLevel level;

        public ViewerMessage(String title, String message, eLevel level) {
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
