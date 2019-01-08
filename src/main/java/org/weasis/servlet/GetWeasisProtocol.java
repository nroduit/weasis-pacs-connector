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
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.mf.thread.ManifestBuilder;

@WebServlet(urlPatterns = { "/weasis" })
public class GetWeasisProtocol extends HttpServlet {

    private static final long serialVersionUID = 2987582758040784229L;
    private static final Logger LOGGER = LoggerFactory.getLogger(GetWeasisProtocol.class);

    private static final String QUOTE = "\"";

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
            buf.append(QUOTE);

            String urlCfg = request.getParameter(WeasisConfig.PARAM_CONFIG_URL);
            if (urlCfg == null) {
                urlCfg = props.getProperty("weasis.config.url");
            }

            buf.append(" $weasis:config");
            if (urlCfg == null) {
                buf.append(' ');
                buf.append(WeasisConfig.PARAM_CODEBASE);
                buf.append("=\"");
                buf.append(WeasisConfig.getCodebase(request, props, false));
                buf.append(QUOTE);
                if (props.getProperty("weasis.ext.url") != null) {
                    buf.append(' ');
                    buf.append(WeasisConfig.PARAM_CODEBASE_EXT);
                    buf.append("=\"");
                    buf.append(WeasisConfig.getCodebase(request, props, true));
                    buf.append(QUOTE);
                }
                Map<String, String[]> params = request.getParameterMap();
                handleRequestParameters(buf, params, WeasisConfig.PARAM_PROPERTY);
                handleRequestParameters(buf, params, WeasisConfig.PARAM_ARGUMENT);
            } else {
                buf.append(' ');
                buf.append(WeasisConfig.PARAM_CONFIG_URL);
                buf.append("=\"");
                buf.append(urlCfg);
                buf.append(QUOTE);
            }

            StringBuilder wurl = new StringBuilder("weasis://");
            wurl.append(URLEncoder.encode(buf.toString(), "UTF-8"));
            response.sendRedirect(wurl.toString());
        } catch (Exception e) {
            LOGGER.error("Redirect to weasis secheme", e);
            ServletUtil.sendResponseError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private static void handleRequestParameters(StringBuilder buf, Map<String, String[]> params, String param) {
        String[] paramValues = ServletUtil.getParameters(params.get(param));
        if (paramValues != null) {
            for (String p : paramValues) {
                buf.append(' ');
                buf.append(param);
                buf.append("=\"");
                buf.append(p);
                buf.append(QUOTE);
            }
        }
    }
}
