/*
 * Copyright (c) 2020-2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.util;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InetUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(InetUtil.class);

  private InetUtil() {}

  /**
   * Returns HostName resolved from request parameter value if present or from request caller
   * ipAddress
   *
   * @param request is the ServletRequest interface
   * @param requestHostParameterName name of the query parameter to be used with for hostName
   *     resolution
   * @return <b>hostName</b> or <b>null</b> if host cannot be resolved.
   * @note : a non-null but empty parameter value would force not to resolve the HostName of the
   *     remote client
   */
  public static String getClientHost(HttpServletRequest request, String requestHostParameterName) {
    String result = null;
    boolean finished = false;
    Objects.requireNonNull(request, "HttpServletRequest cannot be null");

    if (!isEmpty(requestHostParameterName)) {
      String hostParamValue = request.getParameter(requestHostParameterName);
      if (hostParamValue != null) {
        result = getClientHostFromAddr(hostParamValue.trim());
        finished = true;
      }
    }
    if (!finished) {
      result = getClientHostFromRequest(request);
    }
    return result;
  }

  /**
   * Returns HostName from request remote ipAddress
   *
   * @param request is the ServletRequest interface
   * @return <b>hostName</b> or <b>null</b> if host cannot be resolved.
   */
  public static String getClientHostFromRequest(HttpServletRequest request) {
    return getClientHostFromAddr(getClientIpAddr(request));
  }

  /**
   * Returns original Internet Protocol (IP) address of the client or last proxy that sent the
   * request even if behind a load balancer.
   *
   * @param request is the ServletRequest interface
   * @return <b>non null</b> client IP address
   */
  public static String getClientIpAddr(HttpServletRequest request) {
    // @see
    // http://stackoverflow.com/questions/4678797/how-do-i-get-the-remote-address-of-a-client-in-servlet

    Objects.requireNonNull(request, "HttpServletRequest cannot be null");

    String clientIP = null;
    for (String header : IP_HEADERS) {
      Enumeration<String> remoteAddr = request.getHeaders(header);
      if (remoteAddr != null) {
        while (remoteAddr.hasMoreElements()) {
          clientIP = remoteAddr.nextElement(); // get the last element
        }
        if (!(isEmpty(clientIP))) {
          LOGGER.debug("Client IP is read from Request Header {} with [{}]", header, clientIP);
          break;
        }
      }
    }
    if (isEmpty(clientIP)) {
      clientIP = request.getRemoteAddr();
      LOGGER.debug("Client IP is read from HttpServlet Request [{}]", clientIP);
    }
    return clientIP;
  }

  private static final List<String> IP_HEADERS =
      Arrays.asList(
          "X-Forwarded-For",
          "X_FORWARDED_FOR",
          "HTTP_CLIENT_IP",
          "HTTP_X_FORWARDED_FOR",
          "Proxy-Client-IP",
          "WL-Proxy-Client-IP");

  /**
   * Gets the host name without the service name part (domain name suffix). A reverse name lookup
   * will be performed and the result will be returned based on the system configured name lookup
   * service.
   *
   * <p>Depending on the underlying system configuration best effort method is applied, meaning a
   * simple textual representation of the IP address may be returned.
   *
   * <p>
   *
   * @param addr as literal IP address (IPV4/IPV6) or hostName (even in fully qualified domain name
   *     form)
   * @return <b>hostName</b> or <b>null</b> if host cannot be resolved.<br>
   */
  public static String getClientHostFromAddr(String addr) {

    if (isEmpty(addr))
      return null; // avoids resolving localhost loopback interface when supplied clientIP address
    // is null

    String clientHost = null;
    boolean isDNSResolved = false;
    try {
      // If a literal IP address is supplied, only the validity of the address format is checked.
      InetAddress inetAddress = InetAddress.getByName(addr);
      if (inetAddress.isLoopbackAddress()) clientHost = InetAddress.getLocalHost().getHostName();
      else clientHost = inetAddress.getHostName();

      isDNSResolved = !(inetAddress.getHostAddress().equalsIgnoreCase(clientHost));

    } catch (Exception e) {
      LOGGER.warn("Can't resolve hostname from address [{}] => {} ", addr, e.getLocalizedMessage());
    }

    if (!(isEmpty(clientHost))) {
      if (isDNSResolved) {
        String clientHostFQDN = clientHost;
        clientHost = removeFQDN(clientHost);

        if (clientHost.equalsIgnoreCase(clientHostFQDN))
          LOGGER.debug("Client HOST is [{}] found from address [{}]", clientHost, addr);
        else
          LOGGER.debug(
              "Client HOST is [{}] with original FQDN format [{}] found from address [{}]",
              clientHost,
              clientHostFQDN,
              addr);
      } else LOGGER.debug("Client HOST address is [{}] can't be resolved by DNS", clientHost);
    }

    return clientHost;
  }

  /**
   * Removes the fully qualified domain name suffix <br>
   *
   * @param host
   * @return <b>hostName</b> in its simplest form without any unneeded SUFFIX part
   */
  private static String removeFQDN(String host) {
    if (!isEmpty(host)) {

      int index = host.indexOf('.');
      if (index != -1) host = host.substring(0, index); // get part before Domain Name
    }
    return host;
  }

  //////////////////////////////////////////////////////////////////////////////

  private static boolean isEmpty(CharSequence cs) {
    return cs == null || cs.isEmpty();
  }
}
