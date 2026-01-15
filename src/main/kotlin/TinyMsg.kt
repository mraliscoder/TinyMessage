package fi.sulku.hytale

import com.hypixel.hytale.protocol.MaybeBool
import com.hypixel.hytale.server.core.Message
import java.awt.Color
import java.util.*
import java.util.regex.Pattern

object TinyMsg {
    // Matches <tag>, <tag:arg>, </tag>
    private val TAG_PATTERN = Pattern.compile("<(/?)([a-zA-Z0-9_]+)(?::([^>]+))?>")

    private val NAMED_COLORS = mapOf(
        "black" to Color(0, 0, 0),
        "dark_blue" to Color(0, 0, 170),
        "dark_green" to Color(0, 170, 0),
        "dark_aqua" to Color(0, 170, 170),
        "dark_red" to Color(170, 0, 0),
        "dark_purple" to Color(170, 0, 170),
        "gold" to Color(255, 170, 0),
        "gray" to Color(170, 170, 170),
        "dark_gray" to Color(85, 85, 85),
        "blue" to Color(85, 85, 255),
        "green" to Color(85, 255, 85),
        "aqua" to Color(85, 255, 255),
        "red" to Color(255, 85, 85),
        "light_purple" to Color(255, 85, 255),
        "yellow" to Color(255, 255, 85),
        "white" to Color(255, 255, 255)
    )

    private data class StyleState(
        val color: Color? = null,
        val gradient: List<Color>? = null,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underlined: Boolean = false,
        val monospace: Boolean = false,
        val link: String? = null
    )

    fun parse(text: String): Message {
        if (!text.contains("<")) {
            return Message.raw(text)
        }

        val root = Message.empty()

        // Stack keeps track of nested styles.
        // Example: Stack = [Base, Bold, Bold+Red]
        val stateStack = ArrayDeque<StyleState>()
        stateStack.push(StyleState()) // Start with default empty state

        val matcher = TAG_PATTERN.matcher(text)
        var lastIndex = 0

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()

            // 1. Handle text BEFORE this tag (using the state at the top of the stack)
            if (start > lastIndex) {
                val content = text.substring(lastIndex, start)
                val segmentMsg = createStyledMessage(content, stateStack.peek())
                root.insert(segmentMsg)
            }

            // 2. Process the tag to update the Stack
            val isClosing = matcher.group(1) == "/"
            val tagName = matcher.group(2).lowercase()
            val tagArg = matcher.group(3)

            if (isClosing) {
                if (stateStack.size > 1) {
                    stateStack.pop()
                }
            } else {
                // Start with the current state, and modify it
                val currentState = stateStack.peek()
                var newState = currentState.copy()

                // If checking named colors directly
                if (tagName in NAMED_COLORS) {
                    newState = newState.copy(color = NAMED_COLORS[tagName], gradient = null)
                } else {
                    when (tagName) {
                        "color", "c", "colour" -> {
                            val c = parseColorArg(tagArg)
                            if (c != null) newState = newState.copy(color = c, gradient = null)
                        }

                        "gradient" -> {
                            if (tagArg != null) {
                                val colors = parseGradientColors(tagArg)
                                if (colors.isNotEmpty()) {
                                    newState = newState.copy(gradient = colors, color = null)
                                }
                            }
                        }

                        "bold", "b" -> newState = newState.copy(bold = true)
                        "italic", "i", "em" -> newState = newState.copy(italic = true)
                        "underline", "u" -> newState = newState.copy(underlined = true)
                        "monospace", "mono" -> newState = newState.copy(monospace = true)
                        "link", "url" -> {
                            if (tagArg != null) newState = newState.copy(link = tagArg)
                        }

                        "reset", "r" -> {
                            stateStack.clear()
                            newState = StyleState()
                        }
                    }
                }
                stateStack.push(newState)
            }

            lastIndex = end
        }

        if (lastIndex < text.length) {
            val content = text.substring(lastIndex)
            val segmentMsg = createStyledMessage(content, stateStack.peek())
            root.insert(segmentMsg)
        }

        return root
    }

    private fun createStyledMessage(content: String, state: StyleState): Message {
        // If we have a gradient, we must return a container with char-by-char coloring
        if (!state.gradient.isNullOrEmpty()) {
            return applyGradient(content, state)
        }

        val msg = Message.raw(content)

        if (state.color != null) msg.color(state.color)
        if (state.bold) msg.bold(true)
        if (state.italic) msg.italic(true)
        if (state.monospace) msg.monospace(true)
        if (state.underlined) msg.formattedMessage.underlined = MaybeBool.True
        if (state.link != null) msg.link(state.link)

        return msg
    }

    private fun applyGradient(text: String, state: StyleState): Message {
        val container = Message.empty()
        val colors = state.gradient!!
        val length = text.length

        text.forEachIndexed { index, char ->
            val progress = index.toFloat() / (length - 1).coerceAtLeast(1)
            val color = interpolateColor(colors, progress)

            val charMsg = Message.raw(char.toString()).color(color)

            if (state.bold) charMsg.bold(true)
            if (state.italic) charMsg.italic(true)
            if (state.monospace) charMsg.monospace(true)
            if (state.underlined) charMsg.formattedMessage.underlined = MaybeBool.True

            if (state.link != null) charMsg.link(state.link)

            container.insert(charMsg)
        }
        return container
    }

    private fun parseColorArg(arg: String?): Color? {
        if (arg == null) return null
        return if (arg in NAMED_COLORS) NAMED_COLORS[arg] else parseHexColor(arg)
    }

    private fun parseGradientColors(arg: String): List<Color> {
        return arg.split(":").mapNotNull { parseColorArg(it) }
    }

    private fun parseHexColor(hex: String): Color? {
        return try {
            val clean = hex.replace("#", "")
            if (clean.length == 6) {
                Color(clean.substring(0, 2).toInt(16), clean.substring(2, 4).toInt(16), clean.substring(4, 6).toInt(16))
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun interpolateColor(colors: List<Color>, progress: Float): Color {
        val clampedProgress = progress.coerceIn(0f, 1f)
        val scaledProgress = clampedProgress * (colors.size - 1)
        val index = scaledProgress.toInt().coerceAtMost(colors.size - 2)
        val localProgress = scaledProgress - index

        val c1 = colors[index]
        val c2 = colors[index + 1]

        return Color(
            (c1.red + (c2.red - c1.red) * localProgress).toInt(),
            (c1.green + (c2.green - c1.green) * localProgress).toInt(),
            (c1.blue + (c2.blue - c1.blue) * localProgress).toInt()
        )
    }
}


