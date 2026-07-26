package com.condation.cms.modules.forms;

/*-
 * #%L
 * forms-module
 * %%
 * Copyright (C) 2024 CondationCMS
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */


import com.condation.cms.api.feature.features.DBFeature;
import com.condation.cms.api.module.SiteModuleContext;
import com.condation.modules.api.ModuleLifeCycleExtension;
import com.condation.modules.api.annotation.Extension;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author t.marx
 */
@Extension(ModuleLifeCycleExtension.class)
public class FormsLifecycleExtension extends ModuleLifeCycleExtension<SiteModuleContext> {

	@Override
	public void init() {
	}

	@Override
	public void activate() {
		Path formsConfig = getContext().get(DBFeature.class).db().getFileSystem().resolve("config/forms.yaml");
		try {
			var config = new Yaml().loadAs(
					Files.readString(formsConfig, StandardCharsets.UTF_8),
					FormsConfig.class);
			if (config == null) {
				throw new IllegalArgumentException("forms.yaml is empty");
			}
			config.validate();
			getContext().add(FormsFeature.class, new FormsFeature(config));
		} catch (IOException | RuntimeException ex) {
			System.getLogger(getClass().getName()).log(
					System.Logger.Level.ERROR,
					"Could not activate forms module: invalid config " + formsConfig,
					ex);
			throw new IllegalStateException("Could not load forms configuration", ex);
		}
	}

}
