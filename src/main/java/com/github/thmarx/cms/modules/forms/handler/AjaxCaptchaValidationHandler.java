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

import com.github.thmarx.cms.modules.forms.FormsLifecycleExtension;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.simplejavamail.email.EmailBuilder;

/**
 *
 * @author t.marx
 */
@Slf4j
public class AjaxCaptchaValidationHandler extends Handler.Abstract {

	private static Gson GSON = new Gson();
	
	@Override
	public boolean handle(Request request, Response response, Callback callback) throws Exception {

		if (!"POST".equalsIgnoreCase(request.getMethod())) {
			response.setStatus(405);
			callback.succeeded();
			return true;
		}
		
		String body = readBody(request);
		var formData = GSON.fromJson(body, FormsData.class);
		
		boolean valid = false;
		String captchaCode = FormsLifecycleExtension.CAPTCHAS.getIfPresent(formData.key());
		if (captchaCode != null && captchaCode.equals(formData.code())) {
			valid = true;
		}

		Content.Sink.write(response, true, GSON.toJson(new ValidationResponse(valid)), callback);

		return true;
	}

	private String readBody(final Request request) {
		try (var inputStream = Request.asInputStream(request)) {
			return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (Exception ex) {
			log.error("", ex);
		}
		return "";
	}

	public record FormsData(String code, String key) {}
	public record ValidationResponse (boolean valid) {}
}
