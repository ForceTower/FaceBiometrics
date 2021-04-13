package dev.forcetower.fullfacepoc

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Size
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.VideoCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dev.forcetower.fullfacepoc.databinding.FragmentVideoCaptureBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@SuppressLint("RestrictedApi")
class VideoCaptureFragment : Fragment() {
    private lateinit var binding: FragmentVideoCaptureBinding
    private lateinit var videoCapture: VideoCapture

    private var recordingStartMillis: Long = 0L
    private var recording = false

    private val requestPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.all { it.value }) initializeCamera()
        else Toast.makeText(requireContext(), "Você precisa dar todas as permissões...", Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentVideoCaptureBinding.inflate(inflater, container, false).also {
            binding = it
        }.root
    }

    override fun onResume() {
        super.onResume()
        requestPermissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {
        configureCamera()

        binding.record.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> lifecycleScope.launch(Dispatchers.IO) {
                    if (recording) {
                        Timber.d("Already recording")
                    } else {
                        recording = true
                        withContext(Dispatchers.Main) {
                            binding.timer.text = "Gravando..."
                        }
                        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                        val outputFile = createFile(getOutputDirectory())
                        val outputOptions = VideoCapture.OutputFileOptions.Builder(outputFile).build()
                        videoCapture.startRecording(
                            outputOptions,
                            ContextCompat.getMainExecutor(requireContext()),
                            object : VideoCapture.OnVideoSavedCallback {
                                override fun onVideoSaved(outputFileResults: VideoCapture.OutputFileResults) {
                                    val uri = outputFileResults.savedUri
                                    Timber.d("Video Saved at $uri!")
                                    uri?.let { extractPictures(uri) }
                                }

                                override fun onError(
                                    videoCaptureError: Int,
                                    message: String,
                                    cause: Throwable?
                                ) {
                                    Timber.e(cause, "Error during video capture. Code: $videoCaptureError. Message: $message")
                                }

                            })

                        recordingStartMillis = System.currentTimeMillis()
                    }
                }

                MotionEvent.ACTION_UP -> lifecycleScope.launch(Dispatchers.IO) {
                    val elapsedTimeMillis = System.currentTimeMillis() - recordingStartMillis
                    if (elapsedTimeMillis < MIN_REQUIRED_RECORDING_TIME_MILLIS) {
                        delay(MIN_REQUIRED_RECORDING_TIME_MILLIS - elapsedTimeMillis)
                    }

                    withContext(Dispatchers.Main) {
                        binding.timer.text = ""
                    }
                    videoCapture.stopRecording()
                    recording = false
                    requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
            true
        }
    }

    private fun configureCamera() {
        val provider = ProcessCameraProvider.getInstance(requireContext())

        provider.addListener({
            val cameraProvider = provider.get()
            val preview = Preview.Builder()
                .build()

            preview.setSurfaceProvider(binding.cameraPreview.surfaceProvider)

            val selector = CameraSelector.DEFAULT_FRONT_CAMERA

            videoCapture = VideoCapture.Builder()
                .setTargetName("VideoCapture")
                .setVideoFrameRate(30) // TODO Check if device supports it
                .setMaxResolution(Size(640, 480)) // VGA Specs
                .build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(viewLifecycleOwner, selector, preview, videoCapture)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun extractPictures(uri: Uri) {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(uri.toFile().absolutePath)

        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0
        Timber.d("Duration of video fragment $duration")

        if (duration > 500) {
            val count = (duration - 8) / 6
            for (time in 4 until duration step count) {
                val bitmap = retriever.getFrameAtTime(time * 1000L)
                bitmap?.let {
                    Timber.d("Encode for frame at time $time")
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                    val data = baos.toByteArray()
                    val encoded = Base64.encodeToString(data, Base64.DEFAULT)
                }
            }
        } else {
            Timber.d("Insuficient time")
            Toast.makeText(requireContext(), "Grave por mais tempo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = requireActivity().externalMediaDirs.firstOrNull()?.let {
            File(it, "recordings").apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else requireActivity().filesDir
    }

    companion object {
        private const val MIN_REQUIRED_RECORDING_TIME_MILLIS: Long = 3000L

        private const val FILENAME = "yyyy_MM_dd_HH_mm_ss"
        private const val VIDEO_EXTENSION = ".mp4"

        private fun createFile(baseFolder: File) =
            File(
                baseFolder,
                SimpleDateFormat(
                    FILENAME,
                    Locale.US
                ).format(System.currentTimeMillis()) + VIDEO_EXTENSION
            )
    }
}
