package com.condation.cms.modules.forms.template;

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

import com.condation.cms.modules.forms.utils.StringUtil;

/**
 *
 * @author t.marx
 */
public class FormsTemplateModel {
	
	private Captcha captcha = new Captcha();
	
	public Captcha getCaptcha () {
		return captcha;
	}
	
	public class Captcha {
		public String generateKey () {
			return StringUtil.random_string();
		}
	}
}