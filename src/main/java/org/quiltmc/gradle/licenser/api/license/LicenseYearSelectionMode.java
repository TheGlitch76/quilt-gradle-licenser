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

package org.quiltmc.gradle.licenser.api.license;

import org.quiltmc.gradle.licenser.api.util.GitUtils;

import java.nio.file.Path;

/**
 * Represents the mode in which the year should be fetched.
 *
 * @author LambdAurora
 * @version 1.0.0
 * @since 1.0.0
 */
public enum LicenseYearSelectionMode {
	SUBPROJECT,
	PROJECT,
	FILE;
	/**
	 * Gets the last modification year in which the file got modified.
	 * <p>
	 * In the case of {@link #SUBPROJECT} the last modification year isn't file dependent.
	 *
	 * @param rootPath the root project the file is in
	 * @param projectPath the project the file is in
	 * @param path the path to the file
	 * @return the last modification year
	 */

	public int getYear(Path rootPath, Path projectPath, Path path) {
		Path commitPath;
		if (this == SUBPROJECT) {
			commitPath = projectPath;
		} else if (this == PROJECT) {
			commitPath = rootPath;
		} else {
			commitPath = path;
		}

		return GitUtils.getModificationYear(rootPath, commitPath);
	}
}
