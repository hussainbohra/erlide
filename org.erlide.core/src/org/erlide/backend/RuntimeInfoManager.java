/*******************************************************************************
 * Copyright (c) 2008 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.backend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.erlide.core.ErlangPlugin;
import org.erlide.core.preferences.PreferencesUtils;
import org.erlide.jinterface.util.ErlLogger;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

public final class RuntimeInfoManager implements IPreferenceChangeListener {

	private RuntimeInfo erlideRuntime;

	private RuntimeInfoManager() {
		getRootPreferenceNode().addPreferenceChangeListener(this);
		load();
	}

	@SuppressWarnings("synthetic-access")
	private static class LazyRuntimeInfoManagerHolder {
		public static final RuntimeInfoManager instance = new RuntimeInfoManager();
	}

	public static synchronized RuntimeInfoManager getDefault() {
		return LazyRuntimeInfoManagerHolder.instance;
	}

	private final Map<String, RuntimeInfo> fRuntimes = new HashMap<String, RuntimeInfo>();
	private String defaultRuntimeName = "";

	private final List<RuntimeInfoListener> fListeners = new ArrayList<RuntimeInfoListener>();

	public Collection<RuntimeInfo> getRuntimes() {
		return new ArrayList<RuntimeInfo>(fRuntimes.values());
	}

	public synchronized void store() {
		IEclipsePreferences root = getRootPreferenceNode();
		try {
			root.removePreferenceChangeListener(this);
			root.removeNode();
			root = getRootPreferenceNode();

			for (final RuntimeInfo rt : fRuntimes.values()) {
				rt.store(root);
			}
			if (defaultRuntimeName != null) {
				root.put("default", defaultRuntimeName);
			}
			if (erlideRuntime != null) {
				root.put("erlide", erlideRuntime.getName());
			}
			try {
				root.flush();
			} catch (final BackingStoreException e) {
				ErlLogger.warn(e);
			}
			root.addPreferenceChangeListener(this);
		} catch (final BackingStoreException e) {
			ErlLogger.warn(e);
		}
	}

	public synchronized void load() {
		fRuntimes.clear();

		loadDefaultPrefs();

		// TODO remove this later
		final String OLD_NAME = "erts";
		final IEclipsePreferences old = new InstanceScope()
				.getNode("org.erlide.basic/");
		final String oldVal = old.get("otp_home", null);
		if (oldVal != null) {
			ErlLogger.debug("** converting old workspace Erlang settings");

			final RuntimeInfo rt = new RuntimeInfo();
			final IWorkspaceRoot wroot = ResourcesPlugin.getWorkspace()
					.getRoot();
			final String location = wroot.getLocation().toPortableString();
			rt.setWorkingDir(location);
			rt.setOtpHome(oldVal);
			rt.setName(OLD_NAME);
			rt.setNodeName(rt.getName());
			addRuntime(rt);
			setDefaultRuntime(OLD_NAME);
			old.remove("otp_home");
			try {
				old.flush();
			} catch (final Exception e) {
				ErlLogger.warn(e);
			}
			store();
		}
		//

		// TODO remove this later
		IEclipsePreferences root = new InstanceScope()
				.getNode("org.erlide.launching/runtimes");
		loadPrefs(root);
		try {
			final Preferences p = root.parent();
			root.removeNode();
			p.flush();
		} catch (final Exception e) {
			ErlLogger.warn(e);
		}
		//

		root = getRootPreferenceNode();
		loadPrefs(root);
	}

	private void loadDefaultPrefs() {
		final IPreferencesService ps = Platform.getPreferencesService();
		final String DEFAULT_ID = "org.erlide";

		final String defName = ps.getString(DEFAULT_ID, "default_name", null,
				null);
		final RuntimeInfo runtime = getRuntime(defName);
		if (defName != null && runtime == null) {
			final RuntimeInfo rt = new RuntimeInfo();
			rt.setName(defName);
			final String path = ps.getString(DEFAULT_ID, "default_"
					+ RuntimeInfo.CODE_PATH, "", null);
			rt.setCodePath(PreferencesUtils.unpackList(path));
			rt.setOtpHome(ps.getString(DEFAULT_ID, "default_"
					+ RuntimeInfo.HOME_DIR, "", null));
			rt.setArgs(ps.getString(DEFAULT_ID, "default_" + RuntimeInfo.ARGS,
					"", null));
			final String wd = ps.getString(DEFAULT_ID, "default_"
					+ RuntimeInfo.WORKING_DIR, "", null);
			if (wd.length() != 0) {
				rt.setWorkingDir(wd);
			}
			rt.setManaged(ps.getBoolean(DEFAULT_ID, "default_"
					+ RuntimeInfo.MANAGED, true, null));
			addRuntime(rt);
		}
		defaultRuntimeName = defName;
	}

	private void loadPrefs(final IEclipsePreferences root) {
		final String defrt = root.get("default", null);
		if (defrt != null) {
			defaultRuntimeName = defrt;
		}

		String[] children;
		try {
			children = root.childrenNames();
			for (final String name : children) {
				final RuntimeInfo rt = new RuntimeInfo();
				rt.load(root.node(name));
				fRuntimes.put(name, rt);
			}
		} catch (final BackingStoreException e) {
			ErlLogger.warn(e);
		}

		if (getDefaultRuntime() == null) {
			if (defaultRuntimeName == null && fRuntimes.size() > 0) {
				defaultRuntimeName = fRuntimes.values().iterator().next()
						.getName();
			}
		}
		final RuntimeInfo rt = getRuntime(root.get("erlide", null));
		setErlideRuntime(rt == null ? getDefaultRuntime() : rt);
	}

	protected IEclipsePreferences getRootPreferenceNode() {
		return new InstanceScope()
				.getNode(ErlangPlugin.PLUGIN_ID + "/runtimes");
	}

	public void setRuntimes(final Collection<RuntimeInfo> elements) {
		fRuntimes.clear();
		for (final RuntimeInfo rt : elements) {
			fRuntimes.put(rt.getName(), rt);
		}
		notifyListeners();
	}

	public void addRuntime(final RuntimeInfo rt) {
		if (!fRuntimes.containsKey(rt.getName())) {
			fRuntimes.put(rt.getName(), rt);
		}
		notifyListeners();
	}

	public Collection<String> getRuntimeNames() {
		return fRuntimes.keySet();
	}

	public boolean isDuplicateName(final String name) {
		for (final RuntimeInfo vm : fRuntimes.values()) {
			if (vm.getName().equals(name)) {
				return true;
			}
		}
		return false;
	}

	public RuntimeInfo getRuntime(final String name) {
		final RuntimeInfo rt = fRuntimes.get(name);
		return rt;
	}

	public void removeRuntime(final String name) {
		fRuntimes.remove(name);
		notifyListeners();
	}

	public String getDefaultRuntimeName() {
		return this.defaultRuntimeName;
	}

	public void setDefaultRuntime(final String name) {
		this.defaultRuntimeName = name;
		notifyListeners();
	}

	public void setErlideRuntime(final RuntimeInfo runtime) {
		if (runtime != null) {
			runtime.setNodeName("erlide");
		}
		final RuntimeInfo old = this.erlideRuntime;
		if (old == null || !old.equals(runtime)) {
			this.erlideRuntime = runtime;
			notifyListeners();
			// this creates infinite recursion!
			// BackendManager.getDefault().getIdeBackend().stop();
		}
	}

	public RuntimeInfo getErlideRuntime() {
		if (erlideRuntime == null) {
			RuntimeInfo ri = null;
			final Iterator<RuntimeInfo> iterator = getRuntimes().iterator();
			if (iterator.hasNext()) {
				ri = iterator.next();
			}
			if (ri != null) {
				setErlideRuntime(ri);
			} else {
				ErlLogger.error("There is no erlideRuntime defined!");
			}
		}
		return erlideRuntime;
	}

	public RuntimeInfo getDefaultRuntime() {
		return getRuntime(getDefaultRuntimeName());
	}

	public void preferenceChange(final PreferenceChangeEvent event) {
		if (event.getNode().absolutePath().contains("org.erlide")) {
			load();
		}
	}

	public void addListener(final RuntimeInfoListener listener) {
		if (!fListeners.contains(listener)) {
			fListeners.add(listener);
		}
	}

	public void removeListener(final RuntimeInfoListener listener) {
		fListeners.remove(listener);
	}

	private void notifyListeners() {
		for (final RuntimeInfoListener listener : fListeners) {
			listener.infoChanged();
		}
	}

	/**
	 * Locate runtimes with this version or newer. If exact matches exists, they
	 * are first in the result list. A null or empty version returns all
	 * runtimes.
	 */
	public List<RuntimeInfo> locateVersion(final String version) {
		final RuntimeVersion vsn = new RuntimeVersion(version);
		return locateVersion(vsn);
	}

	public List<RuntimeInfo> locateVersion(final RuntimeVersion vsn) {
		final List<RuntimeInfo> result = new ArrayList<RuntimeInfo>();
		for (final RuntimeInfo info : getRuntimes()) {
			final RuntimeVersion v = info.getVersion();
			if (v.equals(vsn)) {
				result.add(info);
			}
		}
		// add even newer versions, but at the end
		for (final RuntimeInfo info : getRuntimes()) {
			final RuntimeVersion v = info.getVersion();
			if (!result.contains(info) && v.compareTo(vsn) > 0) {
				result.add(info);
			}
		}
		return result;
	}

	public RuntimeInfo getRuntime(final RuntimeVersion runtimeVersion,
			final String runtimeName) {
		final List<RuntimeInfo> vsns = locateVersion(runtimeVersion);
		if (vsns.size() == 0) {
			return null;
		} else if (vsns.size() == 1) {
			return vsns.get(0);
		} else {
			for (final RuntimeInfo ri : vsns) {
				if (ri.getName().equals(runtimeName)) {
					return ri;
				}
			}
			return vsns.get(0);
		}
	}
}