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


import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;

/**
 *
 * @author t.marx
 */
public class MailTest {

	private static GreenMail greenMail;
	private static Mailer mailer;

	@BeforeAll
	public static void setup() {
		greenMail = new GreenMail(ServerSetupTest.SMTP.dynamicPort());
		greenMail.start();
		
		mailer = MailerBuilder.withSMTPServer(greenMail.getSmtp().getBindTo(), greenMail.getSmtp().getPort()).buildMailer();
	}
	
	@AfterAll
	public static void stop () {
		greenMail.stop();
	}
	
	@Test
	void send_simple_mail () {
		mailer.sendMail(EmailBuilder.startingBlank()
				.to("to@example.test")
				.from("from@example.test")
				.appendText("It's about my question!")
				.withSubject("Test mail")
				.buildEmail());
		
		Assertions.assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
		
		Assertions.assertThat(greenMail.getReceivedMessages().length).isEqualTo(1);
	}

}
