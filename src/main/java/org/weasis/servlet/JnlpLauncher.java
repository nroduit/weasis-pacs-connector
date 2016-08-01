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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jdom2.Attribute;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.input.sax.XMLReaders;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.util.FileUtil;
import org.weasis.dicom.util.StringUtil;

public class JnlpLauncher extends HttpServlet {
    private static final long serialVersionUID = 5979263846495591025L;
    private static final Logger LOGGER = LoggerFactory.getLogger(JnlpLauncher.class);

    public static final String DEFAULT_JNLP_TEMPLATE_NAME = "weasis.jnlp";
    public static final String JNLP_EXTENSION = ".jnlp";
    public static final String JNLP_MIME_TYPE = "application/x-java-jnlp-file";

    static final String INITIAL_HEAP_SIZE = "128m";
    static final String MAX_HEAP_SIZE = "512m";

    protected static final String PARAM_ARGUMENT = "arg";
    protected static final String PARAM_PROPERTY = "pro";

    protected static final String PARAM_UPLOAD = "upload";
    protected static final String PARAM_CODEBASE = "cdb";
    protected static final String PARAM_CODEBASE_EXT = "cdb-ext";
    protected static final String PARAM_SOURCE = "src";

    protected static final String ATTRIBUTE_UPLOADED_ARGUMENT = "org.weasis.uploaded.arg";

    protected static final String PARM_SERVER_PATH = "svr";
    protected static final String PARM_JVM_INITIAL_HEAP_SIZE = "ihs";
    protected static final String PARM_JVM_MAX_HEAP_SIZE = "mhs";

    protected static final String JNLP_TAG_ELT_ROOT = "jnlp";
    protected static final String JNLP_TAG_ATT_CODEBASE = "codebase";

    protected static final String JNLP_TAG_ELT_RESOURCES = "resources";
    protected static final String JNLP_TAG_ATT_HREF = "href";
    protected static final String JNLP_TAG_ELT_PROPERTY = "property";
    protected static final String JNLP_TAG_PRO_NAME = "name";
    protected static final String JNLP_TAG_PRO_VALUE = "value";

    protected static final String JNLP_TAG_ELT_APPLICATION_DESC = "application-desc";
    protected static final String JNLP_TAG_ELT_ARGUMENT = "argument";

    protected static final String JNLP_TAG_ELT_APPLET_DESC = "applet-desc";
    protected static final String JNLP_TAG_ELT_PARAM = "param";

    public JnlpLauncher() {
        super();
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        try {
            if (handleRequestAndRedirect(request, response)) {
                return;
            }
            response.setContentType(JNLP_MIME_TYPE);
        } catch (Exception e) {
            LOGGER.error("JNLP head", e);
            ServletUtil.sendResponseError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String uploadParam = request.getParameter(JnlpLauncher.PARAM_UPLOAD);
        if (StringUtil.hasText(uploadParam)) {
            try (BufferedReader reader = request.getReader()) {
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    buf.append(line);
                }
                if (buf.length() > 0) {
                    request.setAttribute(ATTRIBUTE_UPLOADED_ARGUMENT, buf.toString());
                }
            } catch (Exception e) {
                LOGGER.error("Uploading jnlp", e);
                ServletUtil.sendResponseError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            }
        }

        buildJNLP(request, response);
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        buildJNLP(request, response);
    }

    protected void buildJNLP(HttpServletRequest request, HttpServletResponse response) {

        try {
            if (LOGGER.isDebugEnabled()) {
                ServletUtil.logInfo(request, LOGGER);
            }
            if (handleRequestAndRedirect(request, response)) {
                return;
            }

            // Store jnlp templates jdom structure in hashMap, avoid to always read the same jnlp file
            Element rootJnlp = null;
            JnlpTemplate launcher = createLauncherTemplate(request);
            final Map<URI, Element> jnlpTemplates =
                (Map<URI, Element>) this.getServletContext().getAttribute("jnlpTemplates");
            if (jnlpTemplates != null) {
                Element element = jnlpTemplates.get(launcher.realPathURL);
                if (element != null) {
                    rootJnlp = element.clone();
                }
            }
            if (rootJnlp == null) {
                parseLauncherTemplate(launcher);
                rootJnlp = launcher.rootElt;
                if (jnlpTemplates != null && rootJnlp != null) {
                    jnlpTemplates.put(launcher.realPathURL, rootJnlp.clone());
                }
            } else {
                launcher.rootElt = rootJnlp;
            }

            Object uploadedArg = request.getAttribute(ATTRIBUTE_UPLOADED_ARGUMENT);

            if (uploadedArg instanceof String) {
                Object argValues =
                    ServletUtil.addParameter(launcher.parameterMap.get(PARAM_ARGUMENT), (String) uploadedArg);
                launcher.parameterMap.put(PARAM_ARGUMENT, argValues);
            }
            String launcherStr = buildJnlpResponse(launcher);

            LOGGER.debug("doGet() - launcherStr = [\n{}\n]", launcherStr);

            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1
            response.setHeader("Pragma", "no-cache"); // HTTP 1.0
            response.setDateHeader("Expires", -1); // Proxies
            response.setContentType(JNLP_MIME_TYPE);
            response.setHeader("Content-Disposition",
                String.format("inline; filename=\"%s\"", launcher.templateFileName));

            ServletUtil.write(launcherStr, response.getOutputStream());
        } catch (ServletErrorException e) {
            LOGGER.error("Build jnlp", e);
            ServletUtil.sendResponseError(response, e.responseErrorCode, e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Build jnlp", e);
            ServletUtil.sendResponseError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    protected boolean handleRequestAndRedirect(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        /*
         * NOTE: in web.xml config file the "<url-pattern>" must be directory mapping and not file mapping like "*.jnlp"
         * otherwise external launcher template from ?src= parameter would never be used and launcher template could
         * never be outside of Servlet context
         *
         * Ex of mapping the root of Servlet context : <servlet-mapping><url-pattern>/</url-pattern></servlet-mapping>
         */

        if (!request.getServletPath().endsWith("/") && !request.getServletPath().endsWith(JNLP_EXTENSION)) {
            LOGGER.debug("handleRequestAndRedirect() - forward request to default dispatcher : {}",
                request.getServletPath());
            RequestDispatcher defaultRequestDispatcher =
                getServletConfig().getServletContext().getNamedDispatcher("default");
            defaultRequestDispatcher.forward(request, response);
            return true;
        }
        return false;
    }

    protected JnlpTemplate createLauncherTemplate(HttpServletRequest request) throws ServletErrorException {

        String serverPath = ServletUtil.getBaseURL(request, false);

        String templatePath = null;
        String templateFileName = null;
        URI templateURL = null;
        String codeBasePath = null;
        String codeBaseExtPath = null;
        Map<String, Object> queryParameterMap = null;

        try {
            // Get launcher Template filename, path and URI
            templatePath = serverPath + request.getContextPath() + request.getServletPath();
            String queryLauncherPath = request.getParameter(PARAM_SOURCE); // this overrides Servlet context path
            if (queryLauncherPath != null) { // template isn't in the Web Servlet Context
                if (queryLauncherPath.startsWith("/")) {
                    templatePath = serverPath + queryLauncherPath; // supposed to be "serverPath/URI"
                } else {
                    templatePath = queryLauncherPath; // supposed to be a new valid URL for launcher template
                }
            }

            LOGGER.info("1 - templatePath = {}", templatePath);

            if (templatePath.endsWith("/")) {
                templateFileName = DEFAULT_JNLP_TEMPLATE_NAME; // default value
            } else {
                int fileNameBeginIndex = templatePath.lastIndexOf("/") + 1;
                templateFileName = templatePath.substring(fileNameBeginIndex);
                templatePath = templatePath.substring(0, fileNameBeginIndex);
            }

            if (templatePath.endsWith("/")) {
                templatePath = templatePath.substring(0, templatePath.length() - 1);
            }

            LOGGER.debug("locateLauncherTemplate() - String templatePath = {}", templatePath);
            LOGGER.debug("locateLauncherTemplate() - String templateFileName = {}", templateFileName);

            if (templatePath.startsWith(serverPath + request.getContextPath())) {
                String uriTemplatePath = templatePath.replaceFirst(serverPath + request.getContextPath(), "");
                templateURL = getServletContext().getResource("/" + uriTemplatePath + templateFileName).toURI();
            } else {
                templateURL = new URI(templatePath + "/" + templateFileName);
            }
            LOGGER.debug("locateLauncherTemplate() - URL templateURL = {}", templateURL);

            // Check if launcher template resource exists
            URLConnection launcherTemplateConnection = templateURL.toURL().openConnection();
            if (launcherTemplateConnection instanceof HttpURLConnection) {
                if (((HttpURLConnection) launcherTemplateConnection).getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("HttpURLConnection not accessible : " + templateURL);
                }
            } else if (launcherTemplateConnection.getContentLength() <= 0) {
                throw new IllegalStateException(
                    "URLConnection  not accessible : " + templatePath + "/" + templateFileName);
            }

            // Get codebase path
            String queryCodeBasePath = request.getParameter(PARAM_CODEBASE);
            if (queryCodeBasePath != null) { // codebase is not in the Web Servlet Context
                if (queryCodeBasePath.startsWith("/")) {
                    codeBasePath = serverPath + queryCodeBasePath;
                } else {
                    codeBasePath = queryCodeBasePath; // supposed to be a new valid URL for codeBase repo
                }
            } else {
                codeBasePath = templatePath; // default value
            }

            if (codeBasePath.endsWith("/")) {
                codeBasePath = codeBasePath.substring(0, codeBasePath.length() - 1);
            }

            LOGGER.debug("locateLauncherTemplate(HttpServletRequest) - String codeBasePath = {}", codeBasePath);

            // Get codebaseExt path
            String queryCodeBaseExtPath = request.getParameter(PARAM_CODEBASE_EXT);
            if (queryCodeBaseExtPath != null) { // codebaseExt is not in the Web Servlet Context
                if (queryCodeBaseExtPath.startsWith("/")) {
                    codeBaseExtPath = serverPath + queryCodeBaseExtPath;
                } else {
                    codeBaseExtPath = queryCodeBaseExtPath; // supposed to be a new valid URL for codeBaseExt repo
                }
            } else {
                codeBaseExtPath = codeBasePath + "-ext"; // default value
            }

            if (codeBaseExtPath.endsWith("/")) {
                codeBaseExtPath = codeBaseExtPath.substring(0, codeBaseExtPath.length() - 1);
            }

            LOGGER.debug("locateLauncherTemplate(HttpServletRequest) - String codeBaseExtPath = {}", codeBaseExtPath);

            // Get other parameters
            queryParameterMap = new HashMap<>(request.getParameterMap());

            String initialHeapSize = request.getParameter(PARM_JVM_INITIAL_HEAP_SIZE);
            if (initialHeapSize == null) {
                initialHeapSize = INITIAL_HEAP_SIZE;
            } else if (!initialHeapSize.endsWith("m")) {
                initialHeapSize += "m";
            }

            String maxHeapSize = request.getParameter(PARM_JVM_MAX_HEAP_SIZE);
            if (maxHeapSize == null) {
                maxHeapSize = MAX_HEAP_SIZE;
            } else if (!maxHeapSize.endsWith("m")) {
                maxHeapSize += "m";
            }

            // Set or override following parameters
            queryParameterMap.put(PARAM_CODEBASE, codeBasePath);
            queryParameterMap.put(PARAM_CODEBASE_EXT, codeBaseExtPath);

            queryParameterMap.put(PARM_SERVER_PATH, serverPath);
            queryParameterMap.put(PARM_JVM_INITIAL_HEAP_SIZE, initialHeapSize);
            queryParameterMap.put(PARM_JVM_MAX_HEAP_SIZE, maxHeapSize);

        } catch (Exception e) {
            throw new ServletErrorException(HttpServletResponse.SC_NOT_FOUND, "", e);
        }

        return new JnlpTemplate(templateFileName, templateURL, queryParameterMap);
    }

    public class ServletErrorException extends Exception {
        private static final long serialVersionUID = -1673431720286835416L;

        final int responseErrorCode;

        public ServletErrorException(int httpServletResponseCode, String message, Throwable cause) {
            super(message, cause);
            this.responseErrorCode = httpServletResponseCode;
        }

        public ServletErrorException(int httpServletResponseCode, String message) {
            super(message);
            this.responseErrorCode = httpServletResponseCode;
        }

        public ServletErrorException(int httpServletResponseCode) {
            super();
            this.responseErrorCode = httpServletResponseCode;
        }
    }

    protected void parseLauncherTemplate(JnlpTemplate launcher) throws ServletErrorException, IOException {

        // Parse JNLP launcher as JDOM
        Element rootElt = null;
        BufferedReader reader = null;

        try {
            // Assume the template has UTF-8 encoding
            reader = new BufferedReader(
                new InputStreamReader(launcher.realPathURL.toURL().openConnection().getInputStream(), "UTF-8"));

            rootElt = new SAXBuilder(XMLReaders.NONVALIDATING, null, null).build(reader).getRootElement();
        } catch (JDOMException e) {
            throw new ServletErrorException(HttpServletResponse.SC_NOT_ACCEPTABLE, "Can't parse launcher template", e);
        } finally {
            FileUtil.safeClose(reader);
        }

        if (!rootElt.getName().equals(JNLP_TAG_ELT_ROOT)) {
            throw new ServletErrorException(HttpServletResponse.SC_NOT_ACCEPTABLE, "Invalid JNLP launcher template");
        }

        launcher.rootElt = rootElt;
    }

    protected String buildJnlpResponse(JnlpTemplate launcher) throws ServletErrorException {

        launcher.rootElt.setAttribute(JNLP_TAG_ATT_CODEBASE,
            ServletUtil.getFirstParameter(launcher.parameterMap.get(PARAM_CODEBASE)));
        launcher.rootElt.removeAttribute(JNLP_TAG_ATT_HREF); // this tag has not to be used inside dynamic JNLP

        handleRequestPropertyParameter(launcher);
        handleRequestArgumentParameter(launcher);

        filterRequestParameterMarker(launcher);

        String outputStr = null;
        try {
            Format format = Format.getPrettyFormat();
            // Converts native encodings to ASCII with escaped Unicode like (ô è é...), necessary for jnlp
            format.setEncoding("US-ASCII");
            outputStr = new XMLOutputter(format).outputString(launcher.rootElt);
        } catch (Exception e) {
            throw new ServletErrorException(HttpServletResponse.SC_NOT_MODIFIED, "Can't build Jnlp launcher ", e);
        }

        return outputStr;
    }

    protected void handleRequestArgumentParameter(JnlpTemplate launcher) throws ServletErrorException {
        String[] argValues = ServletUtil.getParameters(launcher.parameterMap.remove(PARAM_ARGUMENT));

        if (launcher.rootElt != null && argValues != null) {
            try {
                Element applicationDescElt = launcher.rootElt.getChild(JNLP_TAG_ELT_APPLICATION_DESC);
                if (applicationDescElt == null) {
                    applicationDescElt = launcher.rootElt.getChild(JNLP_TAG_ELT_APPLET_DESC);
                    if (applicationDescElt == null) {
                        throw new IllegalStateException("JNLP TAG : <application-desc> or <applet-desc> is not found");
                    } else {
                        // Arguments in Applet are formatted as properties
                        for (String newContent : argValues) {
                            // split any whitespace character: [ \t\n\x0B\f\r ]
                            String[] property = Pattern.compile("\\s").split(newContent, 2);

                            String propertyName = property != null && property.length > 0 ? property[0] : null;
                            String propertyValue = property != null && property.length > 1 ? property[1] : null;

                            if (propertyName != null && propertyValue != null) {
                                Element paramElt = new Element(JNLP_TAG_ELT_PARAM);
                                paramElt.setAttribute(JNLP_TAG_PRO_NAME, propertyName);
                                paramElt.setAttribute(JNLP_TAG_PRO_VALUE, propertyValue);
                                applicationDescElt.addContent(paramElt);
                            } else {
                                throw new IllegalStateException(
                                    "Applet Query Parameter {property} is invalid : " + Arrays.toString(argValues));
                            }
                        }
                    }
                } else {
                    for (String newContent : argValues) {
                        applicationDescElt.addContent(new Element(JNLP_TAG_ELT_ARGUMENT).addContent(newContent));
                    }
                }
            } catch (Exception e) {
                throw new ServletErrorException(HttpServletResponse.SC_NOT_ACCEPTABLE,
                    "Can't add argument parameter to launcher template ", e);
            }
        }
    }

    protected void handleRequestPropertyParameter(JnlpTemplate launcher) throws ServletErrorException {
        String[] propValues = ServletUtil.getParameters(launcher.parameterMap.remove(PARAM_PROPERTY));

        if (launcher.rootElt != null && propValues != null) {
            try {
                Element resourcesElt = launcher.rootElt.getChild(JNLP_TAG_ELT_RESOURCES);

                if (resourcesElt == null) {
                    throw new IllegalStateException("JNLP TAG : <" + JNLP_TAG_ELT_RESOURCES + "> is not found");
                }

                for (int i = 0; i < propValues.length; i++) {
                    // split any whitespace character: [ \t\n\x0B\f\r ]
                    String[] property = Pattern.compile("\\s").split(propValues[i], 2);

                    String propertyName = property != null && property.length > 0 ? property[0] : null;
                    String propertyValue = property != null && property.length > 1 ? property[1] : null;

                    if (propertyName != null && propertyValue != null) {
                        boolean valueReplaced = false;

                        Iterator<Element> itr = resourcesElt.getChildren(JNLP_TAG_ELT_PROPERTY).iterator();
                        while (itr.hasNext()) {
                            Element elt = itr.next();
                            Attribute name = elt.getAttribute(JNLP_TAG_PRO_NAME);
                            if (name != null && name.getValue().equals(propertyName)) {
                                elt.setAttribute(JNLP_TAG_PRO_VALUE, propertyValue);
                                valueReplaced = true;
                                break;
                            }
                        }

                        if (!valueReplaced) {
                            Element propertyElt = new Element(JNLP_TAG_ELT_PROPERTY);
                            propertyElt.setAttribute(JNLP_TAG_PRO_NAME, propertyName);
                            propertyElt.setAttribute(JNLP_TAG_PRO_VALUE, propertyValue);
                            resourcesElt.addContent(propertyElt);
                        }
                    } else {
                        throw new IllegalStateException(
                            "Query Parameter {property} is invalid : " + Arrays.toString(propValues));
                    }
                }
            } catch (Exception e) {
                throw new ServletErrorException(HttpServletResponse.SC_NOT_ACCEPTABLE,
                    "Cannot add property parameter to launcher template", e);
            }
        }
    }

    protected void filterRequestParameterMarker(JnlpTemplate launcher) throws ServletErrorException {

        if (launcher != null && launcher.rootElt != null) {
            try {
                Element resourcesElt = launcher.rootElt.getChild(JNLP_TAG_ELT_RESOURCES);
                if (resourcesElt == null) {
                    throw new IllegalStateException("JNLP TAG : <" + JNLP_TAG_ELT_RESOURCES + "> is not found");
                }

                filterMarkerInAttribute(resourcesElt.getChildren(), launcher.parameterMap);

                Element applicationElt = launcher.rootElt.getChild(JNLP_TAG_ELT_APPLICATION_DESC);
                if (applicationElt == null) {
                    applicationElt = launcher.rootElt.getChild(JNLP_TAG_ELT_APPLET_DESC);
                    if (applicationElt == null) {
                        throw new IllegalStateException("JNLP TAG : <application-desc> or <applet-desc> is not found");
                    } else {
                        filterMarkerInElement(applicationElt.getChildren(JNLP_TAG_ELT_PARAM), launcher.parameterMap);
                    }
                } else {
                    filterMarkerInElement(applicationElt.getChildren(JNLP_TAG_ELT_ARGUMENT), launcher.parameterMap);
                }

            } catch (Exception e) {
                throw new ServletErrorException(HttpServletResponse.SC_NOT_ACCEPTABLE,
                    "Can't modify URLProperty value in launcher template", e);
            }
        }
    }

    static void filterMarkerInAttribute(List<Element> eltList, Map<String, Object> parameterMap) {

        final Pattern patternMarker = Pattern.compile("\\$\\{([^}]+)\\}"); // matching pattern is "${..}"

        for (Element elt : eltList) {
            for (Attribute attribute : elt.getAttributes()) {
                String attributeValue = attribute.getValue();

                if (attributeValue == null) {
                    continue;
                }

                Matcher matcher = patternMarker.matcher(attributeValue);
                while (matcher.find()) {
                    String marker = matcher.group(0); // full pattern matched "${..}"
                    String markerName = matcher.group(1); // get only text between curly braces
                    String parameterValue = ServletUtil.getFirstParameter(parameterMap.get(markerName));
                    if (parameterValue != null) {
                        attributeValue = attributeValue.replace(marker, parameterValue);
                        attribute.setValue(attributeValue);
                    } else {
                        LOGGER.warn("Found marker \"" + marker + "\" with NO matching parameter in Element <"
                            + elt.getName() + "> " + attribute);
                    }
                }
            }
        }
    }

    static void filterMarkerInElement(List<Element> eltList, Map<String, Object> parameterMap) {
        final Pattern patternMarker = Pattern.compile("\\$\\{([^}]+)\\}"); // matching pattern is "${..}"

        for (Element elt : eltList) {
            String elementValue = elt.getText();

            if (elementValue == null) {
                continue;
            }

            Matcher matcher = patternMarker.matcher(elementValue);
            while (matcher.find()) {
                String marker = matcher.group(0); // full pattern matched "${..}"
                String markerName = matcher.group(1); // get only text between curly braces
                String parameterValue = ServletUtil.getFirstParameter(parameterMap.get(markerName));

                if (parameterValue != null) {
                    elementValue = elementValue.replace(marker, parameterValue);
                    elt.setText(elementValue);

                } else {
                    LOGGER.warn("Found marker \"" + marker + "\" with NO matching parameter in Element <"
                        + elt.getName() + ">");
                }
            }
        }
    }

    static class JnlpTemplate {

        final String templateFileName;
        final URI realPathURL;
        final Map<String, Object> parameterMap;

        Element rootElt;

        public JnlpTemplate(String templateFileName, URI realPathURL, Map<String, Object> queryParameterMap) {
            this.templateFileName = templateFileName;
            this.realPathURL = realPathURL;
            this.parameterMap = queryParameterMap;
        }
    }
}
