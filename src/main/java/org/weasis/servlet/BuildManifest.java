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

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.mf.thread.ManifestBuilder;

@WebServlet(urlPatterns = "/manifest")
public class BuildManifest extends HttpServlet {

    private static final long serialVersionUID = 575795035231900320L;
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildManifest.class);

    public BuildManifest() {
        super();
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        response.setContentType("text/xml");
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        buildManifest(request, response);
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        buildManifest(request, response);
    }

    private void buildManifest(HttpServletRequest request, HttpServletResponse response) {

        response.setStatus(HttpServletResponse.SC_ACCEPTED);

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1
        response.setHeader("Pragma", "no-cache"); // HTTP 1.0
        response.setDateHeader("Expires", -1); // Proxies

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

            boolean gzip = request.getParameter("gzip") != null;

            ManifestBuilder builder = ServletUtil.buildManifest(request, props);
            // BUILDER IS NULL WHEN NO ALLOWED PARAMETER ARE GIVEN WHICH LEADS TO NO MANIFEST BUILT

            if (builder == null) {
                // NO body in response, see: https://httpstatuses.com/204
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                response.setHeader("Cause", "No allowed parameters have been given to build a manifest");
            } else {
                String wadoQueryUrl = ServletUtil.buildManifestURL(request, builder, props, gzip);
                wadoQueryUrl = response.encodeRedirectURL(wadoQueryUrl);
                response.setStatus(HttpServletResponse.SC_OK);

                if (request.getParameter(ConnectorProperties.PARAM_URL) != null) {
                    response.setContentType("text/plain");
                    response.getWriter().print(wadoQueryUrl);
                } else {
                    response.sendRedirect(wadoQueryUrl);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Building manifest", e);
            ServletUtil.sendResponseError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
