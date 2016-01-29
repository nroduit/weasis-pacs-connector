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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.util.StringUtil;
import org.weasis.dicom.wado.WadoQuery.WadoMessage;
import org.weasis.servlet.ConnectorProperties;

public class DicomQueryParams {
    private static final Logger LOGGER = LoggerFactory.getLogger(DicomQueryParams.class);

    public static final String SeriesUID = "seriesUID";
    public static final String ObjectUID = "objectUID";

    /* IHE Radiology Technical Framework Supplement – Invoke Image Display (IID) */
    // HTTP Request Parameters – Patient-based
    public static final String RequestType = "requestType";
    public static final String PatientID = "patientID";
    public static final String PatientName = "patientName";
    public static final String PatientBirthDate = "patientBirthDate";
    public static final String LowerDateTime = "lowerDateTime";
    public static final String UpperDateTime = "upperDateTime";
    public static final String MostRecentResults = "mostRecentResults";
    public static final String ModalitiesInStudy = "modalitiesInStudy";
    public static final String ViewerType = "viewerType";
    public static final String DiagnosticQuality = "diagnosticQuality";
    public static final String KeyImagesOnly = "keyImagesOnly";
    // Additional patient-based parameters
    public static final String keywords = "containsInDescription";

    // HTTP Request Parameters – Study-based
    public static final String StudyUID = "studyUID";
    public static final String AccessionNumber = "accessionNumber";
    // Well-Known Values for Viewer Type Parameter
    public static final String IHE_BIR = "IHE_BIR";
    public static final String PatientLevel = "PATIENT";
    public static final String StudyLevel = "STUDY";

    private final ConnectorProperties properties;
    private final DicomNode callingNode;
    private final List<PacsConfiguration> pacsList;
    private final Map<String, String[]> requestMap;

    public DicomQueryParams(HttpServletRequest request, ConnectorProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("callingNode or calledNode cannot be null!");
        }
        this.properties = properties;
        this.pacsList = new ArrayList<PacsConfiguration>();
        this.pacsList.add(new PacsConfiguration(properties));
        for (Properties p : properties.getPacsPropertiesList()) {
            this.pacsList.add(new PacsConfiguration(p));
        }
        this.callingNode = new DicomNode(properties.getProperty("aet", "PACS-CONNECTOR"));
        this.requestMap = new HashMap<String, String[]>(request.getParameterMap());
    }

    public DicomNode getCallingNode() {
        return callingNode;
    }

    public boolean isAcceptNoImage() {
        return StringUtil.getNULLtoFalse(properties.getProperty("accept.noimage"));
    }

    public ConnectorProperties getProperties() {
        return properties;
    }

    public List<PacsConfiguration> getPacsList() {
        return pacsList;
    }

    public void addGeneralWadoMessage(WadoMessage wadoMessage) {
        if (!pacsList.isEmpty()) {
            pacsList.get(0).getWadoMessages().add(wadoMessage);
        }
    }

    public boolean hasPatients() {
        for (PacsConfiguration pacsConfiguration : pacsList) {
            if (!pacsConfiguration.getPatients().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public void clearAllPatients() {
        for (PacsConfiguration pacsConfiguration : pacsList) {
            pacsConfiguration.getPatients().clear();
        }
    }

    public void removePatientId(List<String> patientIdList) {
        for (PacsConfiguration pacsConfiguration : pacsList) {
            pacsConfiguration.removePatientId(patientIdList);
        }
    }

    public void removeStudyUid(List<String> studyUidList) {
        for (PacsConfiguration pacsConfiguration : pacsList) {
            pacsConfiguration.removeStudyUid(studyUidList);
        }
    }

    public void removeAccessionNumber(List<String> accessionNumberList) {
        for (PacsConfiguration pacsConfiguration : pacsList) {
            pacsConfiguration.removeAccessionNumber(accessionNumberList);
        }
    }

    public void removeSeriesUid(List<String> seriesUidList) {
        for (PacsConfiguration pacsConfiguration : pacsList) {
            pacsConfiguration.removeSeriesUid(seriesUidList);
        }
    }

    public String getRequestType() {
        return getFirstParam(requestMap.get(RequestType));
    }

    public String getReqPatientID() {
        return getFirstParam(requestMap.get(PatientID));
    }

    public String getPatientName() {
        return getFirstParam(requestMap.get(PatientName));
    }

    public String getPatientBirthDate() {
        return getFirstParam(requestMap.get(PatientBirthDate));
    }

    public String getLowerDateTime() {
        return getFirstParam(requestMap.get(LowerDateTime));
    }

    public String getUpperDateTime() {
        return getFirstParam(requestMap.get(UpperDateTime));
    }

    public String getMostRecentResults() {
        return getFirstParam(requestMap.get(MostRecentResults));
    }

    public String getKeywords() {
        return getFirstParam(requestMap.get(keywords));
    }

    public String getModalitiesInStudy() {
        return getFirstParam(requestMap.get(ModalitiesInStudy));
    }

    public String getReqStudyUID() {
        return getFirstParam(requestMap.get(StudyUID));
    }

    public String getReqAccessionNumber() {
        return getFirstParam(requestMap.get(AccessionNumber));
    }

    public String getReqSeriesUID() {
        return getFirstParam(requestMap.get(SeriesUID));
    }

    public String getReqObjectUID() {
        return getFirstParam(requestMap.get(ObjectUID));
    }

    public String[] getReqPatientIDs() {
        return requestMap.get(PatientID);
    }

    public String[] getReqStudyUIDs() {
        return requestMap.get(StudyUID);
    }

    public String[] getReqAccessionNumbers() {
        return requestMap.get(AccessionNumber);
    }

    public String[] getReqSeriesUIDs() {
        return requestMap.get(SeriesUID);
    }

    public String[] getReqObjectUIDs() {
        return requestMap.get(ObjectUID);
    }

    private static String getFirstParam(String[] val) {
        if (val != null && val.length > 0) {
            return val[0];
        }
        return null;
    }

}
