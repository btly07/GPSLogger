package com.example.savegpsdata

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LocationScreen(viewModel: LocationViewModel, onLowPowerToggle: (Boolean) -> Unit) {
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {

        Text(
            text = viewModel.locationText.value,
            fontSize = 16.sp,
            color = MaterialTheme.colors.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = viewModel.satelliteText.value,
            fontSize = 16.sp,
            color = MaterialTheme.colors.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "節電モード：${if (viewModel.lowPowerMode.value) "ON" else "OFF"}",
                fontSize = 16.sp,
                color = MaterialTheme.colors.onBackground
            )
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                modifier = Modifier.padding(end = 4.dp),
                checked = viewModel.lowPowerMode.value,
                onCheckedChange = { isChecked ->
                    viewModel.toggleLowPowerMode(isChecked)
                    onLowPowerToggle(isChecked)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF66BB6A),     // 明るい緑
                    checkedTrackColor = Color(0xFF388E3C),     // 濃い緑
                    uncheckedThumbColor = Color(0xFFCCCCCC),   // 薄グレー
                    uncheckedTrackColor = Color(0xFF888888),   // やや明るい濃グレー
                    disabledCheckedThumbColor = Color(0xFFA5D6A7),
                    disabledCheckedTrackColor = Color(0xFF81C784),
                    disabledUncheckedThumbColor = Color(0xFFEEEEEE),
                    disabledUncheckedTrackColor = Color(0xFFBDBDBD)
                )

            )
        }
    }
}
