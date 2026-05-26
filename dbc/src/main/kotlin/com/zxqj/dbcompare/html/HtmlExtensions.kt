package com.zxqj.dbcompare.html

import kotlinx.html.*

fun LABEL.dataField() {
    attributes["data-field"] = ""
}

fun SMALL.dataHint() {
    attributes["data-hint"] = ""
}

fun FlowContent.flexLabel(flex: Int, classes: String? = null, content: LABEL.() -> Unit) {
    label(classes) {
        dataField()
        style = "flex:$flex"
        content()
    }
}

fun FlowContent.chipLabelHint(
    label: String,
    inputName: String,
    inputPlaceHolder: String,
    hint: String,
    classes: String? = null
) {
    label(classes) {
        dataField()
        +label
        chipInput(inputName, inputPlaceHolder)
        small {
            dataHint()
            +hint
        }
    }
}

fun CommonAttributeGroupFacade.dataBind(value: String) {
    attributes["data-bind"] = value
}

fun CommonAttributeGroupFacade.dataRef(value: String) {
    attributes["data-ref"] = value
}

fun CommonAttributeGroupFacade.dataOnChange(value: String) {
    attributes["data-on:change"] = value
}

fun CommonAttributeGroupFacade.dataOnClick(value: String) {
    attributes["data-on:click"] = value
}

fun CommonAttributeGroupFacade.dataIndicator(value: String) {
    attributes["data-indicator"] = value
}

fun CommonAttributeGroupFacade.dataShowIf(value: String) {
    attributes["data-show"] = "\$$value"
}

fun CommonAttributeGroupFacade.dataShowNot(value: String) {
    attributes["data-show"] = "!\$$value"
}