package dev.forcetower.fullfacepoc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.navigation.findNavController
import dev.forcetower.fullfacepoc.databinding.ActivityFullfaceBinding

class FullfaceActivity : AppCompatActivity() {
    private val navController
        get() = findNavController(R.id.fragment_container)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataBindingUtil.setContentView<ActivityFullfaceBinding>(this, R.layout.activity_fullface)
    }

    override fun onSupportNavigateUp() = navController.navigateUp()
}