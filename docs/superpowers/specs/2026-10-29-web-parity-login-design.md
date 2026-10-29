# Login screen at web parity (short design)

## Intent

The Android login looks different from the web apps: Material `OutlinedTextField` is 56dp tall and the button is 44dp, while the web's controls on a phone are 36px. The owner wants the same sizes, shapes and behavior as the web (`@mts241alikhlash/ui`), starting with the login screen. Later screens (exam list, exam sheet) reuse the same controls.

## Goals

- Field and button heights, radius, text sizes, border and label behavior equal the web's phone values.
- One token file holds the values, each named after its web token, so later screens stay in step.

## Non-goals

- No change to the exam list or exam sheet in this step.
- No change to `BrandNavy` and the other existing colors used app-wide.
- No new login behavior (same view model, same fields, same errors).

## Anti-goals

- A field that only looks close: the label must sit on the border, not inside the box.
- A control that loses keyboard behavior (IME action, password masking, focus) or TalkBack labels.

## Approaches

- A. Own `FloatingTextField` and `PrimaryButton` on `BasicTextField` and a plain clickable box, values copied from the web. Exact, more code. Chosen by the owner.
- B. Shrink `OutlinedTextField`. Material locks the minimum height and label size, so only close. Rejected.

The owner chose 36dp, the web's phone `--control-h-md`, over 40dp, knowing it is below Android's 48dp touch target.

## Values (from `theme.css`, `FloatingLabelField.vue`, `Input.vue`, `Button`)

| Part | Web (phone) | Android |
| --- | --- | --- |
| Field and button height | `--control-h-md` 2.25rem | 36dp |
| Corner radius | `--radius-md` 0.8 x 0.625rem | 8dp |
| Field text | `text-sm` | 14sp |
| Button text | `--text-control` 0.8125rem, weight 500 | 13sp, Medium |
| Field padding | `px-2`, `py-0.5` | 8dp horizontal |
| Border | `--input` | 1dp, #8A8C90 |
| Border focused | `--ring` = primary | 1dp, #1D3D76 |
| Border error | `--destructive` | 1dp, #A92321 |
| Label floated | 0.625rem, weight 400, 70% foreground, on the top border, 2px side padding, page background behind it | 10sp |
| Label at rest | `--text-control`, muted foreground, centered in the field | 13sp, #6A7282 |
| Label focused | primary | #1D3D76 |
| Label on error | the error message replaces the label text, destructive color | same |
| Text in a focused or filled field | `padding-top` 0.1875rem, `padding-bottom` 0, so the text sits 1.5px below center | 1.5dp lower |
| Button | `bg-primary`, primary-foreground text, disabled 50% opacity | #1D3D76 on #F9FAFB |
| Field gap above | `pt-3` for the floated label zone | 12dp |

Web colors are oklch; the hex values are their sRGB conversion. The existing `BrandNavy` (#1A365D) differs slightly from the web primary (#1D3D76). The new controls use the web value through the token file; unifying the rest of the app is a separate decision.

## Parts

- `ui/theme/WebTokens.kt`: colors and sizes above, named after the web tokens.
- `ui/components/FloatingTextField.kt`: label, value, error text, password option, IME action, enabled, keyboard actions.
- `ui/components/PrimaryButton.kt`: label, loading state, enabled, click.
- `LoginScreen.kt` uses both. The card, logo and copy stay as they are.

## Left to the builder

- Exact label animation timing (web uses 150ms).
- Where the error message sits when it replaces the label and is longer than the field (web truncates with an ellipsis).

## Verification

- Compose UI test or unit test on the token values is not enough to judge looks; check on the phone with a UI dump for sizes (36dp equals 36 x density in px) and by eye against the web login at phone width.
- `testDebugUnitTest assembleDebug lintDebug` stay green; the login view model tests are unchanged.
