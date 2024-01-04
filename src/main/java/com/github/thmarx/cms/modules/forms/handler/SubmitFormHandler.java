package com.github.thmarx.cms.modules.forms.handler;

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
import com.github.thmarx.cms.modules.forms.FormHandlingException;
import com.github.thmarx.cms.modules.forms.FormsHandling;
import com.github.thmarx.cms.modules.forms.FormsLifecycleExtension;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.http.MultiPart;
import org.eclipse.jetty.http.MultiPartFormData;
import org.eclipse.jetty.server.FormFields;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Fields;

/**
 *
 * @author t.marx
 */
@Slf4j
public class SubmitFormHandler extends Handler.Abstract {

	private static Gson GSON = new Gson();

	@Override
	public boolean handle(Request request, Response response, Callback callback) throws Exception {

		if (!"POST".equalsIgnoreCase(request.getMethod())) {
			Response.writeError(request, response, callback, HttpStatus.METHOD_NOT_ALLOWED_405, "invalid request");
			return true;
		}

		String contentType = request.getHeaders().get(HttpHeader.CONTENT_TYPE);

		FormsHandling formHandling = new FormsHandling();

		if (MimeTypes.Type.FORM_ENCODED.is(contentType)) {
			CompletableFuture<Fields> completableFields = FormFields.from(request, StandardCharsets.UTF_8);
			completableFields.whenComplete((fields, failure) -> {
				try {
					if (failure == null) {
						final String formName = fields.get("form").getValue();
						var form = FormsLifecycleExtension.FORMSCONFIG.findForm(formName).get();
						formHandling.handleForm(form, (field) -> {
							if (fields.get(field) != null) {
								return fields.get(field).getValue();
							}
							return field;
						});
						response.getHeaders().add("Location", form.getRedirects().getSuccess());
						response.setStatus(HttpStatus.MOVED_TEMPORARILY_302);
						callback.succeeded();
					} else {
						response.getHeaders().add("Location", FormsLifecycleExtension.FORMSCONFIG.getRedirects().getError());
						response.setStatus(HttpStatus.MOVED_TEMPORARILY_302);
					}
				} catch (FormHandlingException fhe) {
					log.error(null, fhe);
					var formOpt = fhe.getForm();
					if (formOpt.isPresent() && !Strings.isNullOrEmpty(formOpt.get().getRedirects().getError())) {
						response.getHeaders().add("Location", formOpt.get().getRedirects().getError());
						response.setStatus(HttpStatus.MOVED_TEMPORARILY_302);
					} else {
						response.getHeaders().add("Location", FormsLifecycleExtension.FORMSCONFIG.getRedirects().getError());
						response.setStatus(HttpStatus.MOVED_TEMPORARILY_302);
					}
				}
				callback.succeeded();
			});
			return true;
		} else if (contentType.startsWith(MimeTypes.Type.MULTIPART_FORM_DATA.asString())) {
			String boundary = MultiPart.extractBoundary(contentType);
			MultiPartFormData.Parser parser = new MultiPartFormData.Parser(boundary);
			parser.setFilesDirectory(Files.createTempDirectory("cms-upload"));
			CompletableFuture<MultiPartFormData.Parts> completableParts = parser.parse(request);

			completableParts.whenComplete((parts, failure)
					-> {
				try {
					if (failure == null) {

						String formName = parts.getFirst("form").getContentAsString(StandardCharsets.UTF_8);
						var form = FormsLifecycleExtension.FORMSCONFIG.findForm(formName).get();
						formHandling.handleForm(form, (field) -> {
							if (parts.getAll(field) != null && !parts.getAll(field).isEmpty()) {
								return parts.getAll(field).getFirst().getContentAsString(StandardCharsets.UTF_8);
							}
							return field;
						});

						response.getHeaders().add("Location", form.getRedirects().getSuccess());
						response.setStatus(HttpStatus.MOVED_TEMPORARILY_302);
					} else {
						response.getHeaders().add("Location", FormsLifecycleExtension.FORMSCONFIG.getRedirects().getError());
						response.setStatus(HttpStatus.MOVED_TEMPORARILY_302);
					}
				} catch (FormHandlingException fhe) {
					log.error(null, fhe);
					var formOpt = fhe.getForm();
					if (formOpt.isPresent() && !Strings.isNullOrEmpty(formOpt.get().getRedirects().getError())) {
						response.getHeaders().add("Location", formOpt.get().getRedirects().getError());
						response.setStatus(HttpStatus.MOVED_TEMPORARILY_302);
					} else {
						response.getHeaders().add("Location", FormsLifecycleExtension.FORMSCONFIG.getRedirects().getError());
						response.setStatus(HttpStatus.MOVED_TEMPORARILY_302);
					}
				}
				callback.succeeded();
			});
			return true;
		}

		response.getHeaders().add("Location", FormsLifecycleExtension.FORMSCONFIG.getRedirects().getError());
		response.setStatus(HttpStatus.MOVED_TEMPORARILY_302);
		callback.succeeded();
		return true;
	}
}
