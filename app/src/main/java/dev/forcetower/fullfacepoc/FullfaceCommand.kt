package dev.forcetower.fullfacepoc

sealed class FullfaceCommand  {
    object CreatedAccountCommand : FullfaceCommand()
    data class CreateErrorCommand(val error: String) : FullfaceCommand()
    data class MatchedAccountCommand(val id: Int, val similarity: Double) : FullfaceCommand()
    data class MatchErrorCommand(val error: String) : FullfaceCommand()
}