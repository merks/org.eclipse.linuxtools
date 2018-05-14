/*******************************************************************************
 * Copyright (c) 2014, 2018 Red Hat Inc. and others.
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

package org.eclipse.linuxtools.internal.docker.ui.views;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.linuxtools.docker.core.IDockerConnection;
import org.eclipse.linuxtools.docker.core.IDockerImage;
import org.eclipse.linuxtools.internal.docker.core.DockerConnection;
import org.eclipse.swt.widgets.Display;

public class DockerImagesContentProvider implements ITreeContentProvider{

	private static final Object[] EMPTY = new Object[0];
	private TableViewer viewer;
	
	@Override
	public void dispose() {
	}

	@Override
	public void inputChanged(final Viewer viewer, final Object oldInput, final Object newInput) {
		this.viewer = (TableViewer)viewer;
	}

	@Override
	public Object[] getElements(final Object inputElement) {
		if(inputElement instanceof IDockerConnection) {
			final IDockerConnection connection = (IDockerConnection)inputElement;
			if (connection.isImagesLoaded()) {
				final List<IDockerImage> images = connection.getImages();
				if(images == null) {
					return EMPTY;
				}
				return images.toArray();
			}
			loadImages(connection);
			return EMPTY;
		} 
		return EMPTY;
	}
	
	/**
	 * Call the {@link IDockerConnection#getImages(boolean)} in a background job
	 * to avoid blocking the UI.
	 * 
	 * @param connection
	 *            the selected {@link DockerConnection}
	 */
	private void loadImages(final IDockerConnection connection) {
		final Job loadImagesJob = new Job(DVMessages
				.getFormattedString("ImagesLoadJob.msg", connection.getUri())) {
			@Override
			protected IStatus run(final IProgressMonitor monitor) {
				connection.getImages(true);
				Display.getDefault().asyncExec(() -> {
					viewer.refresh();
				});
				return Status.OK_STATUS;
			}
		};
		loadImagesJob.schedule();
	}
	
	@Override
	public Object[] getChildren(Object parentElement) {
		return getElements(parentElement);
	}

	@Override
	public Object getParent(Object element) {
		return null;
	}

	@Override
	public boolean hasChildren(Object element) {
		return false;
	}

}
