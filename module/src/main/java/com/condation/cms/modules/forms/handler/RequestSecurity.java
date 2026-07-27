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
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Locale;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Request;

final class RequestSecurity {

	private RequestSecurity() {
	}

	static boolean isAllowed(final Request request, final FormsConfig.Csrf policy) {
		if (policy == null || !policy.isEnabled()) {
			return true;
		}

		String origin = request.getHeaders().get(HttpHeader.ORIGIN);
		if (origin != null && policy.getAllowedOrigins() != null
				&& policy.getAllowedOrigins().contains(origin)) {
			return true;
		}

		String fetchSite = request.getHeaders().get("Sec-Fetch-Site");
		if ("cross-site".equalsIgnoreCase(fetchSite)) {
			return false;
		}
		if (origin == null) {
			return true;
		}

		String host = request.getHeaders().get(HttpHeader.HOST);
		if (host == null) {
			return false;
		}
		try {
			var originUri = URI.create(origin);
			return originUri.getRawAuthority() != null
					&& originUri.getRawAuthority().toLowerCase(Locale.ROOT)
							.equals(host.toLowerCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}

	static String clientIdentifier(final Request request) {
		var remote = request.getConnectionMetaData().getRemoteSocketAddress();
		if (remote instanceof InetSocketAddress inet) {
			return inet.getAddress() == null
					? inet.getHostString()
					: inet.getAddress().getHostAddress();
		}
		return String.valueOf(remote);
	}
}
