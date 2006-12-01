/*******************************************************************************
 * Copyright (c) 2005 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.ui.views.outline;

import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextInputListener;
import org.eclipse.jface.text.ITextListener;
import org.eclipse.jface.text.TextEvent;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;

public final class TestListener implements IResourceChangeListener,
		ISelectionChangedListener, ITextInputListener, ITextListener {

	public static final TestListener instance = new TestListener();

	private TestListener() {

	}

	public void resourceChanged(IResourceChangeEvent event) {
		System.out.println("� resourceChanged " + event);
	}

	public void selectionChanged(SelectionChangedEvent event) {
		System.out.println("� selectionChanged " + event);
	}

	public void inputDocumentAboutToBeChanged(IDocument oldInput,
			IDocument newInput) {
		System.out.println("� inputDocumentAboutToBeChanged " + oldInput + " "
				+ newInput);
	}

	public void inputDocumentChanged(IDocument oldInput, IDocument newInput) {
		System.out.println("� inputDocumentChanged " + oldInput + " "
				+ newInput);
	}

	public void textChanged(TextEvent event) {
		System.out.println("� textChanged " + event);
	}

}
