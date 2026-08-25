package com.honeyfile.security.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp

private fun buildVector(name: String, block: PathBuilder.() -> Unit): ImageVector {
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        addPath(
            fill = SolidColor(Color.White),
            pathData = PathBuilder().apply(block).nodes
        )
    }.build()
}

object HoneyIcons {

    val Security: ImageVector = buildVector("Security") {
        moveTo(12.0f, 1.0f)
        lineTo(3.0f, 5.0f)
        verticalLineToRelative(6.0f)
        curveToRelative(0.0f, 5.55f, 3.84f, 10.74f, 9.0f, 12.0f)
        curveToRelative(5.16f, -1.26f, 9.0f, -6.45f, 9.0f, -12.0f)
        verticalLineTo(5.0f)
        lineTo(12.0f, 1.0f)
        close()
    }

    val Article: ImageVector = buildVector("Article") {
        moveTo(19.0f, 3.0f)
        horizontalLineTo(5.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(14.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(14.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineTo(5.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        close()
        moveTo(14.0f, 17.0f)
        horizontalLineTo(7.0f)
        verticalLineToRelative(-2.0f)
        horizontalLineToRelative(7.0f)
        verticalLineToRelative(2.0f)
        close()
        moveTo(17.0f, 13.0f)
        horizontalLineTo(7.0f)
        verticalLineToRelative(-2.0f)
        horizontalLineToRelative(10.0f)
        verticalLineToRelative(2.0f)
        close()
        moveTo(17.0f, 9.0f)
        horizontalLineTo(7.0f)
        verticalLineTo(7.0f)
        horizontalLineToRelative(10.0f)
        verticalLineToRelative(2.0f)
        close()
    }

    val PhotoLibrary: ImageVector = buildVector("PhotoLibrary") {
        moveTo(22.0f, 16.0f)
        verticalLineTo(4.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        horizontalLineTo(8.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(12.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(12.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        close()
        moveTo(11.0f, 12.0f)
        lineToRelative(2.03f, 2.71f)
        lineTo(16.0f, 11.0f)
        lineToRelative(4.0f, 5.0f)
        horizontalLineTo(8.0f)
        lineToRelative(3.0f, -4.0f)
        close()
        moveTo(2.0f, 6.0f)
        verticalLineToRelative(14.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(14.0f)
        verticalLineToRelative(-2.0f)
        horizontalLineTo(4.0f)
        verticalLineTo(6.0f)
        horizontalLineTo(2.0f)
        close()
    }

    val Analytics: ImageVector = buildVector("Analytics") {
        moveTo(19.0f, 3.0f)
        horizontalLineTo(5.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(14.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(14.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineTo(5.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        close()
        moveTo(9.0f, 17.0f)
        horizontalLineTo(7.0f)
        verticalLineToRelative(-5.0f)
        horizontalLineToRelative(2.0f)
        verticalLineToRelative(5.0f)
        close()
        moveTo(13.0f, 17.0f)
        horizontalLineToRelative(-2.0f)
        verticalLineToRelative(-8.0f)
        horizontalLineToRelative(2.0f)
        verticalLineToRelative(8.0f)
        close()
        moveTo(17.0f, 17.0f)
        horizontalLineToRelative(-2.0f)
        verticalLineToRelative(-4.0f)
        horizontalLineToRelative(2.0f)
        verticalLineToRelative(4.0f)
        close()
    }

    val FlashOn: ImageVector = buildVector("FlashOn") {
        moveTo(7.0f, 2.0f)
        verticalLineToRelative(11.0f)
        horizontalLineToRelative(3.0f)
        verticalLineToRelative(9.0f)
        lineToRelative(7.0f, -12.0f)
        horizontalLineToRelative(-4.0f)
        lineToRelative(4.0f, -8.0f)
        close()
    }

    val People: ImageVector = buildVector("People") {
        moveTo(16.0f, 11.0f)
        curveToRelative(1.66f, 0.0f, 2.99f, -1.34f, 2.99f, -3.0f)
        reflectiveCurveTo(17.66f, 5.0f, 16.0f, 5.0f)
        curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
        reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
        close()
        moveTo(8.0f, 11.0f)
        curveToRelative(1.66f, 0.0f, 2.99f, -1.34f, 2.99f, -3.0f)
        reflectiveCurveTo(9.66f, 5.0f, 8.0f, 5.0f)
        curveTo(6.34f, 5.0f, 5.0f, 6.34f, 5.0f, 8.0f)
        reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
        close()
        moveTo(8.0f, 13.0f)
        curveToRelative(-2.33f, 0.0f, -7.0f, 1.17f, -7.0f, 3.5f)
        verticalLineTo(19.0f)
        horizontalLineToRelative(14.0f)
        verticalLineToRelative(-2.5f)
        curveToRelative(0.0f, -2.33f, -4.67f, -3.5f, -7.0f, -3.5f)
        close()
        moveTo(16.0f, 13.0f)
        curveToRelative(-0.29f, 0.0f, -0.62f, 0.02f, -0.97f, 0.05f)
        curveToRelative(1.16f, 0.84f, 1.97f, 1.97f, 1.97f, 3.45f)
        verticalLineTo(19.0f)
        horizontalLineToRelative(6.0f)
        verticalLineToRelative(-2.5f)
        curveToRelative(0.0f, -2.33f, -4.67f, -3.5f, -7.0f, -3.5f)
        close()
    }

    val Folder: ImageVector = buildVector("Folder") {
        moveTo(10.0f, 4.0f)
        horizontalLineTo(4.0f)
        curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
        lineTo(2.0f, 18.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(16.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineTo(8.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        horizontalLineToRelative(-8.0f)
        lineToRelative(-2.0f, -2.0f)
        close()
    }

    val FolderOpen: ImageVector = buildVector("FolderOpen") {
        moveTo(20.0f, 6.0f)
        horizontalLineToRelative(-8.0f)
        lineToRelative(-2.0f, -2.0f)
        horizontalLineTo(4.0f)
        curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
        lineTo(2.0f, 18.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(16.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineTo(8.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        close()
        moveTo(20.0f, 18.0f)
        horizontalLineTo(4.0f)
        verticalLineTo(8.0f)
        horizontalLineToRelative(16.0f)
        verticalLineToRelative(10.0f)
        close()
    }

    val FolderSpecial: ImageVector = buildVector("FolderSpecial") {
        moveTo(20.0f, 6.0f)
        horizontalLineToRelative(-8.0f)
        lineToRelative(-2.0f, -2.0f)
        horizontalLineTo(4.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(12.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(16.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineTo(8.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        close()
        moveTo(17.99f, 17.0f)
        lineTo(15.0f, 15.28f)
        lineTo(12.01f, 17.0f)
        lineToRelative(0.77f, -3.42f)
        lineToRelative(-2.61f, -2.27f)
        lineToRelative(3.49f, -0.3f)
        lineTo(15.0f, 7.78f)
        lineToRelative(1.34f, 3.23f)
        lineToRelative(3.49f, 0.3f)
        lineToRelative(-2.61f, 2.27f)
        lineToRelative(0.77f, 3.42f)
        close()
    }

    val KeyboardArrowUp: ImageVector = buildVector("KeyboardArrowUp") {
        moveTo(7.41f, 15.41f)
        lineTo(12.0f, 10.83f)
        lineToRelative(4.59f, 4.58f)
        lineTo(18.0f, 14.0f)
        lineToRelative(-6.0f, -6.0f)
        lineToRelative(-6.0f, 6.0f)
        close()
    }

    val KeyboardArrowDown: ImageVector = buildVector("KeyboardArrowDown") {
        moveTo(7.41f, 8.59f)
        lineTo(12.0f, 13.17f)
        lineToRelative(4.59f, -4.58f)
        lineTo(18.0f, 10.0f)
        lineToRelative(-6.0f, 6.0f)
        lineToRelative(-6.0f, -6.0f)
        lineToRelative(1.41f, -1.41f)
        close()
    }

    val CameraAlt: ImageVector = buildVector("CameraAlt") {
        moveTo(12.0f, 12.0f)
        moveToRelative(-3.2f, 0.0f)
        arcToRelative(3.2f, 3.2f, 0.0f, isMoreThanHalf = true, isPositiveArc = true, 6.4f, 0.0f)
        arcToRelative(3.2f, 3.2f, 0.0f, isMoreThanHalf = true, isPositiveArc = true, -6.4f, 0.0f)
        moveTo(9.0f, 2.0f)
        lineTo(7.17f, 4.0f)
        horizontalLineTo(4.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(12.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(16.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineTo(6.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        horizontalLineToRelative(-3.17f)
        lineTo(15.0f, 2.0f)
        horizontalLineTo(9.0f)
        close()
        moveTo(12.0f, 17.0f)
        curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
        reflectiveCurveToRelative(2.24f, -5.0f, 5.0f, -5.0f)
        reflectiveCurveToRelative(5.0f, 2.24f, 5.0f, 5.0f)
        reflectiveCurveToRelative(-2.24f, 5.0f, -5.0f, 5.0f)
        close()
    }

    val PersonAdd: ImageVector = buildVector("PersonAdd") {
        moveTo(15.0f, 12.0f)
        curveToRelative(2.21f, 0.0f, 4.0f, -1.79f, 4.0f, -4.0f)
        reflectiveCurveToRelative(-1.79f, -4.0f, -4.0f, -4.0f)
        reflectiveCurveToRelative(-4.0f, 1.79f, -4.0f, 4.0f)
        reflectiveCurveToRelative(1.79f, 4.0f, 4.0f, 4.0f)
        close()
        moveTo(6.0f, 10.0f)
        verticalLineTo(7.0f)
        horizontalLineTo(4.0f)
        verticalLineToRelative(3.0f)
        horizontalLineTo(1.0f)
        verticalLineToRelative(2.0f)
        horizontalLineToRelative(3.0f)
        verticalLineToRelative(3.0f)
        horizontalLineToRelative(2.0f)
        verticalLineToRelative(-3.0f)
        horizontalLineToRelative(3.0f)
        verticalLineToRelative(-2.0f)
        horizontalLineTo(6.0f)
        close()
        moveTo(15.0f, 14.0f)
        curveToRelative(-2.67f, 0.0f, -8.0f, 1.34f, -8.0f, 4.0f)
        verticalLineToRelative(2.0f)
        horizontalLineToRelative(16.0f)
        verticalLineToRelative(-2.0f)
        curveToRelative(0.0f, -2.66f, -5.33f, -4.0f, -8.0f, -4.0f)
        close()
    }

    val ElectricBolt: ImageVector = buildVector("ElectricBolt") {
        moveTo(15.0f, 2.0f)
        lineTo(3.0f, 14.0f)
        horizontalLineToRelative(6.5f)
        lineTo(7.0f, 22.0f)
        lineToRelative(12.0f, -12.0f)
        horizontalLineToRelative(-6.5f)
        lineTo(15.0f, 2.0f)
        close()
    }

    val FileDownload: ImageVector = buildVector("FileDownload") {
        moveTo(19.0f, 9.0f)
        horizontalLineToRelative(-4.0f)
        verticalLineTo(3.0f)
        horizontalLineTo(9.0f)
        verticalLineToRelative(6.0f)
        horizontalLineTo(5.0f)
        lineToRelative(7.0f, 7.0f)
        lineToRelative(7.0f, -7.0f)
        close()
        moveTo(5.0f, 18.0f)
        verticalLineToRelative(2.0f)
        horizontalLineToRelative(14.0f)
        verticalLineToRelative(-2.0f)
        horizontalLineTo(5.0f)
        close()
    }
}
