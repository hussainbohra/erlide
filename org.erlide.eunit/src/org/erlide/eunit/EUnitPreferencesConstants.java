package org.erlide.eunit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.InstanceScope;

/**
 * Defines constants which are used to refer to values in the plugin's
 * preference store.
 */
public class EUnitPreferencesConstants {
	/**
	 * Boolean preference controlling whether the failure stack should be
	 * filtered.
	 */
	public static final String DO_FILTER_STACK = EUnitPlugin.PLUGIN_ID
			+ ".do_filter_stack"; //$NON-NLS-1$

	/**
	 * Boolean preference controlling whether the JUnit view should be shown on
	 * errors only.
	 */
	public static final String SHOW_ON_ERROR_ONLY = EUnitPlugin.PLUGIN_ID
			+ ".show_on_error"; //$NON-NLS-1$

	/**
	 * Boolean preference controlling whether the JUnit view should be shown on
	 * errors only.
	 */
	public static final String ENABLE_ASSERTIONS = EUnitPlugin.PLUGIN_ID
			+ ".enable_assertions"; //$NON-NLS-1$

	/**
	 * List of active stack filters. A String containing a comma separated list
	 * of fully qualified type names/patterns.
	 */
	public static final String PREF_ACTIVE_FILTERS_LIST = EUnitPlugin.PLUGIN_ID
			+ ".active_filters"; //$NON-NLS-1$

	/**
	 * List of inactive stack filters. A String containing a comma separated
	 * list of fully qualified type names/patterns.
	 */
	public static final String PREF_INACTIVE_FILTERS_LIST = EUnitPlugin.PLUGIN_ID
			+ ".inactive_filters"; //$NON-NLS-1$

	/**
	 * Maximum number of remembered test runs.
	 */
	public static final String MAX_TEST_RUNS = EUnitPlugin.PLUGIN_ID
			+ ".max_test_runs"; //$NON-NLS-1$

	/**
	 * Javadoc location for JUnit 3
	 */
	public static final String JUNIT3_JAVADOC = EUnitPlugin.PLUGIN_ID
			+ ".junit3.javadoclocation"; //$NON-NLS-1$

	/**
	 * Javadoc location for JUnit 4
	 */
	public static final String JUNIT4_JAVADOC = EUnitPlugin.PLUGIN_ID
			+ ".junit4.javadoclocation"; //$NON-NLS-1$

	/**
	 * Javadoc location for org.hamcrest.core (JUnit 4)
	 */
	public static final String HAMCREST_CORE_JAVADOC = EUnitPlugin.PLUGIN_ID
			+ ".junit4.hamcrest.core.javadoclocation"; //$NON-NLS-1$

	private static final String[] fgDefaultFilterPatterns = new String[] {
			"org.eclipse.jdt.internal.junit.runner.*", //$NON-NLS-1$
			"org.eclipse.jdt.internal.junit4.runner.*", //$NON-NLS-1$
			"org.eclipse.jdt.internal.junit.ui.*", //$NON-NLS-1$
			"junit.framework.TestCase", //$NON-NLS-1$
			"junit.framework.TestResult", //$NON-NLS-1$
			"junit.framework.TestResult$1", //$NON-NLS-1$
			"junit.framework.TestSuite", //$NON-NLS-1$
			"junit.framework.Assert", //$NON-NLS-1$
			"org.junit.*", //$NON-NLS-1$ //TODO: filter all these?
			"java.lang.reflect.Method.invoke", //$NON-NLS-1$
			"sun.reflect.*", //$NON-NLS-1$
	};

	private static final String[] EMPTY_STRING_LIST = new String[0];

	private EUnitPreferencesConstants() {
		// no instance
	}

	/**
	 * Returns the default list of active stack filters.
	 * 
	 * @return list
	 */
	public static List<String> createDefaultStackFiltersList() {
		return Arrays.asList(fgDefaultFilterPatterns);
	}

	/**
	 * Serializes the array of strings into one comma-separated string.
	 * 
	 * @param list
	 *            array of strings
	 * @return a single string composed of the given list
	 */
	public static String serializeList(final String[] list) {
		if (list == null) {
			return ""; //$NON-NLS-1$
		}

		final StringBuffer buffer = new StringBuffer();
		for (int i = 0; i < list.length; i++) {
			if (i > 0) {
				buffer.append(',');
			}

			buffer.append(list[i]);
		}
		return buffer.toString();
	}

	/**
	 * Parses the comma-separated string into an array of strings.
	 * 
	 * @param listString
	 *            a comma-separated string
	 * @return an array of strings
	 */
	public static String[] parseList(final String listString) {
		if (listString == null) {
			return EMPTY_STRING_LIST;
		}
		final List<String> list = new ArrayList<String>(10);
		final StringTokenizer tokenizer = new StringTokenizer(listString, ","); //$NON-NLS-1$
		while (tokenizer.hasMoreTokens()) {
			list.add(tokenizer.nextToken());
		}
		return list.toArray(new String[list.size()]);
	}

	public static String[] getFilterPatterns() {
		return EUnitPreferencesConstants.parseList(Platform
				.getPreferencesService().getString(EUnitPlugin.PLUGIN_ID,
						PREF_ACTIVE_FILTERS_LIST, null, null));
	}

	public static boolean getFilterStack() {
		return Platform.getPreferencesService().getBoolean(
				EUnitPlugin.PLUGIN_ID, DO_FILTER_STACK, true, null);
	}

	public static void setFilterStack(final boolean filter) {
		new InstanceScope().getNode(EUnitPlugin.PLUGIN_ID).putBoolean(
				DO_FILTER_STACK, filter);
	}
}
