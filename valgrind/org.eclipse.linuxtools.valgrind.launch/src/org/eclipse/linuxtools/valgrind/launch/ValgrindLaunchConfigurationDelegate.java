/*******************************************************************************
 * Copyright (c) 2008 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Elliott Baron <ebaron@redhat.com> - initial API and implementation
 *******************************************************************************/ 
package org.eclipse.linuxtools.valgrind.launch;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.eclipse.cdt.debug.core.ICDTLaunchConfigurationConstants;
import org.eclipse.cdt.launch.AbstractCLaunchDelegate;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IPersistableSourceLocator;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.linuxtools.valgrind.core.ValgrindCommand;
import org.eclipse.linuxtools.valgrind.core.utils.LaunchConfigurationConstants;
import org.eclipse.linuxtools.valgrind.history.HistoryFile;
import org.eclipse.linuxtools.valgrind.history.IValgrindPersistable;
import org.eclipse.linuxtools.valgrind.history.MementoConstants;
import org.eclipse.linuxtools.valgrind.history.ValgrindHistoryPlugin;
import org.eclipse.linuxtools.valgrind.ui.ValgrindUIPlugin;
import org.eclipse.ui.XMLMemento;

public class ValgrindLaunchConfigurationDelegate extends AbstractCLaunchDelegate {

	protected static final String EMPTY_STRING = ""; //$NON-NLS-1$
	protected static final String NO = "no"; //$NON-NLS-1$
	protected static final String YES = "yes"; //$NON-NLS-1$
	protected static final String EQUALS = "="; //$NON-NLS-1$

	protected static final String LOG_PREFIX = "valgrind_"; //$NON-NLS-1$
	protected static final String LOG_FILE = LOG_PREFIX + "%p.xml"; //$NON-NLS-1$
	protected static final FileFilter LOG_FILTER = new FileFilter() {
		public boolean accept(File pathname) {
			return pathname.getName().startsWith(LOG_PREFIX);
		}		
	};
	
	protected String toolID;
	protected ValgrindCommand command;
	protected IValgrindLaunchDelegate dynamicDelegate;
	protected ILaunchConfiguration config;
	protected ILaunch launch;
	protected IProcess process;
	protected String launchStr;

	public void launch(ILaunchConfiguration config, String mode,
			ILaunch launch, IProgressMonitor m) throws CoreException {
		if (m == null) {
			m = new NullProgressMonitor();
		}
		SubMonitor monitor = SubMonitor.convert(m, Messages.getString("ValgrindLaunchConfigurationDelegate.Profiling_Local_CCPP_Application"), 10); //$NON-NLS-1$
		// check for cancellation
		if (monitor.isCanceled()) {
			return;
		}

		this.config = config;
		this.launch	= launch;
		try {
			command = new ValgrindCommand(HistoryFile.getInstance().createOutputDir());
			monitor.worked(1);
			IPath exePath = verifyProgramPath(config);

			String[] arguments = getProgramArgumentsArray(config);
			
			// tool that was launched
			toolID = getTool(config);
			// ask tool extension for arguments
			dynamicDelegate = ValgrindLaunchPlugin.getDefault().getToolDelegate(toolID);
			String[] opts = getValgrindArgumentsArray(config);

			// set the default source locator if required
			setDefaultSourceLocator(launch, config);

			File wd = getWorkingDirectory(config);
			if (wd == null) {
				wd = new File(System.getProperty("user.home", ".")); //$NON-NLS-1$ //$NON-NLS-2$
			}
			
			ArrayList<String> cmdLine = new ArrayList<String>(1 + arguments.length);
			cmdLine.add(ValgrindCommand.VALGRIND_CMD);
			cmdLine.addAll(Arrays.asList(opts));
			cmdLine.add(exePath.toOSString());
			cmdLine.addAll(Arrays.asList(arguments));
			String[] commandArray = (String[]) cmdLine.toArray(new String[cmdLine.size()]);
			boolean usePty = config.getAttribute(ICDTLaunchConfigurationConstants.ATTR_USE_TERMINAL, ICDTLaunchConfigurationConstants.USE_TERMINAL_DEFAULT);
			monitor.worked(3);

			// call Valgrind
			command.execute(commandArray, getEnvironment(config), wd, usePty);
			monitor.worked(3);
			process = DebugPlugin.newProcess(launch, command.getProcess(), renderProcessLabel(commandArray[0]));
			
			// create launch summary string to distinguish this launch
			launchStr = createLaunchStr();
			
			// create view
			ValgrindUIPlugin.getDefault().createView(launchStr, toolID);
			
			// pass off control to extender
			dynamicDelegate.launch(command, config, launch, monitor.newChild(3));
			
			// refresh view
			ValgrindUIPlugin.getDefault().refreshView();
			
			// save results of launch to persistent storage
			saveState();
		} catch (IOException e) {
			abort(Messages.getString("ValgrindLaunchConfigurationDelegate.Error_starting_process"), e, ICDTLaunchConfigurationConstants.ERR_INTERNAL_ERROR); //$NON-NLS-1$
		} finally {
			monitor.done();			
		}
	}

	protected String createLaunchStr() {
		return config.getName() + " [" + ValgrindLaunchPlugin.getDefault().getToolName(toolID) + "] " + process.getLabel(); //$NON-NLS-1$ //$NON-NLS-2$
	}

	protected String[] getValgrindArgumentsArray(ILaunchConfiguration config) throws CoreException, IOException {
		ArrayList<String> opts = new ArrayList<String>();
		opts.add(ValgrindCommand.OPT_TOOL + EQUALS + ValgrindLaunchPlugin.getDefault().getToolName(toolID));

		opts.add(ValgrindCommand.OPT_LOGFILE + EQUALS + command.getDatadir().getCanonicalPath() + File.separator + LOG_FILE);

		opts.add(ValgrindCommand.OPT_TRACECHILD + EQUALS + (config.getAttribute(LaunchConfigurationConstants.ATTR_GENERAL_TRACECHILD, false) ? YES : NO));
		opts.add(ValgrindCommand.OPT_CHILDSILENT + EQUALS + YES);//(config.getAttribute(ValgrindLaunchPlugin.ATTR_GENERAL_CHILDSILENT, false) ? YES : NO));
		//		opts.add(ValgrindCommand.OPT_TRACKFDS + EQUALS + (config.getAttribute(ValgrindLaunchPlugin.ATTR_GENERAL_TRACKFDS, false) ? YES : NO));
		//		opts.add(ValgrindCommand.OPT_TIMESTAMP + EQUALS + (config.getAttribute(ValgrindLaunchPlugin.ATTR_GENERAL_TIMESTAMP, false) ? YES : NO));
		opts.add(ValgrindCommand.OPT_FREERES + EQUALS + (config.getAttribute(LaunchConfigurationConstants.ATTR_GENERAL_FREERES, true) ? YES : NO));

		opts.add(ValgrindCommand.OPT_DEMANGLE + EQUALS + (config.getAttribute(LaunchConfigurationConstants.ATTR_GENERAL_DEMANGLE, true) ? YES : NO));
		opts.add(ValgrindCommand.OPT_NUMCALLERS + EQUALS + config.getAttribute(LaunchConfigurationConstants.ATTR_GENERAL_NUMCALLERS, 12));
		opts.add(ValgrindCommand.OPT_ERRLIMIT + EQUALS + (config.getAttribute(LaunchConfigurationConstants.ATTR_GENERAL_ERRLIMIT, true) ? YES : NO));
		opts.add(ValgrindCommand.OPT_BELOWMAIN + EQUALS + (config.getAttribute(LaunchConfigurationConstants.ATTR_GENERAL_BELOWMAIN, false) ? YES : NO));
		opts.add(ValgrindCommand.OPT_MAXFRAME + EQUALS + config.getAttribute(LaunchConfigurationConstants.ATTR_GENERAL_MAXFRAME, 2000000));

		String strpath = config.getAttribute(LaunchConfigurationConstants.ATTR_GENERAL_SUPPFILE, EMPTY_STRING);
		if (!strpath.equals(EMPTY_STRING)) {
			File suppfile = ValgrindLaunchPlugin.getDefault().parseWSPath(strpath);
			if (suppfile != null) {
				String escapedPath = ValgrindLaunchPlugin.getDefault().escapeAndQuote(suppfile.getCanonicalPath());
				opts.add(ValgrindCommand.OPT_SUPPFILE + EQUALS + escapedPath);
			}
		}

		opts.addAll(Arrays.asList(dynamicDelegate.getCommandArray(command, config)));		

		String[] ret = new String[opts.size()];
		return opts.toArray(ret);
	}
	
	protected void saveState() throws CoreException, IOException {
		XMLMemento memento = XMLMemento.createWriteRoot(MementoConstants.ELEMENT_ROOT);
		memento.putString(MementoConstants.ELEMENT_CONFIG, config.getMemento());
		memento.putString(MementoConstants.ELEMENT_LOCATOR, ((IPersistableSourceLocator) launch.getSourceLocator()).getMemento());
		memento.putString(MementoConstants.ELEMENT_LABEL, process.getLabel());
		memento.putString(MementoConstants.ELEMENT_DATADIR, command.getDatadir().getCanonicalPath());
		
		IValgrindPersistable persistable = ValgrindHistoryPlugin.getDefault().getPersistable(toolID);
		if (persistable != null) {
			persistable.saveState(memento);
		}
		
		// write launch history to history file
		HistoryFile.getInstance().write(launchStr, memento, command.getDatadir());
	}

	protected String getTool(ILaunchConfiguration config) throws CoreException {
		return config.getAttribute(LaunchConfigurationConstants.ATTR_TOOL, ValgrindLaunchPlugin.TOOL_EXT_DEFAULT);
	}


	@Override
	protected String getPluginID() {
		return ValgrindLaunchPlugin.PLUGIN_ID;
	}
	
}
