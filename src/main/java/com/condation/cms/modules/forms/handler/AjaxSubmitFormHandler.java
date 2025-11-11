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
import com.condation.cms.modules.forms.FormsLifecycleExtension;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.http.MultiPart;
import org.eclipse.jetty.http.MultiPartFormData;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.FormFields;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.Promise;

/**
 *
 * @author t.marx
 */
@Slf4j
@RequiredArgsConstructor
public class AjaxSubmitFormHandler implements HttpHandler {

	private final static Gson GSON = new Gson();

	private final HookSystem hookSystem;

	@Override
	public boolean handle(Request request, Response response, Callback callback) throws Exception {

		String contentType = request.getHeaders().get(HttpHeader.CONTENT_TYPE);

		FormsHandling formHandling = new FormsHandling(hookSystem);

		final AtomicReference<FormResponse> formResponse = new AtomicReference<>();
		try {
			if (MimeTypes.Type.FORM_ENCODED.is(contentType)) {
				FormFields.onFields(request, StandardCharsets.UTF_8, new Promise.Invocable<Fields>() {
					@Override
					public void succeeded(Fields fields) {
						try {
							final String formName = fields.get("form").getValue();
							var form = FormsLifecycleExtension.FORMSCONFIG.findForm(formName).get();
							formHandling.handleForm(form, (field) -> {
								if (fields.get(field) != null) {
									return fields.get(field).getValue();
								}
								return field;
							});
							formResponse.set(new FormResponse(false));
						} catch (FormHandlingException fhe) {
							log.error(null, fhe);
							formResponse.set(new FormResponse(true));
						}
					}

					@Override
					public void failed(Throwable x) {
						formResponse.set(new FormResponse(true));
					}
				});
			} else if (contentType.startsWith(MimeTypes.Type.MULTIPART_FORM_DATA.asString())) {
				String boundary = MultiPart.extractBoundary(contentType);
				MultiPartFormData.Parser parser = new MultiPartFormData.Parser(boundary);
				parser.setFilesDirectory(Files.createTempDirectory("cms-upload"));

				parser.parse(request, new Promise.Invocable<MultiPartFormData.Parts>() {
					@Override
					public void failed(Throwable x) {
						formResponse.set(new FormResponse(true));
					}

					@Override
					public void succeeded(MultiPartFormData.Parts parts) {
						try {

							String formName = parts.getFirst("form").getContentAsString(StandardCharsets.UTF_8);
							var form = FormsLifecycleExtension.FORMSCONFIG.findForm(formName).get();
							formHandling.handleForm(form, (field) -> {
								if (parts.getAll(field) != null && !parts.getAll(field).isEmpty()) {
									return parts.getAll(field).getFirst().getContentAsString(StandardCharsets.UTF_8);
								}
								return field;
							});

							formResponse.set(new FormResponse(false));

						} catch (FormHandlingException fhe) {
							log.error(null, fhe);
							formResponse.set(new FormResponse(true));
						}
					}

				});
			}
		} catch (Exception e) {
			log.error("error processing form", e);
		}

		if (formResponse.get().error()) {
			response.setStatus(HttpStatus.BAD_REQUEST_400);
		} else {
			response.setStatus(HttpStatus.OK_200);
		}
		Content.Sink.write(response, true, GSON.toJson(formResponse.get()), callback);

		return true;
	}

	private static record FormResponse(boolean error) {

	}
;
}
