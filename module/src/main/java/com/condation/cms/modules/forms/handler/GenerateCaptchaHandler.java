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
import com.condation.cms.api.module.SiteModuleContext;
import com.condation.cms.api.utils.HTTPUtil;
import com.condation.cms.modules.forms.FormsFeature;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.logicsquad.nanocaptcha.image.ImageCaptcha;
import net.logicsquad.nanocaptcha.image.filter.StretchImageFilter;
import net.logicsquad.nanocaptcha.image.noise.StraightLineNoiseProducer;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

/**
 *
 * @author t.marx
 */
public class GenerateCaptchaHandler implements HttpHandler {

	private static final int DEFAULT_CAPTCHA_WIDTH = 250;
	private static final int DEFAULT_CAPTCHA_HEIGHT = 80;
	private static final int MIN_CAPTCHA_SIZE = 32;
	private final SiteModuleContext siteModuleContext;

	public GenerateCaptchaHandler(final SiteModuleContext siteModuleContext) {
		this.siteModuleContext = siteModuleContext;
	}
	
	@Override
	public boolean handle(Request request, Response response, Callback callback) throws Exception {

		var queryParameters = HTTPUtil.queryParameters(request.getHttpURI().getQuery());
		
		int width = getSizeParam("width", queryParameters, DEFAULT_CAPTCHA_WIDTH);
		int height = getSizeParam("height", queryParameters, DEFAULT_CAPTCHA_HEIGHT);

		String key = first(queryParameters, "key");
		String formName = first(queryParameters, "form");
		var feature = siteModuleContext.get(FormsFeature.class);
		if (!validKey(key) || feature.config().findForm(formName).isEmpty()) {
			Response.writeError(request, response, callback, HttpStatus.BAD_REQUEST_400, "invalid captcha request");
			return true;
		}
		String client = RequestSecurity.clientIdentifier(request);
		if (!feature.allow("captcha:" + client, feature.config().getCaptchaRateLimit())) {
			response.getHeaders().put(HttpHeader.CACHE_CONTROL, "no-store");
			Response.writeError(request, response, callback, HttpStatus.TOO_MANY_REQUESTS_429, "rate limit exceeded");
			return true;
		}
		
		ImageCaptcha imageCaptcha = new ImageCaptcha.Builder(width, height).addContent()
				.addFilter(new StretchImageFilter())
				.addNoise(new StraightLineNoiseProducer())
				.build();
		
		feature.captchas().put(
				key,
				new FormsFeature.CaptchaChallenge(imageCaptcha.getContent(), formName, 0));
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(imageCaptcha.getImage(), "PNG", baos);
		byte[] bytes = baos.toByteArray();
		response.getHeaders().put(HttpHeader.CONTENT_TYPE, "image/png");
		response.getHeaders().put(HttpHeader.CACHE_CONTROL, "no-store, no-cache, must-revalidate");
		response.getHeaders().put("Pragma", "no-cache");
		Content.Sink.write(response, true, ByteBuffer.wrap(bytes));
		callback.succeeded();
		
		return true;
	}
	
	private int getSizeParam (final String name, Map<String, List<String>> queryParameters, final int defaultValue) {
		String sizeParam = queryParameters.getOrDefault(name, List.of(String.valueOf(defaultValue))).get(0);

		try {
			int intValue = Integer.parseInt(sizeParam.trim());
			return Math.clamp(intValue, MIN_CAPTCHA_SIZE, defaultValue);
		} catch (NumberFormatException ex) {
			return defaultValue;
		}
	}

	private String first(final Map<String, List<String>> parameters, final String name) {
		var values = parameters.get(name);
		return values == null || values.isEmpty() ? null : values.getFirst();
	}

	private boolean validKey(final String key) {
		return key != null && key.length() >= 32 && key.length() <= 128
				&& key.matches("[A-Za-z0-9_-]+");
	}
}
