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

package org.weasis.servlet;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.mf.thread.ManifestBuilder;

@WebServlet(urlPatterns = { "/weasis" })
public class GetWeasisProtocol extends HttpServlet {

    private static final long serialVersionUID = 2987582758040784229L;
    private static final Logger LOGGER = LoggerFactory.getLogger(GetWeasisProtocol.class);

    private static final String SERVICE_CONFIG = "weasis.config.url";
    private static final String SERVICE_PREFS = "weasis.pref.url";

    public GetWeasisProtocol() {
        super();
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            if (LOGGER.isDebugEnabled()) {
                ServletUtil.logInfo(request, LOGGER);
            }

            ConnectorProperties connectorProperties =
                (ConnectorProperties) this.getServletContext().getAttribute("componentProperties");
            // Check if the source of this request is allowed
            if (!ServletUtil.isRequestAllowed(request, connectorProperties, LOGGER)) {
                return;
            }

            ConnectorProperties props = connectorProperties.getResolveConnectorProperties(request);

            ManifestBuilder builder = ServletUtil.buildManifest(request, props);
            String wadoQueryUrl = ServletUtil.buildManifestURL(request, builder, props, true);
            wadoQueryUrl = response.encodeRedirectURL(wadoQueryUrl);

            StringBuilder buf = new StringBuilder();
            int startIndex = wadoQueryUrl.indexOf(':');
            if (startIndex > 0) {
                buf.append("$dicom:get -w \"");
            } else {
                throw new IllegalStateException("Cannot not get a valid manifest URL " + wadoQueryUrl);
            }
            buf.append(wadoQueryUrl);
            buf.append("\"");

            String urlCfg = request.getParameter(WeasisConfig.PARAM_CONFIG_URL);
            if (urlCfg == null) {
                urlCfg = props.getProperty(SERVICE_CONFIG);
            }

            buf.append(" $weasis:config");
            if (urlCfg == null) {
                addElement(buf, WeasisConfig.PARAM_CODEBASE, WeasisConfig.getCodebase(request, props, false));
                addElement(buf, WeasisConfig.PARAM_CODEBASE_EXT, WeasisConfig.getCodebase(request, props, true));

                // Add properties
                Map<String, String[]> params = request.getParameterMap();
                Map<String, String> properties = getPropertiesFromRequestParameters(params);
                if (properties.get(SERVICE_PREFS) == null) {
                    String prefs = props.getProperty(SERVICE_PREFS);
                    if (StringUtil.hasText(prefs)) {
                        // Add the preference service URL from this component configuration
                        properties.put(SERVICE_PREFS, prefs);
                    }
                }

                for (Entry<String, String> entry : properties.entrySet()) {
                    StringBuilder b = new StringBuilder(entry.getKey());
                    b.append(' ');
                    b.append(entry.getValue());
                    addElement(buf, WeasisConfig.PARAM_PROPERTY, b.toString());
                }

                // Add arguments
                handleRequestParameters(buf, params, WeasisConfig.PARAM_ARGUMENT);
            } else {
                addElement(buf, WeasisConfig.PARAM_CONFIG_URL, urlCfg);
            }
            addElement(buf, WeasisConfig.PARAM_AUTHORIZATION, ServletUtil.getAuthorizationValue(request));

            StringBuilder wurl = new StringBuilder("weasis://");
            wurl.append(URLEncoder.encode(buf.toString(), "UTF-8"));
            response.sendRedirect(wurl.toString());
        } catch (Exception e) {
            LOGGER.error("Redirect to weasis secheme", e);
            ServletUtil.sendResponseError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private static void addElement(StringBuilder buf, String key, String val) {
        if (StringUtil.hasText(val)) {
            buf.append(' ');
            buf.append(key);
            buf.append("=\"");
            buf.append(val);
            buf.append("\"");
        }
    }

    private static Map<String, String> getPropertiesFromRequestParameters(Map<String, String[]> params) {
        Map<String, String> props = new HashMap<>();
        String[] paramValues = ServletUtil.getParameters(params.get(WeasisConfig.PARAM_PROPERTY));
        if (paramValues != null) {
            Pattern pattern = Pattern.compile("\\s+");
            for (String p : paramValues) {
                String[] res = pattern.split(removeEnglobingQuotes(p), 2);
                if (res.length == 2) {
                    props.put(res[0], res[1]);
                } else {
                    LOGGER.warn("Cannot parse property: {0}", p);
                }
            }
        }
        return props;
    }

    private static void handleRequestParameters(StringBuilder buf, Map<String, String[]> params, String param) {
        String[] paramValues = ServletUtil.getParameters(params.get(param));
        if (paramValues != null) {
            for (String p : paramValues) {
                addElement(buf, param, removeEnglobingQuotes(p));
            }
        }
    }

    private static String removeEnglobingQuotes(String value) {
        return value.replaceAll("^\"|\"$", "");
    }
}
