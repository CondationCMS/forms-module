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
import com.google.common.base.Strings;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.simplejavamail.email.EmailBuilder;

/**
 *
 * @author t.marx
 */
@RequiredArgsConstructor
@Slf4j
public class FormsHandling {

	private final HookSystem hookSystem;
	
	private void validateCaptcha(final FormsConfig.Form form, final String key, final String code) throws FormHandlingException {
		String captchaCode = FormsLifecycleExtension.CAPTCHAS.getIfPresent(key);
		if (captchaCode == null || !captchaCode.equals(code)) {
			throw new FormHandlingException("invalid captcha", form);
		}

	}

	private String buildMessage(final FormsConfig.Form form, final Function<String, String> parameters) {
		StringBuilder message = new StringBuilder();

		if (form.getFields() != null) {
			form.getFields().forEach(field -> {
				var value = parameters.apply(field);
				message.append("field: ").append(field).append("\r\n").append(value);
			});
		}

		return message.toString();
	}

	private Map<String, Object> hookData (final FormsConfig.Form form, final Function<String, String> parameters) {
		Map<String, Object> data = new HashMap<>();

		if (form.getFields() != null) {
			form.getFields().forEach(field -> {
				var value = parameters.apply(field);
				data.put(field, value);
			});
		}

		return data;
	}
	
	public void handleForm(final FormsConfig.Form form, final Function<String, String> parameters) throws FormHandlingException {
		try {
			final String key = parameters.apply("key");
			String captchaCode = FormsLifecycleExtension.CAPTCHAS.getIfPresent(key);

			validateCaptcha(form, key, captchaCode);
			FormsLifecycleExtension.CAPTCHAS.invalidate(key);

			var data = hookData(form, parameters);
			data.put("form", form.getName());
			hookSystem.execute("forms/%s/submit", data);
			
			if (Strings.isNullOrEmpty(form.getTo())) {
				return;
			}
			FormsLifecycleExtension.MAILER.sendMail(EmailBuilder.startingBlank()
					.to(form.getTo())
					.from(parameters.apply("from"))
					.appendText(buildMessage(form, parameters))
					.withSubject(form.getSubject())
					.buildEmail());
		} catch (Exception e) {
			log.error(null, e);
			throw new FormHandlingException(e.getMessage());
		}
	}
}
