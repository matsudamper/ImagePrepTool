package com.imagepreptool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imagepreptool.model.WorkflowStep
import com.imagepreptool.presentation.ImagePrepUiState
import com.imagepreptool.presentation.ImagePrepViewModel

@Composable
fun App() {
    val viewModel = remember { ImagePrepViewModel() }
    val uiState by viewModel.uiStateFlow.collectAsState()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = uiState.currentStep.ordinal) {
                    WorkflowStep.entries.forEach { step ->
                        Tab(
                            selected = uiState.currentStep == step,
                            onClick = { viewModel.goToStep(step) },
                            text = { Text(step.label) },
                        )
                    }
                }

                when (uiState.currentStep) {
                    WorkflowStep.Select -> Step1SelectScreen(uiState, viewModel)
                    WorkflowStep.Edit -> Step2EditScreen(uiState, viewModel)
                }

                StatusBar(uiState)
            }
        }
    }
}

@Composable
private fun StatusBar(uiState: ImagePrepUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(uiState.statusMessage, style = MaterialTheme.typography.body2)
        if (uiState.toolsChecked) {
            val ok = uiState.toolStatuses.count { it.available }
            Text("外部ツール: $ok / ${uiState.toolStatuses.size}", style = MaterialTheme.typography.body2)
        }
    }
}
