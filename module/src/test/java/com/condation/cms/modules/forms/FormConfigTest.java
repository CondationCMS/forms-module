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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author t.marx
 */
public class FormConfigTest {
	
	@Test
	void test_config () throws Exception {
		var FORMSCONFIG = new Yaml().loadAs(
				Files.readString(Path.of("src/test/resources/config/forms.yaml"), StandardCharsets.UTF_8), FormsConfig.class);
		FORMSCONFIG.validate();
		
		Assertions.assertThat(FORMSCONFIG.findForm("contact")).isPresent();
		Assertions.assertThat(FORMSCONFIG.findForm("test-form")).isPresent();
		Assertions.assertThat(FORMSCONFIG.findForm("missing")).isEmpty();
		
		Assertions.assertThat(FORMSCONFIG.findForm("contact").get().getMail().getAccount()).isEqualTo("default");
		Assertions.assertThat(FORMSCONFIG.findForm("contact").get().getMail().getFrom()).isEqualTo("forms@example.com");
		Assertions.assertThat(FORMSCONFIG.findForm("contact").get().getFields().get("from").getType()).isEqualTo("email");
		Assertions.assertThat(FORMSCONFIG.findForm("contact").get().getFields().get("message").getMinLength()).isEqualTo(10);
		Assertions.assertThat(FORMSCONFIG.findForm("test-form").get().getMail().getAccount()).isEqualTo("other");
	}

	@Test
	void captcha_defaults_to_enabled_and_can_be_disabled() throws Exception {
		var yaml = """
				forms:
				  - name: with-default
				    fields:
				      message: {}
				  - name: without-captcha
				    captcha:
				      enabled: false
				    fields:
				      message: {}
				""";
		var config = new org.yaml.snakeyaml.Yaml().loadAs(yaml, FormsConfig.class);
		config.validate();

		Assertions.assertThat(config.findForm("with-default").get().getCaptcha().isEnabled()).isTrue();
		Assertions.assertThat(config.findForm("without-captcha").get().getCaptcha().isEnabled()).isFalse();
	}
}
