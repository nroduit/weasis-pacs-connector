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

@WebServlet(urlPatterns = { "/getJnlpScheme/*" })
public class GetJnlpProtocol extends HttpServlet {
    private static final long serialVersionUID = 3831796275365799251L;
    private static final Logger LOGGER = LoggerFactory.getLogger(GetJnlpProtocol.class);

    public GetJnlpProtocol() {
        super();
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            StringBuilder buf = new StringBuilder();
            String baseUrl = request.getRequestURL().toString();
            if (baseUrl.startsWith("http:")) {
                buf.append("jnlp");
                buf.append(baseUrl.substring(4).replaceFirst("/getJnlpScheme", ""));
            } else if (baseUrl.startsWith("https:")) {
                buf.append("jnlps");
                buf.append(baseUrl.substring(5).replaceFirst("/getJnlpScheme", ""));
            } else {
                throw new IllegalStateException("Cannot not convert to jnlp no http or https request");
            }

            
            
            String params = request.getQueryString();
            if (params != null) {
                buf.append("?");
                buf.append(request.getQueryString());
            }

            response.sendRedirect(buf.toString());
        } catch (Exception e) {
            LOGGER.error("Redirect to jnlp secheme", e);
            ServletUtil.sendResponseError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
