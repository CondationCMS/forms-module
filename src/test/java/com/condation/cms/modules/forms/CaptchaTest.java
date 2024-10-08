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


import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.logicsquad.nanocaptcha.image.ImageCaptcha;
import org.junit.jupiter.api.Test;

/**
 *
 * @author t.marx
 */
public class CaptchaTest {
	
	@Test
	void test_captcha () throws IOException {
		ImageCaptcha imageCaptcha = new ImageCaptcha.Builder(200, 50).addContent().build();
		ImageIO.write(imageCaptcha.getImage(), "png", new File("target/captcha.png"));
		System.out.println(imageCaptcha.getContent());
		System.out.println(imageCaptcha.isCorrect(imageCaptcha.getContent()));
	}
}
