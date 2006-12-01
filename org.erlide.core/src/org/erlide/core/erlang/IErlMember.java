/*******************************************************************************
 * Copyright (c) 2004 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution.
 *
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.core.erlang;

import com.ericsson.otp.erlang.OtpErlangObject;

public interface IErlMember extends IErlElement, ISourceReference,
		ISourceManipulation {

	/**
	 * Returns the compilation unit in which this member is declared, or
	 * <code>null</code> if this member is not declared in a compilation unit
	 * (for example, a binary type). This is a handle-only method.
	 * 
	 * @return the compilation unit in which this member is declared, or
	 *         <code>null</code> if this member is not declared in a
	 *         compilation unit (for example, a binary type)
	 */
	IErlModule getModule();

	OtpErlangObject getParseTree();

	ISourceRange getNameRange();

	String getHoverHelp();

}
