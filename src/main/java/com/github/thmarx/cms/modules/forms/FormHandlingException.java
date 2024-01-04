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

import java.util.Optional;

/**
 *
 * @author t.marx
 */
public class FormHandlingException extends Exception {

	private FormsConfig.Form form = null;
	
	/**
	 * Creates a new instance of <code>FormHandlingException</code> without detail message.
	 */
	public FormHandlingException() {
	}

	/**
	 * Constructs an instance of <code>FormHandlingException</code> with the specified detail message.
	 *
	 * @param msg the detail message.
	 */
	public FormHandlingException(String msg) {
		super(msg);
	}
	
	public FormHandlingException(String msg, final FormsConfig.Form form) {
		super(msg);
		this.form = form;
	}
	
	public Optional<FormsConfig.Form> getForm () {
		return Optional.ofNullable(form);
	}
}
