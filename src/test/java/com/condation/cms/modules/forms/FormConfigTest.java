package com.condation.cms.modules.forms;

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
		
		Assertions.assertThat(FORMSCONFIG.findForm("contact")).isPresent();
		Assertions.assertThat(FORMSCONFIG.findForm("test-form")).isPresent();
	}
}
