package com.example.powernap.presentation.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.yml.charts.axis.AxisData
import co.yml.charts.common.model.Point
import co.yml.charts.ui.barchart.BarChart
import co.yml.charts.ui.barchart.models.BarChartData
import co.yml.charts.ui.barchart.models.BarData
import com.example.powernap.presentation.PowerNapViewModel

val softBlue = Color(0xFF738290)
val softGreen = Color(0xFF789E88)
val softRed = Color(0xFFA67676)
val softYellow = Color(0xFFD9CAB3)

@Composable
fun BarChartScreen(viewModel: PowerNapViewModel = viewModel()) {

    val stepSize = 6
    val customLabels = arrayOf("δ", "θ", "α", "β")
    val barsData = listOf(
        BarData(
            point = Point(1f, viewModel.dData),
            color = softBlue,
        ),
        BarData(
            point = Point(2f, viewModel.tData),
            color = softGreen,
        ),
        BarData(
            point = Point(3f, viewModel.aData),
            color = softRed,
        ),
        BarData(
            point = Point(4f, viewModel.bData),
            color = softYellow,
        )
    )

    val xAxisData = AxisData.Builder()
        .axisStepSize(400.dp)
        .steps(barsData.size - 1)
        .bottomPadding(40.dp)
        .startDrawPadding(30.dp)
        .labelData { index -> customLabels.getOrNull(index) ?: "N/A"  }
        .build()

    val yAxisData = AxisData.Builder()
        .steps(stepSize)
        .labelAndAxisLinePadding(20.dp)
        .axisOffset(20.dp)
        .labelData {  index -> val value = 2E-6 + index * 2E-6
            String.format("%.1E", value)
        }
        .build()

    val barChartData = BarChartData(
        chartData = barsData,
        xAxisData = xAxisData,
        yAxisData = yAxisData
    )

    BarChart(
        modifier = Modifier
            .width(300.dp)
            .height(300.dp)
            .background(Color.LightGray),
        barChartData = barChartData)
    Text(
        text = "Hz",
        style = MaterialTheme.typography.subtitle2,
        modifier = Modifier.padding(start = 16.dp)
    )
}
