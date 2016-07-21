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
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.mf.thread.ManifestBuilder;

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
            String wadoQueryUrl = ServletUtil.buildManifestURL(request, builder, props, gzip);
            wadoQueryUrl = response.encodeRedirectURL(wadoQueryUrl);

            response.setStatus(HttpServletResponse.SC_OK);
            response.sendRedirect(wadoQueryUrl);

        } catch (Exception e) {
            LOGGER.error("Building manifest", e);
            ServletUtil.sendResponseError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
