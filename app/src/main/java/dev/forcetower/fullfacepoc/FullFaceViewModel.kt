package dev.forcetower.fullfacepoc

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Base64
import androidx.core.net.toFile
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dev.forcetower.fullfacepoc.model.AuthModel
import dev.forcetower.fullfacepoc.model.RegisterModel
import dev.forcetower.fullfacepoc.service.FullfaceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import timber.log.Timber
import java.io.ByteArrayOutputStream


class FullFaceViewModel constructor(
    private val resolver: ContentResolver,
    private val service: FullfaceService
) {
    private val _sendingImages = MutableLiveData<Boolean>()
    val sending: LiveData<Boolean> = _sendingImages

    private val _command = MutableLiveData<FullfaceCommand>()
    val command: LiveData<FullfaceCommand> = _command

    suspend fun handleCreateAccount(images: List<Uri>) {
        _sendingImages.value = true

        // TODO Move everything to a use case
        withContext(Dispatchers.IO) {
            val encodedImages = images.map {
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

            try {
                val response = service.register(RegisterModel(encodedImages))
                Timber.d("Response: $response")
                withContext(Dispatchers.Main) {
                    sendCommand(FullfaceCommand.CreatedAccountCommand)
                }
            } catch (exception: HttpException) {
                Timber.e(exception, "Error loading")
                val code = exception.code()
                val str = exception.response()?.errorBody()?.string() ?: "Invalid response error"
                withContext(Dispatchers.Main) {
                    sendCommand(FullfaceCommand.CreateErrorCommand("$code: $str"))
                }
            } catch (error: Throwable) {
                Timber.e(error, "Error loading")
                withContext(Dispatchers.Main) {
                    sendCommand(FullfaceCommand.CreateErrorCommand(error.message ?: "Invalid error"))
                }
            }
        }

        _sendingImages.value = false
    }

    private fun sendCommand(command: FullfaceCommand) {
        _command.value = command
    }

    suspend fun handleMatchBiometrics(interaction: FullfaceInteraction.MatchBiometric) {
        val images = interaction.images
        _sendingImages.value = true

        // TODO Move everything to a use case
        withContext(Dispatchers.IO) {
            val encodedImages = images.map {
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

            try {
                val response = service.authenticate(AuthModel(encodedImages))
                Timber.d("Response: $response")
                withContext(Dispatchers.Main) {
                    sendCommand(FullfaceCommand.MatchedAccountCommand(response.id, response.similarity))
                }
            } catch (exception: HttpException) {
                Timber.e(exception, "Error loading")
                val code = exception.code()
                val str = exception.response()?.errorBody()?.string() ?: "Invalid response error"
                withContext(Dispatchers.Main) {
                    sendCommand(FullfaceCommand.MatchErrorCommand("$code: $str"))
                }
            } catch (error: Throwable) {
                Timber.e(error, "Error loading")
                withContext(Dispatchers.Main) {
                    sendCommand(FullfaceCommand.MatchErrorCommand(error.message ?: "Invalid error"))
                }
            }
        }

        _sendingImages.value = false
    }

    private fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun flipHorizontal(bitmap: Bitmap): Bitmap {
        val matrix = Matrix()
        matrix.setValues(floatArrayOf(-1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))
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
}