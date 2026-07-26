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

import com.condation.cms.api.feature.Feature;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;

/**
 * Site-local state of the forms module.
 */
public final class FormsFeature implements Feature {

	private final FormsConfig config;
	private final Cache<String, CaptchaChallenge> captchas;
	private final Cache<String, RateLimitWindow> rateLimits;

	public FormsFeature(final FormsConfig config) {
		this.config = config;
		this.captchas = Caffeine.newBuilder()
				.maximumSize(10_000)
				.expireAfterWrite(Duration.ofMinutes(5))
				.build();
		this.rateLimits = Caffeine.newBuilder()
				.maximumSize(50_000)
				.expireAfterAccess(Duration.ofHours(1))
				.build();
	}

	public FormsConfig config() {
		return config;
	}

	public Cache<String, CaptchaChallenge> captchas() {
		return captchas;
	}

	public boolean allow(final String key, final FormsConfig.RateLimit policy) {
		if (policy == null || !policy.isEnabled()) {
			return true;
		}

		var window = rateLimits.get(key, ignored -> new RateLimitWindow());
		return window.tryAcquire(policy.getRequests(), Duration.ofSeconds(policy.getPeriodSeconds()));
	}

	public record CaptchaChallenge(String answer, String formName, int attempts) {

		public CaptchaChallenge failedAttempt() {
			return new CaptchaChallenge(answer, formName, attempts + 1);
		}
	}

	private static final class RateLimitWindow {

		private Instant startedAt = Instant.now();
		private int requests;

		synchronized boolean tryAcquire(final int maximumRequests, final Duration period) {
			var now = Instant.now();
			if (!now.isBefore(startedAt.plus(period))) {
				startedAt = now;
				requests = 0;
			}
			if (requests >= maximumRequests) {
				return false;
			}
			requests++;
			return true;
		}
	}
}
