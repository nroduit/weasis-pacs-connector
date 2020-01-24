/*******************************************************************************
 * Copyright (c) 2014 Weasis Team. All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Nicolas Roduit - initial API and implementation
 *******************************************************************************/

package org.weasis.servlet;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.mf.UploadXml;
import org.weasis.dicom.mf.XmlManifest;
import org.weasis.dicom.mf.thread.ManifestBuilder;
import org.weasis.query.CommonQueryParams;

@WebServlet(name = "GetWeasisProtocol", urlPatterns = { "/weasis", "/IHEInvokeImageDisplay" })
public class GetWeasisProtocol extends HttpServlet {

    private static final long serialVersionUID = 2987582758040784229L;
    private static final Logger LOGGER = LoggerFactory.getLogger(GetWeasisProtocol.class);

    public static final String CODEBASE_PROPERTY = "weasis.base.url";
    public static final String CODEBASE_EXT_PROPERTY = "weasis.ext.url";
    public static final String SERVICE_CONFIG_PROPERTY = "weasis.config.url";
    public static final String SERVICE_PREFS_PROPERTY = "weasis.pref.url";

    public GetWeasisProtocol() {
        super();
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        UploadXml manifest = WeasisLauncher.uploadManifest(request, response);
        if (manifest != null && "INVALID".equals(manifest.xmlManifest(null))) {
            return;
        }
        invokeWeasis(request, response, manifest);
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        invokeWeasis(request, response, null);
    }

    private static void invokeWeasis(HttpServletRequest request, HttpServletResponse response, XmlManifest manifest) {
        try {
            if (LOGGER.isDebugEnabled()) {
                ServletUtil.logInfo(request, LOGGER);
            }

            ServletContext ctx = request.getSession().getServletContext();
            final ConnectorProperties connectorProperties =
                (ConnectorProperties) ctx.getAttribute("componentProperties");
            // Check if the source of this request is allowed
            if (!ServletUtil.isRequestAllowed(request, connectorProperties, LOGGER)) {
                return;
            }

            ConnectorProperties props = connectorProperties.getResolveConnectorProperties(request);
            StringBuilder buf = new StringBuilder();

            // BUILD WADO MANIFEST FROM WORKERTHREAD AND GET WADO QUERY URL TO RETRIEVE IT LATER

            ManifestBuilder builder;
            if (manifest == null) {
                builder = ServletUtil.buildManifest(request, props);
            } else {
                builder = ServletUtil.buildManifest(request, new ManifestBuilder(manifest));
            }

            // BUILDER IS NULL WHEN NO ALLOWED PARAMETER ARE GIVEN WHICH LEADS TO NO MANIFEST BUILT

            if (builder != null) {
                String wadoQueryUrl = ServletUtil.buildManifestURL(request, builder, props, true);
                wadoQueryUrl = response.encodeRedirectURL(wadoQueryUrl);

                // ADD WADO MANIFEST PARAMETER >> $dicom:get -w "..."

                int startIndex = wadoQueryUrl.indexOf(':');
                if (startIndex > 0) {
                    buf.append("$dicom:get -w \"");
                } else {
                    throw new IllegalStateException("Cannot not get a valid manifest URL " + wadoQueryUrl);
                }
                buf.append(wadoQueryUrl);
                buf.append("\"");
            }

            //// HANDLE REQUEST PARAMETERS

            Map<String, String[]> requestParams = new LinkedHashMap<>(request.getParameterMap());

            CommonQueryParams.removeParams.accept(requestParams.keySet());
            ConnectorProperties.removeParams.accept(requestParams.keySet());

            // GET PROPERTIES PARAMETERS FROM REQUEST PARAMETERS
            Map<String, String> requestProperties = getPropertiesFromRequestParameters(requestParams);

            // ADD ARGUMENTS PARAMETERS
            handleRequestParameters(buf, requestParams, WeasisConfig.PARAM_ARGUMENT);

            // GET weasisConfigUrl FROM REQUEST PARAMETERS
            String weasisConfigUrl = ServletUtil.getFirstParameter(requestParams.remove(WeasisConfig.PARAM_CONFIG_URL));

            String weasisConfigUrlProp = requestProperties.remove(SERVICE_CONFIG_PROPERTY);
            if (weasisConfigUrl == null)
                weasisConfigUrl = weasisConfigUrlProp;

            // OR GET weasisConfigUrl FROM CONNECTOR'S CONFIGURATION
            if (weasisConfigUrl == null) { // request argument with empty value overrides connector's configuration
                weasisConfigUrl = props.getProperty(SERVICE_CONFIG_PROPERTY);
            }

            boolean isRemoteLaunchConfigDefined = StringUtil.hasText(weasisConfigUrl);
            // NOTE : if remote launch config is defined then any request PROPERTIES would be delegated to the remote
            // service instead of being forwarded to Weasis

            StringBuilder configParamBuf = new StringBuilder();

            // GET weasisBaseUrl FROM REQUEST PARAMETERS
            String weasisBaseUrl = getCodeBaseFromRequest(request);
            requestParams.remove(WeasisConfig.PARAM_CODEBASE);

            String weasisBaseUrlProp = requestProperties.remove(CODEBASE_PROPERTY);
            if (weasisBaseUrl == null)
                weasisBaseUrl = weasisBaseUrlProp;

            // OR GET weasisBaseUrl FROM CONNECTOR'S CONFIGURATION IF REMOTE LAUNCH CONFIG IS NOT DEFINED
            if (weasisBaseUrl == null && !isRemoteLaunchConfigDefined) {
                weasisBaseUrl = getCodeBaseFromConnectorProperties(props);
            }
            addElementWithEmptyValue(configParamBuf, WeasisConfig.PARAM_CODEBASE, weasisBaseUrl,
                isRemoteLaunchConfigDefined);

            // GET weasisExtUrl FROM REQUEST PARAMETERS
            String weasisExtUrl = getCodeBaseExtFromRequest(request);
            requestParams.remove(WeasisConfig.PARAM_CODEBASE_EXT);

            String weasisExtUrlProp = requestProperties.remove(CODEBASE_EXT_PROPERTY);
            if (weasisExtUrl == null)
                weasisExtUrl = weasisExtUrlProp;

            // OR GET weasisExtUrl FROM CONNECTOR'S CONFIGURATION IF REMOTE LAUNCH CONFIG IS NOT DEFINED
            if (weasisExtUrl == null && !isRemoteLaunchConfigDefined) {
                weasisExtUrl = getCodeBaseExtFromConnectorProperties(props);
            }
            addElementWithEmptyValue(configParamBuf, WeasisConfig.PARAM_CODEBASE_EXT, weasisExtUrl,
                isRemoteLaunchConfigDefined);

            // GET weasisPrefURL FROM REQUEST PROPERTIES PARAMETERS
            String weasisPrefURL = requestProperties.get(SERVICE_PREFS_PROPERTY);

            // OR GET weasisPrefURL FROM CONNECTOR'S CONFIGURATION IF REMOTE LAUNCH CONFIG IS NOT DEFINED
            if (weasisPrefURL == null && !isRemoteLaunchConfigDefined) {
                weasisPrefURL = props.getProperty(SERVICE_PREFS_PROPERTY);
                if (StringUtil.hasText(weasisPrefURL)) {
                    requestProperties.put(SERVICE_PREFS_PROPERTY, weasisPrefURL.trim());
                }
            }

            // ADD PROPERTIES PARAMETERS
            for (Entry<String, String> entry : requestProperties.entrySet()) {
                StringBuilder propSB = new StringBuilder(entry.getKey());
                propSB.append(' ');
                propSB.append(entry.getValue());
                addElement(configParamBuf, WeasisConfig.PARAM_PROPERTY, propSB.toString(), isRemoteLaunchConfigDefined);
            }

            // ADD AUTHORIZATION PARAMETERS
            addElement(configParamBuf, WeasisConfig.PARAM_AUTHORIZATION, ServletUtil.getAuthorizationValue(request),
                isRemoteLaunchConfigDefined);

            requestParams.remove(WeasisConfig.PARAM_AUTHORIZATION);
            requestParams.remove("access_token");

            // ADD WEASISCONFIG PARAMETERS >> $weasis:config "..."
            buf.append(" $weasis:config");

            if (isRemoteLaunchConfigDefined) {

                // ADD ANY OTHER UNHANDLED PARAMETERS (those not removed)
                Iterator<Entry<String, String[]>> itParams = requestParams.entrySet().iterator();

                while (itParams.hasNext()) {
                    Entry<String, String[]> param = itParams.next();
                    String value = ServletUtil.getFirstParameter(param.getValue());
                    if (StringUtil.hasText(value))
                        value = removeEnglobingQuotes(value);
                    addElementWithEmptyValue(configParamBuf, param.getKey(), value, isRemoteLaunchConfigDefined);
                    itParams.remove();
                }

                // ADD weasisConfigUrl URL WITH HANDLED PARAMETERS
                if (configParamBuf.length() > 0) {
                    configParamBuf.replace(0, 1, "?"); // replace first query separator '&' by "?"
                    weasisConfigUrl += configParamBuf.toString();
                }
                addElement(buf, WeasisConfig.PARAM_CONFIG_URL, weasisConfigUrl);

            } else {
                // OR BUILD CUSTOM CONFIG THAT WOULD BE HANDLED BY WEASIS AND NOT BY REMOTE LAUNCH CONFIG SERVICE
                buf.append(" ").append(configParamBuf);
            }

            // BUILD LAUNCH URL
            String launcherUrlStr = "weasis://" + URLEncoder.encode(buf.toString().trim(), "UTF-8");

            response.sendRedirect(launcherUrlStr);

        } catch (Exception e) {
            LOGGER.error("Redirect to weasis secheme", e);
            ServletUtil.sendResponseError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private static void addElementWithEmptyValue(StringBuilder buf, String key, String val) {
        addElementWithEmptyValue(buf, key, val, false);
    }

    private static void addElementWithEmptyValue(StringBuilder buf, String key, String val,
        boolean isElementQueryParameter) {
        if (val != null) {
            buf.append(isElementQueryParameter ? '&' : ' ');
            buf.append(key);
            if (StringUtil.hasText(val)) {
                if (isElementQueryParameter)
                    buf.append("=").append(val);
                else
                    buf.append("=\"").append(val).append("\"");
            }
        }
    }

    private static void addElement(StringBuilder buf, String key, String val) {
        addElement(buf, key, val, false);
    }

    private static void addElement(StringBuilder buf, String key, String val, boolean isElementQueryParameter) {
        if (StringUtil.hasText(val)) {
            if (WeasisConfig.PARAM_ARGUMENT.equalsIgnoreCase(key)) {
                buf.append(" ");
                if (!(val.startsWith("$")))
                    buf.append("$");
                buf.append(val);
                // For identifying the commands at start-up, the symbol “$” must be added before the command
                // @see https://nroduit.github.io/en/basics/commands/
            } else {
                buf.append(isElementQueryParameter ? '&' : ' ');
                buf.append(key);
                if (isElementQueryParameter)
                    buf.append("=").append(val);
                else
                    buf.append("=\"").append(val).append("\"");
            }
        }
    }

    protected static String getCodeBaseFromConnectorProperties(ConnectorProperties props) {
        return getCodeBaseFromConnectorProperties(props, false);
    }

    protected static String getCodeBaseExtFromConnectorProperties(ConnectorProperties props) {
        return getCodeBaseFromConnectorProperties(props, true);
    }

    protected static String getCodeBaseFromConnectorProperties(ConnectorProperties props, boolean extCodeBase) {
        String codeBasePath = props.getProperty(CODEBASE_PROPERTY);

        if (StringUtil.hasText(codeBasePath)) {
            codeBasePath = codeBasePath.trim();

            if (extCodeBase) {
                if (codeBasePath.endsWith("/"))
                    codeBasePath = codeBasePath.substring(0, codeBasePath.length() - 1);
                codeBasePath = props.getProperty(CODEBASE_EXT_PROPERTY, codeBasePath + "-ext");
            }

            if (codeBasePath.endsWith("/")) {
                codeBasePath = codeBasePath.substring(0, codeBasePath.length() - 1);
            }
        }
        return codeBasePath;
    }

    public static String getCodeBaseFromRequest(HttpServletRequest request) {
        return getCodeBaseFromRequest(request, false);
    }

    public static String getCodeBaseExtFromRequest(HttpServletRequest request) {
        return getCodeBaseFromRequest(request, true);
    }

    protected static String getCodeBaseFromRequest(HttpServletRequest request, boolean extCodeBase) {
        String codeBasePath =
            request.getParameter(extCodeBase ? WeasisConfig.PARAM_CODEBASE_EXT : WeasisConfig.PARAM_CODEBASE);

        if (StringUtil.hasText(codeBasePath)) {
            codeBasePath = codeBasePath.trim();

            if (codeBasePath.startsWith("/")) {
                codeBasePath = ServletUtil.getBaseURL(request, false) + codeBasePath;
            } else {
                // supposed to be a new valid URL for codeBase
            }
            if (codeBasePath.endsWith("/"))
                codeBasePath = codeBasePath.substring(0, codeBasePath.length() - 1);
        }

        return codeBasePath;
    }

    private static Map<String, String> getPropertiesFromRequestParameters(Map<String, String[]> params) {
        Map<String, String> props = new HashMap<>();
        String[] paramValues = ServletUtil.getParameters(params.remove(WeasisConfig.PARAM_PROPERTY));
        if (paramValues != null) {
            Pattern pattern = Pattern.compile("\\s+");
            for (String p : paramValues) {
                String[] res = pattern.split(removeEnglobingQuotes(p), 2);
                if (res.length == 2) {
                    props.putIfAbsent(res[0], res[1]);
                } else {
                    LOGGER.warn("Cannot parse property: {}", p);
                }
            }
        }
        return props;
    }

    private static void handleRequestParameters(StringBuilder buf, Map<String, String[]> params, String param) {
        String[] paramValues = ServletUtil.getParameters(params.remove(param));

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
