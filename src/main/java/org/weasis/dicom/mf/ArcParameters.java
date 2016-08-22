package org.weasis.dicom.mf;

import java.util.ArrayList;
import java.util.List;

public class ArcParameters {

    // Manifest 2.5
    public static final String TAG_DOCUMENT_ROOT = "manifest";
    public static final String SCHEMA =
        "xmlns=\"http://www.weasis.org/xsd/2.5\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"";
    public static final String TAG_ARC_QUERY = "arcQuery";
    public static final String ARCHIVE_ID = "arcId";
    public static final String BASE_URL = "baseUrl";

    // Manifest 1
    public static final String TAG_HTTP_TAG = "httpTag";
    public static final String ADDITIONNAL_PARAMETERS = "additionnalParameters";
    public static final String OVERRIDE_TAGS = "overrideDicomTagsList";
    public static final String WEB_LOGIN = "webLogin";

    private final String baseURL;
    private final String archiveID;
    private final String additionnalParameters;
    private final String overrideDicomTagsList;
    private final String webLogin;
    private final List<ArcParameters.HttpTag> httpTaglist;

    protected static class HttpTag {
        private final String key;
        private final String value;

        public HttpTag(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }
    }

    public ArcParameters(String archiveID, String baseURL, String additionnalParameters, String overrideDicomTagsList,
        String webLogin) {
        if (archiveID == null || baseURL == null) {
            throw new IllegalArgumentException("archiveID and wadoURL cannot be null");
        }
        this.archiveID = archiveID;
        this.baseURL = baseURL;
        this.webLogin = webLogin == null ? null : webLogin.trim();
        this.additionnalParameters = additionnalParameters == null ? "" : additionnalParameters;
        this.overrideDicomTagsList = overrideDicomTagsList;
        this.httpTaglist = new ArrayList<>(2);
    }

    public List<ArcParameters.HttpTag> getHttpTaglist() {
        return httpTaglist;
    }

    public void addHttpTag(String key, String value) {
        if (key != null && value != null) {
            httpTaglist.add(new HttpTag(key, value));
        }
    }

    public String getArchiveID() {
        return archiveID;
    }

    public String getBaseURL() {
        return baseURL;
    }

    public String getWebLogin() {
        return webLogin;
    }

    public String getAdditionnalParameters() {
        return additionnalParameters;
    }

    public String getOverrideDicomTagsList() {
        return overrideDicomTagsList;
    }

}