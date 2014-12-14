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
import java.util.Enumeration;
import java.util.Properties;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.data.xml.TagUtil;
import org.weasis.dicom.util.StringUtil;
import org.weasis.dicom.wado.thread.ManifestBuilder;

public class WeasisLauncher extends HttpServlet {
    private static final long serialVersionUID = 8946852726380985736L;
    private static final Logger LOGGER = LoggerFactory.getLogger(WeasisLauncher.class);

    public WeasisLauncher() {
        super();
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            RequestDispatcher dispatcher = getServletContext().getRequestDispatcher("/jnlpBuilder");
            dispatcher.forward(request, response);
        } catch (Exception e) {
            StringUtil.logError(LOGGER, e, "jnlpBuilder request");
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doGet(request, response);
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Properties pacsProperties = (Properties) this.getServletContext().getAttribute("componentProperties");
        // Check if the source of this request is allowed
        if (!ServletUtil.isRequestAllowed(request, pacsProperties, LOGGER)) {
            return;
        }

        Properties extProps = new Properties();
        extProps.put(
            "server.base.url",
            ServletUtil.getBaseURL(request,
                StringUtil.getNULLtoFalse(pacsProperties.getProperty("server.canonical.hostname.mode"))));

        Properties dynamicProps = (Properties) pacsProperties.clone();

        // Perform variable substitution for system properties.
        for (Enumeration<?> e = pacsProperties.propertyNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            dynamicProps.setProperty(name,
                TagUtil.substVars(pacsProperties.getProperty(name), name, null, pacsProperties, extProps));
        }

        dynamicProps.putAll(extProps);

        invokeWeasis(request, response, dynamicProps);
    }

    public static void invokeWeasis(HttpServletRequest request, HttpServletResponse response, Properties props)
        throws IOException {

        try {
            if (LOGGER.isDebugEnabled()) {
                ServletUtil.logInfo(request, LOGGER);
            }

            String wadoQueryUrl = "";

            try {
                ManifestBuilder builder = ServletUtil.buildManifest(request, props);
                wadoQueryUrl = ServletUtil.buildManifestURL(request, builder, props, true);
            } catch (Exception e) {
                LOGGER.error(e.getMessage());
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }

            StringBuilder buf = new StringBuilder("/");

            String queryCodeBasePath = request.getParameter(SLwebstart_launcher.PARAM_CODEBASE);
            if (queryCodeBasePath == null) {
                // If weasis codebase is not in the request, set the url from the weasis-pacs-connector properties.
                String weasisBaseURL = props.getProperty("weasis.base.url", props.getProperty("server.base.url") + "/weasis");
                buf.append("?");
                buf.append(SLwebstart_launcher.PARAM_CODEBASE);
                buf.append("=");
                buf.append(weasisBaseURL);
            }

            buf.append(buf.length() > 1 ? "&" : "?");
            buf.append(SLwebstart_launcher.PARAM_ARGUMENT);
            buf.append("=$dicom:get -w ");
            buf.append(wadoQueryUrl);

            RequestDispatcher dispatcher =
                request.getSession().getServletContext().getRequestDispatcher(buf.toString());
            dispatcher.forward(request, response);

        } catch (Exception e) {
            LOGGER.error("doGet(HttpServletRequest, HttpServletResponse)", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
