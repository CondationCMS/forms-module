package com.condation.cms.modules.forms;

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

import com.condation.cms.api.extensions.TemplateModelExtendingExtentionPoint;
import com.condation.cms.api.template.TemplateEngine;
import com.condation.cms.api.extensions.TemplateModelExtendingExtentionPoint;
import com.condation.cms.modules.forms.template.FormsTemplateModel;
import com.condation.modules.api.annotation.Extension;
import com.condation.modules.api.annotation.Extension;

/**
 *
 * @author t.marx
 */
@Extension(TemplateModelExtendingExtentionPoint.class)
public class FormsTemplateModelExtensionPoint extends TemplateModelExtendingExtentionPoint{

	@Override
	public void extendModel(TemplateEngine.Model model) {
		model.values.put("forms", new FormsTemplateModel());
	}

}