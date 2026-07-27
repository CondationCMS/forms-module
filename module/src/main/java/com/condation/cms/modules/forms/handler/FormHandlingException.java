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


import com.condation.cms.modules.forms.FormsConfig;
import java.util.Map;
import java.util.Optional;

/**
 *
 * @author t.marx
 */
public class FormHandlingException extends Exception {

	private FormsConfig.Form form = null;
	private final String code;
	private final Map<String, String> fieldErrors;
	
	/**
	 * Creates a new instance of <code>FormHandlingException</code> without detail message.
	 */
	public FormHandlingException() {
		this("FORM_ERROR", "form handling failed", null, Map.of());
	}

	/**
	 * Constructs an instance of <code>FormHandlingException</code> with the specified detail message.
	 *
	 * @param msg the detail message.
	 */
	public FormHandlingException(String msg) {
		this("FORM_ERROR", msg, null, Map.of());
	}
	
	public FormHandlingException(String msg, final FormsConfig.Form form) {
		this("FORM_ERROR", msg, form, Map.of());
	}

	public FormHandlingException(
			final String code,
			final String msg,
			final FormsConfig.Form form,
			final Map<String, String> fieldErrors) {
		super(msg);
		this.form = form;
		this.code = code;
		this.fieldErrors = Map.copyOf(fieldErrors);
	}
	
	public Optional<FormsConfig.Form> getForm () {
		return Optional.ofNullable(form);
	}

	public String getCode() {
		return code;
	}

	public Map<String, String> getFieldErrors() {
		return fieldErrors;
	}
}
