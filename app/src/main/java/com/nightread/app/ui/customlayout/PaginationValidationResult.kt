package com.nightread.app.ui.customlayout

data class PaginationValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)
