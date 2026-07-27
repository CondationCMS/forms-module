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

import com.condation.cms.api.extensions.HttpHandler;
import com.condation.cms.api.hooks.HookSystem;
import com.condation.cms.api.module.SiteModuleContext;
import com.condation.cms.modules.forms.FormsConfig;
import com.condation.cms.modules.forms.FormsFeature;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.FormFields;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.Promise;

public class AjaxSubmitFormHandler implements HttpHandler {

	private static final Gson GSON = new Gson();

	private final HookSystem hookSystem;
	private final SiteModuleContext siteModuleContext;

	public AjaxSubmitFormHandler(final HookSystem hookSystem, final SiteModuleContext siteModuleContext) {
		this.hookSystem = hookSystem;
		this.siteModuleContext = siteModuleContext;
	}

	@Override
	public boolean handle(final Request request, final Response response, final Callback callback) {
		response.getHeaders().put(HttpHeader.CONTENT_TYPE, "application/json; charset=utf-8");
		response.getHeaders().put(HttpHeader.CACHE_CONTROL, "no-store");

		if (!"POST".equalsIgnoreCase(request.getMethod())) {
			response.getHeaders().put(HttpHeader.ALLOW, "POST");
			write(response, callback, HttpStatus.METHOD_NOT_ALLOWED_405,
					new FormResponse(false, "METHOD_NOT_ALLOWED", Map.of()));
			return true;
		}

		var feature = siteModuleContext.get(FormsFeature.class);
		if (!RequestSecurity.isAllowed(request, feature.config().getCsrf())) {
			write(response, callback, HttpStatus.FORBIDDEN_403,
					new FormResponse(false, "CSRF_REJECTED", Map.of()));
			return true;
		}

		String contentType = request.getHeaders().get(HttpHeader.CONTENT_TYPE);
		if (contentType == null || !MimeTypes.Type.FORM_ENCODED.is(contentType)) {
			write(response, callback, HttpStatus.UNSUPPORTED_MEDIA_TYPE_415,
					new FormResponse(false, "UNSUPPORTED_MEDIA_TYPE", Map.of()));
			return true;
		}

		var formHandling = new FormsHandling(hookSystem, siteModuleContext);
		FormFields.onFields(request, StandardCharsets.UTF_8, new Promise.Invocable<Fields>() {
			@Override
			public void failed(final Throwable failure) {
				logger().log(System.Logger.Level.WARNING, "Could not parse AJAX form submission", failure);
				write(response, callback, HttpStatus.BAD_REQUEST_400,
						new FormResponse(false, "INVALID_REQUEST", Map.of()));
			}

			@Override
			public void succeeded(final Fields fields) {
				FormsConfig.Form form = null;
				try {
					var formName = value(fields, "form");
					form = feature.config().findForm(formName)
							.orElseThrow(() -> new FormHandlingException(
							"UNKNOWN_FORM", "unknown form", null, Map.of()));
					enforceRateLimit(request, feature, form);
					var selectedForm = form;
					formHandling.handleForm(selectedForm, name -> value(fields, name));
					write(response, callback, HttpStatus.OK_200,
							new FormResponse(true, null, Map.of()));
				} catch (FormHandlingException ex) {
					logger().log(System.Logger.Level.INFO, "Rejected AJAX form submission: " + ex.getCode());
					int status = "RATE_LIMITED".equals(ex.getCode())
							? HttpStatus.TOO_MANY_REQUESTS_429 : HttpStatus.BAD_REQUEST_400;
					write(response, callback, status,
							new FormResponse(false, ex.getCode(), ex.getFieldErrors()));
				} catch (RuntimeException ex) {
					logger().log(System.Logger.Level.ERROR, "Unexpected AJAX form submission error", ex);
					write(response, callback, HttpStatus.INTERNAL_SERVER_ERROR_500,
							new FormResponse(false, "INTERNAL_ERROR", Map.of()));
				}
			}
		});
		return true;
	}

	private void enforceRateLimit(
			final Request request,
			final FormsFeature feature,
			final FormsConfig.Form form) throws FormHandlingException {
		var client = RequestSecurity.clientIdentifier(request);
		if (!feature.allow("submit:" + form.getName() + ":" + client, form.getRateLimit())) {
			throw new FormHandlingException("RATE_LIMITED", "rate limit exceeded", form, Map.of());
		}
	}

	private static String value(final Fields fields, final String name) {
		var field = fields.get(name);
		return field == null ? null : field.getValue();
	}

	private static void write(
			final Response response,
			final Callback callback,
			final int status,
			final FormResponse formResponse) {
		response.setStatus(status);
		Content.Sink.write(response, true, GSON.toJson(formResponse), callback);
	}

	private record FormResponse(boolean success, String code, Map<String, String> fieldErrors) {
	}

	private static System.Logger logger() {
		return System.getLogger(AjaxSubmitFormHandler.class.getName());
	}
}
