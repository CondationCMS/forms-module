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


import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Data;

/**
 *
 * @author t.marx
 */
@Data
public class FormsConfig {
	
	private List<Form> forms;
	
	private Redirects redirects;
	
	public Optional<Form> findForm (final String name) {
		return forms.stream().filter(form -> form.getName().equals(name)).findFirst();
	}
	
	@Data
	public static class Form {
		private String name;
		private Redirects redirects;
		private List<String> fields;
		private String to;
		private String subject;
		private Map<String, Object> data;
		
		private Mail mail = new Mail();
	}
	
	@Data
	public static class Redirects {
		private String error;
		private String success;
	}
	
	@Data
	public static class Mail {
		private String account = "default";
		
	}
}
