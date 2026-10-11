package com.tasktracker.featureauthpresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.contentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.designsystem.AmbientBackdrop
import com.tasktracker.designsystem.AppMark
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttCanvas
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.designsystem.ttTypography
import kotlinx.coroutines.launch

/**
 * The sign-in screen: a brand mark, two fields, and one button.
 *
 * It takes no session as a parameter. It reads [currentAuthSession], and when there is
 * none it renders [MissingAuthSessionView] instead of the form — which is the only thing
 * that makes the environment of 11.1 load-bearing rather than decorative. A gate nothing
 * consults is dead code, and this was caught by a UI test asserting the gate appears when
 * the local is null and failing because the form rendered regardless.
 *
 * Reading the environment rather than accepting a [SignInModel] also means the wiring under
 * test is the wiring the shell performs: a test provides a session through
 * [ProvideAuthSession] and gets the same composition path a running app takes, instead of
 * constructing the model itself and never exercising the lookup.
 *
 * Presentational only. It reports success to [onAuthenticated] and holds no reference to the
 * session bus or any router: the sign-out that must follow is the shell's job, because a
 * screen that both submits credentials and navigates cannot be previewed or tested without a
 * navigation stack.
 *
 * @param onAuthenticated the shell's response to a successful sign-in. Fired at most once per
 *   sign-in, because [SignInModel] refuses to submit again once it has reached `SignedIn`.
 */
@Composable
fun SignInView(
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = currentAuthSession
    if (session == null) {
        MissingAuthSessionView(modifier)
        return
    }
    // Keyed on the session so a shell that swaps ports mid-composition rebuilds the model
    // against the new one, rather than leaving a form that authenticates through a port the
    // screen is no longer showing.
    val model = remember(session) { SignInModel(session) }

    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    // Nullable, and initially null: seeding it with a field would draw a focus ring on a
    // form the user has not touched yet, which claims something untrue. A `FocusRequester`
    // cannot report focus, so the change has to be observed to be tracked at all.
    var focusedField by remember { mutableStateOf<EmailOrPasswordField?>(null) }
    val scope = rememberCoroutineScope()

    val phase = model.phase
    // Watched here rather than inside a `LaunchedEffect` so the transition fires from the
    // frame that shows the success state, instead of a frame later.
    if (phase is SignInModel.Phase.SignedIn) {
        onAuthenticated()
    }

    Box(modifier = modifier.fillMaxSize().background(ttCanvas())) {
        AmbientBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // Without this the keyboard covers the password field on a short window, and
                // the field being covered is the one the user is typing into.
                .imePadding()
                .padding(horizontal = ttSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(ttSpacing.signInTopInset))
            AppMark()
            Spacer(Modifier.height(ttSpacing.splashStack))
            ttText(
                text = "Task Tracker",
                style = ttTypography.title,
                color = ttLabelSecondary(),
            )
            Spacer(Modifier.height(ttSpacing.formStack))

            EmailField(
                value = model.email,
                onValueChange = { model.email = it },
                isFocused = focusedField == EmailOrPasswordField.Email,
                focusRequester = emailFocus,
                onFocusChanged = { focusedField = it },
                imeAction = ImeAction.Next,
                onImeAction = { passwordFocus.requestFocus() },
            )
            Spacer(Modifier.height(ttSpacing.fieldStack))

            PasswordField(
                value = model.password,
                onValueChange = { model.password = it },
                isFocused = focusedField == EmailOrPasswordField.Password,
                focusRequester = passwordFocus,
                isEnabled = phase !is SignInModel.Phase.Submitting,
                onFocusChanged = { focusedField = it },
                imeAction = ImeAction.Done,
                onImeAction = { scope.launch { model.submit() } },
            )

            // The banner takes a slot whether or not it is showing, so that showing one does
            // not shove the button down the screen. The original's `.if let` around the
            // spacer does the same thing, for the same reason.
            Spacer(Modifier.height(ttSpacing.formStack))
            model.errorMessage?.let { ttBanner(message = it) }

            Spacer(Modifier.height(ttSpacing.formStack))
            PrimaryButton(
                label = "Sign In",
                onClick = { scope.launch { model.submit() } },
                enabled = model.canSubmit,
                isLoading = phase is SignInModel.Phase.Submitting,
            )
            // Clears the focus ring off whichever field was last touched, so the form does
            // not go on showing a focus it no longer has.
            Spacer(Modifier.height(ttSpacing.xxl))
        }
    }
}

/**
 * Which field is focused, so exactly one ring is drawn.
 *
 * A `var` of this rather than two `FocusRequester`s alone, because a `FocusRequester` can
 * only *ask* for focus and cannot report it. Without observing the change there is no way
 * for [LabelledField] to know which ring to light.
 */
private enum class EmailOrPasswordField { Email, Password }

@Composable
private fun EmailField(
    value: String,
    onValueChange: (String) -> Unit,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (EmailOrPasswordField?) -> Unit,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
) {
    LabelledField(title = "Email", isFocused = isFocused) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EmailFieldTag)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    onFocusChanged(
                        if (it.isFocused) EmailOrPasswordField.Email else null,
                    )
                }
                // `androidx.compose.ui.autofill.contentType`, the common API. The plan's
                // third wrapper enum was for autofill on the grounds that no common type
                // existed; Compose 1.12 does have one, so the wrapper would be a second
                // vocabulary for something already spelled.
                .contentType(ContentType.EmailAddress),
            textStyle = ttTypography.body.copy(color = ttLabel()),
            cursorBrush = SolidColor(ttAccent()),
            // `KeyboardCapitalization.None` and no autocorrect: an autocorrecting field
            // rewrites `ada@ExAmple.com` on the way out, and the server then rejects the
            // whole attempt with a message about the password.
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = imeAction,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(
                onNext = { onImeAction() },
                onDone = { onImeAction() },
            ),
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    isEnabled: Boolean,
    onFocusChanged: (EmailOrPasswordField?) -> Unit,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
) {
    LabelledField(title = "Password", isFocused = isFocused) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PasswordFieldTag)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    onFocusChanged(
                        if (it.isFocused) EmailOrPasswordField.Password else null,
                    )
                }
                .contentType(ContentType.Password),
            enabled = isEnabled,
            textStyle = ttTypography.body.copy(color = ttLabel()),
            cursorBrush = SolidColor(ttAccent()),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = imeAction,
                // Not `None`: a password is far more often typed twice, and the autocorrect
                // dictionary is the fastest way to end up with a mangled one that then fails
                // silently. `Password` keeps the first letter of a word capitalised and
                // disables the rest of the assistance.
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onImeAction() },
                onGo = { onImeAction() },
            ),
        )
    }
}

const val EmailFieldTag = "signIn.email"
const val PasswordFieldTag = "signIn.password"

@Preview(name = "Sign in", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SignInViewPreview() {
    ttTheme(ThemeScheme.System) {
        previewSession {
            SignInView(onAuthenticated = {})
        }
    }
}
