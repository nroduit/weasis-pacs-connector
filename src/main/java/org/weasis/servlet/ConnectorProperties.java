package org.weasis.servlet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.weasis.dicom.data.xml.TagUtil;
import org.weasis.dicom.util.StringUtil;

public class ConnectorProperties extends Properties {
    private static final long serialVersionUID = -1461425609157253501L;

    public static final String CONFIG_FILENAME = "config.filename";
    public static final String MANIFEST_VERSION = "mfv";

    private final List<Properties> list;

    public ConnectorProperties() {
        list = new ArrayList<>();
    }

    public ConnectorProperties(Properties defaults) {
        super(defaults);
        list = new ArrayList<>();
    }

    @Override
    public synchronized boolean equals(Object o) {
        if (o instanceof ConnectorProperties) {
            ConnectorProperties c = (ConnectorProperties) o;
            boolean identical = super.equals(c);
            if (identical && c.list.size() == list.size()) {
                for (int i = 0; i < list.size(); i++) {
                    Object oa = list.get(i);
                    Object ob = c.list.get(i);
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
        for (Properties p : list) {
            code ^= p.hashCode();
        }
        return code;
    }

    public void addArchiveProperties(Properties archiveProps) {
        list.add(archiveProps);
    }

    public List<Properties> getArchivePropertiesList() {
        return Collections.unmodifiableList(list);
    }

    public ConnectorProperties getDeepCopy() {
        ConnectorProperties newObject = new ConnectorProperties();
        newObject.putAll(this);
        for (Properties properties : list) {
            newObject.list.add((Properties) properties.clone());
        }
        return newObject;
    }

    public ConnectorProperties getResolveConnectorProperties(HttpServletRequest request) {
        Properties extProps = new Properties();
        boolean canonical = StringUtil.getNULLtoFalse(this.getProperty("server.canonical.hostname.mode"));
        String serverHost = ServletUtil.getServerHost(request, canonical);
        extProps.put("server.host", serverHost);
        extProps.put("server.base.url", request.getScheme() + "://" + serverHost + ":" + request.getServerPort());

        ConnectorProperties dynamicProps = getDeepCopy();

        // Perform variable substitution for system properties.
        for (Enumeration<?> e = this.propertyNames(); e.hasMoreElements();) {
            String name = (String) e.nextElement();
            dynamicProps.setProperty(name, TagUtil.substVars(this.getProperty(name), name, null, this, extProps));
        }

        dynamicProps.putAll(extProps);

        for (Properties dynProps : dynamicProps.list) {
            // Perform variable substitution for system properties.
            for (Enumeration<?> e = dynProps.propertyNames(); e.hasMoreElements();) {
                String name = (String) e.nextElement();
                dynProps.setProperty(name,
                    TagUtil.substVars(dynProps.getProperty(name), name, null, dynProps, extProps));
            }
        }

        String manifestVersion = request.getParameter(MANIFEST_VERSION);
        if (manifestVersion != null) {
            dynamicProps.put("manifest.version", manifestVersion);
        }

        return dynamicProps;

    }
}
