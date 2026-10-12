package com.lchat.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import com.lchat.app.databinding.FragmentCameraBinding
import com.lchat.app.snap.CreateSnapActivity
import java.io.File
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: androidx.camera.core.Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var bound = false
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var lastPinchDistance = 0f

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                binding.permissionView.isVisible = false
                startCamera()
            } else {
                binding.permissionView.isVisible = true
            }
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                val file = copyToCache(uri)
                if (file != null) {
                    openCreateSnap(file.absolutePath)
                } else {
                    Snackbar.make(binding.root, "Could not open that image", Snackbar.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.captureButton.setOnClickListener { takePhoto() }
        binding.galleryButton.setOnClickListener { openGallery() }
        binding.openGalleryButton.setOnClickListener { openGallery() }
        binding.flipCameraButton.setOnClickListener { flipCamera() }
        binding.flashButton.setOnClickListener { cycleFlash() }
        binding.grantCameraButton.setOnClickListener { requestCameraPermission.launch(Manifest.permission.CAMERA) }

        setupZoomAndFocus()

        if (hasCameraPermission()) {
            binding.permissionView.isVisible = false
            startCamera()
        } else {
            binding.permissionView.isVisible = true
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupZoomAndFocus() {
        binding.previewView.setOnTouchListener { v, event ->
            val camera = camera ?: return@setOnTouchListener false
            val control = camera.cameraControl
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    val point = binding.previewView.meteringPointFactory
                        .createPoint(event.x, event.y)
                    control.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount >= 2) {
                        val d = pinchDistance(event)
                        val zoomState = camera.cameraInfo.zoomState.value
                        if (zoomState != null && lastPinchDistance > 0f && d > 0f) {
                            val newZoom = (zoomState.zoomRatio * (d / lastPinchDistance))
                                .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                            control.setZoomRatio(newZoom)
                        }
                        lastPinchDistance = d
                    }
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> lastPinchDistance = 0f
            }
            true
        }
    }

    private fun pinchDistance(event: android.view.MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    private fun cycleFlash() {
        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
        imageCapture?.flashMode = flashMode
        val label = when (flashMode) {
            ImageCapture.FLASH_MODE_AUTO -> "Auto"
            ImageCapture.FLASH_MODE_ON -> "On"
            else -> "Off"
        }
        Snackbar.make(binding.root, "Flash: $label", Snackbar.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        if (hasCameraPermission() && !bound) {
            startCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        stopCamera()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopCamera()
        _binding = null
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        if (_binding == null || !hasCameraPermission()) return

        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            if (_binding == null) return@addListener
            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build()

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setResolutionSelector(resolutionSelector)
                    .build()
                capture.flashMode = flashMode
                imageCapture = capture

                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                provider.unbindAll()
                camera = provider.bindToLifecycle(viewLifecycleOwner, selector, preview, capture)
                bound = true
                binding.permissionView.isVisible = false
            } catch (e: Exception) {
                bound = false
                binding.permissionView.isVisible = true
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        bound = false
    }

    private fun flipCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        if (bound) startCamera()
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        binding.cameraProgress.isVisible = true

        val file = File(requireContext().cacheDir, "snap_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (_binding == null) return
                    binding.cameraProgress.isVisible = false
                    openCreateSnap(file.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (_binding == null) return
                    binding.cameraProgress.isVisible = false
                    Snackbar.make(
                        binding.root,
                        "Could not take photo: ${exception.message}",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun openGallery() {
        pickImage.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun copyToCache(uri: android.net.Uri): File? = try {
        val file = File(requireContext().cacheDir, "pick_${System.currentTimeMillis()}.jpg")
        requireContext().contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        file
    } catch (e: Exception) {
        null
    }

    private fun openCreateSnap(imagePath: String) {
        val intent = Intent(requireContext(), CreateSnapActivity::class.java)
        intent.putExtra(CreateSnapActivity.EXTRA_IMAGE_PATH, imagePath)
        startActivity(intent)
    }
}
