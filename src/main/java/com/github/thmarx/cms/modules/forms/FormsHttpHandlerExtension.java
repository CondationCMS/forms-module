package com.github.thmarx.cms.modules.forms;

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

import com.github.thmarx.cms.api.extensions.JettyHttpHandlerExtensionPoint;
import com.github.thmarx.cms.api.extensions.Mapping;
import com.github.thmarx.cms.modules.forms.handler.AjaxCaptchaValidationHandler;
import com.github.thmarx.cms.modules.forms.handler.GenerateCaptchaHandler;
import com.github.thmarx.cms.modules.forms.handler.AjaxSubmitFormHandler;
import com.github.thmarx.cms.modules.forms.handler.SubmitFormHandler;
import com.github.thmarx.modules.api.annotation.Extension;
import org.eclipse.jetty.http.pathmap.PathSpec;

/**
 *
 * @author t.marx
 */
@Extension(JettyHttpHandlerExtensionPoint.class)
public class FormsHttpHandlerExtension extends JettyHttpHandlerExtensionPoint {

	@Override
	public Mapping getMapping() {
		Mapping mapping = new Mapping();
		
		mapping.add(PathSpec.from("/captcha/validate"), new AjaxCaptchaValidationHandler());
		mapping.add(PathSpec.from("/captcha/generate"), new GenerateCaptchaHandler());
		mapping.add(PathSpec.from("/form/submit/ajax"), new AjaxSubmitFormHandler());
		mapping.add(PathSpec.from("/form/submit"), new SubmitFormHandler());
		
		return mapping;
	}
	
}
