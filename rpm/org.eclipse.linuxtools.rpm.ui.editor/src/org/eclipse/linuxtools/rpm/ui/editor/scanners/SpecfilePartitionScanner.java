/*******************************************************************************
 * Copyright (c) 2007 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Red Hat - initial API and implementation
 *    Alphonse Van Assche
 *******************************************************************************/

package org.eclipse.linuxtools.rpm.ui.editor.scanners;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedPartitionScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.linuxtools.rpm.ui.editor.rules.SectionRule;

import static org.eclipse.linuxtools.rpm.ui.editor.RpmSections.*;

public class SpecfilePartitionScanner extends RuleBasedPartitionScanner {

	public final static String SPEC_PREP = "__spec_prep";
	public final static String SPEC_SCRIPT = "__spec_script";
	public final static String SPEC_FILES = "__spec_files";
	public final static String SPEC_CHANGELOG = "__spec_changelog";
	public final static String SPEC_PACKAGES = "__spec_packages";
	
	public static String[] SPEC_PARTITION_TYPES = { IDocument.DEFAULT_CONTENT_TYPE, SPEC_PREP, SPEC_SCRIPT,
			SPEC_FILES, SPEC_CHANGELOG, SPEC_PACKAGES};
	
	/** All possible headers for sections of the type SPEC_SCRIPT */
	private static String[] sectionHeaders = { BUILD_SECTION, INSTALL_SECTION, 
		PRETRANS_SECTION, PRE_SECTION, PREUN_SECTION, POST_SECTION, POSTUN_SECTION,
		POSTTRANS_SECTION, CLEAN_SECTION};

	/** All possible headers for section that can come after sections of the type SPEC_SCRIPT */
	private static String[] sectionEndingHeaders = { BUILD_SECTION, INSTALL_SECTION, 
		PRETRANS_SECTION, PRE_SECTION, PREUN_SECTION, POST_SECTION, POSTUN_SECTION, POSTTRANS_SECTION, 
		CLEAN_SECTION, FILES_SECTION};
	
	public SpecfilePartitionScanner() {
		// FIXME:  do we need this?
		super();
		
		IToken specPrep = new Token(SPEC_PREP);
		IToken specScript = new Token(SPEC_SCRIPT);
		IToken specFiles = new Token(SPEC_FILES);
		IToken specChangelog = new Token(SPEC_CHANGELOG);
		IToken specPackages = new Token(SPEC_PACKAGES);
		
		List<IRule> rules = new ArrayList<IRule>();
		
		// RPM packages
		for (String packageTag :SpecfilePackagesScanner.PACKAGES_TAGS) {
			rules.add(new SingleLineRule(packageTag, "", specPackages, (char)0 , true));		
		}
		
		// %prep
		rules.add(new SectionRule("%prep", new String[] { "%build" }, specPrep));
		
		// %changelog
		rules.add(new MultiLineRule("%changelog", "", specChangelog, (char)0 , true));
		
		// "%build", "%install", "%pre", "%preun", "%post", "%postun"
		for (String sectionHeader : sectionHeaders)
			rules.add(new SectionRule(sectionHeader, sectionEndingHeaders, specScript));

		// comments
		rules.add(new EndOfLineRule("#", specScript));
		
		
		
		// %files
		rules.add(new SectionRule("%files", new String[] { "%files",
				"%changelog" }, specFiles));
		
		IPredicateRule[] result= new IPredicateRule[rules.size()];
		rules.toArray(result);
		setPredicateRules(result);
	}
}
