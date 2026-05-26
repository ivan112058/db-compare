package com.zxqj.dbcompare.html

import kotlinx.html.*

fun HTML.commonHead() {
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title("DB Compare")
        link(rel = "stylesheet", href = "/css/oat.min.css")
        link(rel = "stylesheet", href = "/css/chip.min.css")
        link(rel = "stylesheet", href = "/css/theme.css")
    }
}

fun BODY.commonScript() {
    script(src = "/js/oat-chip-input.js") { attributes["defer"] = "" }
    script(src = "/js/oat.min.js") { attributes["defer"] = "" }
    script(src = "/js/chip.min.js") { attributes["defer"] = "" }
    script(type = "module", src = "/js/datastar.js") {}
}