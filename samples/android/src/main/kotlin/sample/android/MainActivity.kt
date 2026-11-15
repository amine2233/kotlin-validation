package sample.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.amine2233.validation.*
import io.github.amine2233.validation.forms.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Scaffold { padding -> SignUpScreen(Modifier.padding(padding)) } } }
    }
}

@Composable
fun SignUpScreen(modifier: Modifier = Modifier) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("") }
    val form = SignUpForm(email, password, age)

    Column(modifier.safeDrawingPadding().padding(16.dp)) {
        ValidatedField("Email", email, form.validation(SignUpForm.Field.Email)) { email = it }
        ValidatedField("Password", password, form.validation(SignUpForm.Field.Password)) { password = it }
        ValidatedField("Age", age, form.validation(SignUpForm.Field.Age)) { age = it }
        Button(
            enabled = form.isValid,
            onClick = {
                result =
                    try {
                        form.validate()
                        "✅ Sign-up payload is valid"
                    } catch (error: ValidateErrors) {
                        "❌ ${error.reason}"
                    }
            },
        ) { Text("Sign up") }
        Text(result, Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun ValidatedField(
    label: String,
    value: String,
    validation: FieldValidation,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = !validation.isValid,
        supportingText = validation.message?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}
