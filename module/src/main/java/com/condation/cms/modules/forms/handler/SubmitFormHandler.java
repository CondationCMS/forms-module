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
import java.nio.charset.StandardCharsets;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.server.FormFields;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.Promise;

/**
 * Handles browser form submissions. Uploads are deliberately rejected until a
 * bounded and validated upload policy exists.
 */
public class SubmitFormHandler implements HttpHandler {

	private final HookSystem hookSystem;
	private final SiteModuleContext siteModuleContext;

	public SubmitFormHandler(final HookSystem hookSystem, final SiteModuleContext siteModuleContext) {
		this.hookSystem = hookSystem;
		this.siteModuleContext = siteModuleContext;
	}

	@Override
	public boolean handle(final Request request, final Response response, final Callback callback) {
		if (!"POST".equalsIgnoreCase(request.getMethod())) {
			response.getHeaders().put(HttpHeader.ALLOW, "POST");
			Response.writeError(request, response, callback, HttpStatus.METHOD_NOT_ALLOWED_405, "invalid request");
			return true;
		}

		var feature = siteModuleContext.get(FormsFeature.class);
		if (!RequestSecurity.isAllowed(request, feature.config().getCsrf())) {
			redirect(response, callback, feature.config().errorRedirect(null));
			return true;
		}
		String contentType = request.getHeaders().get(HttpHeader.CONTENT_TYPE);
		if (contentType == null || !MimeTypes.Type.FORM_ENCODED.is(contentType)) {
			redirect(response, callback, feature.config().errorRedirect(null));
			return true;
		}

		var formHandling = new FormsHandling(hookSystem, siteModuleContext);
		FormFields.onFields(request, StandardCharsets.UTF_8, new Promise.Invocable<Fields>() {
			@Override
			public void failed(final Throwable failure) {
				logger().log(System.Logger.Level.WARNING, "Could not parse form submission", failure);
				redirect(response, callback, feature.config().errorRedirect(null));
			}

			@Override
			public void succeeded(final Fields fields) {
				FormsConfig.Form form = null;
				try {
					var formName = value(fields, "form");
					form = feature.config().findForm(formName)
							.orElseThrow(() -> new FormHandlingException(
							"UNKNOWN_FORM", "unknown form", null, java.util.Map.of()));
					enforceRateLimit(request, feature, form);
					var selectedForm = form;
					formHandling.handleForm(selectedForm, name -> value(fields, name));
					redirect(response, callback, feature.config().successRedirect(selectedForm));
				} catch (FormHandlingException ex) {
					logger().log(System.Logger.Level.INFO, "Rejected form submission: " + ex.getCode());
					redirect(response, callback, feature.config().errorRedirect(form));
				} catch (RuntimeException ex) {
					logger().log(System.Logger.Level.ERROR, "Unexpected form submission error", ex);
					redirect(response, callback, feature.config().errorRedirect(form));
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
			throw new FormHandlingException(
					"RATE_LIMITED", "rate limit exceeded", form, java.util.Map.of());
		}
	}

	private static String value(final Fields fields, final String name) {
		var field = fields.get(name);
		return field == null ? null : field.getValue();
	}

	private static void redirect(
			final Response response,
			final Callback callback,
			final String location) {
		response.getHeaders().put(HttpHeader.LOCATION, location);
		response.setStatus(HttpStatus.SEE_OTHER_303);
		callback.succeeded();
	}

	private static System.Logger logger() {
		return System.getLogger(SubmitFormHandler.class.getName());
	}
}
