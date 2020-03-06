package org.weasis.servlet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import org.weasis.core.api.util.LangUtil;

public class ConnectorProperties extends Properties {
    private static final long serialVersionUID = -1461425609157253501L;

    private static final String DELIM_START = "${";
    private static final String DELIM_STOP = "}";

    public static final String CONFIG_FILENAME = "config.filename";

    public static final String MANIFEST_VERSION = "mfv";
    public static final String PARAM_URL = "url";
    public static final String PARAM_LAUNCH = "launch";

    private static final Set<String> connectorsParams =
        Stream.of(MANIFEST_VERSION, PARAM_URL, PARAM_LAUNCH).collect(Collectors.toSet());

    public static final Consumer<Collection<String>> removeParams = c -> c.removeAll(connectorsParams);

    private final List<Properties> arcList;

    public ConnectorProperties() {
        arcList = new ArrayList<>();
    }

    public ConnectorProperties(Properties defaults) {
        super(defaults);
        arcList = new ArrayList<>();
    }

    @Override
    public synchronized boolean equals(Object o) {
        if (o instanceof ConnectorProperties) {
            ConnectorProperties c = (ConnectorProperties) o;
            boolean identical = super.equals(c);
            if (identical && c.arcList.size() == arcList.size()) {
                for (int i = 0; i < arcList.size(); i++) {
                    Object oa = arcList.get(i);
                    Object ob = c.arcList.get(i);
                    // Handle both are null
                    if (oa == null && ob == null) {
                        continue;
                    }
                    if (oa == null || !oa.equals(ob)) {
                        return false;
                    }
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public synchronized int hashCode() {
        int code = super.hashCode();
        for (Properties p : arcList) {
            code ^= p.hashCode();
        }
        return code;
    }

    public void addArchiveProperties(Properties archiveProps) {
        arcList.add(archiveProps);
    }

    public List<Properties> getArchivePropertiesList() {
        return Collections.unmodifiableList(arcList);
    }

    public ConnectorProperties getDeepCopy() {
        ConnectorProperties newObject = new ConnectorProperties();
        newObject.putAll(this);
        for (Properties properties : arcList) {
            newObject.arcList.add((Properties) properties.clone());
        }
        return newObject;
    }

    public ConnectorProperties getResolveConnectorProperties(HttpServletRequest request) {
        Properties extProps = new Properties();
        boolean canonical = LangUtil.getEmptytoFalse(this.getProperty("server.canonical.hostname.mode"));

        String serverHost = ServletUtil.getServerHost(request, canonical);
        extProps.put("server.host", serverHost);

        String serverBaseUrl = request.getScheme() + "://" + serverHost + ":" + request.getServerPort();
        extProps.put("server.base.url", serverBaseUrl);

        String applicationContextUrl = serverBaseUrl + request.getContextPath();
        extProps.put("application.context.url", applicationContextUrl);

        // This should be done to ensure property change like serverBaseUrl when calling the servlet through different networks (LAN and WAN)
        ConnectorProperties dynamicProps = getDeepCopy();

        // Perform variable substitution with System OR configProps OR extProps properties
        for (Enumeration<?> e = this.propertyNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            dynamicProps.setProperty(name, substVars(this.getProperty(name), name, null, this, extProps));
        }

        dynamicProps.putAll(extProps);

        // Perform variable substitution for archive properties with System OR configProps OR extProps properties
        for (Properties dynProps : dynamicProps.arcList) {
            for (Enumeration<?> e = dynProps.propertyNames(); e.hasMoreElements();) {
                String name = (String) e.nextElement();
                dynProps.setProperty(name, substVars(dynProps.getProperty(name), name, null, dynProps, extProps));
            }
        }

        String manifestVersion = request.getParameter(MANIFEST_VERSION);
        if (manifestVersion != null) {
            dynamicProps.put("manifest.version", manifestVersion);
        }

        return dynamicProps;

    }

    static String substVars(String val, String currentKey, Map<String, String> cycleMap, Properties configProps,
        Properties extProps) {

        Map<String, String> map = cycleMap == null ? new HashMap<>() : cycleMap;
        map.put(currentKey, currentKey);

        int stopDelim = -1;
        int startDelim;

        do {
            stopDelim = val.indexOf(DELIM_STOP, stopDelim + 1);
            if (stopDelim < 0) {
                return val;
            }
            startDelim = val.indexOf(DELIM_START);
            if (startDelim < 0) {
                return val;
            }
            while (stopDelim >= 0) {
                int idx = val.indexOf(DELIM_START, startDelim + DELIM_START.length());
                if ((idx < 0) || (idx > stopDelim)) {
                    break;
                } else if (idx < stopDelim) {
                    startDelim = idx;
                }
            }
        } while ((startDelim > stopDelim) && (stopDelim >= 0));

        String variable = val.substring(startDelim + DELIM_START.length(), stopDelim);

        if (map.get(variable) != null) {
            throw new IllegalArgumentException("recursive variable reference: " + variable);
        }
        String substValue = System.getProperty(variable);
        if (substValue == null) {
            substValue = configProps == null ? null : configProps.getProperty(variable, null);
            if (substValue == null) {
                substValue = extProps == null ? null : extProps.getProperty(variable, null);
            }
        }

        map.remove(variable);
        String result =
            val.substring(0, startDelim) + substValue + val.substring(stopDelim + DELIM_STOP.length(), val.length());
        return substVars(result, currentKey, map, configProps, extProps);
    }
}
