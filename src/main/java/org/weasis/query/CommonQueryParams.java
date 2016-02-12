package org.weasis.query;

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
import org.weasis.query.db.DbQueryConfiguration;
import org.weasis.query.dicom.DicomQueryConfiguration;
import org.weasis.servlet.ConnectorProperties;

public class CommonQueryParams {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonQueryParams.class);

    // Non IID request parameters
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

    protected final ConnectorProperties properties;
    protected final List<AbstractQueryConfiguration> archiveList;
    protected final Map<String, String[]> requestMap;

    public CommonQueryParams(HttpServletRequest request, ConnectorProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties cannot be null!");
        }
        this.properties = properties;
        this.archiveList = new ArrayList<AbstractQueryConfiguration>();
        this.requestMap = new HashMap<String, String[]>(request.getParameterMap());

        DicomNode callingNode = new DicomNode(properties.getProperty("aet", "PACS-CONNECTOR"));

        for (Properties p : properties.getArchivePropertiesList()) {
            if (p.getProperty("arc.aet") != null) {
                this.archiveList.add(new DicomQueryConfiguration(p, callingNode));
            } else if (p.getProperty("arc.db.driver") != null) {
                this.archiveList.add(new DbQueryConfiguration(p));
            }
        }

        if (archiveList.isEmpty()) {
            // No configuration found. Build with default dcm4chee values
            this.archiveList.add(new DicomQueryConfiguration(properties, callingNode));
        }
    }

    private static String getFirstParam(String[] val) {
        if (val != null && val.length > 0) {
            return val[0];
        }
        return null;
    }

    public boolean isAcceptNoImage() {
        return StringUtil.getNULLtoFalse(properties.getProperty("accept.noimage"));
    }

    public ConnectorProperties getProperties() {
        return properties;
    }

    public List<AbstractQueryConfiguration> getArchiveList() {
        return archiveList;
    }

    public void addGeneralWadoMessage(WadoMessage wadoMessage) {
        if (!archiveList.isEmpty()) {
            archiveList.get(0).getWadoMessages().add(wadoMessage);
        }
    }

    public boolean hasPatients() {
        for (AbstractQueryConfiguration arcConfig : archiveList) {
            if (!arcConfig.getPatients().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public void clearAllPatients() {
        for (AbstractQueryConfiguration arcConfig : archiveList) {
            arcConfig.getPatients().clear();
        }
    }

    public void removePatientId(List<String> patientIdList) {
        for (AbstractQueryConfiguration arcConfig : archiveList) {
            arcConfig.removePatientId(patientIdList);
        }
    }

    public void removeStudyUid(List<String> studyUidList) {
        for (AbstractQueryConfiguration arcConfig : archiveList) {
            arcConfig.removeStudyUid(studyUidList);
        }
    }

    public void removeAccessionNumber(List<String> accessionNumberList) {
        for (AbstractQueryConfiguration arcConfig : archiveList) {
            arcConfig.removeAccessionNumber(accessionNumberList);
        }
    }

    public void removeSeriesUid(List<String> seriesUidList) {
        for (AbstractQueryConfiguration arcConfig : archiveList) {
            arcConfig.removeSeriesUid(seriesUidList);
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

}