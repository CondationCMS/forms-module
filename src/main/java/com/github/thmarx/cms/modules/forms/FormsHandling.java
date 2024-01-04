package com.github.thmarx.cms.modules.forms;

/*-
 * #%L
 * forms-module
 * %%
 * Copyright (C) 2023 Marx-Software
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

import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.simplejavamail.email.EmailBuilder;

/**
 *
 * @author t.marx
 */
@Slf4j
public class FormsHandling {

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

	public void handleForm(final FormsConfig.Form form, final Function<String, String> parameters) throws FormHandlingException {
		try {
			final String key = parameters.apply("key");
			String captchaCode = FormsLifecycleExtension.CAPTCHAS.getIfPresent(key);

			validateCaptcha(form, key, captchaCode);
			FormsLifecycleExtension.CAPTCHAS.invalidate(key);

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
