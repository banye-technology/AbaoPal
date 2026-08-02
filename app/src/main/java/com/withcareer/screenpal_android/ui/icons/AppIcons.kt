package com.withcareer.screenpal_android.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 自定义图标集合，替代 material-icons-extended
 */
object AppIcons {
    val ContentCopy: ImageVector
        get() {
            if (_contentCopy != null) return _contentCopy!!
            _contentCopy = ImageVector.Builder(
                name = "ContentCopy",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(16.0f, 1.0f)
                    lineTo(4.0f, 1.0f)
                    curveTo(2.9f, 1.0f, 2.0f, 1.9f, 2.0f, 3.0f)
                    verticalLineToRelative(14.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineTo(3.0f)
                    horizontalLineToRelative(12.0f)
                    verticalLineTo(1.0f)
                    close()
                    moveTo(19.0f, 5.0f)
                    lineTo(8.0f, 5.0f)
                    curveTo(6.9f, 5.0f, 6.0f, 5.9f, 6.0f, 7.0f)
                    verticalLineToRelative(14.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(11.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    verticalLineTo(7.0f)
                    curveTo(21.0f, 5.9f, 20.1f, 5.0f, 19.0f, 5.0f)
                    close()
                    moveTo(19.0f, 21.0f)
                    lineTo(8.0f, 21.0f)
                    verticalLineTo(7.0f)
                    horizontalLineToRelative(11.0f)
                    verticalLineTo(21.0f)
                    close()
                }
            }.build()
            return _contentCopy!!
        }
    private var _contentCopy: ImageVector? = null

    val KeyboardArrowDown: ImageVector
        get() {
            if (_keyboardArrowDown != null) return _keyboardArrowDown!!
            _keyboardArrowDown = ImageVector.Builder(
                name = "KeyboardArrowDown",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(7.41f, 8.59f)
                    lineTo(12.0f, 13.17f)
                    lineToRelative(4.59f, -4.58f)
                    lineTo(18.0f, 10.0f)
                    lineToRelative(-6.0f, 6.0f)
                    lineToRelative(-6.0f, -6.0f)
                    lineTo(7.41f, 8.59f)
                    close()
                }
            }.build()
            return _keyboardArrowDown!!
        }
    private var _keyboardArrowDown: ImageVector? = null

    val KeyboardArrowUp: ImageVector
        get() {
            if (_keyboardArrowUp != null) return _keyboardArrowUp!!
            _keyboardArrowUp = ImageVector.Builder(
                name = "KeyboardArrowUp",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(7.41f, 15.41f)
                    lineTo(12.0f, 10.83f)
                    lineToRelative(4.59f, 4.58f)
                    lineTo(18.0f, 14.0f)
                    lineToRelative(-6.0f, -6.0f)
                    lineToRelative(-6.0f, 6.0f)
                    lineTo(7.41f, 15.41f)
                    close()
                }
            }.build()
            return _keyboardArrowUp!!
        }
    private var _keyboardArrowUp: ImageVector? = null

    val OpenInFull: ImageVector
        get() {
            if (_openInFull != null) return _openInFull!!
            _openInFull = ImageVector.Builder(
                name = "OpenInFull",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(21.0f, 11.0f)
                    verticalLineTo(3.0f)
                    horizontalLineToRelative(-8.0f)
                    lineToRelative(3.29f, 3.29f)
                    lineToRelative(-4.17f, 4.17f)
                    lineToRelative(1.42f, 1.42f)
                    lineToRelative(4.17f, -4.17f)
                    lineTo(21.0f, 11.0f)
                    close()
                    moveTo(3.0f, 13.0f)
                    verticalLineToRelative(8.0f)
                    horizontalLineToRelative(8.0f)
                    lineToRelative(-3.29f, -3.29f)
                    lineToRelative(4.17f, -4.17f)
                    lineToRelative(-1.42f, -1.42f)
                    lineToRelative(-4.17f, 4.17f)
                    lineTo(3.0f, 13.0f)
                    close()
                }
            }.build()
            return _openInFull!!
        }
    private var _openInFull: ImageVector? = null

    val KeyboardArrowRight: ImageVector
        get() {
            if (_keyboardArrowRight != null) return _keyboardArrowRight!!
            _keyboardArrowRight = ImageVector.Builder(
                name = "KeyboardArrowRight",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f,
                autoMirror = true
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(8.59f, 16.59f)
                    lineTo(13.17f, 12.0f)
                    lineTo(8.59f, 7.41f)
                    lineTo(10.0f, 6.0f)
                    lineToRelative(6.0f, 6.0f)
                    lineToRelative(-6.0f, 6.0f)
                    lineTo(8.59f, 16.59f)
                    close()
                }
            }.build()
            return _keyboardArrowRight!!
        }
    private var _keyboardArrowRight: ImageVector? = null



    val List: ImageVector
        get() {
            if (_list != null) return _list!!
            _list = ImageVector.Builder(
                name = "List",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f,
                autoMirror = true
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(3.0f, 13.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineTo(3.0f)
                    verticalLineTo(13.0f)
                    close()
                    moveTo(3.0f, 17.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineTo(3.0f)
                    verticalLineTo(17.0f)
                    close()
                    moveTo(3.0f, 9.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineTo(7.0f)
                    horizontalLineTo(3.0f)
                    verticalLineTo(9.0f)
                    close()
                    moveTo(7.0f, 13.0f)
                    horizontalLineToRelative(14.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineTo(7.0f)
                    verticalLineTo(13.0f)
                    close()
                    moveTo(7.0f, 17.0f)
                    horizontalLineToRelative(14.0f)
                    verticalLineToRelative(-2.0f)
                    horizontalLineTo(7.0f)
                    verticalLineTo(17.0f)
                    close()
                    moveTo(7.0f, 7.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(14.0f)
                    verticalLineTo(7.0f)
                    horizontalLineTo(7.0f)
                    close()
                }
            }.build()
            return _list!!
        }
    private var _list: ImageVector? = null

    val Mic: ImageVector
        get() {
            if (_mic != null) return _mic!!
            _mic = ImageVector.Builder(
                name = "Mic",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(12.0f, 14.0f)
                    curveToRelative(1.66f, 0.0f, 3.0f, -1.34f, 3.0f, -3.0f)
                    verticalLineTo(5.0f)
                    curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                    curveTo(10.34f, 2.0f, 9.0f, 3.34f, 9.0f, 5.0f)
                    verticalLineToRelative(6.0f)
                    curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
                    close()
                    moveTo(17.0f, 11.0f)
                    curveToRelative(0.0f, 2.76f, -2.24f, 5.0f, -5.0f, 5.0f)
                    curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                    horizontalLineTo(5.0f)
                    curveToRelative(0.0f, 3.53f, 2.61f, 6.43f, 6.0f, 6.92f)
                    verticalLineTo(21.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(-3.08f)
                    curveToRelative(3.39f, -0.49f, 6.0f, -3.39f, 6.0f, -6.92f)
                    horizontalLineToRelative(-2.0f)
                    close()
                }
            }.build()
            return _mic!!
        }
    private var _mic: ImageVector? = null

    val Pause: ImageVector
        get() {
            if (_pause != null) return _pause!!
            _pause = ImageVector.Builder(
                name = "Pause",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(6.0f, 19.0f)
                    horizontalLineToRelative(4.0f)
                    verticalLineTo(5.0f)
                    horizontalLineTo(6.0f)
                    verticalLineToRelative(14.0f)
                    close()
                    moveTo(14.0f, 5.0f)
                    verticalLineToRelative(14.0f)
                    horizontalLineToRelative(4.0f)
                    verticalLineTo(5.0f)
                    horizontalLineToRelative(-4.0f)
                    close()
                }
            }.build()
            return _pause!!
        }
    private var _pause: ImageVector? = null

    val Stop: ImageVector
        get() {
            if (_stop != null) return _stop!!
            _stop = ImageVector.Builder(
                name = "Stop",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(6.0f, 6.0f)
                    horizontalLineToRelative(12.0f)
                    verticalLineToRelative(12.0f)
                    horizontalLineTo(6.0f)
                    close()
                }
            }.build()
            return _stop!!
        }
    private var _stop: ImageVector? = null

    val Assignment: ImageVector
        get() {
            if (_assignment != null) return _assignment!!
            _assignment = ImageVector.Builder(
                name = "Assignment",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f,
                autoMirror = true
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(19.0f, 3.0f)
                    horizontalLineToRelative(-4.18f)
                    curveTo(14.4f, 1.84f, 13.3f, 1.0f, 12.0f, 1.0f)
                    curveToRelative(-1.3f, 0.0f, -2.4f, 0.84f, -2.82f, 2.0f)
                    horizontalLineTo(5.0f)
                    curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                    verticalLineToRelative(14.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(14.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    verticalLineTo(5.0f)
                    curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                    close()
                    moveTo(12.0f, 3.0f)
                    curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                    curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                    curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                    curveToRelative(0.0f, -0.55f, 0.45f, -1.0f, 1.0f, -1.0f)
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
            }.build()
            return _assignment!!
        }
    private var _assignment: ImageVector? = null

    val Delete: ImageVector
        get() {
            if (_delete != null) return _delete!!
            _delete = ImageVector.Builder(
                name = "Delete",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(6.0f, 19.0f)
                    curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                    horizontalLineToRelative(8.0f)
                    curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                    verticalLineTo(7.0f)
                    horizontalLineTo(6.0f)
                    verticalLineToRelative(12.0f)
                    close()
                    moveTo(19.0f, 4.0f)
                    horizontalLineToRelative(-3.5f)
                    lineToRelative(-1.0f, -1.0f)
                    horizontalLineToRelative(-5.0f)
                    lineToRelative(-1.0f, 1.0f)
                    horizontalLineTo(5.0f)
                    verticalLineToRelative(2.0f)
                    horizontalLineToRelative(14.0f)
                    verticalLineTo(4.0f)
                    close()
                }
            }.build()
            return _delete!!
        }
    private var _delete: ImageVector? = null

    val Send: ImageVector
        get() {
            if (_send != null) return _send!!
            _send = ImageVector.Builder(
                name = "Send",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f,
                autoMirror = true
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(2.01f, 21.0f)
                    lineTo(23.0f, 12.0f)
                    lineTo(2.01f, 3.0f)
                    lineTo(2.0f, 10.0f)
                    lineToRelative(15.0f, 2.0f)
                    lineToRelative(-15.0f, 2.0f)
                    close()
                }
            }.build()
            return _send!!
        }
    private var _send: ImageVector? = null

    val Clear: ImageVector
        get() {
            if (_clear != null) return _clear!!
            _clear = ImageVector.Builder(
                name = "Clear",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(19.0f, 6.41f)
                    lineTo(17.59f, 5.0f)
                    lineTo(12.0f, 10.59f)
                    lineTo(6.41f, 5.0f)
                    lineTo(5.0f, 6.41f)
                    lineTo(10.59f, 12.0f)
                    lineTo(5.0f, 17.59f)
                    lineTo(6.41f, 19.0f)
                    lineTo(12.0f, 13.41f)
                    lineTo(17.59f, 19.0f)
                    lineTo(19.0f, 17.59f)
                    lineTo(13.41f, 12.0f)
                    close()
                }
            }.build()
            return _clear!!
        }
    private var _clear: ImageVector? = null

    val Check: ImageVector
        get() {
            if (_check != null) return _check!!
            _check = ImageVector.Builder(
                name = "Check",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(9.0f, 16.17f)
                    lineTo(4.83f, 12.0f)
                    lineToRelative(-1.42f, 1.41f)
                    lineTo(9.0f, 19.0f)
                    lineTo(21.0f, 7.0f)
                    lineToRelative(-1.41f, -1.41f)
                    close()
                }
            }.build()
            return _check!!
        }
    private var _check: ImageVector? = null

    val Edit: ImageVector
        get() {
            if (_edit != null) return _edit!!
            _edit = ImageVector.Builder(
                name = "Edit",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(3.0f, 17.25f)
                    verticalLineTo(21.0f)
                    horizontalLineToRelative(3.75f)
                    lineTo(17.81f, 9.94f)
                    lineToRelative(-3.75f, -3.75f)
                    lineTo(3.0f, 17.25f)
                    close()
                    moveTo(20.71f, 7.04f)
                    curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                    lineToRelative(-2.34f, -2.34f)
                    curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                    lineToRelative(-1.83f, 1.83f)
                    lineToRelative(3.75f, 3.75f)
                    lineToRelative(1.83f, -1.83f)
                    close()
                }
            }.build()
            return _edit!!
        }
    private var _edit: ImageVector? = null

    val Settings: ImageVector
        get() {
            if (_settings != null) return _settings!!
            _settings = ImageVector.Builder(
                name = "Settings",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(19.14f, 12.94f)
                    curveToRelative(0.04f, -0.3f, 0.06f, -0.61f, 0.06f, -0.94f)
                    curveToRelative(0.0f, -0.32f, -0.02f, -0.64f, -0.07f, -0.94f)
                    lineToRelative(2.03f, -1.58f)
                    curveToRelative(0.18f, -0.14f, 0.23f, -0.41f, 0.12f, -0.61f)
                    lineToRelative(-1.92f, -3.32f)
                    curveToRelative(-0.12f, -0.22f, -0.37f, -0.29f, -0.59f, -0.22f)
                    lineToRelative(-2.39f, 0.96f)
                    curveToRelative(-0.5f, -0.38f, -1.03f, -0.7f, -1.62f, -0.94f)
                    lineToRelative(-0.36f, -2.54f)
                    curveToRelative(-0.04f, -0.24f, -0.24f, -0.41f, -0.48f, -0.41f)
                    horizontalLineToRelative(-3.84f)
                    curveToRelative(-0.24f, 0.0f, -0.43f, 0.17f, -0.47f, 0.41f)
                    lineToRelative(-0.36f, 2.54f)
                    curveToRelative(-0.59f, 0.24f, -1.13f, 0.57f, -1.62f, 0.94f)
                    lineToRelative(-2.39f, -0.96f)
                    curveToRelative(-0.22f, -0.08f, -0.47f, 0.0f, -0.59f, 0.22f)
                    lineTo(2.74f, 8.87f)
                    curveToRelative(-0.12f, 0.21f, -0.08f, 0.47f, 0.12f, 0.61f)
                    lineToRelative(2.03f, 1.58f)
                    curveToRelative(-0.05f, 0.3f, -0.09f, 0.63f, -0.09f, 0.94f)
                    curveToRelative(0.0f, 0.31f, 0.02f, 0.64f, 0.07f, 0.94f)
                    lineToRelative(-2.03f, 1.58f)
                    curveToRelative(-0.18f, 0.14f, -0.23f, 0.41f, -0.12f, 0.61f)
                    lineToRelative(1.92f, 3.32f)
                    curveToRelative(0.12f, 0.22f, 0.37f, 0.29f, 0.59f, 0.22f)
                    lineToRelative(2.39f, -0.96f)
                    curveToRelative(0.5f, 0.38f, 1.03f, 0.7f, 1.62f, 0.94f)
                    lineToRelative(0.36f, 2.54f)
                    curveToRelative(0.05f, 0.24f, 0.24f, 0.41f, 0.48f, 0.41f)
                    horizontalLineToRelative(3.84f)
                    curveToRelative(0.24f, 0.0f, 0.44f, -0.17f, 0.47f, -0.41f)
                    lineToRelative(0.36f, -2.54f)
                    curveToRelative(0.59f, -0.24f, 1.13f, -0.58f, 1.62f, -0.94f)
                    lineToRelative(2.39f, 0.96f)
                    curveToRelative(0.22f, 0.08f, 0.47f, 0.0f, 0.59f, -0.22f)
                    lineToRelative(1.92f, -3.32f)
                    curveToRelative(0.12f, -0.22f, 0.07f, -0.47f, -0.12f, -0.61f)
                    lineToRelative(-2.01f, -1.58f)
                    close()
                    moveTo(12.0f, 15.6f)
                    curveToRelative(-1.98f, 0.0f, -3.6f, -1.62f, -3.6f, -3.6f)
                    curveToRelative(0.0f, -1.98f, 1.62f, -3.6f, 3.6f, -3.6f)
                    curveToRelative(1.98f, 0.0f, 3.6f, 1.62f, 3.6f, 3.6f)
                    curveToRelative(0.0f, 1.98f, -1.62f, 3.6f, -3.6f, 3.6f)
                    close()
                }
            }.build()
            return _settings!!
        }
    private var _settings: ImageVector? = null

    val Visibility: ImageVector
        get() {
            if (_visibility != null) return _visibility!!
            _visibility = ImageVector.Builder(
                name = "Visibility",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(12.0f, 4.5f)
                    curveTo(7.0f, 4.5f, 2.73f, 7.61f, 1.0f, 12.0f)
                    curveToRelative(1.73f, 4.39f, 6.0f, 7.5f, 11.0f, 7.5f)
                    curveToRelative(5.0f, 0.0f, 9.27f, -3.11f, 11.0f, -7.5f)
                    curveTo(21.27f, 7.61f, 17.0f, 4.5f, 12.0f, 4.5f)
                    close()
                    moveTo(12.0f, 17.0f)
                    curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                    curveToRelative(0.0f, -2.76f, 2.24f, -5.0f, 5.0f, -5.0f)
                    curveToRelative(2.76f, 0.0f, 5.0f, 2.24f, 5.0f, 5.0f)
                    curveTo(17.0f, 14.76f, 14.76f, 17.0f, 12.0f, 17.0f)
                    close()
                    moveTo(12.0f, 9.0f)
                    curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                    curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
                    curveToRelative(1.66f, 0.0f, 3.0f, -1.34f, 3.0f, -3.0f)
                    curveTo(15.0f, 10.34f, 13.66f, 9.0f, 12.0f, 9.0f)
                    close()
                }
            }.build()
            return _visibility!!
        }
    private var _visibility: ImageVector? = null

    val VisibilityOff: ImageVector
        get() {
            if (_visibilityOff != null) return _visibilityOff!!
            _visibilityOff = ImageVector.Builder(
                name = "VisibilityOff",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(12.0f, 7.0f)
                    curveToRelative(2.76f, 0.0f, 5.0f, 2.24f, 5.0f, 5.0f)
                    curveToRelative(0.0f, 0.65f, -0.13f, 1.26f, -0.36f, 1.83f)
                    lineToRelative(2.92f, 2.92f)
                    curveToRelative(1.51f, -1.26f, 2.7f, -2.89f, 3.43f, -4.75f)
                    curveToRelative(-1.73f, -4.39f, -6.0f, -7.5f, -11.0f, -7.5f)
                    curveToRelative(-1.4f, 0.0f, -2.74f, 0.25f, -3.98f, 0.7f)
                    lineToRelative(2.16f, 2.16f)
                    curveTo(10.74f, 7.13f, 11.35f, 7.0f, 12.0f, 7.0f)
                    close()
                    moveTo(2.0f, 4.27f)
                    lineToRelative(2.28f, 2.28f)
                    lineToRelative(0.46f, 0.46f)
                    curveTo(3.08f, 8.3f, 1.78f, 10.02f, 1.0f, 12.0f)
                    curveToRelative(1.73f, 4.39f, 6.0f, 7.5f, 11.0f, 7.5f)
                    curveToRelative(1.55f, 0.0f, 3.03f, -0.3f, 4.38f, -0.84f)
                    lineToRelative(0.42f, 0.42f)
                    lineTo(19.73f, 22.0f)
                    lineTo(21.0f, 20.73f)
                    lineTo(3.27f, 3.0f)
                    lineTo(2.0f, 4.27f)
                    close()
                    moveTo(7.53f, 9.8f)
                    lineToRelative(1.55f, 1.55f)
                    curveToRelative(-0.05f, 0.21f, -0.08f, 0.43f, -0.08f, 0.65f)
                    curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
                    curveToRelative(0.22f, 0.0f, 0.44f, -0.03f, 0.65f, -0.08f)
                    lineToRelative(1.55f, 1.55f)
                    curveToRelative(-0.67f, 0.33f, -1.41f, 0.53f, -2.2f, 0.53f)
                    curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                    curveToRelative(0.0f, -0.79f, 0.2f, -1.53f, 0.53f, -2.2f)
                    close()
                    moveTo(11.84f, 9.02f)
                    lineToRelative(3.15f, 3.15f)
                    lineToRelative(0.02f, -0.16f)
                    curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
                    lineToRelative(-0.17f, 0.01f)
                    close()
                }
            }.build()
            return _visibilityOff!!
        }
    private var _visibilityOff: ImageVector? = null

    val Info: ImageVector
        get() {
            if (_info != null) return _info!!
            _info = ImageVector.Builder(
                name = "Info",
                defaultWidth = 24.0.dp,
                defaultHeight = 24.0.dp,
                viewportWidth = 24.0f,
                viewportHeight = 24.0f
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(12.0f, 2.0f)
                    curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                    curveToRelative(0.0f, 5.52f, 4.48f, 10.0f, 10.0f, 10.0f)
                    curveToRelative(5.52f, 0.0f, 10.0f, -4.48f, 10.0f, -10.0f)
                    curveTo(22.0f, 6.48f, 17.52f, 2.0f, 12.0f, 2.0f)
                    close()
                    moveTo(13.0f, 17.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineToRelative(-6.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(6.0f)
                    close()
                    moveTo(13.0f, 9.0f)
                    horizontalLineToRelative(-2.0f)
                    verticalLineTo(7.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(2.0f)
                    close()
                }
            }.build()
            return _info!!
        }
    private var _info: ImageVector? = null
}
