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
package org.weasis.dicom.data.xml;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.weasis.dicom.data.TagW;

public class TagUtil {
    private static final String DELIM_START = "${";
    private static final String DELIM_STOP = "}";

    public static String substVars(String val, String currentKey, Map<String, String> cycleMap, Properties configProps, Properties extProps)
        throws IllegalArgumentException {
        if (cycleMap == null) {
            cycleMap = new HashMap<String, String>();
        }
        cycleMap.put(currentKey, currentKey);

        int stopDelim = -1;
        int startDelim = -1;

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

        if (cycleMap.get(variable) != null) {
            throw new IllegalArgumentException("recursive variable reference: " + variable);
        }
        String substValue = System.getProperty(variable);
        if (substValue == null) {            
             substValue = configProps == null ?null : configProps.getProperty(variable, null);
             if (substValue == null) {            
                 substValue = extProps == null ? null : extProps.getProperty(variable, null);
             }
        }

        cycleMap.remove(variable);
        val = val.substring(0, startDelim) + substValue + val.substring(stopDelim + DELIM_STOP.length(), val.length());
        val = substVars(val, currentKey, cycleMap, configProps,extProps);
        return val;
    }

    public static void addXmlAttribute(TagW tag, String value, StringBuilder result) {
        if (tag != null && value != null) {
            result.append(tag.getTagName());
            result.append("=\"");
            result.append(EscapeChars.forXML(value));
            result.append("\" ");
        }
    }

    public static void addXmlAttribute(String tag, String value, StringBuilder result) {
        if (tag != null && value != null) {
            result.append(tag);
            result.append("=\"");
            result.append(EscapeChars.forXML(value));
            result.append("\" ");
        }
    }

    public static void addXmlAttribute(String tag, Boolean value, StringBuilder result) {
        if (tag != null && value != null) {
            result.append(tag);
            result.append("=\"");
            result.append(value ? "true" : "false");
            result.append("\" ");
        }
    }

    public static void addXmlAttribute(String tag, List<String> value, StringBuilder result) {
        if (tag != null && value != null) {
            result.append(tag);
            result.append("=\"");
            int size = value.size();
            for (int i = 0; i < size - 1; i++) {
                result.append(EscapeChars.forXML(value.get(i)) + ",");
            }
            if (size > 0) {
                result.append(EscapeChars.forXML(value.get(size - 1)));
            }
            result.append("\" ");
        }
    }
}
