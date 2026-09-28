package com.imagepreptool.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.imagepreptool.model.WorkflowStep
import com.imagepreptool.state.AppState

@Composable
fun App() {
    val state = remember { AppState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        state.refreshToolCheck()
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = state.currentStep.ordinal) {
                    WorkflowStep.entries.forEach { step ->
                        Tab(
                            selected = state.currentStep == step,
                            onClick = { state.goToStep(step) },
                            text = { Text(step.label) },
                        )
                    }
                }

                when (state.currentStep) {
                    WorkflowStep.Select -> Step1SelectScreen(state)
                    WorkflowStep.Edit -> Step2EditScreen(state, scope)
                }

                StatusBar(state)
            }
        }
    }
}

@Composable
private fun StatusBar(state: AppState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(state.statusMessage, style = MaterialTheme.typography.body2)
        if (state.toolsChecked) {
            val ok = state.toolStatuses.count { it.available }
            Text("外部ツール: $ok / ${state.toolStatuses.size}", style = MaterialTheme.typography.body2)
        }
    }
}
