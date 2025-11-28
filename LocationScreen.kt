package com.example.savegpsdata

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LocationScreen(viewModel: LocationViewModel) {
    Column(modifier = Modifier.padding(16.dp)) {
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
    }
}
