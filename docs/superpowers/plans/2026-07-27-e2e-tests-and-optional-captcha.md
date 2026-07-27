# E2E Tests and Optional Captcha Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adapt the video-module-derived `E2ETest`/`test-server` scaffolding to forms-module, fix the module-deploy gap that leaves the CMS test server with 0 loaded extensions, and make captcha optional per form so the E2E suite can exercise real form submissions (including actual mail delivery via GreenMail) without solving a captcha.

**Architecture:** `module/pom.xml` gains an assembly `dir` format plus a `maven-resources-plugin` copy step bound to `pre-integration-test`, so `mvn verify` produces a deployable module layout under `test-server/modules/forms-module/` before Failsafe runs. `E2ETest.java` is renamed to `E2EIT.java` so Failsafe (not Surefire) picks it up in the `integration-test` phase, after `package`. `FormsConfig.Form` gets a `captcha.enabled` flag (default `true`); `FormsHandling` skips captcha validation when it's `false`. `test-server/hosts/demo/` is rewritten with a real `forms.yaml`/`mail.yaml`, Pebble-syntax templates, and content pages so the E2E suite can drive two forms (plain POST + AJAX) through Playwright, with GreenMail acting as the SMTP backend.

**Tech Stack:** Java 25, Maven (assembly/resources/failsafe/surefire plugins), JUnit 5, Playwright (Java), GreenMail (`greenmail-junit5`), SnakeYAML, Lombok `@Data`.

## Global Constraints

- Module id/artifact stays `forms-module`; module basedir is `module/` (sibling of `test-server/`), so any path passed to `CMSServerExtension` must be `"../test-server"` relative to `module/`, not `"test-server"`.
- Captcha default must remain `true` — no existing YAML config may change behavior.
- `demo/` (the old Thymeleaf example project) is explicitly out of scope and must not be modified.
- No secrets/passwords beyond throwaway test credentials (GreenMail test account) are introduced.
- Rate limiting, CSRF, and honeypot logic must not be touched except where explicitly noted.
- All new/modified Java files keep the existing GPLv3 header block (the `license-maven-plugin` `update-file-header` goal regenerates it on `process-sources`, so it's fine to omit it while writing and let the build add it — but keep the package/import structure consistent with existing files).

---

## File Structure

| File | Responsibility |
|---|---|
| `module/src/main/java/com/condation/cms/modules/forms/FormsConfig.java` | Add `Form.Captcha` nested config (`enabled`, default `true`). |
| `module/src/main/java/com/condation/cms/modules/forms/handler/FormsHandling.java` | Skip `validateCaptcha(...)` when `form.getCaptcha().isEnabled()` is `false`. |
| `module/src/test/java/com/condation/cms/modules/forms/FormConfigTest.java` | Assert captcha default/override parsing. |
| `module/src/test/java/com/condation/cms/modules/forms/FormsHandlingTest.java` | Assert captcha-disabled form skips captcha validation. |
| `module/src/main/assembly/assembly.xml` | Add `dir` format alongside existing `zip`. |
| `module/pom.xml` | Add `maven-resources-plugin` copy execution (`pre-integration-test`), `maven-failsafe-plugin`. |
| `module/src/test/java/com/condation/cms/modules/forms/e2e/E2ETest.java` → `E2EIT.java` | Rewritten E2E suite: server boot, plain-form success/validation/honeypot, AJAX success/validation, GreenMail assertion. |
| `test-server/hosts/demo/site.toml` | Activate `forms-module`. |
| `test-server/hosts/demo/config/forms.yaml` (new) | `contact` + `ajax-contact` form definitions, captcha disabled, rate limit disabled. |
| `test-server/hosts/demo/config/mail.yaml` (new) | `default` SMTP account pointing at GreenMail's fixed port. |
| `test-server/hosts/demo/templates/contact.html` (new) | Plain POST form, Pebble syntax, no captcha markup. |
| `test-server/hosts/demo/templates/ajax.html` (new) | AJAX form + fetch-based submit script. |
| `test-server/hosts/demo/content/contact.md`, `content/ajax.md`, `content/forms/contact/success.md`, `content/forms/error.md` (new) | Pages rendered by the two templates above. |
| `test-server/hosts/demo/content/index.md` | Title updated from "video-module test page" to a forms-module-specific title. |
| `.gitignore` (module root) | Add `test-server/modules/`, `test-server/logs/`, `test-server/cms.pid`, `test-server/hosts/demo/modules_data/`, `test-server/hosts/demo/temp/`, `test-server/hosts/demo/data/`. |
| `test-server/hosts/demo/assets/thumbnails/`, `test-server/hosts/demo/config/media.toml` | Removed (video-module leftovers, unreferenced by new templates). |

---

### Task 1: Make captcha optional per form

**Files:**
- Modify: `module/src/main/java/com/condation/cms/modules/forms/FormsConfig.java:126-143` (the `Form` class)
- Modify: `module/src/main/java/com/condation/cms/modules/forms/handler/FormsHandling.java:132-146` (`handleForm`)
- Test: `module/src/test/java/com/condation/cms/modules/forms/FormConfigTest.java`
- Test: `module/src/test/java/com/condation/cms/modules/forms/FormsHandlingTest.java`

**Interfaces:**
- Produces: `FormsConfig.Form.getCaptcha()` returning `FormsConfig.Captcha` with `isEnabled()` (default `true`), used by `FormsHandling.handleForm` and by the E2E test-server config (`captcha.enabled: false` in YAML).

- [ ] **Step 1: Write the failing config test**

Add to `module/src/test/java/com/condation/cms/modules/forms/FormConfigTest.java` (inside the existing `FormConfigTest` class, as a new `@Test` method):

```java
	@Test
	void captcha_defaults_to_enabled_and_can_be_disabled() throws Exception {
		var yaml = """
				forms:
				  - name: with-default
				    fields:
				      message: {}
				  - name: without-captcha
				    captcha:
				      enabled: false
				    fields:
				      message: {}
				""";
		var config = new org.yaml.snakeyaml.Yaml().loadAs(yaml, FormsConfig.class);
		config.validate();

		Assertions.assertThat(config.findForm("with-default").get().getCaptcha().isEnabled()).isTrue();
		Assertions.assertThat(config.findForm("without-captcha").get().getCaptcha().isEnabled()).isFalse();
	}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd module && mvn -q -Dtest=FormConfigTest#captcha_defaults_to_enabled_and_can_be_disabled test`
Expected: compilation error — `getCaptcha()` does not exist on `FormsConfig.Form`.

- [ ] **Step 3: Add the `Captcha` config class and wire it into `Form`**

In `module/src/main/java/com/condation/cms/modules/forms/FormsConfig.java`, inside `public static class Form { ... }` (currently ending at line 143), add a field:

```java
		private Captcha captcha = new Captcha();
```

so the full `Form` class becomes:

```java
	@Data
	public static class Form {
		private String name;
		private Redirects redirects;
		private Map<String, Field> fields = new LinkedHashMap<>();
		private String to;
		private String subject;
		private Map<String, Object> data;
		private Mail mail = new Mail();
		private Spam spam = new Spam();
		private RateLimit rateLimit = new RateLimit();
		private Captcha captcha = new Captcha();

		public void setFields(final Map<String, Field> configuredFields) {
			this.fields = configuredFields == null
					? new LinkedHashMap<>()
					: new LinkedHashMap<>(configuredFields);
		}
	}
```

Then add a new nested class next to `Csrf` (after the `Csrf` class, before the closing brace of `FormsConfig`):

```java
	@Data
	public static class Captcha {
		private boolean enabled = true;
	}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd module && mvn -q -Dtest=FormConfigTest#captcha_defaults_to_enabled_and_can_be_disabled test`
Expected: PASS.

- [ ] **Step 5: Write the failing handling test**

Add to `module/src/test/java/com/condation/cms/modules/forms/FormsHandlingTest.java` (new `@Test` method in the existing class):

```java
	@Test
	void skipsCaptchaValidationWhenDisabled() throws Exception {
		form.getCaptcha().setEnabled(false);
		var values = validValues();
		values.remove("key");
		values.remove("code");

		handling.handleForm(form, values::get);
	}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `cd module && mvn -q -Dtest=FormsHandlingTest#skipsCaptchaValidationWhenDisabled test`
Expected: FAIL — `FormHandlingException: INVALID_CAPTCHA` is thrown because `key`/`code` are missing and captcha is still enforced.

- [ ] **Step 7: Make captcha validation conditional**

In `module/src/main/java/com/condation/cms/modules/forms/handler/FormsHandling.java`, locate `handleForm` (currently):

```java
	public void handleForm(final FormsConfig.Form form, final Function<String, String> parameters) throws FormHandlingException {
		validateSpam(form, parameters);
		validateFields(form, parameters);
		validateCaptcha(form, parameters.apply("key"), parameters.apply("code"));
```

Change the captcha line to:

```java
	public void handleForm(final FormsConfig.Form form, final Function<String, String> parameters) throws FormHandlingException {
		validateSpam(form, parameters);
		validateFields(form, parameters);
		if (form.getCaptcha().isEnabled()) {
			validateCaptcha(form, parameters.apply("key"), parameters.apply("code"));
		}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `cd module && mvn -q -Dtest=FormsHandlingTest#skipsCaptchaValidationWhenDisabled test`
Expected: PASS.

- [ ] **Step 9: Run the full unit test suite**

Run: `cd module && mvn -q test`
Expected: all tests pass, including the pre-existing `FormsHandlingTest` cases (`rejectsSubmittedCaptchaCodeInsteadOfComparingStoredValueWithItself`, `acceptsAndConsumesCorrectCaptcha`, etc.), which still exercise the default (`captcha.enabled = true`) path unchanged.

- [ ] **Step 10: Commit**

```bash
cd /Users/thorstenmarx/entwicklung/workspaces/tma/cms/modules/forms-module
git add module/src/main/java/com/condation/cms/modules/forms/FormsConfig.java \
        module/src/main/java/com/condation/cms/modules/forms/handler/FormsHandling.java \
        module/src/test/java/com/condation/cms/modules/forms/FormConfigTest.java \
        module/src/test/java/com/condation/cms/modules/forms/FormsHandlingTest.java
git commit -m "Make captcha optional per form"
```

---

### Task 2: Fix the module-deploy gap (assembly dir format + copy step + failsafe)

**Files:**
- Modify: `module/src/main/assembly/assembly.xml`
- Modify: `module/pom.xml`
- Rename: `module/src/test/java/com/condation/cms/modules/forms/e2e/E2ETest.java` → `E2EIT.java` (content rewritten in Task 3; this task only renames + fixes the constructor path so the class still compiles as-is)

**Interfaces:**
- Produces: after `mvn package`, `module/target/forms-module-bin/` exists containing `module.properties` and `libs/*.jar` (module jar + runtime deps). After `mvn pre-integration-test` (or later phases), `test-server/modules/forms-module/` mirrors that directory. Failsafe runs any `**/*IT.java` in `integration-test`/`verify`.

- [ ] **Step 1: Add the `dir` format to the assembly descriptor**

Current `module/src/main/assembly/assembly.xml`:

```xml
<assembly xmlns="http://maven.apache.org/plugins/maven-assembly-plugin/assembly/1.1.0" 
		  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
		  xsi:schemaLocation="http://maven.apache.org/plugins/maven-assembly-plugin/assembly/1.1.0 http://maven.apache.org/xsd/assembly-1.1.0.xsd">
	<id>bin</id>
	<formats>
		<format>zip</format>
	</formats>
	<files>
		<file>
			<source>target/${project.build.finalName}.${project.packaging}</source>
			<outputDirectory>libs/</outputDirectory>
		</file>
	</files>
	<fileSets>
		<fileSet>
			<directory>${project.basedir}</directory>
			<outputDirectory>/</outputDirectory>
			<includes>
				<include>module.properties</include>
			</includes>
			<filtered>true</filtered>
		</fileSet>
	</fileSets>
	<dependencySets>
		<dependencySet>
			<outputDirectory>libs</outputDirectory>
			<useProjectArtifact>true</useProjectArtifact>
			<scope>runtime</scope>
		</dependencySet>
	</dependencySets>
</assembly>
```

Change `<formats>` to include `dir`:

```xml
	<formats>
		<format>zip</format>
		<format>dir</format>
	</formats>
```

Everything else in the file stays the same. With `<id>bin</id>` and `${module.id}` = `forms-module`, the assembly plugin (per its `finalName` config, see Step 2) will produce `target/forms-module-bin/` as a real directory in addition to `target/forms-module-bin.zip`.

- [ ] **Step 2: Add the resources-copy execution and failsafe plugin to `module/pom.xml`**

Current relevant block in `module/pom.xml`:

```xml
	<build>
		<plugins>
			<plugin>
				<artifactId>maven-assembly-plugin</artifactId>
				<version>3.8.0</version>
				<configuration>
					<descriptors>
						<descriptor>src/main/assembly/assembly.xml</descriptor>
					</descriptors>
					<finalName>${module.id}</finalName>
				</configuration>
				<executions>
					<execution>
						<phase>package</phase>
						<goals>
							<goal>single</goal>
						</goals>
					</execution>
				</executions>
			</plugin>	
		</plugins>
	</build>
```

Replace it with:

```xml
	<build>
		<plugins>
			<plugin>
				<artifactId>maven-assembly-plugin</artifactId>
				<version>3.8.0</version>
				<configuration>
					<descriptors>
						<descriptor>src/main/assembly/assembly.xml</descriptor>
					</descriptors>
					<finalName>${module.id}</finalName>
				</configuration>
				<executions>
					<execution>
						<phase>package</phase>
						<goals>
							<goal>single</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-resources-plugin</artifactId>
				<version>3.3.1</version>
				<executions>
					<execution>
						<id>deploy-module-to-test-server</id>
						<phase>pre-integration-test</phase>
						<goals>
							<goal>copy-resources</goal>
						</goals>
						<configuration>
							<outputDirectory>${project.basedir}/../test-server/modules/${module.id}</outputDirectory>
							<overwrite>true</overwrite>
							<resources>
								<resource>
									<directory>${project.build.directory}/${module.id}-bin</directory>
								</resource>
							</resources>
						</configuration>
					</execution>
				</executions>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-failsafe-plugin</artifactId>
				<version>3.5.4</version>
				<executions>
					<execution>
						<goals>
							<goal>integration-test</goal>
							<goal>verify</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>
```

Notes:
- `${project.build.directory}/${module.id}-bin` resolves to `target/forms-module-bin`, matching the assembly `<id>bin</id>` + `finalName=${module.id}` combination.
- `${project.basedir}/../test-server/modules/${module.id}` resolves to `module/../test-server/modules/forms-module` = `test-server/modules/forms-module`, a sibling of `module/`.
- Failsafe's default includes (`**/*IT.java`, `**/IT*.java`, `**/*ITCase.java`) will pick up `E2EIT.java` once renamed in Step 3; default excludes keep Surefire from also running it (Surefire's default excludes already skip `**/*IT.java`).

- [ ] **Step 3: Rename the E2E test class**

```bash
cd /Users/thorstenmarx/entwicklung/workspaces/tma/cms/modules/forms-module
git mv module/src/test/java/com/condation/cms/modules/forms/e2e/E2ETest.java \
       module/src/test/java/com/condation/cms/modules/forms/e2e/E2EIT.java
```

Edit the file to rename the class declaration (content will be fully rewritten in Task 3, but make the minimal rename now so the module still compiles):

Open `module/src/test/java/com/condation/cms/modules/forms/e2e/E2EIT.java` and change:

```java
public class E2ETest {
```

to:

```java
public class E2EIT {
```

Also fix the `CMSServerExtension` path, which currently reads `"test-server"` (correct for the video-module's flat layout, wrong here since `module/` and `test-server/` are siblings under `forms-module/`):

```java
	@RegisterExtension
	static CMSServerExtension serverExtensions = new CMSServerExtension("test-server");
```

becomes:

```java
	@RegisterExtension
	static CMSServerExtension serverExtensions = new CMSServerExtension("../test-server");
```

Leave the three existing `@Test` methods (`server_is_started`, `start_page`, `contains_header`) as-is for now — they still reference video-module content and will be replaced in Task 3.

- [ ] **Step 4: Verify `mvn package` produces the dir layout**

Run: `cd module && mvn -q clean package -DskipTests`
Expected: exit code 0. Then check:

```bash
ls module/target/forms-module-bin/
```
Expected output includes `module.properties` and a `libs/` directory containing `forms-module-<version>.jar` plus runtime dependency jars (nanocaptcha, caffeine, snakeyaml, gson, etc.).

- [ ] **Step 5: Verify the copy step deploys to test-server**

Run: `cd module && mvn -q pre-integration-test -DskipTests`
Expected: exit code 0. Then check:

```bash
ls test-server/modules/forms-module/
ls test-server/modules/forms-module/libs/ | grep forms-module
```
Expected: `module.properties` and `libs/forms-module-<version>.jar` present under `test-server/modules/forms-module/`.

- [ ] **Step 6: Commit**

```bash
cd /Users/thorstenmarx/entwicklung/workspaces/tma/cms/modules/forms-module
git add module/src/main/assembly/assembly.xml module/pom.xml \
        module/src/test/java/com/condation/cms/modules/forms/e2e/E2EIT.java
git status --short module/src/test/java/com/condation/cms/modules/forms/e2e/
git commit -m "Deploy built module jar to test-server before integration tests"
```

(`git status` first to confirm the old `E2ETest.java` path is gone and only `E2EIT.java` is staged, since `git mv` already recorded the rename.)

---

### Task 3: Rewrite `test-server/` content for forms-module (site config, forms.yaml, mail.yaml, templates, content)

**Files:**
- Modify: `test-server/hosts/demo/site.toml`
- Create: `test-server/hosts/demo/config/forms.yaml`
- Create: `test-server/hosts/demo/config/mail.yaml`
- Create: `test-server/hosts/demo/templates/contact.html`
- Create: `test-server/hosts/demo/templates/ajax.html`
- Create: `test-server/hosts/demo/content/contact.md`
- Create: `test-server/hosts/demo/content/ajax.md`
- Create: `test-server/hosts/demo/content/forms/contact/success.md`
- Create: `test-server/hosts/demo/content/forms/error.md`
- Modify: `test-server/hosts/demo/content/index.md`
- Delete: `test-server/hosts/demo/assets/thumbnails/mountains.jpg`, `test-server/hosts/demo/config/media.toml`

**Interfaces:**
- Produces: two working forms reachable at `/contact` (plain POST to `/module/forms-module/form/submit`) and `/ajax` (fetch POST to `/module/forms-module/form/submit/ajax`), form names `contact` and `ajax-contact`, both with `captcha.enabled: false` and `rateLimit.enabled: false`. Mail account `default` in `mail.yaml` with a placeholder port `3025` (GreenMail in Task 4 binds exactly this port before the server starts).

- [ ] **Step 1: Activate forms-module in `site.toml`**

Current `test-server/hosts/demo/site.toml`:

```toml
id = "demo-site"
hostname = [ "localhost", "127.0.0.1" ]
baseurl = "http://localhost:2020"
locale = "en_US"
context_path = "/"

# modules to load for this site
[modules]
#active = ["videos-module"]         # list of active modules for this sites
```

Replace with:

```toml
id = "demo-site"
hostname = [ "localhost", "127.0.0.1" ]
baseurl = "http://localhost:2020"
locale = "en_US"
context_path = "/"

# modules to load for this site
[modules]
active = ["forms-module"]
```

- [ ] **Step 2: Create `test-server/hosts/demo/config/forms.yaml`**

```yaml
forms:
  - name: contact
    to: contact@example.com
    subject: New contact form submission
    captcha:
      enabled: false
    rateLimit:
      enabled: false
    fields:
      from:
        type: email
        required: true
      message:
        required: true
        minLength: 3
        maxLength: 5000
    mail:
      account: default
      from: forms@example.com
    spam:
      honeypot:
        enabled: true
        field: website
    redirects:
      success: /forms/contact/success
  - name: ajax-contact
    captcha:
      enabled: false
    rateLimit:
      enabled: false
    fields:
      from:
        type: email
        required: true
      message:
        required: true
        minLength: 3
    spam:
      honeypot:
        enabled: true
        field: website
redirects:
  error: /forms/error
```

- [ ] **Step 3: Create `test-server/hosts/demo/config/mail.yaml`**

```yaml
accounts:
  default:
    host: localhost
    fromMail: forms@example.com
    port: 3025
    username: forms-test
    password: forms-test-password
```

(Port `3025` and the `forms-test`/`forms-test-password` credentials must match exactly what `E2EIT.java` configures on the `GreenMailExtension` in Task 4 — see that task's `greenMail.setUser("forms-test", "forms-test-password")` call.)

- [ ] **Step 4: Create `test-server/hosts/demo/templates/contact.html`**

```html
<!DOCTYPE html>
<html>

<head>
	<title>{{ node.meta.title }}</title>
	<meta charset="UTF-8" />
</head>

<body>

	{{ node.content | raw }}

	<form method="post" action="/module/forms-module/form/submit"
		enctype="application/x-www-form-urlencoded" id="contactForm">
		<input type="hidden" name="form" value="contact" />
		<input type="text" name="website" tabindex="-1" autocomplete="off" style="display:none" />
		<div>
			<label for="from">Your mail</label>
			<input type="email" name="from" id="from" />
		</div>
		<div>
			<label for="message">Message</label>
			<textarea name="message" id="message"></textarea>
		</div>
		<div>
			<button type="submit" id="submit-btn">Send</button>
		</div>
	</form>

</body>

</html>
```

- [ ] **Step 5: Create `test-server/hosts/demo/templates/ajax.html`**

```html
<!DOCTYPE html>
<html>

<head>
	<title>{{ node.meta.title }}</title>
	<meta charset="UTF-8" />
</head>

<body>

	{{ node.content | raw }}

	<form method="post" action="/module/forms-module/form/submit/ajax"
		enctype="application/x-www-form-urlencoded" onsubmit="return false;" id="ajaxForm">
		<input type="hidden" name="form" value="ajax-contact" />
		<input type="text" name="website" tabindex="-1" autocomplete="off" style="display:none" />
		<div>
			<label for="from">Your mail</label>
			<input type="email" name="from" id="from" />
		</div>
		<div>
			<label for="message">Message</label>
			<textarea name="message" id="message"></textarea>
		</div>
		<div>
			<button type="submit" id="submit-btn">Send</button>
		</div>
	</form>
	<div id="ajaxResult"></div>

	<script>
		document.getElementById("ajaxForm").addEventListener("submit", (event) => {
			event.preventDefault();
			var form = event.target;
			var formData = new URLSearchParams(new FormData(form));
			fetch(form.action, {
				method: "post",
				headers: { "Content-Type": "application/x-www-form-urlencoded" },
				body: formData
			}).then(res => res.json()).then(result => {
				document.getElementById("ajaxResult").setAttribute("data-success", result.success);
				document.getElementById("ajaxResult").setAttribute("data-code", result.code || "");
				document.getElementById("ajaxResult").textContent = JSON.stringify(result);
			});
			return false;
		});
	</script>

</body>

</html>
```

- [ ] **Step 6: Create content pages**

`test-server/hosts/demo/content/contact.md`:

```markdown
---
title: Contact
template: contact.html
search:
  index: false
published: true
---

# Contact us
```

`test-server/hosts/demo/content/ajax.md`:

```markdown
---
title: Ajax Contact
template: ajax.html
search:
  index: false
published: true
---

# Contact us via ajax
```

`test-server/hosts/demo/content/forms/contact/success.md` (uses the plain `start.html` template already present in `test-server/hosts/demo/templates/start.html`, which just renders `node.content` — reusing `contact.html` here would incorrectly re-render the form itself):

```markdown
---
title: Form submitted
template: start.html
search:
  index: false
published: true
---

## Your request was successfully submitted
```

`test-server/hosts/demo/content/forms/error.md` (same reasoning — plain `start.html`, not `contact.html`):

```markdown
---
title: Error sending form
template: start.html
search:
  index: false
published: true
---

## Error submitting your request!
```

- [ ] **Step 7: Update `test-server/hosts/demo/content/index.md`**

Current:

```markdown
---
title: video-module test page
template: start.html
search:
  index: false
published: true
---

# Vimeo Shortcode

[[video type="vimeo" id="170338499" title="Everybody loves little cats" /]]
```

Replace with:

```markdown
---
title: forms-module test page
template: start.html
search:
  index: false
published: true
---

# Forms module test page
```

- [ ] **Step 8: Remove video-module leftovers**

```bash
cd /Users/thorstenmarx/entwicklung/workspaces/tma/cms/modules/forms-module
git rm -r test-server/hosts/demo/assets/thumbnails test-server/hosts/demo/config/media.toml
```

- [ ] **Step 9: Commit**

```bash
cd /Users/thorstenmarx/entwicklung/workspaces/tma/cms/modules/forms-module
git add test-server/hosts/demo/site.toml \
        test-server/hosts/demo/config/forms.yaml \
        test-server/hosts/demo/config/mail.yaml \
        test-server/hosts/demo/templates/contact.html \
        test-server/hosts/demo/templates/ajax.html \
        test-server/hosts/demo/content/contact.md \
        test-server/hosts/demo/content/ajax.md \
        test-server/hosts/demo/content/forms \
        test-server/hosts/demo/content/index.md
git commit -m "Rewrite test-server content for forms-module"
```

(The `git rm` from Step 8 is already staged as part of the deletion; it will be included in this commit too — run `git status --short` beforehand if you want to double check exactly what's staged.)

---

### Task 4: Rewrite the E2E test suite (`E2EIT.java`) with GreenMail

**Files:**
- Modify: `module/src/test/java/com/condation/cms/modules/forms/e2e/E2EIT.java` (full rewrite)

**Interfaces:**
- Consumes: `CMSServerExtension("../test-server")` (Task 2), `GreenMailExtension` with fixed `ServerSetup(3025, "127.0.0.1", ServerSetup.PROTOCOL_SMTP)` (from `com.icegreen.greenmail.util.ServerSetup`, constructor `ServerSetup(int port, String bindAddress, String protocol)`), matching `host: localhost` in Task 3's `mail.yaml`, forms `contact` and `ajax-contact` as configured in Task 3's `forms.yaml`, mail account `default`/port `3025`/user `forms-test`/password `forms-test-password` as configured in Task 3's `mail.yaml`.
- Produces: no new public interface; this is the terminal artifact for this plan.

- [ ] **Step 1: Write the full `E2EIT.java` test class**

Replace the entire content of `module/src/test/java/com/condation/cms/modules/forms/e2e/E2EIT.java` with:

```java
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
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.UsePlaywright;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 *
 * @author thorstenmarx
 */
@UsePlaywright
public class E2EIT {

	@RegisterExtension
	static GreenMailExtension greenMail = new GreenMailExtension(
			new ServerSetup(3025, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));

	@RegisterExtension
	static CMSServerExtension serverExtensions = new CMSServerExtension("../test-server");

	@Test
	void server_is_started() throws Exception {
		Assertions.assertThat(CLIServerUtils.getCMSProcess()).isPresent();
	}

	@Test
	void start_page(Page page) {
		page.navigate("http://localhost:2020");
		Assertions.assertThat(page.locator("title").innerText()).isEqualTo("forms-module test page");
	}

	@Test
	void successful_submission_sends_mail_and_redirects(Page page) {
		greenMail.setUser("forms-test", "forms-test-password");

		page.navigate("http://localhost:2020/contact");
		page.fill("#from", "visitor@example.com");
		page.fill("#message", "Hello from the E2E test");
		page.click("#submit-btn");

		Assertions.assertThat(page.url()).contains("/forms/contact/success");

		var messages = greenMail.getReceivedMessagesForDomain("contact@example.com");
		Assertions.assertThat(messages).hasSize(1);
		Assertions.assertThat(messages[0].getSubject()).isEqualTo("New contact form submission");
		Assertions.assertThat(GreenMailUtil.getBody(messages[0])).contains("Hello from the E2E test");
	}

	@Test
	void missing_required_field_redirects_to_error_and_sends_no_mail(Page page) {
		page.navigate("http://localhost:2020/contact");
		page.fill("#from", "visitor@example.com");
		page.click("#submit-btn");

		Assertions.assertThat(page.url()).contains("/forms/error");
		Assertions.assertThat(greenMail.getReceivedMessages()).isEmpty();
	}

	@Test
	void filled_honeypot_redirects_to_error_and_sends_no_mail(Page page) {
		page.navigate("http://localhost:2020/contact");
		page.fill("#from", "visitor@example.com");
		page.fill("#message", "Hello from the E2E test");
		page.fill("input[name=website]", "https://spam.example");
		page.click("#submit-btn");

		Assertions.assertThat(page.url()).contains("/forms/error");
		Assertions.assertThat(greenMail.getReceivedMessages()).isEmpty();
	}

	@Test
	void ajax_form_returns_success_json(Page page) {
		page.navigate("http://localhost:2020/ajax");
		page.fill("#from", "visitor@example.com");
		page.fill("#message", "Hello via ajax");
		page.click("#submit-btn");

		page.waitForFunction("() => document.getElementById('ajaxResult').hasAttribute('data-success')");

		Assertions.assertThat(page.locator("#ajaxResult").getAttribute("data-success")).isEqualTo("true");
	}

	@Test
	void ajax_form_returns_validation_error_json(Page page) {
		page.navigate("http://localhost:2020/ajax");
		page.fill("#from", "not-an-email");
		page.fill("#message", "Hello via ajax");
		page.click("#submit-btn");

		page.waitForFunction("() => document.getElementById('ajaxResult').hasAttribute('data-success')");

		Assertions.assertThat(page.locator("#ajaxResult").getAttribute("data-success")).isEqualTo("false");
		Assertions.assertThat(page.locator("#ajaxResult").getAttribute("data-code")).isEqualTo("VALIDATION_FAILED");
	}
}
```

Notes on the code above:
- `GreenMailExtension` is declared *before* `CMSServerExtension` as a field, and JUnit 5 runs static `@RegisterExtension` fields' `beforeAll` callbacks in declaration order for top-level static extensions registered this way — GreenMail's SMTP listener is bound first, so `config/mail.yaml`'s `port: 3025` is already accepting connections before `Startup.run()` (triggered by `CMSServerExtension.beforeAll`) constructs `DefaultMailService`.
- `greenMail.setUser("forms-test", "forms-test-password")` only needs to be called once before the mail-sending test; GreenMail's SMTP server does not require authentication to accept a message by default, but this matches the credentials in `mail.yaml` for clarity and future-proofing if the mailer library enforces auth.
- `messages[0].getSubject()` and `GreenMailUtil.getBody(messages[0])` use `jakarta.mail.internet.MimeMessage` (returned by `getReceivedMessagesForDomain`) and the `com.icegreen.greenmail.util.GreenMailUtil.getBody(Part)` helper — both already on the test classpath via `greenmail-junit5`.
- The honeypot field is targeted via `page.fill("input[name=website]", ...)` instead of an `id` selector since the hidden honeypot input in the templates (Task 3) has no `id` attribute, matching the existing `demo/` convention of using `name="website"` for this field.

- [ ] **Step 2: Run the E2E suite**

Run: `cd module && mvn -q verify`
Expected: exit code 0. All Failsafe-run tests in `E2EIT` pass:
- `server_is_started`
- `start_page`
- `successful_submission_sends_mail_and_redirects`
- `missing_required_field_redirects_to_error_and_sends_no_mail`
- `filled_honeypot_redirects_to_error_and_sends_no_mail`
- `ajax_form_returns_success_json`
- `ajax_form_returns_validation_error_json`

If any test fails, check `module/test-server-logs-equivalent` — actually check `test-server/logs/` (the running CMS server's own logs) for stack traces, since `CMSServerExtension` runs the server in-process but its own logging still writes there.

- [ ] **Step 3: Run the full build one more time from a clean state to confirm reproducibility**

Run: `cd module && mvn -q clean verify`
Expected: exit code 0 (clean removes `target/`, so this re-validates that `package` → `pre-integration-test` copy → `integration-test` all run in the correct order from scratch).

- [ ] **Step 4: Commit**

```bash
cd /Users/thorstenmarx/entwicklung/workspaces/tma/cms/modules/forms-module
git add module/src/test/java/com/condation/cms/modules/forms/e2e/E2EIT.java
git commit -m "Add E2E tests for forms-module covering success, validation, spam, and ajax paths"
```

---

### Task 5: Finalize `.gitignore` and verify overall repo cleanliness

**Files:**
- Modify: `.gitignore` (module root, i.e. `/Users/thorstenmarx/entwicklung/workspaces/tma/cms/modules/forms-module/.gitignore`)

**Interfaces:** none (repo hygiene only).

- [ ] **Step 1: Update `.gitignore`**

Current content:

```
target/
demo/lib
demo/logs
demo/modules
demo/hosts/demo/modules_data
demo/cms.pid
demo/*.jar
demo/LICENSE
demo/log4j2.xml
demo/README.md
demo/server.yaml
.vscode/settings.json
```

Append these new lines at the end:

```
test-server/modules/
test-server/logs/
test-server/cms.pid
test-server/hosts/demo/modules_data/
test-server/hosts/demo/temp/
test-server/hosts/demo/data/
```

- [ ] **Step 2: Verify no unwanted build/runtime artifacts remain tracked**

Run: `git status --short`
Expected: only the intentional source/config files from Tasks 1-4 show as staged/committed; `test-server/modules/`, `test-server/logs/`, and any `*.log`/`cms.pid`/`modules_data`/`temp`/`data` paths under `test-server/hosts/demo/` do not appear as untracked (they're now ignored) or, if they were already tracked from a prior accidental commit, remove them:

```bash
git rm -r --cached test-server/logs test-server/hosts/demo/modules_data 2>/dev/null || true
```

(This is a no-op if those paths were never tracked — safe to run unconditionally.)

- [ ] **Step 3: Commit**

```bash
cd /Users/thorstenmarx/entwicklung/workspaces/tma/cms/modules/forms-module
git add .gitignore
git status --short
git commit -m "Ignore test-server build and runtime artifacts"
```

---

## Final Verification

- [ ] Run `cd module && mvn -q clean verify` one final time end-to-end.
- [ ] Confirm `mvn -q clean test` (Surefire only, no `verify`) still passes quickly without needing the module deployed to `test-server/` — this proves unit tests (`FormConfigTest`, `FormsHandlingTest`, `CaptchaTest`) remain fast and independent of the E2E machinery.
- [ ] Confirm `demo/` has zero diffs: `git status --short demo/` shows nothing.
