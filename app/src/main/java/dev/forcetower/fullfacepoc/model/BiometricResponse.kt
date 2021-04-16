package dev.forcetower.fullfacepoc.model

data class BiometricResponse(
    val id: Int,
    val birthYear: Int,
    val gender: String,
    val keys: List<ModelEntry>,
    val similarity: Double
)
