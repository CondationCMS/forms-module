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

import com.condation.cms.api.hooks.HookSystem;
import com.condation.cms.api.module.SiteModuleContext;
import com.condation.cms.modules.forms.handler.FormHandlingException;
import com.condation.cms.modules.forms.handler.FormsHandling;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FormsHandlingTest {

	private FormsConfig.Form form;
	private FormsFeature feature;
	private FormsHandling handling;

	@BeforeEach
	void setUp() {
		form = new FormsConfig.Form();
		form.setName("contact");
		var email = new FormsConfig.Field();
		email.setType("email");
		email.setRequired(true);
		form.setFields(Map.of("email", email));

		var config = new FormsConfig();
		config.setForms(java.util.List.of(form));
		feature = new FormsFeature(config);

		var context = new SiteModuleContext();
		context.add(FormsFeature.class, feature);
		var hooks = (HookSystem) Proxy.newProxyInstance(
				HookSystem.class.getClassLoader(),
				new Class<?>[]{HookSystem.class},
				(proxy, method, arguments) -> method.getName().equals("doAction")
						? java.util.List.of() : null);
		handling = new FormsHandling(hooks, context);
	}

	@Test
	void rejectsSubmittedCaptchaCodeInsteadOfComparingStoredValueWithItself() {
		feature.captchas().put("key", new FormsFeature.CaptchaChallenge("correct", "contact", 0));
		var values = validValues();
		values.put("code", "wrong");

		Assertions.assertThatThrownBy(() -> handling.handleForm(form, values::get))
				.isInstanceOf(FormHandlingException.class)
				.extracting("code")
				.isEqualTo("INVALID_CAPTCHA");
	}

	@Test
	void acceptsAndConsumesCorrectCaptcha() throws Exception {
		feature.captchas().put("key", new FormsFeature.CaptchaChallenge("correct", "contact", 0));
		handling.handleForm(form, validValues()::get);

		Assertions.assertThat(feature.captchas().getIfPresent("key")).isNull();
	}

	@Test
	void reportsMissingRequiredFields() {
		feature.captchas().put("key", new FormsFeature.CaptchaChallenge("correct", "contact", 0));
		var values = validValues();
		values.remove("email");

		Assertions.assertThatThrownBy(() -> handling.handleForm(form, values::get))
				.isInstanceOf(FormHandlingException.class)
				.satisfies(ex -> Assertions.assertThat(((FormHandlingException) ex).getFieldErrors())
						.containsEntry("email", "required"));
	}

	@Test
	void rejectsFilledHoneypot() {
		form.getSpam().getHoneypot().setEnabled(true);
		var values = validValues();
		values.put("website", "https://spam.example");

		Assertions.assertThatThrownBy(() -> handling.handleForm(form, values::get))
				.isInstanceOf(FormHandlingException.class)
				.extracting("code")
				.isEqualTo("SPAM_REJECTED");
	}

	@Test
	void skipsCaptchaValidationWhenDisabled() throws Exception {
		form.getCaptcha().setEnabled(false);
		var values = validValues();
		values.remove("key");
		values.remove("code");

		handling.handleForm(form, values::get);
	}

	private Map<String, String> validValues() {
		var values = new LinkedHashMap<String, String>();
		values.put("email", "visitor@example.com");
		values.put("key", "key");
		values.put("code", "correct");
		return values;
	}
}
