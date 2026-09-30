package solutions.laxmi.omnicompiler.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Lucide-style line icons (24 grid, 2 px stroke, square ends) matching the design's inline SVGs.
 * Built lazily from path data so the set stays declarative and cheap.
 */
object OmniIcons {
    val Menu by icon("M4 6h16", "M4 12h16", "M4 18h10")
    val Search by icon(circle(11f, 11f, 7f), "m20 20-3.5-3.5")
    val Minimap by icon(rect(3f, 3f, 18f, 18f), "M16 3v18")
    val MoreVertical by icon(circle(12f, 5f, 1f), circle(12f, 12f, 1f), circle(12f, 19f, 1f))
    val Play by filled("M6 3 20 12 6 21Z")
    val Stop by filled("M6 6h12v12H6Z")
    val ChevronDown by icon("m6 9 6 6 6-6")
    val ChevronUp by icon("m18 15-6-6-6 6")
    val ChevronRight by icon("m9 18 6-6-6-6")
    val ChevronLeft by icon("m15 18-6-6 6-6")
    val Plus by icon("M5 12h14", "M12 5v14")
    val Close by icon("M18 6 6 18", "M6 6l12 12")
    val Undo by icon("M3 7v6h6", "M21 17a9 9 0 0 0-15-6.7L3 13")
    val Redo by icon("M21 7v6h-6", "M3 17a9 9 0 0 1 15-6.7L21 13")
    val Filter by icon("M22 3H2l8 9.46V19l4 2v-8.54z")
    val Trash by icon("M3 6h18", "M8 6V4h8v2", "M19 6l-1 14H6L5 6")
    val Copy by icon(rect(9f, 9f, 13f, 13f), "M5 15H2V2h13v3")
    val ArrowLeft by icon("M19 12H5", "M12 19l-7-7 7-7")
    val ArrowRight by icon("M5 12h14", "M12 5l7 7-7 7")
    val Check by icon("M20 6 9 17l-5-5")
    val Mail by icon(rect(2f, 4f, 20f, 16f), "m22 7-10 6L2 7")
    val Lock by icon(rect(3f, 11f, 18f, 11f), "M7 11V7a5 5 0 0 1 10 0v4")
    val Eye by icon("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", circle(12f, 12f, 3f))
    val EyeOff by icon(
        "M9.88 9.88a3 3 0 1 0 4.24 4.24",
        "M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68",
        "M6.61 6.61A13.53 13.53 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61",
        "M2 2l20 20",
    )
    val History by icon("M3 12a9 9 0 1 0 3-6.7L3 8", "M3 3v5h5", "M12 7v5l4 2")
    val Chart by icon("M3 3v18h18", "M8 17V9", "M13 17V5", "M18 17v-3")
    val Key by icon(circle(7.5f, 15.5f, 5.5f), "m21 2-9.6 9.6", "m15.5 7.5 3 3L22 7l-3-3")
    val Settings by icon("M20 7h-9", "M14 17H5", circle(17f, 17f, 3f), circle(7f, 7f, 3f))
    val WifiOff by icon(
        "M2 2l20 20", "M8.5 16.5a5 5 0 0 1 7 0", "M2 8.82a15 15 0 0 1 4.17-2.65",
        "M10.66 5c4.01-.36 8.14.9 11.34 3.76", "M16.85 11.25a10 10 0 0 1 2.22 1.68",
        "M5 13a10 10 0 0 1 5.24-2.76", "M12 20h.01",
    )
    val File by icon("M14 2H6v20h12V8z", "M14 2v6h6")
    val Folder by icon("M4 4h6l2 3h8v13H4z")
    val Refresh by icon("M3 12a9 9 0 0 1 15-6.7L21 8", "M21 3v5h-5", "M21 12a9 9 0 0 1-15 6.7L3 16", "M8 16H3v5")
    val Download by icon("M21 15v6H3v-6", "M7 10l5 5 5-5", "M12 15V3")
    val Upload by icon("M21 15v6H3v-6", "M17 8l-5-5-5 5", "M12 3v12")
    val Share by icon(
        circle(18f, 5f, 3f), circle(6f, 12f, 3f), circle(18f, 19f, 3f),
        "M8.59 13.51l6.83 3.98", "M15.41 6.51l-6.82 3.98",
    )
    val ExternalLink by icon("M15 3h6v6", "M10 14 21 3", "M18 13v8H3V6h8")
    val User by icon(circle(12f, 8f, 4f), "M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1")
    val LogOut by icon("M9 21H3V3h6", "M16 17l5-5-5-5", "M21 12H9")
    val Book by icon("M4 19.5V4h16v18H6.5A2.5 2.5 0 0 1 4 19.5z", "M4 19.5A2.5 2.5 0 0 1 6.5 17H20")
    val Zap by icon("M13 2 3 14h9l-1 8 10-12h-9l1-8z")
    val Braces by icon(
        "M8 3H7a2 2 0 0 0-2 2v5a2 2 0 0 1-2 2 2 2 0 0 1 2 2v5a2 2 0 0 0 2 2h1",
        "M16 21h1a2 2 0 0 0 2-2v-5a2 2 0 0 1 2-2 2 2 0 0 1-2-2V5a2 2 0 0 0-2-2h-1",
    )
    val Alert by icon("M12 3 2 21h20z", "M12 10v4", "M12 17h.01")
    val Info by icon(circle(12f, 12f, 10f), "M12 16v-4", "M12 8h.01")
    val Edit by icon("M17 3l4 4L7 21H3v-4z")
    val ShieldCheck by icon("M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z", "M9 12l2 2 4-4")
    val Timer by icon(circle(12f, 13f, 8f), "M12 9v4l2 2", "M9 2h6")
    val WrapText by icon("M3 6h18", "M3 12h15a3 3 0 1 1 0 6h-4", "M16 16l-2 2 2 2", "M3 18h7")
    val Sort by icon("M3 16l4 4 4-4", "M7 20V4", "M21 8l-4-4-4 4", "M17 4v16")
    val Layers by icon("m12 2 10 5-10 5L2 7z", "m2 17 10 5 10-5", "m2 12 10 5 10-5")
    val Link by icon("M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71", "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71")
    val Terminal by icon("m4 17 6-6-6-6", "M12 19h8")
    val Keyboard by icon(rect(2f, 5f, 20f, 14f), "M6 9h.01", "M10 9h.01", "M14 9h.01", "M18 9h.01", "M7 15h10")

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun rect(x: Float, y: Float, w: Float, h: Float) = "M$x ${y}h${w}v${h}h${-w}Z"

    private fun icon(vararg paths: String) = lazy { build(paths, filled = false) }

    private fun filled(vararg paths: String) = lazy { build(paths, filled = true) }

    private fun build(paths: Array<out String>, filled: Boolean): ImageVector =
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach { data ->
                    addPath(
                        pathData = addPathNodes(data),
                        fill = if (filled) SolidColor(Color.Black) else null,
                        stroke = if (filled) null else SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Miter,
                    )
                }
            }
            .build()
}
