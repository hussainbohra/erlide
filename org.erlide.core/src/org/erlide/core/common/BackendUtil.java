/*******************************************************************************
 * Copyright (c) 2004 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.core.common;

import java.io.IOException;
import java.net.InetAddress;

import org.erlide.jinterface.ErlLogger;

import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangTuple;
import com.ericsson.otp.erlang.OtpNode;

/**
 * 
 * 
 * @author Vlad Dumitrescu [vladdu55 at gmail dot com]
 */
public final class BackendUtil {
    private BackendUtil() {
    }

    public static OtpErlangObject ok(final OtpErlangObject v0) {
        if (!(v0 instanceof OtpErlangTuple)) {
            return v0;
        }
        final OtpErlangTuple v = (OtpErlangTuple) v0;
        if (Util.isOk(v)) {
            return v.elementAt(1);
        }
        return v;
    }

    public static String buildLocalNodeName(final String label,
            final boolean longName) {
        if (label.indexOf('@') > 0) {
            // ignore unique here?
            return label;
        }
        if (longName) {
            final String host = getHost();
            return label + "@" + host;
        } else {
            return label;
        }
    }

    public static String getHost() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
            if (System.getProperty("erlide.host") != null) {
                final int dot = host.indexOf(".");
                if (dot != -1) {
                    host = host.substring(0, dot);
                }
            }
        } catch (final IOException e) {
            host = "localhost";
            ErlLogger.error(e);
        }
        return host;
    }

    public static String createJavaNodeName() {
        final String fUniqueId = BackendUtil.getTimeSuffix();
        return "jerlide_" + fUniqueId;
    }

    public static String getLabelProperty() {
        return System.getProperty("erlide.label", null);
    }

    static String getTimeSuffix() {
        String fUniqueId;
        fUniqueId = Long.toHexString(System.currentTimeMillis() & 0xFFFFFFF);
        return fUniqueId;
    }

    public static OtpNode createOtpNode(final String cookie) throws IOException {
        OtpNode node;
        if (cookie == null) {
            node = new OtpNode(createJavaNodeName());
        } else {
            node = new OtpNode(createJavaNodeName(), cookie);
        }
        final String nodeCookie = node.cookie();
        final int len = nodeCookie.length();
        final String trimmed = len > 7 ? nodeCookie.substring(0, 7)
                : nodeCookie;
        ErlLogger.debug("using cookie '%s...'%d (info: '%s')", trimmed, len,
                cookie);
        return node;
    }

}
