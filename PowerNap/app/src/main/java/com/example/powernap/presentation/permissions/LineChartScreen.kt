package com.example.powernap.presentation.permissions

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import co.yml.charts.axis.AxisData
import co.yml.charts.ui.linechart.LineChart
import co.yml.charts.ui.linechart.model.Line
import co.yml.charts.ui.linechart.model.LineChartData
import co.yml.charts.ui.linechart.model.LinePlotData
import co.yml.charts.ui.linechart.model.LineStyle
import co.yml.charts.ui.linechart.model.LineType
import com.example.powernap.presentation.PowerNapViewModel
import kotlin.math.max

@Composable
fun LineChartScreen(viewModel: PowerNapViewModel = viewModel()) {

    val pointsData1 = viewModel.pointsData1
    val pointsData2 = viewModel.pointsData2

    val steps = 5
    val xAxisData = AxisData.Builder()
        .axisStepSize(10.dp)
        .backgroundColor(Color.Transparent)
        .steps(max(pointsData1.size, pointsData2.size) / 2 - 1)
        .labelData { i -> (i * 2).toString() }
        .labelAndAxisLinePadding(3.dp)
        .axisLineColor(MaterialTheme.colors.primary)
        .axisLabelColor(MaterialTheme.colors.primary)
        .build()

    val yAxisData = AxisData.Builder()
        .steps(steps)
        .backgroundColor(Color.Transparent)
        .labelAndAxisLinePadding(3.dp)
        .labelData { i ->
            val yMin = -450f
            val yMax = 300f
            val yRange = yMax - yMin
            val yStep = yRange / steps
            val yValue = yMin + i * yStep

            String.format("%.2f", yValue)
        }
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
        backgroundColor = MaterialTheme.colors.background,
        xAxisData = xAxisData,
        yAxisData = yAxisData
    )

    LineChart(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp),
        lineChartData = lineChartData
    )
}

