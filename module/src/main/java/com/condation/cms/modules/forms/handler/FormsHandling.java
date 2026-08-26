package com.condation.cms.modules.forms.handler;

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


import com.condation.cms.api.feature.features.InjectorFeature;
import com.condation.cms.api.hooks.HookSystem;
import com.condation.cms.api.mail.MailService;
import com.condation.cms.api.mail.Message;
import com.condation.cms.api.module.SiteModuleContext;
import com.condation.cms.modules.forms.FormsConfig;
import com.condation.cms.modules.forms.FormsFeature;
import com.condation.cms.modules.forms.utils.StringUtil;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 *
 * @author t.marx
 */
public class FormsHandling {

	private static final Pattern EMAIL = Pattern.compile(
			"^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	private static final Set<String> TRUE_VALUES = Set.of("true", "1", "on", "yes");
	private static final Set<String> FALSE_VALUES = Set.of("false", "0", "off", "no");

	private final HookSystem hookSystem;
	
	private final SiteModuleContext siteModuleContext;

	public FormsHandling(final HookSystem hookSystem, final SiteModuleContext siteModuleContext) {
		this.hookSystem = hookSystem;
		this.siteModuleContext = siteModuleContext;
	}
	
	private void validateCaptcha(final FormsConfig.Form form, final String key, final String code) throws FormHandlingException {
		var captchas = siteModuleContext.get(FormsFeature.class).captchas();
		var challenge = key == null ? null : captchas.getIfPresent(key);
		if (challenge == null || challenge.formName() != null && !challenge.formName().equals(form.getName())
				|| code == null || !challenge.answer().equalsIgnoreCase(code.trim())) {
			if (challenge != null) {
				if (challenge.attempts() >= 4) {
					captchas.invalidate(key);
				} else {
					captchas.put(key, challenge.failedAttempt());
				}
			}
			throw new FormHandlingException("INVALID_CAPTCHA", "invalid captcha", form, Map.of());
		}
		captchas.invalidate(key);
	}

	private String buildMessage(final FormsConfig.Form form, final Function<String, String> parameters) {
		StringBuilder message = new StringBuilder();

		if (form.getFields() != null) {
			form.getFields().keySet().forEach(field -> {
				var value = parameters.apply(field);
				message.append(field).append(":\r\n")
						.append(value == null ? "" : value)
						.append("\r\n\r\n");
			});
		}

		return message.toString();
	}

	private Map<String, Object> hookData (final FormsConfig.Form form, final Function<String, String> parameters) {
		Map<String, Object> data = new HashMap<>();

		if (form.getFields() != null) {
			form.getFields().keySet().forEach(field -> {
				var value = parameters.apply(field);
				data.put(field, value);
			});
		}
		
		if (form.getData() != null) {
			data.putAll(form.getData());
		}
		
		return data;
	}

	private void validateSpam(final FormsConfig.Form form, final Function<String, String> parameters)
			throws FormHandlingException {
		var spam = form.getSpam();
		if (spam != null && spam.getHoneypot() != null && spam.getHoneypot().isEnabled()) {
			var value = parameters.apply(spam.getHoneypot().getField());
			if (!StringUtil.isNullOrEmpty(value)) {
				throw new FormHandlingException("SPAM_REJECTED", "submission rejected", form, Map.of());
			}
		}
	}

	private void validateFields(final FormsConfig.Form form, final Function<String, String> parameters)
			throws FormHandlingException {
		var errors = new LinkedHashMap<String, String>();
		form.getFields().forEach((name, definition) -> {
			var value = parameters.apply(name);
			if (StringUtil.isNullOrEmpty(value)) {
				if (definition.isRequired()) {
					errors.put(name, "required");
				}
				return;
			}

			var normalized = value.trim();
			if (definition.getMinLength() != null && normalized.length() < definition.getMinLength()) {
				errors.put(name, "min_length");
			} else if (definition.getMaxLength() != null && normalized.length() > definition.getMaxLength()) {
				errors.put(name, "max_length");
			} else if ("email".equals(definition.getType()) && !EMAIL.matcher(normalized).matches()) {
				errors.put(name, "invalid_email");
			} else if ("integer".equals(definition.getType())) {
				try {
					Long.valueOf(normalized);
				} catch (NumberFormatException ex) {
					errors.put(name, "invalid_integer");
				}
			} else if ("boolean".equals(definition.getType())
					&& !TRUE_VALUES.contains(normalized.toLowerCase())
					&& !FALSE_VALUES.contains(normalized.toLowerCase())) {
				errors.put(name, "invalid_boolean");
			} else if (definition.getPattern() != null
					&& !Pattern.compile(definition.getPattern()).matcher(value).matches()) {
				errors.put(name, "pattern");
			} else if (definition.getAllowedValues() != null
					&& !definition.getAllowedValues().isEmpty()
					&& !definition.getAllowedValues().contains(value)) {
				errors.put(name, "not_allowed");
			}
		});
		if (!errors.isEmpty()) {
			throw new FormHandlingException(
					"VALIDATION_FAILED", "field validation failed", form, errors);
		}
	}
	
	public void handleForm(final FormsConfig.Form form, final Function<String, String> parameters) throws FormHandlingException {
		validateSpam(form, parameters);
		validateFields(form, parameters);
		if (form.getCaptcha().isEnabled()) {
			validateCaptcha(form, parameters.apply("key"), parameters.apply("code"));
		}

		try {
			var data = hookData(form, parameters);
			data.put("form", form.getName());
			hookSystem.doAction(
					"forms/%s/submit".formatted(form.getName()), 
					data);
			
			if (StringUtil.isNullOrEmpty(form.getTo())) {
				return;
			}
			
			var mailService = siteModuleContext.get(InjectorFeature.class).injector().getInstance(MailService.class);
			
			var message = new Message(
					form.getMail().getFrom(),
					new com.condation.cms.api.mail.Message.Recipient("", form.getTo()), 
					sanitizeHeader(form.getSubject()),
					buildMessage(form, parameters)
			);
			
			mailService.sendText(form.getMail().getAccount(), message);
		} catch (Exception e) {
			System.getLogger(getClass().getName()).log(
					System.Logger.Level.ERROR,
					"Actions failed for form " + form.getName(),
					e);
			throw new FormHandlingException("ACTION_FAILED", "form actions failed", form, Map.of());
		}
	}

	private String sanitizeHeader(final String value) {
		return value == null ? "" : value.replace("\r", "").replace("\n", "");
	}
}
