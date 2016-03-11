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

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.mf.UploadXml;
import org.weasis.dicom.mf.XmlManifest;
import org.weasis.dicom.util.StringUtil;

public class WeasisAppletLauncher extends HttpServlet {

    private static final long serialVersionUID = -9044908938729820195L;
    private static final Logger LOGGER = LoggerFactory.getLogger(WeasisAppletLauncher.class);

    public WeasisAppletLauncher() {
        super();
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        try {
            RequestDispatcher dispatcher = getServletContext().getRequestDispatcher("/jnlpBuilder");
            dispatcher.forward(request, response);
        } catch (Exception e) {
            LOGGER.error("JNLP dispatcher error", e);
            ServletUtil.sendResponseError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
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

            boolean embeddedManifest = request.getParameterMap().containsKey(WeasisLauncher.PARAM_EMBED);
            String wadoQueryUrl = WeasisLauncher.buildManifest(request, manifest);

            String serverPath = props.getProperty("server.base.url");
            StringBuilder buf = new StringBuilder(serverPath);
            buf.append(request.getContextPath());
            // TODO servlet parameter
            buf.append("/weasisApplet.jnlp");

            WeasisLauncher.builRequest(request, buf, props);

            // TODO should transmit codebase ext, props and args

            /*
             * Issue when setting directly in the jnlp building url into jnlp_href. It seems some characters must be
             * escaped.
             */
            // buf.append("&#38;");
            // buf.append(JnlpLauncher.PARAM_ARGUMENT);
            // buf.append("=commands=$dicom:get -w ");
            // buf.append(wadoQueryUrl);

            String addparams = props.getProperty("request.addparams", null);
            if (addparams != null) {
                buf.append(addparams);
            }

            String manifestCmd = embeddedManifest ? "" : "&commands=$dicom:get -w " + wadoQueryUrl;

            RequestDispatcher dispatcher = request
                .getRequestDispatcher("/applet.jsp?jnlp=" + URLEncoder.encode(buf.toString(), "UTF-8") + manifestCmd);
            dispatcher.forward(request, response);

        } catch (Exception e) {
            LOGGER.error("Weasis Applet Servlet Launcher", e);
            String msg = e.getMessage();
            if (StringUtil.hasText(msg) && msg.startsWith("Unautorized")) {
                ServletUtil.sendResponseError(response, HttpServletResponse.SC_FORBIDDEN, msg);
            } else {
                ServletUtil.sendResponseError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg);
            }
        }
    }
}
