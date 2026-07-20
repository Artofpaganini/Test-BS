package com.zorderlab

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onexui.demo.XbsDemoActivity

// Единая точка входа приложения: развилка между двумя экспериментами лабы. Кнопка запускает
// соответствующую Activity explicit-интентом; код самих экспериментов не менялся — LAUNCHER переехал сюда.
class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LabTheme {
                LauncherScreen(
                    onOpenZOrderLab = { startActivity(Intent(this, MainActivity::class.java)) },
                    onOpenXbsDemo = { startActivity(Intent(this, XbsDemoActivity::class.java)) },
                )
            }
        }
    }
}

@Composable
private fun LauncherScreen(
    onOpenZOrderLab: () -> Unit,
    onOpenXbsDemo: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "BS Z-Order Lab", style = MaterialTheme.typography.headlineSmall)
            Button(modifier = Modifier.fillMaxWidth(), onClick = onOpenZOrderLab) {
                Text(text = "Z-Order лаба (кнопки 1–8)")
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = onOpenXbsDemo) {
                Text(text = "XBS Demo (кейсы a–k)")
            }
        }
    }
}
