package dev.forcetower.fullfacepoc.model

data class RegisterModel(
    val pictures: List<String>,
    val keys: List<ModelEntry> = listOf(ModelEntry("abc", "123")),
    val accessToken: String = "",
    val projectName: String = ""
)
