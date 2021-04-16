package dev.forcetower.fullfacepoc

import android.net.Uri

sealed class FullfaceInteraction {
    data class CreateAccount(val images: List<Uri>) : FullfaceInteraction()
    data class MatchBiometric(val images: List<Uri>) : FullfaceInteraction()
}
