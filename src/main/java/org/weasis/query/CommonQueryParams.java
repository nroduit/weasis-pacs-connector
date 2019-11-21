package org.weasis.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import org.weasis.core.api.util.LangUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.mf.QueryResult;
import org.weasis.dicom.mf.ViewerMessage;
import org.weasis.dicom.param.DicomNode;
import org.weasis.query.db.DbQueryConfiguration;
import org.weasis.query.dicom.DicomQueryConfiguration;
import org.weasis.servlet.ConnectorProperties;
import org.weasis.servlet.ServletUtil;

public class CommonQueryParams {

    // HTTP Request Parameters – Patient-based
    public static final String PATIENT_ID = "patientID";
    public static final String PATIENT_NAME = "patientName";
    public static final String PATIENT_BIRTHDATE = "patientBirthDate";
    public static final String LOWER_DATETIME = "lowerDateTime";
    public static final String UPPER_DATETIME = "upperDateTime";
    public static final String MOST_RECENT_RESULTS = "mostRecentResults";
    public static final String MODALITIES_IN_STUDY = "modalitiesInStudy";
    public static final String VIEWER_TYPE = "viewerType";
    public static final String DIAGNOSTIC_QUALITY = "diagnosticQuality";
    public static final String KEY_IMAGES_ONLY = "keyImagesOnly";
    // Additional patient-based parameters
    public static final String KEYWORDS = "containsInDescription";

    // HTTP Request Parameters – Study-based
    public static final String STUDY_UID = "studyUID";
    public static final String ACCESSION_NUMBER = "accessionNumber";

    // Non IID request parameters
    public static final String SERIES_UID = "seriesUID";
    public static final String OBJECT_UID = "objectUID";

    // Archive property name to be used instead of those defined by "arc.config.list" in component properties
    public static final String ARCHIVE = "archive";

    // IHE Radiology Technical Framework Supplement – Invoke Image Display (IID)
    public static final String REQUEST_TYPE = "requestType";

    // Well-Known Values for Viewer Type Parameter
    public static final String IHE_BIR = "IHE_BIR";
    public static final String PATIENT_LEVEL = "PATIENT";
    public static final String STUDY_LEVEL = "STUDY";

    private static final Set<String> wadoQueryParams =
        Stream.of(PATIENT_ID, PATIENT_NAME, PATIENT_BIRTHDATE, LOWER_DATETIME, UPPER_DATETIME, MOST_RECENT_RESULTS,
            MODALITIES_IN_STUDY, VIEWER_TYPE, DIAGNOSTIC_QUALITY, KEY_IMAGES_ONLY, KEYWORDS, STUDY_UID,
            ACCESSION_NUMBER, SERIES_UID, OBJECT_UID, REQUEST_TYPE, ARCHIVE).collect(Collectors.toSet());

    public static final Consumer<Collection<String>> removeParams = c -> c.removeAll(wadoQueryParams);

    protected final ConnectorProperties properties;
    protected final List<AbstractQueryConfiguration> archiveList;
    protected final Map<String, String[]> requestMap;

    public CommonQueryParams(HttpServletRequest request, ConnectorProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties cannot be null!");
        }
        this.properties = properties;
        this.archiveList = new ArrayList<>();
        this.requestMap = new HashMap<>(request.getParameterMap());
        initArchiveList(request);
    }

    private void initArchiveList(HttpServletRequest request) {
        DicomNode callingNode = new DicomNode(properties.getProperty("aet", "PACS-CONNECTOR"));
        String[] archives = requestMap.get(ARCHIVE);
        String auth = ServletUtil.getAuthorizationValue(request);

        if (archives != null && archives.length > 0) {
            for (String archiveID : archives) {
                if (StringUtil.hasText(archiveID)) {
                    for (Properties p : properties.getArchivePropertiesList()) {
                        boolean buildConfig = false;
                        String id = p.getProperty("arc.id");
                        if (archiveID.equals(id)) {
                            buildConfig = true;
                        } else {
                            String oldIDs = p.getProperty("arc.inherit.ids");
                            if (StringUtil.hasText(oldIDs)) {
                                for (String s : oldIDs.split(",")) {
                                    if (archiveID.equals(s.trim())) {
                                        buildConfig = true;
                                        break;
                                    }
                                }
                            }
                        }

                        if (buildConfig) {
                            addArchive(p, callingNode, auth);
                            break;
                        }
                    }
                }
            }
        } else {
            for (Properties p : properties.getArchivePropertiesList()) {
                if (LangUtil.getEmptytoFalse(p.getProperty("arc.activate"))) {
                    addArchive(p, callingNode, auth);
                }
            }
        }
    }

    private void addArchive(Properties p, DicomNode callingNode, String auth) {
        if (StringUtil.hasText(auth)) {
            String tag = "Authorization:" + auth;
            String val = p.getProperty("wado.httpTags");
            if (StringUtil.hasText(val)) {
                val = val + "," + tag;
            } else {
                val = tag;
            }
            p.setProperty("wado.httpTags", val);
        }

        if (p.getProperty("arc.aet") != null) {
            this.archiveList.add(new DicomQueryConfiguration(p, callingNode));
        } else if (p.getProperty("arc.db.driver") != null) {
            this.archiveList.add(new DbQueryConfiguration(p));
        }
    }

    private static String getFirstParam(String[] val) {
        if (val != null && val.length > 0) {
            return val[0];
        }
        return null;
    }

    public boolean isAcceptNoImage() {
        return LangUtil.getEmptytoFalse(properties.getProperty("accept.noimage"));
    }

    public ConnectorProperties getProperties() {
        return properties;
    }

    public List<AbstractQueryConfiguration> getArchiveList() {
        return archiveList;
    }

    public void addGeneralViewerMessage(ViewerMessage viewerMessage) {
        if (!archiveList.isEmpty()) {
            archiveList.get(0).setViewerMessage(viewerMessage);
        }
    }

    public boolean hasGeneralViewerMessage() {
        return archiveList.get(0).getViewerMessage() != null;
    }

    public boolean hasPatients() {
        for (QueryResult arcConfig : archiveList) {
            if (!arcConfig.getPatients().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public void clearAllPatients() {
        for (QueryResult arcConfig : archiveList) {
            arcConfig.getPatients().clear();
        }
    }

    public void removePatientId(List<String> patientIdList, boolean containsIssuer) {
        for (QueryResult arcConfig : archiveList) {
            arcConfig.removePatientId(patientIdList, containsIssuer);
        }
    }

    public void removeStudyUid(List<String> studyUidList) {
        for (QueryResult arcConfig : archiveList) {
            arcConfig.removeStudyUid(studyUidList);
        }
    }

    public void removeAccessionNumber(List<String> accessionNumberList) {
        for (QueryResult arcConfig : archiveList) {
            arcConfig.removeAccessionNumber(accessionNumberList);
        }
    }

    public void removeSeriesUid(List<String> seriesUidList) {
        for (QueryResult arcConfig : archiveList) {
            arcConfig.removeSeriesUid(seriesUidList);
        }
    }

    public String getReqPatientID() {
        return getFirstParam(requestMap.get(PATIENT_ID));
    }

    public String getPatientName() {
        return getFirstParam(requestMap.get(PATIENT_NAME));
    }

    public String getPatientBirthDate() {
        return getFirstParam(requestMap.get(PATIENT_BIRTHDATE));
    }

    public String getLowerDateTime() {
        return getFirstParam(requestMap.get(LOWER_DATETIME));
    }

    public String getUpperDateTime() {
        return getFirstParam(requestMap.get(UPPER_DATETIME));
    }

    public String getMostRecentResults() {
        return getFirstParam(requestMap.get(MOST_RECENT_RESULTS));
    }

    public String getModalitiesInStudy() {
        return getFirstParam(requestMap.get(MODALITIES_IN_STUDY));
    }

    public String getKeywords() {
        return getFirstParam(requestMap.get(KEYWORDS));
    }

    public String getReqStudyUID() {
        return getFirstParam(requestMap.get(STUDY_UID));
    }

    public String getReqAccessionNumber() {
        return getFirstParam(requestMap.get(ACCESSION_NUMBER));
    }

    public String getReqSeriesUID() {
        return getFirstParam(requestMap.get(SERIES_UID));
    }

    public String getReqObjectUID() {
        return getFirstParam(requestMap.get(OBJECT_UID));
    }

    public String getRequestType() {
        return getFirstParam(requestMap.get(REQUEST_TYPE));
    }

    public String[] getReqPatientIDs() {
        return requestMap.get(PATIENT_ID);
    }

    public String[] getReqStudyUIDs() {
        return requestMap.get(STUDY_UID);
    }

    public String[] getReqAccessionNumbers() {
        return requestMap.get(ACCESSION_NUMBER);
    }

    public String[] getReqSeriesUIDs() {
        return requestMap.get(SERIES_UID);
    }

    public String[] getReqObjectUIDs() {
        return requestMap.get(OBJECT_UID);
    }

    public static boolean isManifestRequest(Map<String, String[]> map) {
        if (map == null) {
            return false;
        }
        return map.get(REQUEST_TYPE) != null || map.get(PATIENT_ID) != null || map.get(STUDY_UID) != null
            || map.get(ACCESSION_NUMBER) != null || map.get(SERIES_UID) != null || map.get(OBJECT_UID) != null;
    }

}