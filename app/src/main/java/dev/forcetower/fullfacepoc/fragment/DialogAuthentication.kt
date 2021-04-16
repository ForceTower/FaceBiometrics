package dev.forcetower.fullfacepoc.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import dev.forcetower.fullfacepoc.FullFaceViewModel
import dev.forcetower.fullfacepoc.FullfaceCommand
import dev.forcetower.fullfacepoc.FullfaceInteraction
import dev.forcetower.fullfacepoc.databinding.DialogBiometricsAuthBinding
import dev.forcetower.fullfacepoc.detection.FaceContourDetectionProcessor
import dev.forcetower.fullfacepoc.service.FullfaceService
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import dev.forcetower.fullfacepoc.R

@SuppressLint("RestrictedApi")
class DialogAuthentication : DialogFragment() {
    private lateinit var binding: DialogBiometricsAuthBinding
    private lateinit var imageCapture: ImageCapture
    private lateinit var imageAnalysis: ImageAnalysis

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(FullfaceService::class.java)

    private lateinit var viewModel: FullFaceViewModel

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
        viewModel = FullFaceViewModel(requireContext().applicationContext.contentResolver, retrofit)
        return DialogBiometricsAuthBinding.inflate(inflater, container, false).also {
            binding = it
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnAuth.setOnClickListener {
            startCapture()
        }

        viewModel.command.observe(viewLifecycleOwner, { handleCommands(it) })
    }

    private fun handleCommands(command: FullfaceCommand) {
        when (command) {
            is FullfaceCommand.CreatedAccountCommand -> showToast("Conta criada")
            is FullfaceCommand.CreateErrorCommand -> showToast(command.error)
            is FullfaceCommand.MatchedAccountCommand -> {
                showToast("ID: ${command.id} >> Similaridade ${command.similarity}%")
                showIcon(R.drawable.ic_baseline_check_24)
            }
            is FullfaceCommand.MatchErrorCommand -> {
                showToast(command.error)
                showIcon(R.drawable.ic_baseline_error_24)
            }
        }
    }

    private fun showIcon(icon: Int) {
        binding.icon.visibility = View.VISIBLE
        binding.icon.setImageResource(icon)
    }

    private fun showToast(string: String) {
        Toast.makeText(requireContext(), string, Toast.LENGTH_LONG).show()
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
        binding.icon.visibility = View.GONE
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        val pictures = withContext(Dispatchers.IO) {
            val f1 = captureImage()
            delay(250L)
            val f2 = captureImage()
            delay(250L)
            val f3 = captureImage()
            delay(250L)
            val f4 = captureImage()
            delay(250L)
            val f5 = captureImage()
            delay(250L)
            val f6 = captureImage()
            listOf(f1, f2, f3, f4, f5, f6)
        }

        viewModel.handleMatchBiometrics(FullfaceInteraction.MatchBiometric(pictures))

//        val resolver = requireContext().applicationContext.contentResolver
//        val result = pictures.map {
//            val stream = resolver.openInputStream(it)
//            val bitmap = toGrayscale(BitmapFactory.decodeStream(stream))
//
//            val exif = ExifInterface(it.toFile().absolutePath)
//            val rotated = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
//                ExifInterface.ORIENTATION_ROTATE_90 -> rotate(bitmap, 90f)
//                ExifInterface.ORIENTATION_ROTATE_180 -> rotate(bitmap, 180f)
//                ExifInterface.ORIENTATION_ROTATE_270 -> rotate(bitmap, 270f)
//                ExifInterface.ORIENTATION_TRANSVERSE -> {
//                    val temp = flipHorizontal(bitmap)
//                    rotate(temp, 90f)
//                }
//                ExifInterface.ORIENTATION_TRANSPOSE -> {
//                    val temp = flipHorizontal(bitmap)
//                    rotate(temp, 270f)
//                }
//                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipHorizontal(bitmap)
//                else -> bitmap
//            }
//
//            val baos = ByteArrayOutputStream()
//            rotated.compress(Bitmap.CompressFormat.PNG, 100, baos)
//            val data = baos.toByteArray()
//            Base64.encodeToString(data, Base64.DEFAULT)
//        }
//
//        result.forEach {
//            Timber.d(it)
//        }

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