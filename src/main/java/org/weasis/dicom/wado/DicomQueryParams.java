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

import org.weasis.dicom.data.Patient;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;

public class DicomQueryParams {

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

    private final Properties properties;
    private final List<Patient> patients;
    private final DicomNode callingNode;
    private final DicomNode calledNode;
    private final AdvancedParams advancedParams;
    private final WadoParameters wadoParameters;
    private final String charsetEncoding;
    private final boolean acceptNoImage;
    private final Map<String, String[]> requestMap;

    public DicomQueryParams(DicomNode callingNode, DicomNode calledNode, HttpServletRequest request,
        WadoParameters wadoParameters, String charsetEncoding, boolean acceptNoImage, AdvancedParams params,
        Properties properties) {
        if (callingNode == null || calledNode == null) {
            throw new IllegalArgumentException("callingNode or calledNode cannot be null!");
        }
        this.properties = properties == null ? new Properties() : properties;
        this.patients = new ArrayList<Patient>();
        this.callingNode = callingNode;
        this.calledNode = calledNode;
        this.wadoParameters = wadoParameters;
        this.charsetEncoding = charsetEncoding;
        this.acceptNoImage = acceptNoImage;
        this.advancedParams = params;
        this.requestMap = new HashMap<String, String[]>(request.getParameterMap());
    }

    public List<Patient> getPatients() {
        return patients;
    }

    public DicomNode getCallingNode() {
        return callingNode;
    }

    public DicomNode getCalledNode() {
        return calledNode;
    }

    public WadoParameters getWadoParameters() {
        return wadoParameters;
    }

    public String getCharsetEncoding() {
        return charsetEncoding;
    }

    public boolean isAcceptNoImage() {
        return acceptNoImage;
    }

    public AdvancedParams getAdvancedParams() {
        return advancedParams;
    }

    public Properties getProperties() {
        return properties;
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
