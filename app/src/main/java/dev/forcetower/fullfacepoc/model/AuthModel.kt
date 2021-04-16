package dev.forcetower.fullfacepoc.model

data class AuthModel(
    val pictures: List<String>,
    val keys: List<ModelEntry> = listOf(ModelEntry("abc", "123")),
    val attributes: List<ModelEntry> = listOf(ModelEntry("abc", "123")),
    val accessToken: String = "",
    val projectName: String = ""
)
