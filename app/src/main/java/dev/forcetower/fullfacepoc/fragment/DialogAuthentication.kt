package dev.forcetower.fullfacepoc.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import dev.forcetower.fullfacepoc.databinding.DialogBiometricsAuthBinding
import dev.forcetower.fullfacepoc.detection.FaceContourDetectionProcessor
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@SuppressLint("RestrictedApi")
class DialogAuthentication : DialogFragment() {
    private lateinit var binding: DialogBiometricsAuthBinding
    private lateinit var imageCapture: ImageCapture
    private lateinit var imageAnalysis: ImageAnalysis

    private val requestInitialPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.all { it.value }) initializeCamera()
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
        return DialogBiometricsAuthBinding.inflate(inflater, container, false).also {
            binding = it
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnAuth.setOnClickListener {
            startCapture()
        }
    }

    override fun onResume() {
        super.onResume()
        requestInitialPermissions.launch(arrayOf(Manifest.permission.CAMERA))
    }

    private fun initializeCamera() {
        configureCamera()
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

            val rotation = requireContext().display!!.rotation
            Timber.d("Rotation: $rotation")
            imageCapture = ImageCapture.Builder()
                .setTargetRotation(rotation)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setMaxResolution(Size(640, 480))
                .setDefaultResolution(Size(640, 480))
                .build()

            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor(), FaceContourDetectionProcessor(binding.overlay))
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(viewLifecycleOwner, selector, preview, imageCapture, imageAnalysis)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun startCapture() = lifecycleScope.launch {
        binding.btnAuth.visibility = View.INVISIBLE
        binding.timer.visibility = View.VISIBLE
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        val pictures = withContext(Dispatchers.IO) {
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
            listOf(f1, f2, f3, f4, f5, f6)
        }

        val resolver = requireContext().applicationContext.contentResolver
        val result = pictures.map {
            val stream = resolver.openInputStream(it)
            val bitmap = toGrayscale(BitmapFactory.decodeStream(stream))

            val exif = ExifInterface(it.toFile().absolutePath)
            val rotated = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotate(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotate(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotate(bitmap, 270f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    val temp = flipHorizontal(bitmap)
                    rotate(temp, 90f)
                }
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    val temp = flipHorizontal(bitmap)
                    rotate(temp, 270f)
                }
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipHorizontal(bitmap)
                else -> bitmap
            }

            val baos = ByteArrayOutputStream()
            rotated.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val data = baos.toByteArray()
            Base64.encodeToString(data, Base64.DEFAULT)
        }

        result.forEach {
            Timber.d(it)
        }

        binding.btnAuth.visibility = View.VISIBLE
        binding.timer.visibility = View.GONE
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun flipHorizontal(bitmap: Bitmap): Bitmap {
        val matrix = Matrix()
        matrix.setValues(floatArrayOf(-1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun toGrayscale(original: Bitmap): Bitmap {
        val height = original.height
        val width = original.width
        val bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmpGrayscale)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        val f = ColorMatrixColorFilter(cm)
        paint.colorFilter = f
        c.drawBitmap(original, 0f, 0f, paint)
        return bmpGrayscale
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