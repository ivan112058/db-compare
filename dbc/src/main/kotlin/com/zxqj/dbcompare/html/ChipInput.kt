package com.zxqj.dbcompare.html

import kotlinx.html.*

class ChipInput(consumer: TagConsumer<*>, initialAttributes: Map<String, String> = emptyMap()) : HTMLTag(
    tagName = "oat-chip-input",
    consumer = consumer,
    initialAttributes = initialAttributes,
    inlineTag = false,
    emptyTag = false
), HtmlBlockTag {
    var name: String by attributes
    var placeholder: String by attributes
}

fun FlowContent.chipInput(name: String, placeholder: String) {
    ChipInput(consumer, mapOf("name" to name, "placeholder" to placeholder)).visit {}
}