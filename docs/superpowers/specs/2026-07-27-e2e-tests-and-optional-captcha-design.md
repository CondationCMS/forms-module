# E2E-Tests für forms-module + optionales Captcha

## Kontext

Die Klasse `E2ETest.java` und der `test-server/`-Ordner wurden 1:1 aus dem
`video-module` kopiert (siehe `module/src/test/java/.../e2e/E2ETest.java`,
`test-server/`) und referenzieren noch video-module-Inhalte (Titel
"video-module test page", `/module/video-module/...`). Sie müssen an
forms-module angepasst werden.

Der alte `demo/`-Ordner (Beispielprojekt) nutzt Thymeleaf-Templatesyntax
(`th:replace`, `th:utext`, `th:with`), die von der aktuellen Template-Engine
nicht mehr unterstützt wird. `demo/` bleibt in diesem Vorhaben **unangetastet**
— eine Migration auf die neue Syntax ist ein separates, späteres Thema.

Das Modul erzwingt aktuell in jedem Formular ein Captcha
(`FormsHandling.validateCaptcha(...)` wird unconditional aufgerufen). Für
automatisiertes E2E-Testing soll das Captcha pro Formular abschaltbar sein.

Ein Vorab-Testlauf des video-module-Vorbilds
(`video-module/target/surefire-reports/....E2ETest.txt`) zeigt, dass dessen
Setup selbst kaputt ist: der Server meldet `Loaded 0 extension libraries`,
weil das gebaute Modul-JAR nie nach `test-server/modules/<id>/libs/` deployt
wird. Dieses Problem wird für forms-module mitbehoben.

## Ziele

1. Captcha ist pro Formular deaktivierbar (Config-Flag), Default bleibt "an"
   (kein Breaking Change für bestehende Configs).
2. Das forms-module-JAR wird beim Build automatisch nach
   `test-server/modules/forms-module/` deployt, sodass der CMS-Server im Test
   das Modul tatsächlich lädt.
3. `test-server/` enthält eine funktionierende, auf forms-module zugeschnittene
   Site-Konfiguration mit zwei Formularen (normal + AJAX), jeweils ohne
   Captcha-Pflicht, plus Mail-Konfiguration für einen lokalen Test-SMTP-Server.
4. Ein Satz Playwright-basierter E2E-Tests deckt den Kernablauf (Erfolg,
   Validierungsfehler, Spam/Honeypot, AJAX-Erfolg, AJAX-Fehler) ab und prüft
   bei erfolgreicher Einreichung auch den tatsächlichen Mailversand via
   GreenMail.
5. `mvn verify` baut, deployt und führt die E2E-Tests aus, ohne manuelle
   Zwischenschritte. Normale Unit-Tests laufen unverändert in der
   `test`-Phase.

## Nicht-Ziele

- Migration von `demo/` auf die neue Template-Syntax.
- Änderungen an Rate-Limiting, CSRF oder sonstigen bestehenden
  Sicherheits-Mechanismen.
- Neue Formular-Feature (z.B. neue Feldtypen).

## Design

### 1. Captcha optional (Config + Handling)

`FormsConfig.Form` erhält ein neues verschachteltes Feld:

```java
private Captcha captcha = new Captcha();

@Data
public static class Captcha {
    private boolean enabled = true;
}
```

Default `true` → bestehende YAML-Configs ohne `captcha:`-Block verhalten sich
exakt wie bisher.

`FormsHandling.handleForm(...)` ruft `validateCaptcha(form, key, code)` nur
noch auf, wenn `form.getCaptcha().isEnabled()` true ist. Ist Captcha
deaktiviert, werden `key`/`code` nicht ausgewertet — die Submission braucht
diese Parameter nicht, und `GenerateCaptchaHandler` muss vom Formular-Template
nicht aufgerufen werden.

`FormConfigTest` bzw. ein neuer Test deckt ab: Default `captcha.enabled=true`,
explizit `false` überschreibbar, `FormsHandlingTest` bekommt einen Fall für
ein Formular mit deaktiviertem Captcha (kein `key`/`code` nötig, keine
`INVALID_CAPTCHA`-Exception).

### 2. Build/Deploy-Pipeline für den Modultest

**Problem:** Das Modul-JAR (inkl. `libs/`-Runtime-Deps) entsteht erst in der
Maven-Phase `package`. E2E-Tests, die einen echten CMS-Server mit geladenem
Modul brauchen, müssen also *nach* `package` laufen — normale Unit-Tests
(Surefire) laufen aber in der früheren Phase `test`.

**Lösung:**

- `module/src/main/assembly/assembly.xml`: zusätzlich zum bisherigen
  `zip`-Format ein `dir`-Format ergänzen. Maven erzeugt dadurch beim
  `package`-Ziel automatisch einen Verzeichnisbaum
  `target/forms-module-bin/` mit dem korrekten Modul-Layout
  (`module.properties` im Root, `libs/*.jar` inkl. Runtime-Dependencies).
- `module/pom.xml`: neue Execution des `maven-resources-plugin`
  (`copy-resources`) in Phase `pre-integration-test`, die
  `target/forms-module-bin/**` nach `test-server/modules/forms-module/`
  kopiert (überschreibend, damit Re-Builds den Stand aktuell halten).
- `maven-failsafe-plugin` wird ergänzt (Standard-Includes
  `**/*IT.java`, gebunden an `integration-test`/`verify`).
- `E2ETest.java` wird zu `E2EIT.java` umbenannt (gleiches Package
  `com.condation.cms.modules.forms.e2e`), damit Failsafe statt Surefire
  greift und der Test erst nach dem Kopierschritt läuft.
- `.gitignore` (im Modul-Root) wird um Build-/Laufzeit-Artefakte ergänzt, die
  aktuell fehlen: `test-server/modules/`, `test-server/logs/`,
  `test-server/cms.pid`, `test-server/hosts/demo/modules_data/`,
  `test-server/hosts/demo/temp/`, `test-server/hosts/demo/data/`.

Ergebnis: `mvn verify` (oder `mvn install`) baut das Modul, kopiert es
automatisch ins Test-Server-Layout und führt anschließend die E2E-Tests
gegen einen echten, das Modul ladenden CMS-Server aus. `mvn test` bleibt
schnell und deckt nur die bestehenden Unit-Tests ab.

### 3. test-server-Inhalte

- `hosts/demo/site.toml`: `[modules] active = ["forms-module"]` aktivieren
  (statt auskommentiertem `videos-module`-Platzhalter).
- `hosts/demo/config/forms.yaml` (neu): zwei Formulare —
  - `contact`: Felder `from` (email, required), `message` (required,
    minLength); `captcha.enabled: false`; Honeypot aktiviert
    (`spam.honeypot.enabled: true`, Feld `website`); `mail.account: default`;
    `redirects.success: /forms/contact/success`; `rateLimit.enabled: false`
    (damit die Testreihe nicht ins Rate-Limit läuft).
  - `ajax-contact`: gleiche Feldstruktur, ebenfalls `captcha.enabled: false`,
    `rateLimit.enabled: false`, kein `to`/Mailversand nötig (AJAX-Pfad testet
    nur JSON-Antwort, nicht Mail).
  - Globale `redirects.error: /forms/error`.
- `hosts/demo/config/mail.yaml` (neu): `accounts.default` mit `host:
  localhost`, `port: 3025`, `fromMail`, `username`/`password` passend zur
  GreenMail-Testkonfiguration im E2E-Test.
- Templates (Pebble-Syntax, siehe reales Vorbild
  `demo/condation-server/themes/demo/templates/contact.html` im
  Gesamtworkspace):
  - `hosts/demo/templates/contact.html`: normales `<form>`,
    `method="post"`, `action="/module/forms-module/form/submit"`, Felder
    `from`/`message`, Honeypot-Feld `website` (versteckt), **kein**
    Captcha-Markup.
  - `hosts/demo/templates/ajax.html`: analoges Formular, `action=".../form/submit/ajax"`,
    per `fetch()` abgeschickt (Skript analog zu `demo/hosts/demo/assets/form-1.js`,
    ohne die Captcha-Reload-Logik), erwartet JSON-Antwort
    `{success, code, fieldErrors}`.
- Content:
  - `hosts/demo/content/contact.md` (`template: contact.html`)
  - `hosts/demo/content/ajax.md` (`template: ajax.html`)
  - `hosts/demo/content/forms/contact/success.md`
  - `hosts/demo/content/forms/error.md`
  - bestehendes `content/index.md` bleibt (Startseite), Titel wird auf einen
    forms-module-spezifischen Text angepasst (`node.meta.title` wird im
    Basistest geprüft).
- Aufräumen: Video-spezifische Leftovers (`assets/thumbnails/mountains.jpg`,
  `config/media.toml`), sofern sie von den neuen Templates nicht referenziert
  werden.

### 4. E2E-Testfälle (`E2EIT.java`)

`GreenMailExtension` mit fixem Port 3025
(`new ServerSetup(3025, null, ServerSetup.PROTOCOL_SMTP)`) wird als
`@RegisterExtension`-Feld **vor** `CMSServerExtension` deklariert, damit der
SMTP-Server steht, bevor der CMS-Prozess (der `config/mail.yaml` beim ersten
Mailversand liest) benötigt wird. Da `CMSServerExtension` den Server nur als
Thread in derselben JVM startet (kein separater OS-Prozess), teilen sich
GreenMail und der CMS-Server denselben Prozessraum unproblematisch.

Testfälle:

1. **Server startet korrekt** (angepasste Version des bestehenden Tests).
2. **Startseite** zeigt den erwarteten, forms-module-spezifischen Titel.
3. **Erfolgreiche Einreichung (`contact`)**: Playwright füllt `from` und
   `message` aus, submittet, erwartet Redirect auf
   `/forms/contact/success`. Zusätzlich: `greenMail.getReceivedMessagesForDomain(...)`
   liefert genau eine Mail mit erwartetem Empfänger/Betreff/Inhalt.
4. **Validierungsfehler**: `message` bleibt leer → Redirect auf
   `/forms/error`, keine Mail bei GreenMail eingegangen.
5. **Honeypot/Spam**: verstecktes Feld `website` wird befüllt → Redirect auf
   `/forms/error`, keine Mail.
6. **AJAX-Erfolg**: Formular `ajax-contact` wird per `fetch` submittet,
   JSON-Antwort `{success: true}`.
7. **AJAX-Validierungsfehler**: ungültige E-Mail-Adresse im Feld `from` →
   JSON-Antwort `{success: false, code: "VALIDATION_FAILED", fieldErrors:
   {...}}`.

Kein E2E-Test prüft den Mailversand für den AJAX-Pfad gesondert — der
Mailversand-Mechanismus ist derselbe wie beim normalen Pfad und wird dort
abgedeckt (YAGNI: keine Doppelabdeckung).

## Betroffene Dateien (Übersicht)

- `module/src/main/java/.../FormsConfig.java` — neues `Captcha`-Feld.
- `module/src/main/java/.../handler/FormsHandling.java` — Captcha-Check
  conditional machen.
- `module/src/test/java/.../FormConfigTest.java`,
  `FormsHandlingTest.java` — Tests für den neuen Schalter.
- `module/src/test/java/.../e2e/E2ETest.java` → `E2EIT.java` (umbenannt,
  inhaltlich neu).
- `module/src/main/assembly/assembly.xml` — `dir`-Format ergänzen.
- `module/pom.xml` — `maven-resources-plugin`-Copy-Step,
  `maven-failsafe-plugin`.
- `test-server/hosts/demo/site.toml`,
  `test-server/hosts/demo/config/forms.yaml` (neu),
  `test-server/hosts/demo/config/mail.yaml` (neu),
  `test-server/hosts/demo/templates/contact.html`,
  `test-server/hosts/demo/templates/ajax.html`,
  `test-server/hosts/demo/content/*.md`.
- `.gitignore` (Modul-Root) — Build-/Laufzeitartefakte ergänzen.
- `demo/` — unverändert.
