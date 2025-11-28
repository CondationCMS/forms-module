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
import com.condation.cms.api.module.SiteRequestContext;
import com.condation.modules.api.ModuleLifeCycleExtension;
import com.condation.modules.api.annotation.Extension;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author t.marx
 */
@Slf4j
@Extension(ModuleLifeCycleExtension.class)
public class FormsLifecycleExtension extends ModuleLifeCycleExtension<SiteModuleContext, SiteRequestContext> {

	public static Cache<String, String> CAPTCHAS;
	public static FormsConfig FORMSCONFIG;

	@Override
	public void init() {

	}

	@Override
	public void activate() {
		CAPTCHAS = Caffeine.newBuilder()
				.maximumSize(10_000)
				.expireAfterWrite(Duration.ofMinutes(5))
				.build();
		
		Path formsConfig = getContext().get(DBFeature.class).db().getFileSystem().resolve("config/forms.yaml");
		try {
			FORMSCONFIG = new Yaml().loadAs(Files.readString(formsConfig, StandardCharsets.UTF_8), FormsConfig.class);
		} catch (IOException ex) {
			log.error(null, ex);
		}
	}

}
