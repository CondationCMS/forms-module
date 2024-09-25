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


import com.condation.cms.api.extensions.HttpHandlerExtensionPoint;
import com.condation.cms.api.extensions.Mapping;
import com.condation.cms.api.feature.features.HookSystemFeature;
import com.condation.cms.modules.forms.handler.AjaxCaptchaValidationHandler;
import com.condation.cms.modules.forms.handler.AjaxSubmitFormHandler;
import com.condation.cms.modules.forms.handler.GenerateCaptchaHandler;
import com.condation.cms.modules.forms.handler.SubmitFormHandler;
import com.condation.modules.api.annotation.Extension;
import org.eclipse.jetty.http.pathmap.PathSpec;

/**
 *
 * @author t.marx
 */
@Extension(HttpHandlerExtensionPoint.class)
public class FormsHttpHandlerExtension extends HttpHandlerExtensionPoint {

	@Override
	public Mapping getMapping() {
		Mapping mapping = new Mapping();
		
		mapping.add(PathSpec.from("/captcha/validate"), new AjaxCaptchaValidationHandler());
		mapping.add(PathSpec.from("/captcha/generate"), new GenerateCaptchaHandler());
		mapping.add(PathSpec.from("/form/submit/ajax"), 
				new AjaxSubmitFormHandler(requestContext.get(HookSystemFeature.class).hookSystem())
		);
		mapping.add(PathSpec.from("/form/submit"), 
				new SubmitFormHandler(requestContext.get(HookSystemFeature.class).hookSystem())
		);
		
		return mapping;
	}
	
}
