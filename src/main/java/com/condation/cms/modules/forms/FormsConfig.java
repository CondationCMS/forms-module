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


import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.Data;

/**
 *
 * @author t.marx
 */
@Data
public class FormsConfig {
	
	private List<Form> forms;
	
	private Redirects redirects;

	private RateLimit captchaRateLimit = RateLimit.captchaDefaults();

	private Csrf csrf = new Csrf();
	
	public Optional<Form> findForm (final String name) {
		if (name == null || forms == null) {
			return Optional.empty();
		}
		return forms.stream()
				.filter(form -> form != null && name.equals(form.getName()))
				.findFirst();
	}

	public void validate() {
		if (forms == null || forms.isEmpty()) {
			throw new IllegalArgumentException("At least one form must be configured");
		}
		var names = new java.util.HashSet<String>();
		for (var form : forms) {
			if (form == null || isBlank(form.getName())) {
				throw new IllegalArgumentException("Every form needs a name");
			}
			if (!names.add(form.getName())) {
				throw new IllegalArgumentException("Duplicate form name: " + form.getName());
			}
			if (!isBlank(form.getTo()) && (form.getMail() == null || isBlank(form.getMail().getFrom()))) {
				throw new IllegalArgumentException("Form '%s' needs mail.from when to is configured".formatted(form.getName()));
			}
			for (var entry : form.getFields().entrySet()) {
				entry.getValue().validate(form.getName(), entry.getKey());
			}
			validateRateLimit(form.getRateLimit(), "form " + form.getName());
			if (form.getSpam() != null && form.getSpam().getHoneypot() != null
					&& form.getSpam().getHoneypot().isEnabled()
					&& isBlank(form.getSpam().getHoneypot().getField())) {
				throw new IllegalArgumentException("Enabled honeypot needs a field for form " + form.getName());
			}
			safeRedirect(form.getRedirects() == null ? null : form.getRedirects().getSuccess(), null);
			safeRedirect(form.getRedirects() == null ? null : form.getRedirects().getError(), null);
		}
		safeRedirect(redirects == null ? null : redirects.getSuccess(), null);
		safeRedirect(redirects == null ? null : redirects.getError(), null);
		validateRateLimit(captchaRateLimit, "captcha");
	}

	private static void validateRateLimit(final RateLimit rateLimit, final String scope) {
		if (rateLimit != null && rateLimit.isEnabled()
				&& (rateLimit.getRequests() < 1 || rateLimit.getPeriodSeconds() < 1)) {
			throw new IllegalArgumentException("Invalid rate limit for " + scope);
		}
	}

	public String successRedirect(final Form form) {
		return safeRedirect(
				form.getRedirects() == null ? null : form.getRedirects().getSuccess(),
				safeRedirect(redirects == null ? null : redirects.getSuccess(), "/"));
	}

	public String errorRedirect(final Form form) {
		return safeRedirect(
				form == null || form.getRedirects() == null ? null : form.getRedirects().getError(),
				safeRedirect(redirects == null ? null : redirects.getError(), "/"));
	}

	private static String safeRedirect(final String redirect, final String fallback) {
		if (isBlank(redirect)) {
			return fallback;
		}
		if (!redirect.startsWith("/") || redirect.startsWith("//")
				|| redirect.contains("\r") || redirect.contains("\n")) {
			throw new IllegalArgumentException("Redirects must be local absolute paths: " + redirect);
		}
		return redirect;
	}

	private static boolean isBlank(final String value) {
		return value == null || value.isBlank();
	}
	
	@Data
	public static class Form {
		private String name;
		private Redirects redirects;
		private Map<String, Field> fields = new LinkedHashMap<>();
		private String to;
		private String subject;
		private Map<String, Object> data;
		private Mail mail = new Mail();
		private Spam spam = new Spam();
		private RateLimit rateLimit = new RateLimit();

		public void setFields(final Map<String, Field> configuredFields) {
			this.fields = configuredFields == null
					? new LinkedHashMap<>()
					: new LinkedHashMap<>(configuredFields);
		}
	}

	@Data
	public static class Field {
		private String type = "string";
		private boolean required;
		private Integer minLength;
		private Integer maxLength;
		private String pattern;
		private Set<String> allowedValues = Collections.emptySet();

		void validate(final String formName, final String fieldName) {
			if (!Set.of("string", "email", "integer", "boolean").contains(type)) {
				throw new IllegalArgumentException("Unsupported type for %s.%s: %s"
						.formatted(formName, fieldName, type));
			}
			if (minLength != null && minLength < 0
					|| maxLength != null && maxLength < 0
					|| minLength != null && maxLength != null && minLength > maxLength) {
				throw new IllegalArgumentException("Invalid length constraints for " + formName + "." + fieldName);
			}
			if (pattern != null) {
				try {
					Pattern.compile(pattern);
				} catch (PatternSyntaxException ex) {
					throw new IllegalArgumentException("Invalid pattern for " + formName + "." + fieldName, ex);
				}
			}
		}
	}
	
	@Data
	public static class Redirects {
		private String error;
		private String success;
	}
	
	@Data
	public static class Mail {
		private String account = "default";
		private String from;
	}

	@Data
	public static class Spam {
		private Honeypot honeypot = new Honeypot();
	}

	@Data
	public static class Honeypot {
		private boolean enabled;
		private String field = "website";
	}

	@Data
	public static class RateLimit {
		private boolean enabled = true;
		private int requests = 5;
		private long periodSeconds = 600;

		static RateLimit captchaDefaults() {
			var result = new RateLimit();
			result.setRequests(20);
			result.setPeriodSeconds(60);
			return result;
		}
	}

	@Data
	public static class Csrf {
		private boolean enabled = true;
		private Set<String> allowedOrigins = Collections.emptySet();
	}
}
