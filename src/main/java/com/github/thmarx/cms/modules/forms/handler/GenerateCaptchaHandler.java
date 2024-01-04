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

import com.github.thmarx.cms.api.utils.HTTPUtil;
import com.github.thmarx.cms.modules.forms.FormsLifecycleExtension;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.logicsquad.nanocaptcha.image.ImageCaptcha;
import net.logicsquad.nanocaptcha.image.filter.RippleImageFilter;
import net.logicsquad.nanocaptcha.image.filter.ShearImageFilter;
import net.logicsquad.nanocaptcha.image.filter.StretchImageFilter;
import net.logicsquad.nanocaptcha.image.noise.StraightLineNoiseProducer;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

/**
 *
 * @author t.marx
 */
public class GenerateCaptchaHandler extends Handler.Abstract {

	private static int DEFAULT_CAPTCHA_WIDTH = 250;
	private static int DEFAULT_CAPTCHA_HEIGHT = 250;
	
	@Override
	public boolean handle(Request request, Response response, Callback callback) throws Exception {

		var queryParameters = HTTPUtil.queryParameters(request.getHttpURI().getQuery());
		
		int width = getSizeParam("width", queryParameters, DEFAULT_CAPTCHA_WIDTH);
		int height = getSizeParam("height", queryParameters, DEFAULT_CAPTCHA_HEIGHT);
		
		String key = queryParameters.getOrDefault("key", List.of("default")).get(0);
		
		ImageCaptcha imageCaptcha = new ImageCaptcha.Builder(width, height).addContent()
				.addFilter(new StretchImageFilter())
				.addNoise(new StraightLineNoiseProducer())
				.build();
		
		FormsLifecycleExtension.CAPTCHAS.put(key, imageCaptcha.getContent());
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(imageCaptcha.getImage(), "PNG", baos);
		byte[] bytes = baos.toByteArray();
		Content.Sink.write(response, true, ByteBuffer.wrap(bytes));
		callback.succeeded();
		
		return true;
	}
	
	private int getSizeParam (final String name, Map<String, List<String>> queryParameters, final int defaultValue) {
		String sizeParam = queryParameters.getOrDefault(name, List.of(String.valueOf(defaultValue))).get(0);
		
		int intValue = Integer.parseInt(sizeParam.trim());
		if (intValue > defaultValue) {
			return defaultValue;
		} else {
			return intValue;
		}
	}

}
