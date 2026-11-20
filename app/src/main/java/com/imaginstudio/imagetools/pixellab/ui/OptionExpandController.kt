package com.imaginstudio.imagetools.pixellab.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.switchmaterial.SwitchMaterial
import com.imaginstudio.imagetools.pixellab.R
import com.imaginstudio.imagetools.pixellab.databinding.ActivityMainBinding
import com.imaginstudio.imagetools.pixellab.model.AppDefaults
import com.imaginstudio.imagetools.pixellab.model.ArrowHeadStyle
import com.imaginstudio.imagetools.pixellab.model.ArrowLayer
import com.imaginstudio.imagetools.pixellab.model.BackgroundType
import com.imaginstudio.imagetools.pixellab.model.BoundedShapeLabel
import com.imaginstudio.imagetools.pixellab.model.CanvasLayer
import com.imaginstudio.imagetools.pixellab.model.FillType
import com.imaginstudio.imagetools.pixellab.model.GradientDirection
import com.imaginstudio.imagetools.pixellab.model.GradientFill
import com.imaginstudio.imagetools.pixellab.model.ImageLayer
import com.imaginstudio.imagetools.pixellab.model.JsonPresetCodec
import com.imaginstudio.imagetools.pixellab.model.ProjectState
import com.imaginstudio.imagetools.pixellab.model.ShapeLayer
import com.imaginstudio.imagetools.pixellab.model.TextLayer
import com.imaginstudio.imagetools.pixellab.view.CanvasStageView
import kotlin.math.min
import kotlin.math.roundToInt

class OptionExpandController(
    private val context: Context,
    private val binding: ActivityMainBinding,
    private val canvasStage: CanvasStageView,
    private val getProject: () -> ProjectState,
    private val getSelectedLayer: () -> CanvasLayer?,
    private val pushHistory: () -> Unit,
    private val updateProjectLive: (ProjectState) -> Unit,
    private val updateSelectedLayerLive: (CanvasLayer) -> Unit,
    private val addLayer: (CanvasLayer) -> Unit,
    private val onPickBgImage: () -> Unit,
    private val onPickTextureImage: () -> Unit,
    private val onRequestImportJson: ((String) -> Unit) -> Unit,
    private val onRequestExportJson: (String, String) -> Unit,
    private val onToolClosed: () -> Unit
) {

    var activeToolId: String? = null
        private set

    private var snapshotBeforeEdit: ProjectState? = null
    private var snapshotSelectedId: String? = null
    private var lastEyedropperColor: Int = Color.WHITE

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).roundToInt()

    init {
        binding.btnOptionCancel.setOnClickListener {
            cancelCurrentTool()
        }

        binding.btnOptionOk.setOnClickListener {
            commitCurrentTool()
        }
    }

    private fun cancelCurrentTool() {
        if (activeToolId == "draw") {
            canvasStage.clearPendingDrawing()
        } else {
            snapshotBeforeEdit?.let { prev ->
                updateProjectLive(prev)
            }
        }
        canvasStage.toolMode = CanvasStageView.StageToolMode.NORMAL
        closePanel()
    }

    private fun commitCurrentTool() {
        if (activeToolId == "draw") {
            val drawing = canvasStage.finishDrawingLayer()
            canvasStage.toolMode = CanvasStageView.StageToolMode.NORMAL
            if (drawing != null) {
                addLayer(drawing)
            }
        }
        snapshotBeforeEdit = null
        closePanel()
    }

    fun isOpen(): Boolean = binding.optionExpandPanel.visibility == View.VISIBLE

    fun closePanel() {
        canvasStage.toolMode = CanvasStageView.StageToolMode.NORMAL
        canvasStage.onEyedropperColor = null
        activeToolId = null
        snapshotBeforeEdit = null
        binding.optionExpandBody.removeAllViews()
        binding.optionExpandPanel.clearAnimation()
        binding.optionExpandPanel.visibility = View.GONE
        onToolClosed()
    }

    fun openTool(toolId: String, title: String) {
        snapshotBeforeEdit = getProject()
        snapshotSelectedId = getSelectedLayer()?.id
        pushHistory()

        activeToolId = toolId
        val customSheet = toolId == "layer_radius" || toolId == "text_color" || toolId == "shape_color" || toolId == "bg_color"
        binding.optionExpandHeader.visibility = if (customSheet) View.GONE else View.VISIBLE
        if (customSheet) {
            binding.optionExpandBody.setPadding(0, 0, 0, 0)
        } else {
            binding.optionExpandBody.setPadding(dp(12), dp(4), dp(12), dp(10))
        }
        binding.optionExpandTitle.text = title
        binding.optionExpandBody.removeAllViews()
        binding.optionExpandPanel.visibility = View.VISIBLE

        // Bottom-up sheet entrance animation
        binding.optionExpandPanel.translationY = dp(180).toFloat()
        binding.optionExpandPanel.alpha = 0f
        binding.optionExpandPanel.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator())
            .start()

        canvasStage.toolMode = when (toolId) {
            "draw" -> CanvasStageView.StageToolMode.DRAW
            "layer_perspective" -> CanvasStageView.StageToolMode.PERSPECTIVE
            "layer_mask" -> CanvasStageView.StageToolMode.MASK
            "bezier_edit" -> CanvasStageView.StageToolMode.BEZIER
            "img_eraser" -> CanvasStageView.StageToolMode.BITMAP_ERASER
            "eyedropper" -> CanvasStageView.StageToolMode.EYEDROPPER
            else -> CanvasStageView.StageToolMode.NORMAL
        }

        populateTool(toolId)
    }

    private fun populateTool(toolId: String) {
        val body = binding.optionExpandBody
        val sel = getSelectedLayer()
        val proj = getProject()

        when (toolId) {
            "text_styles" -> {
                val tl = sel as? TextLayer ?: return
                val assetStyles = com.imaginstudio.imagetools.pixellab.model.AssetRepository.loadTextStyles(context)
                if (assetStyles.isNotEmpty()) {
                    val hs = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
                    val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    for (style in assetStyles) {
                        val thumbBmp = style.thumbUri?.let {
                            com.imaginstudio.imagetools.pixellab.engine.PixelLabRenderer.loadBitmap(context, it)
                        }
                        if (thumbBmp != null) {
                            val iv = android.widget.ImageView(context).apply {
                                layoutParams = LinearLayout.LayoutParams(170, 96).apply { setMargins(6, 4, 6, 4) }
                                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                setBackgroundColor(Color.parseColor("#E2E8F0"))
                                setImageBitmap(thumbBmp)
                                setOnClickListener {
                                    (getSelectedLayer() as? TextLayer)?.let { cur ->
                                        val s = style.template
                                        val applied = cur.copy(
                                            fontName = s.fontName,
                                            bold = s.bold,
                                            italic = s.italic,
                                            underline = s.underline,
                                            fillType = s.fillType,
                                            color = s.color,
                                            gradient = s.gradient,
                                            textureUri = s.textureUri,
                                            stroke = s.stroke,
                                            shadow = s.shadow,
                                            innerShadow = s.innerShadow,
                                            emboss = s.emboss,
                                            background = s.background,
                                            threeD = s.threeD,
                                            reflection = s.reflection
                                        )
                                        val (nw, nh) = com.imaginstudio.imagetools.pixellab.engine.PixelLabRenderer.measureTextBounds(applied, context)
                                        updateSelectedLayerLive(applied.copy(width = nw, height = nh))
                                    }
                                }
                            }
                            row.addView(iv)
                        }
                    }
                    hs.addView(row)
                    body.addView(hs)
                }
                addRowButtons(
                    listOf(
                        "Default" to {
                            updateSelectedLayerLive(
                                tl.copy(
                                    fillType = FillType.COLOR, color = Color.WHITE,
                                    stroke = tl.stroke.copy(enabled = false),
                                    threeD = tl.threeD.copy(enabled = false),
                                    shadow = tl.shadow.copy(enabled = true)
                                )
                            )
                        },
                        "Gradient Pop" to {
                            updateSelectedLayerLive(
                                tl.copy(
                                    fillType = FillType.GRADIENT,
                                    gradient = AppDefaults.defaultGradients[8],
                                    stroke = tl.stroke.copy(enabled = true, color = Color.BLACK, width = 5f)
                                )
                            )
                        },
                        "3D Block" to {
                            updateSelectedLayerLive(
                                tl.copy(
                                    threeD = tl.threeD.copy(enabled = true, depth = 18, colorAuto = true)
                                )
                            )
                        },
                        "Neon" to {
                            updateSelectedLayerLive(
                                tl.copy(
                                    fillType = FillType.COLOR,
                                    color = Color.parseColor("#F0ABFC"),
                                    shadow = tl.shadow.copy(
                                        enabled = true, color = Color.parseColor("#E879F9"),
                                        blur = 28f, offsetX = 0f, offsetY = 0f
                                    )
                                )
                            )
                        }
                    )
                )
            }

            "position" -> {
                val l = sel ?: return
                var step = 12f
                addSliderRow("Nudge Step", 1, 60, step.toInt()) { step = it.toFloat() }
                addRowButtons(
                    listOf(
                        "Left" to { getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(x = it.x - step)) } },
                        "Up" to { getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(y = it.y - step)) } },
                        "Down" to { getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(y = it.y + step)) } },
                        "Right" to { getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(x = it.x + step)) } }
                    )
                )
            }

            "rel_position" -> {
                val l = sel ?: return
                addRowButtons(
                    listOf(
                        "Left" to { getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(x = it.width / 2f + 24f)) } },
                        "Center H" to { getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(x = proj.width / 2f)) } },
                        "Right" to { getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(x = proj.width - it.width / 2f - 24f)) } },
                        "Top" to { getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(y = it.height / 2f + 24f)) } },
                        "Center V" to { getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(y = proj.height / 2f)) } },
                        "Bottom" to { getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(y = proj.height - it.height / 2f - 24f)) } }
                    )
                )
            }

            "rel_size" -> {
                val l = sel ?: return
                val curWPct = ((l.width / proj.width.coerceAtLeast(1)) * 100f).roundToInt().coerceIn(5, 100)
                val curHPct = ((l.height / proj.height.coerceAtLeast(1)) * 100f).roundToInt().coerceIn(5, 100)
                addSliderRow("Width (%)", 5, 100, curWPct) { v ->
                    getSelectedLayer()?.let { cur ->
                        updateSelectedLayerLive(cur.copyBase(width = proj.width * (v / 100f)))
                    }
                }
                addSliderRow("Height (%)", 5, 100, curHPct) { v ->
                    getSelectedLayer()?.let { cur ->
                        updateSelectedLayerLive(cur.copyBase(height = proj.height * (v / 100f)))
                    }
                }
            }

            "text_size" -> {
                val tl = sel as? TextLayer ?: return
                addSliderRow("Size", 16, 260, tl.fontSize.toInt()) { v ->
                    (getSelectedLayer() as? TextLayer)?.let { cur ->
                        val updated = cur.copy(fontSize = v.toFloat())
                        val (nw, nh) = com.imaginstudio.imagetools.pixellab.engine.PixelLabRenderer.measureTextBounds(updated, context)
                        updateSelectedLayerLive(updated.copy(width = nw, height = nh))
                    }
                }
            }

            "text_padding" -> {
                val tl = sel as? TextLayer ?: return
                addSliderRow("Left Pad", 0, 100, tl.paddingLeft.toInt()) { v ->
                    (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(paddingLeft = v.toFloat())) }
                }
                addSliderRow("Right Pad", 0, 100, tl.paddingRight.toInt()) { v ->
                    (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(paddingRight = v.toFloat())) }
                }
            }

            "text_color", "shape_color" -> {
                openFillSheet(body, isBackground = false)
            }

            "layer_radius" -> {
                openRadiusSheet(body)
            }

            "opacity" -> {
                val l = sel ?: return
                addSliderRow("Opacity (%)", 0, 100, l.opacity) { v ->
                    getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(opacity = v)) }
                }
            }

            "rotate" -> {
                val l = sel ?: return
                addSliderRow("Angle (°)", -180, 180, l.rotation.roundToInt()) { v ->
                    getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(rotation = v.toFloat())) }
                }
            }

            "3d_rotate" -> {
                val l = sel ?: return
                addSliderRow("X Rotation", -60, 60, l.rotX.roundToInt()) { v ->
                    getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(rotX = v.toFloat())) }
                }
                addSliderRow("Y Rotation", -60, 60, l.rotY.roundToInt()) { v ->
                    getSelectedLayer()?.let { updateSelectedLayerLive(it.copyBase(rotY = v.toFloat())) }
                }
            }

            "text_style" -> {
                val tl = sel as? TextLayer ?: return
                addRowButtons(
                    listOf(
                        "B (Bold)" to {
                            (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(bold = !it.bold)) }
                        },
                        "I (Italic)" to {
                            (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(italic = !it.italic)) }
                        },
                        "U (Underline)" to {
                            (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(underline = !it.underline)) }
                        },
                        "S (Strike)" to {
                            (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(strikethrough = !it.strikethrough)) }
                        }
                    )
                )
            }

            "text_curve" -> {
                val tl = sel as? TextLayer ?: return
                addSliderRow("Bending", -100, 100, tl.curve) { v ->
                    (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(curve = v)) }
                }
            }

            "text_bg" -> {
                val tl = sel as? TextLayer ?: return
                addToggleRow("Enable Background", tl.background.enabled) { en ->
                    (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(background = it.background.copy(enabled = en))) }
                }
                addColorPaletteRow("Bg Color") { c ->
                    (getSelectedLayer() as? TextLayer)?.let {
                        updateSelectedLayerLive(it.copy(background = it.background.copy(enabled = true, fillType = FillType.COLOR, color = c)))
                    }
                }
                addSliderRow("Corner Radius", 0, 60, tl.background.radius.toInt()) { v ->
                    (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(background = it.background.copy(radius = v.toFloat()))) }
                }
            }

            "text_align" -> {
                val tl = sel as? TextLayer ?: return
                addRowButtons(
                    listOf(
                        "Left" to { (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(align = 0)) } },
                        "Center" to { (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(align = 1)) } },
                        "Right" to { (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(align = 2)) } }
                    )
                )
            }

            "text_spacing" -> {
                val tl = sel as? TextLayer ?: return
                addSliderRow("Letter Spacing", -10, 80, (tl.letterSpacing * 100f).roundToInt()) { v ->
                    (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(letterSpacing = v / 100f)) }
                }
                addSliderRow("Line Spacing", 70, 220, (tl.lineSpacing * 100f).roundToInt()) { v ->
                    (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(lineSpacing = v / 100f)) }
                }
            }

            "text_stroke", "shape_stroke" -> {
                val st = when (sel) {
                    is TextLayer -> sel.stroke
                    is ShapeLayer -> sel.stroke
                    is ArrowLayer -> sel.stroke
                    is ImageLayer -> sel.stroke
                    else -> return
                }
                addToggleRow("Enable Stroke", st.enabled) { en ->
                    updateStroke { it.copy(enabled = en) }
                }
                addSliderRow("Stroke Width", 1, 32, st.width.toInt()) { v ->
                    updateStroke { it.copy(enabled = true, width = v.toFloat()) }
                }
                addColorPaletteRow("Stroke Color") { c ->
                    updateStroke { it.copy(enabled = true, color = c) }
                }
            }

            "text_shadow", "shape_shadow" -> {
                val sh = when (sel) {
                    is TextLayer -> sel.shadow
                    is ShapeLayer -> sel.shadow
                    is ArrowLayer -> sel.shadow
                    is ImageLayer -> sel.shadow
                    else -> return
                }
                addToggleRow("Enable Shadow", sh.enabled) { en ->
                    updateShadow { it.copy(enabled = en) }
                }
                addSliderRow("Blur Radius", 0, 50, sh.blur.toInt()) { v ->
                    updateShadow { it.copy(enabled = true, blur = v.toFloat()) }
                }
                addSliderRow("Offset X", -40, 40, sh.offsetX.toInt()) { v ->
                    updateShadow { it.copy(enabled = true, offsetX = v.toFloat()) }
                }
                addSliderRow("Offset Y", -40, 40, sh.offsetY.toInt()) { v ->
                    updateShadow { it.copy(enabled = true, offsetY = v.toFloat()) }
                }
                addColorPaletteRow("Shadow Color") { c ->
                    updateShadow { it.copy(enabled = true, color = c) }
                }
            }

            "text_3d", "shape_3d" -> {
                val td = when (sel) {
                    is TextLayer -> sel.threeD
                    is ShapeLayer -> sel.threeD
                    else -> return
                }
                addToggleRow("Enable 3D Depth", td.enabled) { en ->
                    when (val cur = getSelectedLayer()) {
                        is TextLayer -> updateSelectedLayerLive(cur.copy(threeD = cur.threeD.copy(enabled = en)))
                        is ShapeLayer -> updateSelectedLayerLive(cur.copy(threeD = cur.threeD.copy(enabled = en)))
                        else -> Unit
                    }
                }
                addSliderRow("Depth", 1, 45, td.depth) { v ->
                    when (val cur = getSelectedLayer()) {
                        is TextLayer -> updateSelectedLayerLive(cur.copy(threeD = cur.threeD.copy(enabled = true, depth = v)))
                        is ShapeLayer -> updateSelectedLayerLive(cur.copy(threeD = cur.threeD.copy(enabled = true, depth = v)))
                        else -> Unit
                    }
                }
                addSliderRow("Angle", 0, 360, td.angle) { v ->
                    when (val cur = getSelectedLayer()) {
                        is TextLayer -> updateSelectedLayerLive(cur.copy(threeD = cur.threeD.copy(enabled = true, angle = v)))
                        is ShapeLayer -> updateSelectedLayerLive(cur.copy(threeD = cur.threeD.copy(enabled = true, angle = v)))
                        else -> Unit
                    }
                }
                addSliderRow("Darken (%)", 10, 85, td.darken) { v ->
                    when (val cur = getSelectedLayer()) {
                        is TextLayer -> updateSelectedLayerLive(cur.copy(threeD = cur.threeD.copy(enabled = true, darken = v)))
                        is ShapeLayer -> updateSelectedLayerLive(cur.copy(threeD = cur.threeD.copy(enabled = true, darken = v)))
                        else -> Unit
                    }
                }
            }

            "text_emboss" -> {
                val tl = sel as? TextLayer ?: return
                addToggleRow("Enable Emboss", tl.emboss.enabled) { en ->
                    (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(emboss = it.emboss.copy(enabled = en))) }
                }
                addSliderRow("Light Angle", 0, 360, tl.emboss.lightAngle) { v ->
                    (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(emboss = it.emboss.copy(enabled = true, lightAngle = v))) }
                }
                addSliderRow("Bevel", 5, 60, tl.emboss.bevel) { v ->
                    (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(emboss = it.emboss.copy(enabled = true, bevel = v))) }
                }
            }

            "text_reflection" -> {
                val tl = sel as? TextLayer ?: return
                addToggleRow("Enable Reflection", tl.reflection.enabled) { en ->
                    (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(reflection = it.reflection.copy(enabled = en))) }
                }
                addSliderRow("Vertical Offset", -40, 80, tl.reflection.offset.toInt()) { v ->
                    (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(reflection = it.reflection.copy(enabled = true, offset = v.toFloat()))) }
                }
                addSliderRow("Opacity (%)", 10, 90, tl.reflection.opacity) { v ->
                    (getSelectedLayer() as? TextLayer)?.let { updateSelectedLayerLive(it.copy(reflection = it.reflection.copy(enabled = true, opacity = v))) }
                }
            }

            "pick_shape" -> {
                val existingShape = sel as? ShapeLayer
                val hs = HorizontalScrollView(context)
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                for (lbl in BoundedShapeLabel.entries) {
                    val btn = Button(context).apply {
                        text = lbl.label
                        textSize = 11f
                        setOnClickListener {
                            val cur = getSelectedLayer() as? ShapeLayer
                            if (cur != null) {
                                updateSelectedLayerLive(cur.copy(shapeLabel = lbl, name = lbl.label))
                            } else {
                                addLayer(
                                    ShapeLayer(
                                        id = "shape_${System.currentTimeMillis()}",
                                        name = lbl.label,
                                        x = proj.width / 2f,
                                        y = proj.height / 2f,
                                        shapeLabel = lbl
                                    )
                                )
                            }
                        }
                    }
                    row.addView(btn)
                }
                hs.addView(row)
                body.addView(hs)
                if (existingShape != null) {
                    addSliderRow("Corner Radius (%)", 0, 100, existingShape.cornerRadius) { v ->
                        (getSelectedLayer() as? ShapeLayer)?.let {
                            updateSelectedLayerLive(it.copy(cornerRadius = v))
                        }
                    }
                }
            }

            "arrow_edit" -> {
                val al = sel as? ArrowLayer ?: return
                addRowButtons(
                    ArrowHeadStyle.entries.map { style ->
                        "End: ${style.name}" to {
                            (getSelectedLayer() as? ArrowLayer)?.let { updateSelectedLayerLive(it.copy(endStyle = style)) }
                        }
                    }
                )
                addRowButtons(
                    ArrowHeadStyle.entries.map { style ->
                        "Start: ${style.name}" to {
                            (getSelectedLayer() as? ArrowLayer)?.let { updateSelectedLayerLive(it.copy(startStyle = style)) }
                        }
                    }
                )
                addSliderRow("Stem Width", 4, 60, al.stemWidth.toInt()) { v ->
                    (getSelectedLayer() as? ArrowLayer)?.let { updateSelectedLayerLive(it.copy(stemWidth = v.toFloat())) }
                }
                addSliderRow("Head Size", 16, 120, al.headSize.toInt()) { v ->
                    (getSelectedLayer() as? ArrowLayer)?.let { updateSelectedLayerLive(it.copy(headSize = v.toFloat())) }
                }
            }

            "draw" -> {
                val brush = canvasStage.brushConfig
                addColorPaletteRow("Pen Color") { c -> brush.color = c }
                addSliderRow("Pen Size", 2, 70, brush.width.toInt()) { v -> brush.width = v.toFloat() }
                addSliderRow("Blur / Softness", 0, 30, brush.blur.toInt()) { v -> brush.blur = v.toFloat() }
                addRowButtons(
                    listOf(
                        "Eraser" to { brush.eraser = !brush.eraser },
                        "Dashed" to { brush.dashed = !brush.dashed },
                        "Neon Glow" to { brush.neon = !brush.neon },
                        "Fill Inside" to { brush.fill = !brush.fill },
                        "Undo Stroke" to { canvasStage.undoPendingStroke() }
                    )
                )
            }

            "img_crop" -> {
                val il = sel as? ImageLayer ?: return
                addRowButtons(
                    listOf(
                        "Circular Crop" to {
                            (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(circularCrop = !it.circularCrop)) }
                        },
                        "Flip Horizontal" to {
                            (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(flipH = !it.flipH)) }
                        },
                        "Flip Vertical" to {
                            (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(flipV = !it.flipV)) }
                        }
                    )
                )
            }

            "img_filters" -> {
                val il = sel as? ImageLayer ?: return
                addSliderRow("Brightness", -100, 100, il.brightness) { v ->
                    (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(brightness = v)) }
                }
                addSliderRow("Contrast", -80, 150, il.contrast) { v ->
                    (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(contrast = v)) }
                }
                addSliderRow("Saturation", -100, 150, il.saturation) { v ->
                    (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(saturation = v)) }
                }
                addSliderRow("Hue", -180, 180, il.hue) { v ->
                    (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(hue = v)) }
                }
            }

            "img_remove_color" -> {
                val il = sel as? ImageLayer ?: return
                addToggleRow("Erase Color (Chroma Key)", il.removeColorEnabled) { en ->
                    (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(removeColorEnabled = en)) }
                }
                addSliderRow("Tolerance (%)", 1, 80, il.removeColorTolerance) { v ->
                    (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(removeColorEnabled = true, removeColorTolerance = v)) }
                }
                addColorPaletteRow("Target Color") { c ->
                    (getSelectedLayer() as? ImageLayer)?.let { updateSelectedLayerLive(it.copy(removeColorEnabled = true, removeColorTarget = c)) }
                }
            }

            "bg_color" -> {
                openFillSheet(body, isBackground = true)
            }

            "bg_crop" -> {
                addRowButtons(
                    listOf(
                        "Pick Image..." to { onPickBgImage() },
                        "Preset Backgrounds..." to { openTool("bg_presets", "Background Presets") },
                        "Rotate 90°" to {
                            val p = getProject()
                            val rot = (p.background.imageRotation + 90) % 360
                            updateProjectLive(p.copy(background = p.background.copy(imageRotation = rot)))
                        },
                        "Flip H" to {
                            val p = getProject()
                            updateProjectLive(p.copy(background = p.background.copy(flipH = !p.background.flipH)))
                        },
                        "Flip V" to {
                            val p = getProject()
                            updateProjectLive(p.copy(background = p.background.copy(flipV = !p.background.flipV)))
                        },
                        "Circular Mask" to {
                            val p = getProject()
                            updateProjectLive(p.copy(background = p.background.copy(circular = !p.background.circular)))
                        }
                    )
                )
            }

            "bg_presets" -> {
                val bgs = com.imaginstudio.imagetools.pixellab.model.AssetRepository.loadBackgrounds(context)
                val hs = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                for (item in bgs) {
                    val bmp = com.imaginstudio.imagetools.pixellab.engine.PixelLabRenderer.loadBitmap(context, item.thumbUri)
                    if (bmp != null) {
                        val iv = android.widget.ImageView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(128, 128).apply { setMargins(6, 4, 6, 4) }
                            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                            setImageBitmap(bmp)
                            setOnClickListener {
                                val p = getProject()
                                updateProjectLive(
                                    p.copy(background = p.background.copy(type = BackgroundType.IMAGE, imageUri = item.fullUri))
                                )
                            }
                        }
                        row.addView(iv)
                    }
                }
                hs.addView(row)
                body.addView(hs)
            }

            "fx_vignette" -> {
                addToggleRow("Enable Vignette", proj.effects.vignetteEnabled) { en ->
                    val p = getProject()
                    updateProjectLive(p.copy(effects = p.effects.copy(vignetteEnabled = en)))
                }
                addSliderRow("Alpha", 0, 255, proj.effects.vignetteAlpha) { v ->
                    val p = getProject()
                    updateProjectLive(p.copy(effects = p.effects.copy(vignetteEnabled = true, vignetteAlpha = v)))
                }
                addColorPaletteRow("Vignette Color") { c ->
                    val p = getProject()
                    updateProjectLive(p.copy(effects = p.effects.copy(vignetteEnabled = true, vignetteColor = c)))
                }
            }

            "fx_noise" -> {
                addToggleRow("Enable Noise", proj.effects.noiseEnabled) { en ->
                    val p = getProject()
                    updateProjectLive(p.copy(effects = p.effects.copy(noiseEnabled = en)))
                }
                addSliderRow("Alpha", 0, 200, proj.effects.noiseAlpha) { v ->
                    val p = getProject()
                    updateProjectLive(p.copy(effects = p.effects.copy(noiseEnabled = true, noiseAlpha = v)))
                }
                addSliderRow("Grain Scale", 2, 20, proj.effects.noiseScale) { v ->
                    val p = getProject()
                    updateProjectLive(p.copy(effects = p.effects.copy(noiseEnabled = true, noiseScale = v)))
                }
            }

            "fx_stripes" -> {
                addToggleRow("Enable Stripes", proj.effects.stripesEnabled) { en ->
                    val p = getProject()
                    updateProjectLive(p.copy(effects = p.effects.copy(stripesEnabled = en)))
                }
                addSliderRow("Alpha", 0, 200, proj.effects.stripesAlpha) { v ->
                    val p = getProject()
                    updateProjectLive(p.copy(effects = p.effects.copy(stripesEnabled = true, stripesAlpha = v)))
                }
                addSliderRow("Repeat Count", 2, 40, proj.effects.stripesRepeat) { v ->
                    val p = getProject()
                    updateProjectLive(p.copy(effects = p.effects.copy(stripesEnabled = true, stripesRepeat = v)))
                }
            }

            "fx_brightness" -> {
                addSliderRow("Brightness", -100, 100, proj.effects.brightness) { v ->
                    val p = getProject()
                    updateProjectLive(p.copy(effects = p.effects.copy(brightness = v)))
                }
            }

            "fx_contrast" -> {
                addSliderRow("Contrast", -80, 150, proj.effects.contrast) { v ->
                    val p = getProject()
                    updateProjectLive(p.copy(effects = p.effects.copy(contrast = v)))
                }
            }

            "fx_hue" -> {
                addSliderRow("Hue (°)", -180, 180, proj.effects.hue) { v ->
                    val p = getProject()
                    updateProjectLive(p.copy(effects = p.effects.copy(hue = v)))
                }
            }

            "text_inner_shadow", "shape_inner_shadow" -> {
                val sh = when (sel) {
                    is TextLayer -> sel.innerShadow
                    is ShapeLayer -> sel.innerShadow
                    is ImageLayer -> sel.innerShadow
                    else -> return
                }
                addToggleRow("Enable Inner Shadow", sh.enabled) { en ->
                    updateInnerShadow { it.copy(enabled = en) }
                }
                addSliderRow("Blur Radius", 0, 40, sh.blur.toInt()) { v ->
                    updateInnerShadow { it.copy(enabled = true, blur = v.toFloat()) }
                }
                addSliderRow("Offset X", -30, 30, sh.offsetX.toInt()) { v ->
                    updateInnerShadow { it.copy(enabled = true, offsetX = v.toFloat()) }
                }
                addSliderRow("Offset Y", -30, 30, sh.offsetY.toInt()) { v ->
                    updateInnerShadow { it.copy(enabled = true, offsetY = v.toFloat()) }
                }
                addColorPaletteRow("Inner Shadow Color") { c ->
                    updateInnerShadow { it.copy(enabled = true, color = c) }
                }
            }

            "text_texture", "shape_texture" -> {
                if (sel !is TextLayer && sel !is ShapeLayer) return
                addRowButtons(
                    listOf(
                        "Pick Texture Image..." to { onPickTextureImage() },
                        "Remove Texture" to {
                            when (val cur = getSelectedLayer()) {
                                is TextLayer -> updateSelectedLayerLive(cur.copy(textureUri = null))
                                is ShapeLayer -> updateSelectedLayerLive(cur.copy(textureUri = null))
                                else -> Unit
                            }
                        }
                    )
                )
            }

            "layer_mask" -> {
                val (en, inv) = when (sel) {
                    is TextLayer -> sel.maskEnabled to sel.maskInvert
                    is ShapeLayer -> sel.maskEnabled to sel.maskInvert
                    is ImageLayer -> sel.maskEnabled to sel.maskInvert
                    else -> return
                }
                addToggleRow("Enable Mask (Tap stage to add points)", en) { checked ->
                    when (val cur = getSelectedLayer()) {
                        is TextLayer -> updateSelectedLayerLive(cur.copy(maskEnabled = checked))
                        is ShapeLayer -> updateSelectedLayerLive(cur.copy(maskEnabled = checked))
                        is ImageLayer -> updateSelectedLayerLive(cur.copy(maskEnabled = checked))
                        else -> Unit
                    }
                }
                addToggleRow("Invert Mask (Out)", inv) { checked ->
                    when (val cur = getSelectedLayer()) {
                        is TextLayer -> updateSelectedLayerLive(cur.copy(maskInvert = checked))
                        is ShapeLayer -> updateSelectedLayerLive(cur.copy(maskInvert = checked))
                        is ImageLayer -> updateSelectedLayerLive(cur.copy(maskInvert = checked))
                        else -> Unit
                    }
                }
                addRowButtons(
                    listOf(
                        "Undo Point" to {
                            when (val cur = getSelectedLayer()) {
                                is TextLayer -> updateSelectedLayerLive(cur.copy(maskPoints = cur.maskPoints.dropLast(1)))
                                is ShapeLayer -> updateSelectedLayerLive(cur.copy(maskPoints = cur.maskPoints.dropLast(1)))
                                is ImageLayer -> updateSelectedLayerLive(cur.copy(maskPoints = cur.maskPoints.dropLast(1)))
                                else -> Unit
                            }
                        },
                        "Clear Mask" to {
                            when (val cur = getSelectedLayer()) {
                                is TextLayer -> updateSelectedLayerLive(cur.copy(maskEnabled = false, maskPoints = emptyList()))
                                is ShapeLayer -> updateSelectedLayerLive(cur.copy(maskEnabled = false, maskPoints = emptyList()))
                                is ImageLayer -> updateSelectedLayerLive(cur.copy(maskEnabled = false, maskPoints = emptyList()))
                                else -> Unit
                            }
                        }
                    )
                )
            }

            "layer_perspective" -> {
                val en = when (sel) {
                    is TextLayer -> sel.perspectiveEnabled
                    is ShapeLayer -> sel.perspectiveEnabled
                    is ImageLayer -> sel.perspectiveEnabled
                    else -> return
                }
                addToggleRow("Enable 4-Corner Perspective", en) { checked ->
                    when (val cur = getSelectedLayer()) {
                        is TextLayer -> updateSelectedLayerLive(cur.copy(perspectiveEnabled = checked))
                        is ShapeLayer -> updateSelectedLayerLive(cur.copy(perspectiveEnabled = checked))
                        is ImageLayer -> updateSelectedLayerLive(cur.copy(perspectiveEnabled = checked))
                        else -> Unit
                    }
                }
                addRowButtons(
                    listOf(
                        "Reset Corners" to {
                            val reset = listOf(
                                com.imaginstudio.imagetools.pixellab.model.DrawPoint(0f, 0f),
                                com.imaginstudio.imagetools.pixellab.model.DrawPoint(1f, 0f),
                                com.imaginstudio.imagetools.pixellab.model.DrawPoint(1f, 1f),
                                com.imaginstudio.imagetools.pixellab.model.DrawPoint(0f, 1f)
                            )
                            when (val cur = getSelectedLayer()) {
                                is TextLayer -> updateSelectedLayerLive(cur.copy(perspectiveEnabled = false, perspectivePoints = reset))
                                is ShapeLayer -> updateSelectedLayerLive(cur.copy(perspectiveEnabled = false, perspectivePoints = reset))
                                is ImageLayer -> updateSelectedLayerLive(cur.copy(perspectiveEnabled = false, perspectivePoints = reset))
                                else -> Unit
                            }
                        }
                    )
                )
            }

            "bezier_edit" -> {
                val bl = sel as? com.imaginstudio.imagetools.pixellab.model.BezierLayer ?: return
                addRowButtons(
                    listOf(
                        "Add Point Mode" to { canvasStage.bezierActionMode = 0 },
                        "Drag Point Mode" to { canvasStage.bezierActionMode = 1 },
                        "Close Path" to {
                            (getSelectedLayer() as? com.imaginstudio.imagetools.pixellab.model.BezierLayer)?.let {
                                updateSelectedLayerLive(it.copy(closed = !it.closed))
                            }
                        },
                        "Toggle Fill" to {
                            (getSelectedLayer() as? com.imaginstudio.imagetools.pixellab.model.BezierLayer)?.let {
                                updateSelectedLayerLive(it.copy(fillEnabled = !it.fillEnabled))
                            }
                        },
                        "Undo Point" to {
                            (getSelectedLayer() as? com.imaginstudio.imagetools.pixellab.model.BezierLayer)?.let {
                                updateSelectedLayerLive(it.copy(anchors = it.anchors.dropLast(1)))
                            }
                        }
                    )
                )
                addSliderRow("Stroke Width", 1, 36, bl.stroke.width.toInt()) { v ->
                    (getSelectedLayer() as? com.imaginstudio.imagetools.pixellab.model.BezierLayer)?.let {
                        updateSelectedLayerLive(it.copy(stroke = it.stroke.copy(enabled = true, width = v.toFloat())))
                    }
                }
                addColorPaletteRow("Bezier Color") { c ->
                    (getSelectedLayer() as? com.imaginstudio.imagetools.pixellab.model.BezierLayer)?.let {
                        updateSelectedLayerLive(it.copy(color = c, stroke = it.stroke.copy(color = c)))
                    }
                }
            }

            "img_eraser" -> {
                if (sel !is ImageLayer) return
                addSliderRow("Eraser Size", 6, 120, canvasStage.bitmapEraserWidth.toInt()) { v ->
                    canvasStage.bitmapEraserWidth = v.toFloat()
                }
                addRowButtons(
                    listOf(
                        "Undo Erase" to {
                            (getSelectedLayer() as? ImageLayer)?.let {
                                updateSelectedLayerLive(it.copy(eraserStrokes = it.eraserStrokes.dropLast(1)))
                            }
                        },
                        "Reset Image" to {
                            (getSelectedLayer() as? ImageLayer)?.let {
                                updateSelectedLayerLive(it.copy(eraserStrokes = emptyList()))
                            }
                        }
                    )
                )
            }

            "eyedropper" -> {
                val preview = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 54)
                    setBackgroundColor(lastEyedropperColor)
                }
                canvasStage.onEyedropperColor = { picked ->
                    lastEyedropperColor = picked
                    preview.setBackgroundColor(picked)
                }
                body.addView(TextView(context).apply {
                    text = "Tap canvas to sample a color, then apply below:"
                    textSize = 12f
                    setTextColor(Color.parseColor("#334155"))
                })
                body.addView(preview)
                addRowButtons(
                    listOf(
                        "Apply to Selected Layer" to {
                            when (val cur = getSelectedLayer()) {
                                is TextLayer -> updateSelectedLayerLive(cur.copy(fillType = FillType.COLOR, color = lastEyedropperColor))
                                is ShapeLayer -> updateSelectedLayerLive(cur.copy(fillType = FillType.COLOR, color = lastEyedropperColor))
                                is ArrowLayer -> updateSelectedLayerLive(cur.copy(fillType = FillType.COLOR, color = lastEyedropperColor))
                                is ImageLayer -> updateSelectedLayerLive(cur.copy(colorOverlayEnabled = true, colorOverlay = lastEyedropperColor))
                                is com.imaginstudio.imagetools.pixellab.model.BezierLayer -> updateSelectedLayerLive(cur.copy(color = lastEyedropperColor, stroke = cur.stroke.copy(color = lastEyedropperColor)))
                                else -> Unit
                            }
                        },
                        "Apply to Background" to {
                            val p = getProject()
                            updateProjectLive(p.copy(background = p.background.copy(type = BackgroundType.COLOR, color = lastEyedropperColor)))
                        }
                    )
                )
            }

            "fx_saturation" -> {
                addSliderRow("Saturation", -100, 150, proj.effects.saturation) { v ->
                    val p = getProject()
                    updateProjectLive(p.copy(effects = p.effects.copy(saturation = v)))
                }
            }
        }
    }

    private fun openRadiusSheet(body: LinearLayout) {
        val layer = getSelectedLayer()
        val isImage = layer is ImageLayer
        val isRectShape = layer is ShapeLayer && layer.shapeLabel == BoundedShapeLabel.RECT
        if (!isImage && !isRectShape) return

        val maxR = (min(layer!!.width, layer.height) / 2f).roundToInt().coerceAtLeast(1)

        var radii = when (layer) {
            is ImageLayer -> layer.cornerRadii.toMutableList()
            is ShapeLayer -> (layer.cornerRadii ?: listOf(0f, 0f, 0f, 0f)).toMutableList()
            else -> mutableListOf(0f, 0f, 0f, 0f)
        }
        while (radii.size < 4) radii.add(0f)

        var enabled = when (layer) {
            is ImageLayer -> layer.cornerEnabled.toMutableList()
            is ShapeLayer -> layer.cornerEnabled.toMutableList()
            else -> mutableListOf(true, true, true, true)
        }
        while (enabled.size < 4) enabled.add(true)

        var locked = when (layer) {
            is ImageLayer -> layer.cornerLocked
            is ShapeLayer -> layer.cornerLocked
            else -> true
        }

        fun applyRadiusChanges() {
            when (val cur = getSelectedLayer()) {
                is ImageLayer -> updateSelectedLayerLive(
                    cur.copy(
                        cornerRadii = radii.toList(),
                        cornerEnabled = enabled.toList(),
                        cornerLocked = locked
                    )
                )
                is ShapeLayer -> updateSelectedLayerLive(
                    cur.copy(
                        cornerRadii = radii.toList(),
                        cornerEnabled = enabled.toList(),
                        cornerLocked = locked
                    )
                )
                else -> Unit
            }
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }

        // Top Header: ic_radius | Lock Pill | Reset | Green OK Circle
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(8))
        }

        val iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            setImageResource(R.drawable.ic_radius)
            setColorFilter(Color.parseColor("#222222"))
        }
        header.addView(iconView)

        header.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })

        val lockBtn = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(30)).apply {
                marginEnd = dp(10)
            }
        }
        val lockIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
        }
        lockBtn.addView(lockIcon)

        fun refreshLockUi() {
            lockBtn.background = GradientDrawable().apply {
                setColor(Color.parseColor(if (locked) "#3778DE" else "#CBD5E1"))
                cornerRadius = dp(999).toFloat()
            }
            lockIcon.setImageResource(if (locked) R.drawable.ic_lock else R.drawable.ic_unlock)
            lockIcon.setColorFilter(if (locked) Color.WHITE else Color.parseColor("#334155"))
        }
        refreshLockUi()

        val resetBtn = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                marginEnd = dp(10)
            }
            background = null
            setImageResource(R.drawable.ic_reset)
            setColorFilter(Color.parseColor("#333333"))
        }

        val okCircle = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#8FB0AF"))
            }
            setImageResource(R.drawable.ic_check)
            setColorFilter(Color.WHITE)
            setPadding(dp(7), dp(7), dp(7), dp(7))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setOnClickListener { commitCurrentTool() }
        }

        header.addView(lockBtn)
        header.addView(resetBtn)
        header.addView(okCircle)
        container.addView(header)

        // 2x2 Grid of Corner Cards: TL(0), TR(1) / BL(3), BR(2)
        val seekBars = arrayOfNulls<SeekBar>(4)
        val valLabels = arrayOfNulls<TextView>(4)
        val switches = arrayOfNulls<SwitchMaterial>(4)
        var updatingUi = false

        fun syncCardControls() {
            updatingUi = true
            for (i in 0..3) {
                val r = radii[i].roundToInt().coerceIn(0, maxR)
                seekBars[i]?.progress = r
                seekBars[i]?.isEnabled = enabled[i]
                valLabels[i]?.text = r.toString()
                switches[i]?.isChecked = enabled[i]
            }
            updatingUi = false
        }

        fun buildCornerCard(cornerIdx: Int): LinearLayout {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), Color.parseColor("#E2E8F0"))
                }
                setPadding(dp(10), dp(6), dp(8), dp(6))
            }

            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val diagram = object : View(context) {
                private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#B0B7C3")
                    style = Paint.Style.STROKE
                    strokeWidth = 2f * context.resources.displayMetrics.density
                }
                private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#3778DE")
                    style = Paint.Style.STROKE
                    strokeWidth = 2.6f * context.resources.displayMetrics.density
                    strokeCap = Paint.Cap.ROUND
                }
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val pad = 3f * context.resources.displayMetrics.density
                    val r = 6f * context.resources.displayMetrics.density
                    val rect = RectF(pad, pad, width - pad, height - pad)
                    canvas.drawRoundRect(rect, r, r, basePaint)
                    val d = r * 2f
                    val arcRect = when (cornerIdx) {
                        0 -> RectF(rect.left, rect.top, rect.left + d, rect.top + d)
                        1 -> RectF(rect.right - d, rect.top, rect.right, rect.top + d)
                        2 -> RectF(rect.right - d, rect.bottom - d, rect.right, rect.bottom)
                        else -> RectF(rect.left, rect.bottom - d, rect.left + d, rect.bottom)
                    }
                    val startAngle = when (cornerIdx) {
                        0 -> 180f
                        1 -> 270f
                        2 -> 0f
                        else -> 90f
                    }
                    canvas.drawArc(arcRect, startAngle, 90f, false, activePaint)
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            }
            topRow.addView(diagram)
            topRow.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })

            val sw = SwitchMaterial(context).apply {
                isChecked = enabled[cornerIdx]
                setOnCheckedChangeListener { _, isChecked ->
                    if (updatingUi) return@setOnCheckedChangeListener
                    enabled[cornerIdx] = isChecked
                    syncCardControls()
                    applyRadiusChanges()
                }
            }
            switches[cornerIdx] = sw
            topRow.addView(sw)
            card.addView(topRow)

            val bottomRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(2), 0, 0)
            }
            val tvVal = TextView(context).apply {
                text = radii[cornerIdx].roundToInt().toString()
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#334155"))
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(dp(34), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            val sb = SeekBar(context).apply {
                max = maxR
                progress = radii[cornerIdx].roundToInt().coerceIn(0, maxR)
                isEnabled = enabled[cornerIdx]
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        if (!fromUser || updatingUi) return
                        if (locked) {
                            for (i in 0..3) radii[i] = p.toFloat()
                        } else {
                            radii[cornerIdx] = p.toFloat()
                        }
                        syncCardControls()
                        applyRadiusChanges()
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) = Unit
                    override fun onStopTrackingTouch(s: SeekBar?) = Unit
                })
            }
            seekBars[cornerIdx] = sb
            valLabels[cornerIdx] = tvVal
            bottomRow.addView(sb)
            bottomRow.addView(tvVal)
            card.addView(bottomRow)

            return card
        }

        val row1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(buildCornerCard(0)) // Top-Left
        row1.addView(buildCornerCard(1)) // Top-Right

        val row2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(buildCornerCard(3)) // Bottom-Left
        row2.addView(buildCornerCard(2)) // Bottom-Right

        lockBtn.setOnClickListener {
            locked = !locked
            if (locked) {
                val baseVal = radii[0]
                for (i in 0..3) radii[i] = baseVal
                syncCardControls()
            }
            refreshLockUi()
            applyRadiusChanges()
        }

        resetBtn.setOnClickListener {
            for (i in 0..3) {
                radii[i] = 0f
                enabled[i] = true
            }
            syncCardControls()
            applyRadiusChanges()
        }

        container.addView(row1)
        container.addView(row2)
        body.addView(container)
    }

    private fun openFillSheet(body: LinearLayout, isBackground: Boolean) {
        val sel = if (isBackground) null else getSelectedLayer() ?: return
        val proj = getProject()

        var isGradientMode = when {
            isBackground -> proj.background.type == BackgroundType.GRADIENT
            sel is TextLayer -> sel.fillType == FillType.GRADIENT
            sel is ShapeLayer -> sel.fillType == FillType.GRADIENT
            sel is ArrowLayer -> sel.fillType == FillType.GRADIENT
            sel is ImageLayer -> sel.colorOverlayFillType == FillType.GRADIENT
            else -> false
        }

        var tintEnabled = (sel as? ImageLayer)?.colorOverlayEnabled ?: true

        fun currentColor(): Int = when {
            isBackground -> proj.background.color
            sel is TextLayer -> (getSelectedLayer() as? TextLayer)?.color ?: sel.color
            sel is ShapeLayer -> (getSelectedLayer() as? ShapeLayer)?.color ?: sel.color
            sel is ArrowLayer -> (getSelectedLayer() as? ArrowLayer)?.color ?: sel.color
            sel is ImageLayer -> (getSelectedLayer() as? ImageLayer)?.colorOverlay ?: sel.colorOverlay
            else -> Color.WHITE
        }

        fun currentGrad(): GradientFill = when {
            isBackground -> getProject().background.gradient
            sel is TextLayer -> (getSelectedLayer() as? TextLayer)?.gradient ?: sel.gradient
            sel is ShapeLayer -> (getSelectedLayer() as? ShapeLayer)?.gradient ?: sel.gradient
            sel is ArrowLayer -> (getSelectedLayer() as? ArrowLayer)?.gradient ?: sel.gradient
            sel is ImageLayer -> (getSelectedLayer() as? ImageLayer)?.colorOverlayGradient ?: sel.colorOverlayGradient
            else -> GradientFill()
        }

        fun applyColor(c: Int) {
            if (isBackground) {
                val p = getProject()
                updateProjectLive(p.copy(background = p.background.copy(type = BackgroundType.COLOR, color = c)))
            } else {
                when (val cur = getSelectedLayer()) {
                    is TextLayer -> updateSelectedLayerLive(cur.copy(fillType = FillType.COLOR, color = c))
                    is ShapeLayer -> updateSelectedLayerLive(cur.copy(fillType = FillType.COLOR, color = c))
                    is ArrowLayer -> updateSelectedLayerLive(cur.copy(fillType = FillType.COLOR, color = c))
                    is ImageLayer -> updateSelectedLayerLive(
                        cur.copy(colorOverlayEnabled = true, colorOverlayFillType = FillType.COLOR, colorOverlay = c)
                    )
                    else -> Unit
                }
            }
        }

        fun applyGradient(g: GradientFill) {
            if (isBackground) {
                val p = getProject()
                updateProjectLive(p.copy(background = p.background.copy(type = BackgroundType.GRADIENT, gradient = g)))
            } else {
                when (val cur = getSelectedLayer()) {
                    is TextLayer -> updateSelectedLayerLive(cur.copy(fillType = FillType.GRADIENT, gradient = g))
                    is ShapeLayer -> updateSelectedLayerLive(cur.copy(fillType = FillType.GRADIENT, gradient = g))
                    is ArrowLayer -> updateSelectedLayerLive(cur.copy(fillType = FillType.GRADIENT, gradient = g))
                    is ImageLayer -> updateSelectedLayerLive(
                        cur.copy(colorOverlayEnabled = true, colorOverlayFillType = FillType.GRADIENT, colorOverlayGradient = g)
                    )
                    else -> Unit
                }
            }
        }

        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        val leftCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }

        // Top Row: IMPORT | [Enabled : Switch if ImageLayer] | EXPORT
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(8))
        }
        val btnImport = TextView(context).apply {
            text = "IMPORT"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#009688"))
            setPadding(dp(4), dp(4), dp(8), dp(4))
        }
        val btnExport = TextView(context).apply {
            text = "EXPORT"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#009688"))
            setPadding(dp(8), dp(4), dp(4), dp(4))
        }
        topRow.addView(btnImport)
        topRow.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })

        val tintBody = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (sel !is ImageLayer || tintEnabled) View.VISIBLE else View.GONE
        }

        if (sel is ImageLayer) {
            topRow.addView(TextView(context).apply {
                text = "Enabled : "
                textSize = 12f
                setTextColor(Color.parseColor("#666666"))
            })
            val sw = SwitchMaterial(context).apply {
                isChecked = tintEnabled
                setOnCheckedChangeListener { _, checked ->
                    tintEnabled = checked
                    tintBody.visibility = if (checked) View.VISIBLE else View.GONE
                    (getSelectedLayer() as? ImageLayer)?.let { cur ->
                        updateSelectedLayerLive(cur.copy(colorOverlayEnabled = checked))
                    }
                }
            }
            topRow.addView(sw)
            topRow.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        }

        topRow.addView(btnExport)
        leftCol.addView(topRow)

        // Pink segmented switcher: color / gradient
        val pink = Color.parseColor("#EC2166")
        val segWrap = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(2), 0, dp(10))
        }
        val segBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(112), LinearLayout.LayoutParams.WRAP_CONTENT)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(1), pink)
                cornerRadius = dp(5).toFloat()
            }
            setPadding(dp(1), dp(1), dp(1), dp(1))
        }
        val btnSegColor = TextView(context).apply {
            text = "color"
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(6))
        }
        val btnSegGrad = TextView(context).apply {
            text = "gradient"
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(6))
        }
        segBox.addView(btnSegColor)
        segBox.addView(btnSegGrad)
        segWrap.addView(segBox)
        tintBody.addView(segWrap)

        // Swatch Strip Container (#A9A9A9 pill background + horizontal swatches)
        val stripFrame = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#A9A9A9"))
                cornerRadius = dp(6).toFloat()
            }
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        val swatchScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val swatchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        swatchScroll.addView(swatchRow)
        stripFrame.addView(swatchScroll)
        tintBody.addView(stripFrame)

        fun renderSwatches() {
            // Update pink switcher visual state
            btnSegColor.setBackgroundColor(if (!isGradientMode) pink else Color.WHITE)
            btnSegColor.setTextColor(if (!isGradientMode) Color.WHITE else pink)
            btnSegGrad.setBackgroundColor(if (isGradientMode) pink else Color.WHITE)
            btnSegGrad.setTextColor(if (isGradientMode) Color.WHITE else pink)

            swatchRow.removeAllViews()

            // "+" add custom color/gradient circle button
            val plusBtn = TextView(context).apply {
                text = "+"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#334155"))
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                    setMargins(dp(4), dp(2), dp(6), dp(2))
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                    setStroke(dp(1), Color.parseColor("#CBD5E1"))
                }
                setOnClickListener {
                    if (isGradientMode) {
                        Dialogs.showGradientMakerDialog(context, currentGrad()) { g ->
                            JsonPresetCodec.saveCustomGradient(context, g)
                            applyGradient(g)
                            renderSwatches()
                        }
                    } else {
                        Dialogs.showColorPickerDialog(context, currentColor()) { picked ->
                            JsonPresetCodec.saveCustomColor(context, picked)
                            applyColor(picked)
                            renderSwatches()
                        }
                    }
                }
            }
            swatchRow.addView(plusBtn)

            if (!isGradientMode) {
                val activeCol = currentColor()
                val allColors = (JsonPresetCodec.loadCustomColors(context) + AppDefaults.defaultColors).distinct()
                for (c in allColors) {
                    val isSel = activeCol == c
                    val dot = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                            setMargins(dp(4), dp(2), dp(4), dp(2))
                        }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(c)
                            setStroke(dp(if (isSel) 3 else 1), if (isSel) Color.WHITE else Color.parseColor("#888888"))
                        }
                        setOnClickListener {
                            applyColor(c)
                            renderSwatches()
                        }
                    }
                    swatchRow.addView(dot)
                }
            } else {
                val allGrads = JsonPresetCodec.loadCustomGradients(context) + AppDefaults.defaultGradients
                val activeGradKey = JsonPresetCodec.gradToWebJson(currentGrad()).toString()
                for (g in allGrads) {
                    val isSel = JsonPresetCodec.gradToWebJson(g).toString() == activeGradKey
                    val cols = if (g.stops.size >= 2) g.stops.map { it.color }.toIntArray()
                    else intArrayOf(Color.BLACK, Color.WHITE)
                    val orient = when (g.type) {
                        GradientDirection.LINEAR_H -> GradientDrawable.Orientation.LEFT_RIGHT
                        GradientDirection.LINEAR_DIAG -> GradientDrawable.Orientation.TL_BR
                        else -> GradientDrawable.Orientation.TOP_BOTTOM
                    }
                    val dot = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                            setMargins(dp(4), dp(2), dp(4), dp(2))
                        }
                        background = GradientDrawable(orient, cols).apply {
                            shape = GradientDrawable.OVAL
                            setStroke(dp(if (isSel) 3 else 1), if (isSel) Color.WHITE else Color.parseColor("#888888"))
                        }
                        setOnClickListener {
                            applyGradient(g)
                            renderSwatches()
                        }
                    }
                    swatchRow.addView(dot)
                }
            }
        }

        btnSegColor.setOnClickListener {
            isGradientMode = false
            applyColor(currentColor())
            renderSwatches()
        }
        btnSegGrad.setOnClickListener {
            isGradientMode = true
            applyGradient(currentGrad())
            renderSwatches()
        }

        btnImport.setOnClickListener {
            onRequestImportJson { raw ->
                runCatching {
                    val res = JsonPresetCodec.importColorsAndGradientsJson(context, raw)
                    if (res.gradients.isNotEmpty()) {
                        isGradientMode = true
                        applyGradient(res.gradients.first())
                    } else if (res.colors.isNotEmpty()) {
                        isGradientMode = false
                        applyColor(res.colors.first())
                    }
                    renderSwatches()
                    Toast.makeText(
                        context,
                        "Imported ${res.colors.size} color(s), ${res.gradients.size} gradient(s)",
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure {
                    Toast.makeText(context, "Invalid color/gradient JSON file", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnExport.setOnClickListener {
            val activeCol = if (!isGradientMode) currentColor() else null
            val activeGrad = if (isGradientMode) currentGrad() else null
            val json = JsonPresetCodec.exportColorsAndGradientsJson(context, activeCol, activeGrad)
            onRequestExportJson("pixellab_colors_gradients.json", json)
        }

        leftCol.addView(tintBody)
        renderSwatches()

        // Right-side vertical sage-green action column (✓ OK / ✕ Cancel)
        val actionCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(52), LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#8FB0AF"))
        }
        val btnOk = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            background = null
            setImageResource(R.drawable.ic_check)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { commitCurrentTool() }
        }
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(Color.parseColor("#789A99"))
        }
        val btnCancel = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            background = null
            setImageResource(R.drawable.ic_close)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { cancelCurrentTool() }
        }
        actionCol.addView(btnOk)
        actionCol.addView(divider)
        actionCol.addView(btnCancel)

        outer.addView(leftCol)
        outer.addView(actionCol)
        body.addView(outer)
    }

    private fun updateInnerShadow(transform: (com.imaginstudio.imagetools.pixellab.model.ShadowConfig) -> com.imaginstudio.imagetools.pixellab.model.ShadowConfig) {
        when (val cur = getSelectedLayer()) {
            is TextLayer -> updateSelectedLayerLive(cur.copy(innerShadow = transform(cur.innerShadow)))
            is ShapeLayer -> updateSelectedLayerLive(cur.copy(innerShadow = transform(cur.innerShadow)))
            is ImageLayer -> updateSelectedLayerLive(cur.copy(innerShadow = transform(cur.innerShadow)))
            else -> Unit
        }
    }

    private fun updateStroke(transform: (com.imaginstudio.imagetools.pixellab.model.StrokeConfig) -> com.imaginstudio.imagetools.pixellab.model.StrokeConfig) {
        when (val cur = getSelectedLayer()) {
            is TextLayer -> updateSelectedLayerLive(cur.copy(stroke = transform(cur.stroke)))
            is ShapeLayer -> updateSelectedLayerLive(cur.copy(stroke = transform(cur.stroke)))
            is ArrowLayer -> updateSelectedLayerLive(cur.copy(stroke = transform(cur.stroke)))
            is ImageLayer -> updateSelectedLayerLive(cur.copy(stroke = transform(cur.stroke)))
            else -> Unit
        }
    }

    private fun updateShadow(transform: (com.imaginstudio.imagetools.pixellab.model.ShadowConfig) -> com.imaginstudio.imagetools.pixellab.model.ShadowConfig) {
        when (val cur = getSelectedLayer()) {
            is TextLayer -> updateSelectedLayerLive(cur.copy(shadow = transform(cur.shadow)))
            is ShapeLayer -> updateSelectedLayerLive(cur.copy(shadow = transform(cur.shadow)))
            is ArrowLayer -> updateSelectedLayerLive(cur.copy(shadow = transform(cur.shadow)))
            is ImageLayer -> updateSelectedLayerLive(cur.copy(shadow = transform(cur.shadow)))
            else -> Unit
        }
    }

    private fun addToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
        val sw = SwitchMaterial(context).apply {
            text = label
            isChecked = checked
            textSize = 13f
            setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        }
        binding.optionExpandBody.addView(sw)
    }

    private fun addSliderRow(label: String, min: Int, max: Int, current: Int, onChange: (Int) -> Unit) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 6, 0, 6)
        }
        val tvLabel = TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(Color.parseColor("#334155"))
            layoutParams = LinearLayout.LayoutParams(220, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val tvVal = TextView(context).apply {
            text = current.toString()
            textSize = 12f
            setTextColor(Color.parseColor("#0079FF"))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(80, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val seek = SeekBar(context).apply {
            this.max = (max - min).coerceAtLeast(1)
            this.progress = (current - min).coerceIn(0, this.max)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = min + progress
                    tvVal.text = value.toString()
                    if (fromUser) onChange(value)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            })
        }
        row.addView(tvLabel)
        row.addView(seek)
        row.addView(tvVal)
        binding.optionExpandBody.addView(row)
    }

    private fun addColorPaletteRow(label: String, onPick: (Int) -> Unit) {
        binding.optionExpandBody.addView(TextView(context).apply {
            text = label
            textSize = 11f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, 6, 0, 2)
        })
        val hs = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val customBtn = Button(context).apply {
            text = "+"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(90, 82).apply { setMargins(4, 4, 8, 4) }
            setOnClickListener {
                Dialogs.showColorPickerDialog(context, Color.parseColor("#0079FF")) { picked ->
                    onPick(picked)
                }
            }
        }
        row.addView(customBtn)
        for (c in AppDefaults.defaultColors) {
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(64, 64).apply { setMargins(6, 4, 6, 4) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c)
                    setStroke(3, Color.parseColor("#CBD5E1"))
                }
                setOnClickListener { onPick(c) }
            }
            row.addView(dot)
        }
        hs.addView(row)
        binding.optionExpandBody.addView(hs)
    }

    private fun addGradientPaletteRow(label: String, onPick: (GradientFill) -> Unit) {
        binding.optionExpandBody.addView(TextView(context).apply {
            text = label
            textSize = 11f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, 8, 0, 2)
        })
        val hs = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        for (g in AppDefaults.defaultGradients) {
            val c1 = g.stops.firstOrNull()?.color ?: Color.BLACK
            val c2 = g.stops.lastOrNull()?.color ?: Color.WHITE
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(68, 68).apply { setMargins(6, 4, 6, 4) }
                background = GradientDrawable(
                    when (g.type) {
                        GradientDirection.LINEAR_H -> GradientDrawable.Orientation.LEFT_RIGHT
                        else -> GradientDrawable.Orientation.TOP_BOTTOM
                    },
                    intArrayOf(c1, c2)
                ).apply {
                    shape = GradientDrawable.OVAL
                    setStroke(3, Color.parseColor("#CBD5E1"))
                }
                setOnClickListener { onPick(g) }
            }
            row.addView(dot)
        }
        hs.addView(row)
        binding.optionExpandBody.addView(hs)
    }

    private fun addRowButtons(items: List<Pair<String, () -> Unit>>) {
        val hs = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        for ((title, action) in items) {
            val btn = Button(context).apply {
                text = title
                textSize = 11f
                isAllCaps = false
                setOnClickListener { action() }
            }
            row.addView(btn)
        }
        hs.addView(row)
        binding.optionExpandBody.addView(hs)
    }
}
