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

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IHEInvokeImageDisplay extends HttpServlet {

    private static final long serialVersionUID = -8426435270216254245L;
    private static final Logger LOGGER = LoggerFactory.getLogger(IHEInvokeImageDisplay.class);

    public IHEInvokeImageDisplay() {
        super();
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            response.setContentType(WeasisLauncher.JNLP_MIME_TYPE);

        } catch (Exception e) {
            LOGGER.error("doHead(HttpServletRequest, HttpServletResponse)", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doGet(request, response);
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // TODO match to the IID profile
        // WeasisLauncher.invokeWeasis(request, response);
    }
}
