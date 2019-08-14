/*******************************************************************************
 * Copyright (c) 2014 Weasis Team. All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Nicolas Roduit - initial API and implementation
 *******************************************************************************/

package org.weasis.servlet;

import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
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

@WebServlet(urlPatterns = { "/weasis", "/IHEInvokeImageDisplay" })
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
            ConnectorProperties connectorProperties = (ConnectorProperties) ctx.getAttribute("componentProperties");
            // Check if the source of this request is allowed
            if (!ServletUtil.isRequestAllowed(request, connectorProperties, LOGGER)) {
                return;
            }

            ConnectorProperties props = connectorProperties.getResolveConnectorProperties(request);

            // BUILD WADO MANIFEST FROM WORKERTHREAD AND GET URL TO RETRIEVE IT LATER

            ManifestBuilder builder;
            if (manifest == null) {
                builder = ServletUtil.buildManifest(request, props);
            } else {
                builder = ServletUtil.buildManifest(request, new ManifestBuilder(manifest));
            }
            String wadoQueryUrl = ServletUtil.buildManifestURL(request, builder, props, true);
            wadoQueryUrl = response.encodeRedirectURL(wadoQueryUrl);

            // ADD WADO MANIFEST PARAMETER >> $dicom:get -w "..."

            StringBuilder buf = new StringBuilder();
            int startIndex = wadoQueryUrl.indexOf(':');
            if (startIndex > 0) {
                buf.append("$dicom:get -w \"");
            } else {
                throw new IllegalStateException("Cannot not get a valid manifest URL " + wadoQueryUrl);
            }
            buf.append(wadoQueryUrl);
            buf.append("\"");

            //// HANDLE REQUEST PARAMETERS

            Map<String, String[]> params = new LinkedHashMap<>(request.getParameterMap());

            // ADD ARGUMENTS PARAMETERS
            handleRequestParameters(buf, params, WeasisConfig.PARAM_ARGUMENT);

            // GET weasisConfigUrl FROM REQUEST ARGUMENTS
            String weasisConfigUrl = ServletUtil.getFirstParameter(params.remove(WeasisConfig.PARAM_CONFIG_URL));

            // OR GET weasisConfigUrl FROM CONNECTOR'S CONFIGURATION
            if (weasisConfigUrl == null) {
                weasisConfigUrl = props.getProperty(SERVICE_CONFIG);
            }

            boolean remoteLaunchConfigDefined = weasisConfigUrl != null;
            // NOTE : if remote config is defined then any request properties has to be delagated to the remote service instead of beeing given directly to Weasis
            // in this case properties can be overidden by launch config remote service

            StringBuilder configParamBuf = new StringBuilder();

            addElementWithNullValue(configParamBuf, WeasisConfig.PARAM_CODEBASE, WeasisConfig.getCodebase(request, props, false), remoteLaunchConfigDefined);
            addElementWithNullValue(configParamBuf, WeasisConfig.PARAM_CODEBASE_EXT, WeasisConfig.getCodebase(request, props, true), remoteLaunchConfigDefined);

            params.remove(WeasisConfig.PARAM_CODEBASE);
            params.remove(WeasisConfig.PARAM_CODEBASE_EXT);

            // GET PROPERTIES PARAMETERS FROM REQUEST ARGUMENTS
            Map<String, String> properties = getPropertiesFromRequestParameters(params);

            // PREFERENCE SERVICE URL IS SET FROM CONNECTOR'S CONFIGURATION IF NOT PROVIDED BY REQUEST
            if (properties.get(SERVICE_PREFS) == null) {
                String prefs = props.getProperty(SERVICE_PREFS);
                if (StringUtil.hasText(prefs)) {
                    properties.put(SERVICE_PREFS, prefs);
                }
            }

            // ADD PROPERTIES PARAMETERS
            for (Entry<String, String> entry : properties.entrySet()) {
                StringBuilder b = new StringBuilder(entry.getKey());
                b.append(' ');
                b.append(entry.getValue());
                addElement(configParamBuf, WeasisConfig.PARAM_PROPERTY, b.toString(), remoteLaunchConfigDefined);
            }

            // ADD AUTHORIZATION PARAMETERS
            addElement(configParamBuf, WeasisConfig.PARAM_AUTHORIZATION, ServletUtil.getAuthorizationValue(request), remoteLaunchConfigDefined);

            params.remove(WeasisConfig.PARAM_AUTHORIZATION);
            params.remove("access_token");

           
            // ADD WEASISCONFIG PARAMETERS >> $weasis:config "..."
            buf.append(" $weasis:config");

            if (remoteLaunchConfigDefined) {
                
                // ADD ANY OTHER UNHANDLED PARAMETERS 
                // note : they can be consumed as placeholders in a template engine from the remoteLaunchConfig service
                Iterator<Entry<String, String[]>> itParams = params.entrySet().iterator();

                while (itParams.hasNext()) {
                    Entry<String, String[]> param = itParams.next();
                    String value = ServletUtil.getFirstParameter(param.getValue());
                    if (StringUtil.hasText(value))
                        value = removeEnglobingQuotes(value);
                    addElementWithNullValue(configParamBuf, param.getKey(), value, remoteLaunchConfigDefined);
                    itParams.remove();
                }
                
                // ADD weasisConfigUrl URL WITH HANDLED PARAMETERS
                // TODO verify URL integrity

                configParamBuf.replace(0, 1, "?");// replace first query separator '&' by "?"
                weasisConfigUrl += configParamBuf.toString();
//                addElement(buf, WeasisConfig.PARAM_CONFIG_URL, URLEncoder.encode(weasisConfigUrl, "UTF-8"));
                addElement(buf, WeasisConfig.PARAM_CONFIG_URL, weasisConfigUrl);


            } else {
                // OR BUILD CUSTOM CONFIG
                buf.append(" ").append(configParamBuf);
            }

            // BUILD LAUNCH URL
            StringBuilder wurl = new StringBuilder("weasis://");
            wurl.append(URLEncoder.encode(buf.toString(), "UTF-8"));

            response.sendRedirect(wurl.toString());
        } catch (

        Exception e) {
            LOGGER.error("Redirect to weasis secheme", e);
            ServletUtil.sendResponseError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private static void addElementWithNullValue(StringBuilder buf, String key, String val) {
        addElementWithNullValue(buf, key, val);
    }

    private static void addElementWithNullValue(StringBuilder buf, String key, String val, boolean querySeparator) {
        buf.append(querySeparator ? '&' : ' ');
        buf.append(key);
        if (StringUtil.hasText(val)) {
            buf.append("=\"");
            buf.append(val);
            buf.append("\"");
        }
    }

    private static void addElement(StringBuilder buf, String key, String val) {
        addElement(buf, key, val, false);
    }

    private static void addElement(StringBuilder buf, String key, String val, boolean querySeparator) {
        if (StringUtil.hasText(val)) {
            buf.append(querySeparator ? '&' : ' ');
            buf.append(key);
            buf.append("=\"");
            buf.append(val);
            buf.append("\"");
        }
    }

    private static Map<String, String> getPropertiesFromRequestParameters(Map<String, String[]> params) {
        Map<String, String> props = new HashMap<>();
        String[] paramValues = ServletUtil.getParameters(params.remove(WeasisConfig.PARAM_PROPERTY));
        if (paramValues != null) {
            Pattern pattern = Pattern.compile("\\s+");
            for (String p : paramValues) {
                String[] res = pattern.split(removeEnglobingQuotes(p), 2);
                if (res.length == 2) {
                    props.put(res[0], res[1]);
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
