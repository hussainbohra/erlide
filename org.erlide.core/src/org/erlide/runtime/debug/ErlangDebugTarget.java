/*******************************************************************************
 * Copyright (c) 2004 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution.
 *
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.runtime.debug;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IMemoryBlock;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IThread;
import org.erlide.core.util.ErlangFunctionCall;
import org.erlide.jinterface.JInterfaceFactory;
import org.erlide.runtime.ErlLogger;
import org.erlide.runtime.backend.Backend;
import org.erlide.runtime.backend.events.EventHandler;

import com.ericsson.otp.erlang.OtpErlangAtom;
import com.ericsson.otp.erlang.OtpErlangList;
import com.ericsson.otp.erlang.OtpErlangLong;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangPid;
import com.ericsson.otp.erlang.OtpErlangRangeException;
import com.ericsson.otp.erlang.OtpErlangTuple;

import erlang.ErlideDebug;

public class ErlangDebugTarget extends ErlangDebugElement implements
        IDebugTarget, IErlangDebugNode {

	public static final IThread[] NO_PROCS = new IThread[] {};

	public static final int INTERPRETED_MODULES_CHANGED = 0;

	private final List<ErlangProcess> fAllProcesses;
	private final List<ErlangProcess> fLocalProcesses;
	private final Backend fBackend;
	private final ILaunch fLaunch;
	private boolean fDisconnected = false;
	// private final DebuggerListener fDbgListener;
	// private final DebuggerEventListener fDebuggerEventListener;
	private boolean fTerminated;
	private boolean fShowSystemProcesses = false;
	private boolean fShowErlideProcesses = false;
	private final Set<String> interpretedModules;
	private final Collection<IProject> projects;

	private final Map<OtpErlangPid, OtpErlangPid> metaPids = new TreeMap<OtpErlangPid, OtpErlangPid>();
	private final Map<OtpErlangPid, OtpErlangPid> pidsFromMeta = new TreeMap<OtpErlangPid, OtpErlangPid>();

	private final String fNodeName;

	// private final WaitingForDebuggerListener waiter;

	public ErlangDebugTarget(final ILaunch launch, final Backend b,
	        final Collection<IProject> projects, final int debugFlags) {
		super(null);
		fBackend = b;
		fNodeName = b.getPeer();
		fLaunch = launch;
		fTerminated = false;
		this.projects = projects;
		fAllProcesses = new ArrayList<ErlangProcess>();
		fLocalProcesses = new ArrayList<ErlangProcess>();
		interpretedModules = new HashSet<String>();

		b.getEventDaemon().addListener(new DebugEventHandler());

		final OtpErlangPid pid = ErlideDebug.startDebug(b, debugFlags);
		ErlLogger.debug("debug started " + pid);
		fBackend.send(pid, JInterfaceFactory.mkTuple(
		        new OtpErlangAtom("parent"), b.getEventPid()));

		DebugPlugin.getDefault().getBreakpointManager().addBreakpointListener(
		        this);
	}

	@Override
	public ILaunch getLaunch() {
		return fLaunch;
	}

	@Override
	public IDebugTarget getDebugTarget() {
		return this;
	}

	public IProcess getProcess() {
		return null;
	}

	public IThread[] getThreads() throws DebugException {
		if (isTerminated()) {
			return NO_PROCS;
		}
		return fLocalProcesses.toArray(new IThread[fLocalProcesses.size()]);
	}

	public boolean hasThreads() throws DebugException {
		return !isTerminated();
	}

	public String getName() throws DebugException {
		return fNodeName;
	}

	public boolean supportsBreakpoint(final IBreakpoint breakpoint) {
		// TODO we should ask the Erlang debugger too...
		if (!isTerminated()
		        && breakpoint.getModelIdentifier().equals(getModelIdentifier())) {
			final IProject bpProject = breakpoint.getMarker().getResource()
			        .getProject();
			for (final IProject p : projects) {
				if (p == bpProject) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean canTerminate() {
		return true;
	}

	public boolean isTerminated() {
		return fTerminated;
	}

	public void terminate() {
		if (fTerminated) {
			return;
		}

		fTerminated = true;
		fBackend.send("erlide_dbg_mon", new OtpErlangAtom("stop"));

		DebugPlugin.getDefault().getBreakpointManager()
		        .removeBreakpointListener(this);

		fireTerminateEvent();
	}

	/**
	 * Notification we have connected to the VM and it has started. Resume the
	 * VM.
	 */
	protected void started() {
		fireCreationEvent();
		installDeferredBreakpoints();
		try {
			resume();
		} catch (final DebugException e) {
			ErlLogger.warn(e);
		}
	}

	/**
	 * Install breakpoints that are already registered with the breakpoint
	 * manager.
	 */
	private void installDeferredBreakpoints() {
		final IBreakpoint[] breakpoints = DebugPlugin.getDefault()
		        .getBreakpointManager().getBreakpoints(getModelIdentifier());
		for (int i = 0; i < breakpoints.length; i++) {
			breakpointAdded(breakpoints[i]);
		}
	}

	public boolean canResume() {
		return false;
	}

	public boolean canSuspend() {
		return false;
	}

	public boolean isSuspended() {
		return false;
	}

	public void resume() throws DebugException {
	}

	public void suspend() throws DebugException {
	}

	public void breakpointAdded(final IBreakpoint breakpoint) {
		if (supportsBreakpoint(breakpoint)) {
			try {
				if (breakpoint.isEnabled()
				        && DebugPlugin.getDefault().getBreakpointManager()
				                .isEnabled() || !breakpoint.isRegistered()) {
					final ErlangLineBreakpoint erlangLineBreakpoint = (ErlangLineBreakpoint) breakpoint;
					erlangLineBreakpoint.install(this);
				}
			} catch (final CoreException e) {
				ErlLogger.warn(e);
			}
		}

	}

	public void breakpointRemoved(final IBreakpoint breakpoint,
	        final IMarkerDelta delta) {
		if (supportsBreakpoint(breakpoint)) {
			final ErlangLineBreakpoint erlangLineBreakpoint = (ErlangLineBreakpoint) breakpoint;
			erlangLineBreakpoint.remove(this);
		}
	}

	public void breakpointChanged(final IBreakpoint breakpoint,
	        final IMarkerDelta delta) {
		if (supportsBreakpoint(breakpoint)) {
			try {
				if (breakpoint.isEnabled()
				        && DebugPlugin.getDefault().getBreakpointManager()
				                .isEnabled()) {
					breakpointAdded(breakpoint);
				} else {
					breakpointRemoved(breakpoint, null);
				}
			} catch (final CoreException e) {
				ErlLogger.warn(e);
			}
		}
	}

	public boolean canDisconnect() {
		return true;
	}

	public void disconnect() throws DebugException {
		// tell backend to stop debugging
		fDisconnected = true;
	}

	public boolean isDisconnected() {
		return fDisconnected;
	}

	public boolean supportsStorageRetrieval() {
		return false;
	}

	public IMemoryBlock getMemoryBlock(final long startAddress,
	        final long length) throws DebugException {
		return null;
	}

	public Backend getBackend() {
		return fBackend;
	}

	public boolean isShowErlideProcesses() {
		return fShowErlideProcesses;
	}

	public void setShowErlideProcesses(final boolean showErlideProcesses) {
		fShowErlideProcesses = showErlideProcesses;
	}

	public boolean isShowSystemProcesses() {
		return fShowSystemProcesses;
	}

	public void setShowSystemProcesses(final boolean showSystemProcesses) {
		fShowSystemProcesses = showSystemProcesses;
	}

	final private static int META_UNKNOWN = 0;
	final private static int META_BREAK_AT = 1;
	final private static int META_WAIT_AT = 2;
	final private static int META_EXIT_AT = 3;

	private void handleMetaEvent(final OtpErlangPid metaPid,
	        final OtpErlangTuple metaEvent) {
		// ErlLogger.debug("handleMetaEvent " + metaEvent + " (" + metaPid +
		// ")");
		final OtpErlangAtom a = (OtpErlangAtom) metaEvent.elementAt(0);
		final String event = a.atomValue();
		final int what = getMetaWhat(event);
		if (what != META_UNKNOWN) {
			OtpErlangAtom mod = null;
			OtpErlangLong lineL = null;
			if (what == META_EXIT_AT) {
				final OtpErlangObject o = metaEvent.elementAt(1);
				if (o instanceof OtpErlangTuple) {
					final OtpErlangTuple t = (OtpErlangTuple) o;
					mod = (OtpErlangAtom) t.elementAt(0);
					lineL = (OtpErlangLong) t.elementAt(1);
				}
			} else {
				mod = (OtpErlangAtom) metaEvent.elementAt(1);
				lineL = (OtpErlangLong) metaEvent.elementAt(2);
			}
			int line = -1;
			if (lineL != null) {
				try {
					line = lineL.intValue();
				} catch (final OtpErlangRangeException e1) {
					ErlLogger.warn(e1);
				}
			}
			String module = null;
			if (mod != null) {
				module = mod.atomValue();
			}
			final OtpErlangPid pid;
			if (what == META_EXIT_AT) {
				pid = (OtpErlangPid) metaEvent.elementAt(4);
			} else {
				pid = getPidFromMeta(metaPid);
			}
			// ErlLogger.debug("  pid " + pid);
			ErlangProcess erlangProcess = getErlangProcess(pid);
			if (erlangProcess == null) {
				erlangProcess = createErlangProcess(pid);
			}
			if (module != null && line != -1) {
				if (what == META_BREAK_AT) {
					// FIXME det funkar inte att h�mta stack i wait-l�ge...
					// borde det g�? borde funka enligt dbg_ui_trace_win....
					// skit ocks�...
					erlangProcess.getStackAndBindings(module, line);
					if (erlangProcess.isStepping()) {
						erlangProcess.fireSuspendEvent(DebugEvent.STEP_END);
					} else {
						erlangProcess.fireSuspendEvent(DebugEvent.BREAKPOINT);
					}
					erlangProcess.setNotStepping();
				} else if (what == META_EXIT_AT) {
					if (metaEvent.arity() > 4) {
						final OtpErlangList erlStackFrames = (OtpErlangList) metaEvent
						        .elementAt(5);
						final OtpErlangList bs = (OtpErlangList) metaEvent
						        .elementAt(6);
						erlangProcess.addStackFrames(module, line,
						        erlStackFrames, bs);
					}
					erlangProcess.fireSuspendEvent(DebugEvent.BREAKPOINT);
				}
			}
		}
	}

	private int getMetaWhat(final String event) {
		if (event.equals("break_at")) {
			return META_BREAK_AT;
		} else if (event.equals("wait_at")) {
			return META_WAIT_AT;
		} else if (event.equals("exit_at")) {
			return META_EXIT_AT;
		} else {
			return META_UNKNOWN;
		}
	}

	private void handleIntEvent(final OtpErlangTuple intEvent) {
		final OtpErlangAtom a = (OtpErlangAtom) intEvent.elementAt(0);
		final String event = a.atomValue();
		if (event.equals("new_break")) {
			// TODO should we do anything here?
		} else if (event.equals("new_status")) {
			final OtpErlangPid pid = (OtpErlangPid) intEvent.elementAt(1);
			ErlangProcess erlangProcess = getErlangProcess(pid);
			if (erlangProcess == null) {
				erlangProcess = createErlangProcess(pid);
			}
			final OtpErlangAtom sa = (OtpErlangAtom) intEvent.elementAt(2);
			final String status = sa.atomValue();
			if (status.equals("break")) {
				erlangProcess.setStatus(status);
				if (!erlangProcess.isStepping()) {
					erlangProcess.fireSuspendEvent(DebugEvent.BREAKPOINT);
				}
			} else if (status.equals("exit")) {
				erlangProcess.setStatus(status);
				final OtpErlangAtom esa = (OtpErlangAtom) intEvent.elementAt(3);
				erlangProcess.setExitStatus(esa.atomValue());
				erlangProcess.fireChangeEvent(DebugEvent.STATE
				        | DebugEvent.CHANGE);
				// removeErlangProcess(pid);
				// erlangProcess.fireTerminateEvent();
			} else if (status.equals("running")) {
				erlangProcess.setStatus(status);
				if (erlangProcess.isStepping()) {
					erlangProcess.fireResumeEvent(DebugEvent.STEP_OVER);
				} else {
					erlangProcess.fireResumeEvent(DebugEvent.RESUME);
				}
			} else {
				erlangProcess.setStatus(status);
				erlangProcess.fireChangeEvent(DebugEvent.STATE
				        | DebugEvent.CHANGE);
			}
		} else if (event.equals("new_process")) {
			final OtpErlangTuple t = (OtpErlangTuple) intEvent.elementAt(1);
			final OtpErlangPid pid = (OtpErlangPid) t.elementAt(0);
			final ErlangProcess erlangProcess = createErlangProcess(pid);
			final OtpErlangAtom statusA = (OtpErlangAtom) t.elementAt(2);
			final String status = statusA.atomValue();
			erlangProcess.setStatus(status);
			final OtpErlangTuple initialCall = (OtpErlangTuple) t.elementAt(1);
			erlangProcess.setInitialCall(new ErlangFunctionCall(initialCall));
			erlangProcess.fireCreationEvent();
		} else if (event.equals("interpret")) {
			final OtpErlangAtom m = (OtpErlangAtom) intEvent.elementAt(1);
			interpretedModules.add(m.atomValue());
			fireEvent(new DebugEvent(this, DebugEvent.MODEL_SPECIFIC,
			        INTERPRETED_MODULES_CHANGED));
		} else if (event.equals("no_interpret")) {
			final OtpErlangAtom m = (OtpErlangAtom) intEvent.elementAt(1);
			interpretedModules.remove(m.atomValue());
			fireEvent(new DebugEvent(this, DebugEvent.MODEL_SPECIFIC,
			        INTERPRETED_MODULES_CHANGED));
		}
	}

	public Set<String> getInterpretedModules() {
		return interpretedModules;
	}

	private ErlangProcess createErlangProcess(final OtpErlangPid pid) {
		final String nodeName = pid.node();
		final IDebugTarget[] targets = getLaunch().getDebugTargets();
		for (final IDebugTarget debugTarget : targets) {
			try {
				if (debugTarget.getName().equals(nodeName)) {
					if (debugTarget instanceof IErlangDebugNode) {
						final IErlangDebugNode edn = (IErlangDebugNode) debugTarget;
						final ErlangProcess p = new ErlangProcess(debugTarget,
						        getBackend(), pid);
						edn.addErlangProcess(p);
						fAllProcesses.add(p);
						return p;
					}
				}
			} catch (final DebugException e) {
				e.printStackTrace();
			}
		}
		final ErlangProcess p = new ErlangProcess(this, getBackend(), pid);
		addErlangProcess(p);
		fAllProcesses.add(p);
		return p;
	}

	private ErlangProcess getErlangProcess(final OtpErlangPid pid) {
		for (int i = 0; i < fAllProcesses.size(); ++i) {
			final ErlangProcess p = fAllProcesses.get(i);
			if (p.getPid().equals(pid)) {
				return p;
			}
		}
		return null;
	}

	private void removeErlangProcess(final OtpErlangPid pid) {
		final ErlangProcess p = getErlangProcess(pid);
		if (p != null) {
			fAllProcesses.remove(p);
			removeErlangProcess(p);
			p.fireTerminateEvent();
		}
	}

	public void sendStarted() {
		ErlideDebug.sendStarted(fBackend, fBackend.getEventPid());
	}

	public OtpErlangPid getMetaFromPid(final OtpErlangPid pid) {
		return metaPids.get(pid);
	}

	public OtpErlangPid getPidFromMeta(final OtpErlangPid metaPid) {
		return pidsFromMeta.get(metaPid);
	}

	public void putMetaPid(final OtpErlangPid metaPid, final OtpErlangPid pid) {
		metaPids.put(pid, metaPid);
		pidsFromMeta.put(metaPid, pid);
	}

	class DebugEventHandler extends EventHandler {

		@Override
		protected void doHandleMsg(final OtpErlangObject msg) throws Exception {
			ErlLogger.debug("@@@ " + msg);
			// TODO More events from erlide_dbg_mon...
			final OtpErlangTuple t = (OtpErlangTuple) msg;
			final OtpErlangObject el0 = t.elementAt(0);
			if (el0 instanceof OtpErlangAtom) {
				final OtpErlangAtom a = (OtpErlangAtom) el0;
				final String tag = a.atomValue();
				if ("started".equals(tag)) {
					started();
				} else if ("terminated".equals(tag)) {
					terminate();
				} else if ("int".equals(tag)) {
					handleIntEvent((OtpErlangTuple) t.elementAt(1));
				} else if ("attached".equals(tag)) {
					final OtpErlangPid pid = (OtpErlangPid) t.elementAt(1);
					if (getMetaFromPid(pid) == null) {
						final OtpErlangPid self = fBackend.getEventPid();
						final OtpErlangPid metaPid = ErlideDebug.attached(
						        fBackend, pid, self);
						ErlLogger.debug("attached: " + pid + ",  meta: "
						        + metaPid);
						if (metaPid != null) {
							putMetaPid(metaPid, pid);
						}
					}
				} else {
					ErlLogger.debug("other event: " + msg);
				}
			} else if (el0 instanceof OtpErlangPid) { // meta event
				final OtpErlangPid pid = (OtpErlangPid) el0;
				final OtpErlangObject metaEvent = t.elementAt(1);
				if (metaEvent instanceof OtpErlangTuple) {
					final OtpErlangTuple metaEventTuple = (OtpErlangTuple) metaEvent;
					handleMetaEvent(pid, metaEventTuple);
				}
			}
		}
	}

	public Collection<IProject> getProjects() {
		return Collections.unmodifiableCollection(projects);
	}

	public void addErlangProcess(final ErlangProcess p) {
		fLocalProcesses.add(p);
	}

	public void removeErlangProcess(final ErlangProcess p) {
		fLocalProcesses.remove(p);
	}

	// public IDebugTarget getTargetForErlangProcess(
	// final ErlangProcess erlangProcess) {
	// final String nodeName = erlangProcess.getPid().node();
	// final IDebugTarget[] targets = getLaunch().getDebugTargets();
	// for (final IDebugTarget debugTarget : targets) {
	// try {
	// if (debugTarget.getName().equals(nodeName)) {
	// return debugTarget;
	// }
	// } catch (final DebugException e) {
	// }
	// }
	// return null;
	// }

	@Override
	public ErlangDebugTarget getErlangDebugTarget() {
		return this;
	}

}
