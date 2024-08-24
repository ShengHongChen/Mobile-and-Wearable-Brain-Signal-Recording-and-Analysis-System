package com.example.powernap.presentation.permissions

import androidx.compose.foundation.layout.Column
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.yml.charts.axis.AxisData
import co.yml.charts.ui.linechart.model.LineStyle
import com.example.powernap.presentation.PowerNapViewModel
import co.yml.charts.ui.linechart.LineChart
import co.yml.charts.ui.linechart.model.Line
import co.yml.charts.ui.linechart.model.LineChartData
import co.yml.charts.ui.linechart.model.LinePlotData
import co.yml.charts.ui.linechart.model.LineType
import kotlin.math.max

@Composable
fun WaveChartScreen(viewModel: PowerNapViewModel = viewModel()) {

    val pointsData1 = viewModel.pointsData1
    val pointsData2 = viewModel.pointsData2
    val steps = 5

    val xAxisData = AxisData.Builder()
        .axisStepSize(10.dp)
        .backgroundColor(Color.Transparent)
        .steps(max(pointsData1.size, pointsData2.size) / 2 - 1)

        .labelAndAxisLinePadding(3.dp)
        .axisLineColor(MaterialTheme.colors.primary)
        .axisLabelColor(MaterialTheme.colors.primary)
        .build()

    val yAxisData = AxisData.Builder()
        .axisStepSize(10.dp)
        .steps(steps)
        .backgroundColor(Color.Transparent)
        .labelAndAxisLinePadding(3.dp)
        .axisLineColor(MaterialTheme.colors.primary)
        .axisLabelColor(MaterialTheme.colors.primary)

        .build()

    val lineChartData = LineChartData(
        linePlotData = LinePlotData(
            lines = listOf(
                Line(
                    dataPoints = pointsData1,
                    LineStyle(
                        color = MaterialTheme.colors.primary,
                        lineType = LineType.Straight(isDotted = false),
                    )
                ),
                Line(
                    dataPoints = pointsData2,
                    LineStyle(
                        color = MaterialTheme.colors.secondary,
                        lineType = LineType.Straight(isDotted = false)
                    )
                )
            )
        ),
        backgroundColor = Color.DarkGray,
        xAxisData = xAxisData,
        yAxisData = yAxisData
    )

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Voltage (µV)",
            style = MaterialTheme.typography.subtitle2,
            modifier = Modifier.padding(start = 16.dp)
        )
        LineChart(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
            lineChartData = lineChartData
        )
        Text(
            text = "time (s)",
            style = MaterialTheme.typography.subtitle2,
            modifier = Modifier.align(Alignment.End).padding(end = 16.dp, top = 8.dp)
        )
    }
}
