package com.zxqj.dbcompare.html

import com.zxqj.dbcompare.listYamlFiles
import kotlinx.html.*
import java.io.File

fun HTML.configPage(configDir: File) {
    commonHead()
    body(classes = "config-body") {
        attributes["data-theme"] = "stone"

        article(classes = "card") {
            header(classes = "flex justify-between") {
                h3 {
                    +"Configuration"
                }
                a(href = "/env") {
                    +"env management"
                }
            }
            div(classes = "flex gap-4") {
                flexLabel(1) {
                    +"Load Config"

                    select {
                        id = "config-select"
                        style = "margin:0"

                        dataBind("selectedConfig")
                        dataOnChange($$"$selectedConfig \\&\\& @get('/api/configs/' + $selectedConfig)")

                        option {
                            value = ""
                            +"-- Select config --"
                        }
                        listYamlFiles(configDir).forEach { fileName ->
                            option {
                                value = fileName
                                +fileName
                            }
                        }
                    }
                }
                flexLabel(1) {
                    +"Save Config"

                    fieldSet(classes = "group") {
                        input(type = InputType.text, name = "saveConfigName") {
                            placeholder = "Config Name (e.g. my-config)"
                            dataRef("saveConfigInput")
                        }
                        button {
                            dataOnClick("@put('/api/configs/' + \$saveConfigInput.value, {contentType: 'form', selector: '#config-form'})")
                            +"Save"
                        }
                    }
                }
            }
        }

        form {
            id = "config-form"

            div(classes = "flex gap-4 mt-4") {
                for (dbType in listOf("target", "source")) {
                    article(classes = "card") {
                        style = "flex:1"

                        header {
                            h3 { +dbType.replaceFirstChar { it.uppercase() } }
                        }
                        div(classes = "flex gap-2") {
                            flexLabel(3) {
                                +"Host"
                                input(type = InputType.text, name = "$dbType.host") {
                                    value = "localhost"
                                }
                            }
                            flexLabel(1) {
                                +"Port"
                                input(type = InputType.number, name = "$dbType.port") {
                                    value = "3306"
                                }
                            }
                        }
                        div(classes = "flex gap-2") {
                            flexLabel(1) {
                                +"Username"
                                input(type = InputType.text, name = "$dbType.username") {
                                    value = "root"
                                }
                            }
                            flexLabel(1) {
                                +"Password"
                                input(type = InputType.password, name = "$dbType.password")
                            }
                        }
                        label {
                            dataField()
                            +"Database"
                            input(type = InputType.text, name = "$dbType.database")
                        }
                    }
                }
            }

            article(classes = "card mt-4") {
                header { h3 { +"Options" } }
                chipLabelHint(
                    label = "Ignore Fields",
                    inputName = "ignoreFields",
                    inputPlaceHolder = "e.g. create_time, table.column. Press ENTER to add.",
                    hint = "Columns that should be ignored during comparison. Global or table-specific.",
                )
                chipLabelHint(
                    label = "Exclude Tables",
                    inputName = "excludeTables",
                    inputPlaceHolder = "Table names. Press ENTER to add.",
                    hint = "Tables that should be excluded from comparison.",
                )
                chipLabelHint(
                    label = "Ignore Data Tables",
                    inputName = "ignoreDataTables",
                    inputPlaceHolder = "Table names. Press ENTER to add.",
                    hint = "Tables whose data should be ignored during comparison.",
                )
                chipLabelHint(
                    label = "Specified Primary Keys",
                    inputName = "specifiedPrimaryKeys",
                    inputPlaceHolder = "table(col1,col2). Press ENTER to add.",
                    hint = "Primary keys that should be used for comparison.",
                )
                chipLabelHint(
                    label = "Tree Table Config",
                    inputName = "treeTableConfig",
                    inputPlaceHolder = "table(id,parent_id). Press ENTER to add.",
                    hint = "Tree-structured tables. MUST set Specified Primary Keys.",
                )
                chipLabelHint(
                    label = "Exclude Data Rows",
                    inputName = "excludeDataRows",
                    inputPlaceHolder = "table(col=val). Press ENTER to add.",
                    hint = "Rows that should be excluded from comparison. This config will be ignored if Include Data Rows is set.",
                )
                chipLabelHint(
                    label = "Include Data Rows",
                    inputName = "includeDataRows",
                    inputPlaceHolder = "table(col=val). Press ENTER to add.",
                    hint = "Rows that should be included in comparison.",
                )
            }

            div(classes = "align-center mt-4") {
                var indicator = "_loading"

                button {
                    dataIndicator(indicator)
                    dataShowNot(indicator)
                    dataOnClick("@post('/api/compare', {contentType: 'form'})")
                    +"Start Comparison"
                }
                button {
                    dataShowIf(indicator)
                    attributes["aria-busy"] = "true"
                    attributes["data-spinner"] = "small"
                    attributes["disabled"] = ""
                }
            }
        }

        commonScript()
    }
}