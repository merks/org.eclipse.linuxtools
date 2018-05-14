/*******************************************************************************
 * Copyright (c) 2015, 2018 Red Hat.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat - Initial Contribution
 *******************************************************************************/

package org.eclipse.linuxtools.internal.docker.ui.testutils.swt;

import static org.hamcrest.Matchers.notNullValue;

import org.assertj.core.api.AbstractAssert;
import org.eclipse.linuxtools.internal.docker.ui.views.DockerExplorerView;

/**
 * Custom assertions on an {@link DockerExplorerView}.
 */
public class DockerExplorerViewAssertions extends AbstractAssert<DockerExplorerViewAssertions, DockerExplorerView> {

	protected DockerExplorerViewAssertions(final DockerExplorerView actual) {
		super(actual, DockerExplorerViewAssertions.class);
	}

	public static DockerExplorerViewAssertions assertThat(final DockerExplorerView actual) {
		return new DockerExplorerViewAssertions(actual);
	}

	public DockerExplorerViewAssertions isEmpty() {
		notNullValue();
		try {
			// let's make sure changes in the UI were taken into account before
			// performing assertions
			Thread.sleep(200);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		if(actual.isShowingConnectionsPane()) {
			failWithMessage("Expected Docker Explorer View to show the explanation pane but it did not");
		}
		return this;
	}

	public DockerExplorerViewAssertions isNotEmpty() {
		notNullValue();
		if(!actual.isShowingConnectionsPane()) {
			failWithMessage("Expected Docker Explorer View to show the connections pane but it did not");
		}
		return this;
	}
}
