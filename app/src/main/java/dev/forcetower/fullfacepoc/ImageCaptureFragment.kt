package dev.forcetower.fullfacepoc

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dev.forcetower.fullfacepoc.databinding.FragmentImageCaptureBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@SuppressLint("RestrictedApi")
class ImageCaptureFragment : Fragment() {
    private lateinit var binding: FragmentImageCaptureBinding
    private lateinit var imageCapture: ImageCapture
//    private lateinit var imageAnalysis: ImageAnalysis

    private var cameraInitialized = false

    private val requestInitialPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.all { it.value }) initializeCamera()
        else Toast.makeText(
            requireContext(),
            "Você precisa dar todas as permissões...",
            Toast.LENGTH_LONG
        ).show()
    }

    private val requestSecondaryPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.all { it.value }) startCapture()
        else Toast.makeText(
            requireContext(),
            "Você precisa dar todas as permissões...",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentImageCaptureBinding.inflate(inflater, container, false).also {
            binding = it
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnStartCapture.setOnClickListener {
            requestSecondaryPermissions.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    override fun onResume() {
        super.onResume()
        requestInitialPermissions.launch(arrayOf(Manifest.permission.CAMERA))
    }

    private fun initializeCamera() {
        if (cameraInitialized) return
        configureCamera()
        cameraInitialized = true
    }

    private fun configureCamera() {
        val provider = ProcessCameraProvider.getInstance(requireContext())

        provider.addListener({
            val cameraProvider = provider.get()
            val preview = Preview.Builder()
                .setMaxResolution(Size(640, 480))
                .setDefaultResolution(Size(640, 480))
                .build()

            preview.setSurfaceProvider(binding.cameraPreview.surfaceProvider)

            val selector = CameraSelector.DEFAULT_FRONT_CAMERA

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(CAPTURE_MODE_MINIMIZE_LATENCY)
                .setMaxResolution(Size(640, 480))
                .setDefaultResolution(Size(640, 480))
                .build()

//            imageAnalysis = ImageAnalysis.Builder()
//                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                .build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(viewLifecycleOwner, selector, preview, imageCapture)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun startCapture() = lifecycleScope.launch(Dispatchers.IO) {
        val f1 = captureImage()
        delay(400L)
        val f2 = captureImage()
        delay(400L)
        val f3 = captureImage()
        delay(400L)
        val f4 = captureImage()
        delay(400L)
        val f5 = captureImage()
        delay(400L)
        val f6 = captureImage()
    }

    private suspend fun captureImage() = suspendCancellableCoroutine<Uri> { continuation ->
        val outputDirectory = getOutputDirectory()

        val metadata = ImageCapture.Metadata().apply {
            isReversedHorizontal = true
        }

        val file = File(outputDirectory, "${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file)
            .setMetadata(metadata)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Timber.d("Image captured at ${Uri.fromFile(file)}")
                    continuation.resume(Uri.fromFile(file))
                }

                override fun onError(exception: ImageCaptureException) {
                    Timber.e(exception, "Error capturing image. Code ${exception.imageCaptureError}")
                    continuation.resumeWithException(exception)
                }
            }
        )
    }

    private fun getOutputDirectory(): File {
        val mediaDir = requireActivity().externalMediaDirs.firstOrNull()?.let {
            File(it, "images").apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else requireActivity().filesDir
    }
}