# Login at Web Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Android login uses controls with the web's phone sizes, shapes and floating-label behavior.

**Architecture:** A token file mirrors the web tokens; a pure label-state file holds the rules that can be unit tested; two composables (`FloatingTextField`, `PrimaryButton`) are built on `BasicTextField` and a clickable box; `LoginScreen` swaps its Material controls for them.

**Tech Stack:** Kotlin, Jetpack Compose (foundation, no Material text field), JUnit 4. JDK 17: `export JAVA_HOME=$(ls -d ~/.jdks/jdk-17* | head -1)`; run Gradle from the repo root.

**Spec:** `docs/superpowers/specs/2026-10-29-web-parity-login-design.md`

## Global Constraints

- Heights 36dp, radius 8dp, field text 14sp, button text 13sp weight Medium, floated label 10sp weight Normal, resting label 13sp.
- Colors: primary `#1D3D76`, primary foreground `#F9FAFB`, input border `#8A8C90`, destructive `#A92321`, muted foreground `#6A7282`, foreground `#030712`.
- No code comments in new code. Package `id.sch.mts241alikhlash.cbt`. Font `DMSansFontFamily`.
- `LoginViewModel`, its state and the login copy do not change. No commits unless the owner asks.

## Review Focus

- Long error message in a floated label: truncated with an ellipsis on one line, never wraps or grows the field.
- Empty field that is focused: label floats and turns primary; unfocused empty field: label rests, muted.
- Disabled field (`enabled = false` while signing in): not editable, 50% opacity, label state unchanged.
- Password field: characters masked, keyboard type Password, IME action kept.
- TalkBack: the field announces its label, the button announces its text and role.

---

### Task 1: Tokens and label rules

**Files:**
- Create: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/ui/theme/WebTokens.kt`
- Create: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/ui/components/FloatingLabelRules.kt`
- Test: `app/src/test/kotlin/id/sch/mts241alikhlash/cbt/ui/components/FloatingLabelRulesTest.kt`

**Interfaces:**
- Produces: `object WebTokens` with `ControlHeight: Dp`, `Radius: Dp`, `FieldTextSize: TextUnit`, `ControlTextSize: TextUnit`, `LabelFloatedSize: TextUnit`, `LabelInset: Dp` (7dp), `LabelZone: Dp` (12dp), `FilledTextShift: Dp` (1.5dp), and `Color` values `Primary`, `PrimaryForeground`, `InputBorder`, `Destructive`, `MutedForeground`, `Foreground`, `Card` (white).
- Produces: `internal fun labelFloated(focused: Boolean, value: String, error: String?): Boolean`; `internal fun labelText(label: String, error: String?): String`; `internal fun labelColor(focused: Boolean, error: String?): Color`; `internal fun borderColor(focused: Boolean, error: String?): Color`.

- [ ] **Step 1: Write the failing tests** in `FloatingLabelRulesTest`, with these names and assertions:
  - `label floats when focused even if empty`: `labelFloated(true, "", null)` is true.
  - `label floats when it has a value`: `labelFloated(false, "a", null)` is true.
  - `label rests when empty and unfocused`: `labelFloated(false, "", null)` is false.
  - `error forces the label to float`: `labelFloated(false, "", "Wajib diisi")` is true.
  - `error text replaces the label`: `labelText("NIS", "Wajib diisi") == "Wajib diisi"` and `labelText("NIS", null) == "NIS"`.
  - `label color follows state`: error gives `WebTokens.Destructive`, focused gives `WebTokens.Primary`, filled unfocused gives `WebTokens.Foreground.copy(alpha = 0.7f)`; resting empty is handled by the composable (muted), so `labelColor(false, null)` returns that 70% foreground.
  - `border color follows state`: error `Destructive`, focused `Primary`, else `InputBorder`.
  - `error beats focus`: `borderColor(true, "x") == WebTokens.Destructive`.

- [ ] **Step 2: Run to verify failure.** Run: `./gradlew testDebugUnitTest --tests '*FloatingLabelRulesTest*' -q`. Expected: compilation FAIL, `labelFloated` unresolved.

- [ ] **Step 3: Implement** `WebTokens` and the four functions in the files above, with the values in Global Constraints (floated sizes `10.sp`, `13.sp`, `14.sp`; `LabelInset` 7.dp; `LabelZone` 12.dp; `FilledTextShift` 1.5.dp).

- [ ] **Step 4: Run to verify pass.** Same command. Expected: PASS, 8 tests.

### Task 2: FloatingTextField and PrimaryButton

**Files:**
- Create: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/ui/components/FloatingTextField.kt`
- Create: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/ui/components/PrimaryButton.kt`

**Interfaces:**
- Consumes: Task 1 tokens and rules.
- Produces: `@Composable fun FloatingTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, error: String? = null, enabled: Boolean = true, password: Boolean = false, keyboardOptions: KeyboardOptions = KeyboardOptions.Default, labelBackground: Color = WebTokens.Card)`; `@Composable fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false)`.

- [ ] **Step 1: Implement `FloatingTextField`.** Structure: outer `Box` with `padding(top = LabelZone)`; inner field `Box` of height `ControlHeight` with `border(1.dp, borderColor(...), RoundedCornerShape(Radius))`, `BasicTextField` (single line, `DMSansFontFamily`, `FieldTextSize`, `Foreground`, cursor `Primary`, 8.dp start padding, text vertically centered and shifted down `FilledTextShift` while the label is floated, `PasswordVisualTransformation` when `password`); the label is a sibling `Text` drawn over the top border, positioned with a `layout` modifier that centers it vertically on y = 0 when floated and on y = `ControlHeight / 2` at rest, x = `LabelInset`, animated with `animateFloatAsState` (150ms) between rest and floated, font size interpolated between `ControlTextSize` and `LabelFloatedSize`, background `labelBackground` with 2.dp horizontal padding, one line with ellipsis, color `MutedForeground` at rest else `labelColor`. Disabled: `alpha(0.5f)` and not focusable. Merge semantics so TalkBack reads the label.

- [ ] **Step 2: Implement `PrimaryButton`.** `Box` of height `ControlHeight`, `clip(RoundedCornerShape(Radius))`, `background(Primary)` at 50% alpha when disabled, `clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)`, centered `Text` in `ControlTextSize`, `FontWeight.Medium`, `DMSansFontFamily`, `PrimaryForeground`; when `loading` a 16.dp `CircularProgressIndicator` (stroke 2.dp, `PrimaryForeground`) replaces the text.

- [ ] **Step 3: Compile.** Run: `./gradlew compileDebugKotlin -q`. Expected: exit 0.

### Task 3: Use them in LoginScreen and check on the phone

**Files:**
- Modify: `app/src/main/kotlin/id/sch/mts241alikhlash/cbt/ui/screens/LoginScreen.kt`

**Interfaces:**
- Consumes: Task 2 composables.

- [ ] **Step 1: Replace both `OutlinedTextField`s with `FloatingTextField`** (labels "NIS / Email" and "Kata sandi", `enabled = !state.loading`, password option on the second, same `KeyboardOptions` as now, 12.dp spacing kept) **and the `Button` with `PrimaryButton`** (text "Masuk", `loading = state.loading`). Delete `fieldColors()` and the imports no longer used. The card, logo, heading, subtitle and the error `Text` stay.

- [ ] **Step 2: Full check.** Run: `./gradlew testDebugUnitTest assembleDebug lintDebug -PAPI_BASE_URL_DEBUG=http://127.0.0.1:8080/ -q`. Expected: exit 0, no new lint warnings.

- [ ] **Step 3: Install and measure on the phone.** `adb install -r app/build/outputs/apk/debug/app-debug.apk`, clear app data, open the app, dump the UI: both fields and the button must be 36dp tall (36 x display density in px; the phone is 1220 px wide at density 3.05 — read it from `wm density`). Type a value and confirm the label floats. Expected: heights match; the owner confirms the look against the web.
