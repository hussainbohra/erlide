/*******************************************************************************
 * Copyright (c) 2006 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.runtime.backend;

import com.ericsson.otp.erlang.OtpErlangAtom;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangTuple;

public class RpcResult {

	private OtpErlangObject fValue;

	private boolean fOk = true;

	public RpcResult(OtpErlangObject res) {
		if ((res instanceof OtpErlangTuple)
				&& (((OtpErlangTuple) res).elementAt(0) instanceof OtpErlangAtom)
				&& (((OtpErlangAtom) (((OtpErlangTuple) res).elementAt(0)))
						.atomValue().equals("badrpc") || ((OtpErlangAtom) (((OtpErlangTuple) res)
						.elementAt(0))).atomValue().equals("EXIT"))) {
			fOk = false;
			fValue = ((OtpErlangTuple) res).elementAt(1);
		} else {
			fOk = true;
			fValue = res;
		}

	}

	public boolean isOk() {
		return fOk;
	}

	public OtpErlangObject getValue() {
		return fValue;
	}

	@Override
	public String toString() {
		return "RPC:" + fOk + ":" + fValue.toString();
	}
}
