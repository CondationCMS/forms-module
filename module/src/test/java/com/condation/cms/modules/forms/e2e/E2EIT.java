package com.condation.cms.modules.forms.e2e;

/*-
 * #%L
 * forms-module
 * %%
 * Copyright (C) 2024 - 2026 CondationCMS
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

import com.condation.cms.cli.tools.CLIServerUtils;
import com.condation.cms.test.e2e.CMSServerExtension;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.UsePlaywright;
import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.RegisterExtension;

/**
 *
 * @author thorstenmarx
 */
@UsePlaywright
public class E2EIT {

	private static final String SMTP_HOST = "127.0.0.1";
	private static final int SMTP_PORT = 3025;
	private static final String SMTP_USERNAME = "test@example.test";
	private static final String SMTP_PASSWORD = "password";
	private static final String BASE_URL = "http://localhost:2020";
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

	@RegisterExtension
	@Order(1)
	static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(
			new ServerSetup(SMTP_PORT, SMTP_HOST, ServerSetup.PROTOCOL_SMTP))
			.withConfiguration(GreenMailConfiguration.aConfig()
					.withUser(SMTP_USERNAME, SMTP_USERNAME, SMTP_PASSWORD))
			.withPerMethodLifecycle(false);

	@RegisterExtension
	@Order(2)
	static final CMSServerExtension SERVER = new CMSServerExtension("../test-server");

	@BeforeEach
	void resetMailServer() throws Exception {
		GREEN_MAIL.purgeEmailFromAllMailboxes();
	}
	
	@Test
	void server_is_started() throws Exception {
		Assertions.assertThat(CLIServerUtils.getCMSProcess()).isPresent();
	}
	
	@Test
	void start_page(Page page) {
		page.navigate("http://localhost:2020");
		Assertions.assertThat(page.title()).isEqualTo("forms test site");
	}

	@Test
	void mail_form_is_rendered(Page page) {
		page.navigate("http://localhost:2020/forms/mail");

		Assertions.assertThat(page.title()).isEqualTo("Mail form test");
		Assertions.assertThat(page.locator("#mail-form").count()).isEqualTo(1);
		Assertions.assertThat(page.locator("input[name=form]").inputValue()).isEqualTo("mail");
	}

	@Test
	void valid_form_sends_mail(Page page) throws Exception {
		page.navigate("http://localhost:2020/forms/mail");
		page.locator("#mail-email").fill("visitor@example.test");
		page.locator("#mail-message").fill("This message was submitted by the E2E test.");
		page.locator("#mail-submit").click();

		page.waitForURL("**/forms/mail-success");
		Assertions.assertThat(page.locator("#result").innerText()).isEqualTo("mail-success");
		Assertions.assertThat(GREEN_MAIL.waitForIncomingEmail(5_000, 1)).isTrue();

		MimeMessage message = GREEN_MAIL.getReceivedMessages()[0];
		Assertions.assertThat(message.getSubject()).isEqualTo("Forms E2E mail");
		Assertions.assertThat(message.getAllRecipients())
				.extracting(Object::toString)
				.containsExactly("recipient@example.test");
		Assertions.assertThat(message.getFrom())
				.extracting(Object::toString)
				.containsExactly("Forms E2E test <test@example.test>");
		Assertions.assertThat(message.getContent().toString())
				.contains("email:", "visitor@example.test")
				.contains("message:", "This message was submitted by the E2E test.");
	}

	@Test
	void invalid_form_redirects_to_its_error_page(Page page) {
		page.navigate("http://localhost:2020/forms/validation");
		page.locator("#validation-email").fill("not-an-email");
		page.locator("#validation-message").fill("short");
		page.locator("#validation-submit").click();

		page.waitForURL("**/forms/validation-error");
		Assertions.assertThat(page.locator("#result").innerText()).isEqualTo("validation-error");
		Assertions.assertThat(GREEN_MAIL.getReceivedMessages()).isEmpty();
	}

	@Test
	void security_features_reject_cross_site_spam_and_excess_requests(Page page) throws Exception {
		page.navigate(BASE_URL + "/forms/security");
		Assertions.assertThat(page.title()).isEqualTo("Security form test");
		Assertions.assertThat(page.locator("#security-form").count()).isEqualTo(1);
		Assertions.assertThat(page.locator("input[name=website]").count()).isEqualTo(1);

		var crossSite = submitSecurityForm(
				"https://attacker.example",
				"form=security&message=Cross-site+submission");
		Assertions.assertThat(crossSite.statusCode()).isEqualTo(403);
		Assertions.assertThat(crossSite.body()).contains("\"code\":\"CSRF_REJECTED\"");

		var honeypot = submitSecurityForm(
				BASE_URL,
				"form=security&message=Automated+submission&website=https%3A%2F%2Fspam.example");
		Assertions.assertThat(honeypot.statusCode()).isEqualTo(400);
		Assertions.assertThat(honeypot.body()).contains("\"code\":\"SPAM_REJECTED\"");

		var valid = submitSecurityForm(
				BASE_URL,
				"form=security&message=Allowed+submission");
		Assertions.assertThat(valid.statusCode()).isEqualTo(200);
		Assertions.assertThat(valid.body()).contains("\"success\":true");

		var rateLimited = submitSecurityForm(
				BASE_URL,
				"form=security&message=One+request+too+many");
		Assertions.assertThat(rateLimited.statusCode()).isEqualTo(429);
		Assertions.assertThat(rateLimited.body()).contains("\"code\":\"RATE_LIMITED\"");
	}

	private HttpResponse<String> submitSecurityForm(
			final String origin,
			final String formBody) throws Exception {
		var request = HttpRequest.newBuilder()
				.uri(URI.create(BASE_URL + "/module/forms-module/form/submit/ajax"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.header("Origin", origin)
				.POST(HttpRequest.BodyPublishers.ofString(formBody))
				.build();
		return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
