package dev.forcetower.fullfacepoc.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dev.forcetower.fullfacepoc.databinding.FragmentSingleBinding

class SingleFragment : Fragment() {
    private lateinit var binding: FragmentSingleBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentSingleBinding.inflate(inflater, container, false).also {
            binding = it
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnAuth.setOnClickListener {
            val auth = DialogAuthentication()
            auth.show(childFragmentManager, "auth")
        }
    }
}