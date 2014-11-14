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
import java.util.List;

public class WadoParameters {

    public static final String TAG_DOCUMENT_ROOT = "wado_query";
    public static final String TAG_SCHEMA =
        " xmlns= \"http://www.weasis.org/xsd\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"";
    public static final String TAG_WADO_URL = "wadoURL";
    public static final String TAG_WADO_ONLY_SOP_UID = "requireOnlySOPInstanceUID";
    public static final String TAG_WADO_ADDITIONNAL_PARAMETERS = "additionnalParameters";
    public static final String TAG_WADO_OVERRIDE_TAGS = "overrideDicomTagsList";
    public static final String TAG_WADO_WEB_LOGIN = "webLogin";
    public static final String TAG_HTTP_TAG = "httpTag";

    private final String wadoURL;
    private final boolean requireOnlySOPInstanceUID;
    private final String additionnalParameters;
    private final String overrideDicomTagsList;
    private final String webLogin;
    private final List<WadoParameters.HttpTag> httpTaglist;

    public WadoParameters(String wadoURL, boolean requireOnlySOPInstanceUID, String additionnalParameters,
        String overrideDicomTagsList, String webLogin) {
        if (wadoURL == null) {
            throw new IllegalArgumentException("wadoURL cannot be null");
        }
        this.wadoURL = wadoURL;
        this.httpTaglist = new ArrayList<WadoParameters.HttpTag>(2);
        this.webLogin = webLogin == null ? null : webLogin.trim();
        this.requireOnlySOPInstanceUID = requireOnlySOPInstanceUID;
        this.additionnalParameters = additionnalParameters == null ? "" : additionnalParameters;
        this.overrideDicomTagsList = overrideDicomTagsList;
    }

    public List<WadoParameters.HttpTag> getHttpTaglist() {
        return httpTaglist;
    }

    public void addHttpTag(String key, String value) {
        if (key != null && value != null) {
            httpTaglist.add(new HttpTag(key, value));
        }
    }

    public String getWebLogin() {
        return webLogin;
    }

    public String getWadoURL() {
        return wadoURL;
    }

    public boolean isRequireOnlySOPInstanceUID() {
        return requireOnlySOPInstanceUID;
    }

    public String getAdditionnalParameters() {
        return additionnalParameters;
    }

    public String getOverrideDicomTagsList() {
        return overrideDicomTagsList;
    }


    static class HttpTag {
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
}
