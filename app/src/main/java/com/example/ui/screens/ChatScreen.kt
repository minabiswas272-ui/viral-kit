package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.studio.StudioViewModel

@Composable
fun ChatScreen(
    viewModel: StudioViewModel,
    onShowToast: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    StrategyChatScreen(
        viewModel = viewModel,
        onShowToast = onShowToast,
        modifier = modifier
    )
}
