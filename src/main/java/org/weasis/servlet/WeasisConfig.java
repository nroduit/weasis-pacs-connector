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
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.StringUtil;

@WebServlet(urlPatterns = "/WeasisConfig")
public class WeasisConfig extends HttpServlet {

    private static final long serialVersionUID = 3012016354418267374L;
    private static final Logger LOGGER = LoggerFactory.getLogger(WeasisConfig.class);

    public static final String PARAM_NO_GZIP = "noGzip";
    public static final String PARAM_CODEBASE_EXT = "cdb-ext";
    public static final String PARAM_CODEBASE = "cdb";
    public static final String PARAM_CONFIG_URL = "wcfg";
    public static final String PARAM_AUTHORIZATION = "auth";
    public static final String PARAM_ARGUMENT = "arg";
    public static final String PARAM_PROPERTY = "pro";

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            doGet(request, response);
        } catch (ServletException | IOException e) {
            LOGGER.error("doGet error", e);
        }
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        response.setStatus(HttpServletResponse.SC_ACCEPTED);

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1
        response.setHeader("Pragma", "no-cache"); // HTTP 1.0
        response.setDateHeader("Expires", -1); // Proxies

        ConnectorProperties props = (ConnectorProperties) this.getServletContext().getAttribute("componentProperties");
        props = props.getResolveConnectorProperties(request);

        Map<String, String> properties = new HashMap<>();
        List<String> arguments = new ArrayList<>();

        String codeBasePath = getCodebase(request, props, false);
        LOGGER.debug("WeasisConf - codeBasePath = {}", codeBasePath);
        properties.put("weasis.codebase.url", codeBasePath);
        properties.put("felix.config.properties", codeBasePath + "/conf/config.properties");
        properties.put("weasis.i18n", codeBasePath + "-i18n"); // Works only if war files are not renamed

        // Get codebaseExt path
        String codeBaseExtPath = getCodebase(request, props, true);
        LOGGER.debug("WeasisConf - codeBaseExtPath = {}", codeBaseExtPath);
        properties.put("weasis.codebase.ext.url", codeBaseExtPath);
        properties.put("felix.extended.config.properties", codeBaseExtPath + "/conf/ext-config.properties");

        Map<String, String[]> params = request.getParameterMap();
        handleRequestPropertyParameter(properties, params);
        handleRequestArgumentParameter(arguments, params);

        String encoding = "UTF-8";
        response.setCharacterEncoding(encoding);
        String xml = null;
        try {
            Writer writer = new StringWriter();
            writeHeader(writer, encoding);
            writeProperties(writer, properties);
            writeArguments(writer, arguments);
            writer.append("\n</weasisConfig>");
            xml = writer.toString();
        } catch (Exception e) {
            LOGGER.error("Cann write xml config", e);
        }

        if (xml == null) {
            ServletUtil.sendResponseError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Error when building the xml manifest.");
            return;
        }

        Boolean gzip = request.getParameter(PARAM_NO_GZIP) == null;

        response.setStatus(HttpServletResponse.SC_OK);
        
        if (gzip) {
            try {
                OutputStream outputStream = response.getOutputStream();
                GZIPOutputStream gzipStream = new GZIPOutputStream(outputStream);
                response.setContentType("application/x-gzip");
                response.setHeader("Content-Disposition", "filename=\"manifest-config.gz\";");

                gzipStream.write(xml.getBytes(encoding));
                gzipStream.finish();
            } catch (Exception e) {
                String errorMsg = "Exception writing GZIP response";
                LOGGER.error(errorMsg, e);
                ServletUtil.sendResponseError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorMsg);
                return;
            }
        } else {
            try {
                PrintWriter writer = response.getWriter();
                response.setContentType("text/xml");
                response.setHeader("Content-Disposition", "filename=\"weasis-config.xml\";");
                response.setContentLength(xml.length());
                writer.print(xml);
            } catch (Exception e) {
                String errorMsg = "Exception writing noGzip response";
                LOGGER.error(errorMsg, e);
                ServletUtil.sendResponseError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorMsg);
                return;
            }
        }

        
    }

    private void handleRequestArgumentParameter(List<String> arguments, Map<String, String[]> params) {
        String[] argValues = ServletUtil.getParameters(params.get(WeasisConfig.PARAM_ARGUMENT));
        if (argValues != null) {
            for (String a : argValues) {
                arguments.add(a);
            }
        }
    }

    private void handleRequestPropertyParameter(Map<String, String> properties, Map<String, String[]> params) {
        String[] propValues = ServletUtil.getParameters(params.get(WeasisConfig.PARAM_PROPERTY));
        if (propValues != null) {
            Pattern pattern = Pattern.compile("\\s");
            for (String p : propValues) {
                // split any whitespace character: [ \t\n\x0B\f\r ]
                String[] property = pattern.split(p, 2);

                String key = property != null && property.length > 0 ? property[0] : null;
                String value = property != null && property.length > 1 ? property[1] : null;

                if (key != null && value != null) {
                    properties.put(key, value);
                }
            }
        }
    }

    private static void writeHeader(Writer mf, String encoding) throws IOException {
        mf.append("<?xml version=\"1.0\" encoding=\"");
        mf.append(encoding);
        mf.append("\" ?>");
        mf.append("\n<weasisConfig>");
    }

    private static void writeProperties(Writer mf, Map<String, String> map) throws IOException {
        mf.append("\n<javaOptions>");
        for (Entry<String, String> prop : map.entrySet()) {
            mf.append("\n<property name=\"");
            mf.append(prop.getKey());
            mf.append("\" value=\"");
            mf.append(prop.getValue());
            mf.append("\" />");
        }
        mf.append("\n</javaOptions>");
    }

    private static void writeArguments(Writer mf, List<String> list) throws IOException {
        mf.append("\n<arguments>");
        for (String arg : list) {
            mf.append("\n<arg>");
            mf.append(arg);
            mf.append("</arg>");
        }
        mf.append("\n</arguments>");
    }

    public static String getCodebase(HttpServletRequest request, ConnectorProperties props, boolean extCodebase) {
        String codeBasePath;
        String queryCodeBasePath = request.getParameter(extCodebase ? PARAM_CODEBASE_EXT : PARAM_CODEBASE);
        if (queryCodeBasePath != null) {
            if (queryCodeBasePath.startsWith("/")) {
                codeBasePath = ServletUtil.getBaseURL(request, false) + queryCodeBasePath;
            } else {
                codeBasePath = StringUtil.hasText(queryCodeBasePath) ? queryCodeBasePath : StringUtil.EMPTY_STRING; // supposed to be a new valid URL for codeBase
            }
        } else {
            // If weasis codebase is not in the request, set the URL from the weasis-pacs-connector properties.
            codeBasePath = props.getProperty("weasis.base.url");
            if(codeBasePath == null) {
                return StringUtil.EMPTY_STRING;
            }
            if(extCodebase) {
                codeBasePath = props.getProperty("weasis.ext.url", codeBasePath + "-ext");  
            }
        }

        if (codeBasePath.endsWith("/")) {
            codeBasePath = codeBasePath.substring(0, codeBasePath.length() - 1);
        }
        return codeBasePath;
    }
}
