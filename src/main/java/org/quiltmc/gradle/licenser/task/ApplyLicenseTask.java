/*
 * Copyright 2022 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.gradle.licenser.task;

import org.eclipse.jgit.api.Git;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jetbrains.annotations.ApiStatus;
import org.quiltmc.gradle.licenser.api.license.LicenseHeader;
import org.quiltmc.gradle.licenser.api.util.GitUtils;
import org.quiltmc.gradle.licenser.extension.QuiltLicenserGradleExtension;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;

@ApiStatus.Internal
public class ApplyLicenseTask extends JavaSourceBasedTask {
	private final LicenseHeader licenseHeader;
	private final WorkerExecutor executor;

	@Inject
	public ApplyLicenseTask(SourceSet sourceSet, QuiltLicenserGradleExtension extension, WorkerExecutor workerExecutor) {
		super(sourceSet, extension.asPatternFilterable());
		this.licenseHeader = extension.getLicenseHeader();
		this.executor = workerExecutor;
		this.setDescription("Applies the correct license headers to source files in the " + sourceSet.getName() + " source set.");
		this.setGroup("generation");

		if (!this.licenseHeader.isValid()) {
			this.setEnabled(false);
		}
	}

	@TaskAction
	public void execute() {
		File rootPath = this.getProject().getRootProject().getRootDir();
		File projectPath = this.getProject().getRootDir();
		File backupFolder = new File(getProject().getBuildDir(), "licenser-backup");
		ListProperty<File> updatedFiles = getProject().getObjects().listProperty(File.class);

		LicenseHeader header = this.licenseHeader;
		WorkQueue queue = this.executor.noIsolation();
		int total = 0;
		try {
			GitUtils.gits.put(rootPath, Git.open(rootPath));
		} catch (IOException e) {
			throw new GradleException("Failed to open git repository at " + rootPath, e);
		}
		for (var sourceFile : this.sourceSet.getAllJava().matching(this.patternFilterable)) {
			queue.submit(Consumer.class, parameters -> {
				parameters.getRootPath().set(rootPath);
				parameters.getProjectPath().set(projectPath);
				parameters.getBackupFolder().set(backupFolder);
				parameters.getLicenseHeader().set(header);
				parameters.getSourceFile().set(sourceFile);
				parameters.getSuccessfulFiles().set(updatedFiles);
			});
			total++;
		}
		queue.await();

		GitUtils.gits.get(rootPath).close();
		for (var path : updatedFiles.get()) {
			getProject().getLogger().lifecycle(" - Updated file {}", path);
		}

		getProject().getLogger().lifecycle("Updated {} out of {} files.", updatedFiles.get().size(), total);
	}

	public abstract static class Consumer implements WorkAction<ApplyLicenseWorkParameters> {
		@Override
		public void execute() {
			try {
				File visiting = getParameters().getSourceFile().get().getAsFile();
	//            if (QuiltLicenserGradlePlugin.DEBUG_MODE) {
	//                getLogger().lifecycle("=> Visiting {}...", visiting);
	//            }

				if (this.getParameters().getLicenseHeader().get().format(this.getParameters())) {
					this.getParameters().getSuccessfulFiles().add(visiting);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	public static interface ApplyLicenseWorkParameters extends WorkParameters {
		/**
		 * The folder of the root project.
		 */
		RegularFileProperty getRootPath();

		/**
		 * The folder of the project directly containing the file to be licensed.
		 */
		RegularFileProperty getProjectPath();

		/**
		 * The file to be licensed.
		 */
		RegularFileProperty getSourceFile();

		/**
		 * The backup folder for files before they were licensed.
		 */
		RegularFileProperty getBackupFolder();

		Property<LicenseHeader> getLicenseHeader();

		ListProperty<File> getSuccessfulFiles();
	}
}
