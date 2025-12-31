package com.noverra.launcher.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.noverra.launcher.R
import com.noverra.launcher.data.DownloadManager
import com.noverra.launcher.data.GameIntegrityManager
import com.noverra.launcher.data.GameRepository
import com.noverra.launcher.data.model.ClientConfig
import com.noverra.launcher.data.model.FileEntry
import com.noverra.launcher.databinding.FragmentHomeBinding
import com.noverra.launcher.util.SAMPManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private val TAG = "HomeFragment"

    // Hardcoded Server
    private val SERVER_IP = "192.168.111.168"
    private val SERVER_PORT = 13146
    
    // Status State
    private var isApkInstalled = false
    private var isCacheReady = false
    private var clientConfig: ClientConfig? = null
    private var gpuDetected = false
    
    // Helper untuk safe UI update - return true jika binding masih valid
    private fun isBindingValid(): Boolean {
        return _binding != null && !isDetached && isAdded && view != null
    }
    
    // Safe update UI - hanya update jika binding valid
    private fun safeUpdateUI(action: () -> Unit) {
        if (isBindingValid()) {
            try {
                action()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating UI: ${e.message}")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize GameIntegrityManager dengan context
        GameIntegrityManager.initialize(requireContext())
        
        // Setup download callbacks
        setupDownloadCallbacks()
        
        // Detect GPU type (sama seperti NoverraLauncher)
        detectGpuType()
        
        setupListeners()
        
        // Check if download is in progress (e.g., user came back to this tab)
        if (DownloadManager.isDownloading) {
            showDownloadingUI()
        }
    }
    
    private fun setupDownloadCallbacks() {
        // Set callbacks untuk DownloadManager
        DownloadManager.onProgressUpdate = { progress ->
            safeUpdateUI {
                binding.progressBar.visibility = View.VISIBLE
                binding.progressBar.max = 100
                binding.progressBar.progress = progress.fileProgress
                binding.textInfo.text = "Downloading: ${progress.currentFile}\n${progress.completedFiles + progress.failedFiles + 1}/${progress.totalFiles}"
                binding.textStatus.text = "Downloading... Success: ${progress.completedFiles}, Failed: ${progress.failedFiles}"
                binding.btnAction.isEnabled = false
            }
        }
        
        DownloadManager.onDownloadComplete = { result ->
            safeUpdateUI {
                binding.progressBar.visibility = View.GONE
                binding.btnAction.isEnabled = true
                
                if (result.success) {
                    isCacheReady = true
                    checkStatus()
                    context?.let {
                        Toast.makeText(it, "All ${result.downloadedFiles} files downloaded!", Toast.LENGTH_SHORT).show()
                    }
                } else if (result.totalFiles == 0 && result.failedFiles == 0) {
                    // No files to download
                    isCacheReady = true
                    checkStatus()
                } else {
                    checkStatus()
                    context?.let {
                        Toast.makeText(
                            it,
                            "Download: ${result.downloadedFiles}/${result.totalFiles} success, ${result.failedFiles} failed",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    // Log failed files
                    if (result.failedFilesList.isNotEmpty()) {
                        Log.e(TAG, "=== FAILED FILES ===")
                        result.failedFilesList.forEach { Log.e(TAG, it) }
                        Log.e(TAG, "===================")
                    }
                }
            }
        }
    }
    
    private fun showDownloadingUI() {
        safeUpdateUI {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnAction.isEnabled = false
            binding.textStatus.text = "Download in progress..."
            
            // Restore progress if available
            DownloadManager.downloadProgress?.let { progress ->
                binding.progressBar.progress = progress.fileProgress
                binding.textInfo.text = "Downloading: ${progress.currentFile}"
            }
        }
    }
    
    /**
     * Detect GPU type sama seperti NoverraLauncher menggunakan GLSurfaceView
     */
    private fun detectGpuType() {
        try {
            // Buat GLSurfaceView sementara untuk detect GPU
            val glView = GLSurfaceView(requireContext())
            glView.setRenderer(object : GLSurfaceView.Renderer {
                override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                    gl?.let {
                        val extensions = it.glGetString(GL10.GL_EXTENSIONS) ?: ""
                        Log.d(TAG, "GPU Extensions: $extensions")
                        
                        GameIntegrityManager.currentGpuType = when {
                            extensions.contains("GL_IMG_texture_compression_pvrtc") -> {
                                Log.d(TAG, "GPU Type: PVR")
                                GameIntegrityManager.GPU_TYPE_PVR
                            }
                            extensions.contains("GL_EXT_texture_compression_dxt1") ||
                            extensions.contains("GL_EXT_texture_compression_s3tc") ||
                            extensions.contains("GL_AMD_compressed_ATC_texture") -> {
                                Log.d(TAG, "GPU Type: DXT")
                                GameIntegrityManager.GPU_TYPE_DXT
                            }
                            else -> {
                                Log.d(TAG, "GPU Type: ETC (default)")
                                GameIntegrityManager.GPU_TYPE_ETC
                            }
                        }
                        gpuDetected = true
                    }
                }
                
                override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {}
                override fun onDrawFrame(gl: GL10?) {}
            })
            
            // Add ke view hierarchy sementara untuk trigger detection
            val container = FrameLayout(requireContext())
            container.addView(glView, 1, 1)
            (binding.root as? ViewGroup)?.addView(container, 0, 0)
            
            // Remove setelah beberapa saat
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    (binding.root as? ViewGroup)?.removeView(container)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing GL view: ${e.message}")
                }
            }, 500)
            
        } catch (e: Exception) {
            Log.e(TAG, "GPU detection failed: ${e.message}", e)
            // Default ke ETC jika gagal
            GameIntegrityManager.currentGpuType = GameIntegrityManager.GPU_TYPE_ETC
            gpuDetected = true
        }
    }

    private fun checkStatus() {
        if (!isBindingValid() || context == null) return
        
        // Check APK
        isApkInstalled = SAMPManager.isInstalled(requireContext())
        
        if (!isApkInstalled) {
             updateUI(false, false, getString(R.string.status_not_installed))
             return
        }

        // Check Permissions for Storage if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // Request Permission first before checking files
                updateUI(false, false, "Grant Storage Permission to Check Files")
                binding.btnAction.text = "Grant Permission"
                binding.btnAction.setOnClickListener { requestStoragePermission() }
                return
            }
        } else {
             // For Android 10 and below
             if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                 updateUI(false, false, "Grant Storage Permission to Check Files")
                 binding.btnAction.text = "Grant Permission"
                 binding.btnAction.setOnClickListener { requestStoragePermission() }
                 return
             }
        }
        
        // Reset Listener
        binding.btnAction.setOnClickListener { onActionClick() }
        
        // Check Files checks need Config first to know URL... 
        // For smoother UX, we can try to fetch config silently or if we already have it
        if (clientConfig == null) {
            binding.textStatus.text = "Checking versions..."
            GameRepository.fetchConfig { result ->
                if (!isBindingValid()) return@fetchConfig
                activity?.runOnUiThread {
                    if (!isBindingValid()) return@runOnUiThread
                    result.fold(
                        onSuccess = { 
                            clientConfig = it
                            checkFilesIntegrity(it)
                        },
                        onFailure = { 
                            safeUpdateUI { binding.textStatus.text = "Check Failed" }
                        }
                    )
                }
            }
        } else {
            checkFilesIntegrity(clientConfig!!)
        }
    }
    
    private fun checkFilesIntegrity(config: ClientConfig) {
        if (!isBindingValid()) return
        
        binding.textStatus.text = "Checking integrity..."
        GameRepository.fetchFileList(config.urlCacheFiles) { result ->
            if (!isBindingValid()) return@fetchFileList
            activity?.runOnUiThread {
                if (!isBindingValid()) return@runOnUiThread
                result.fold(
                    onSuccess = { files ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            val (correct, missing) = GameIntegrityManager.checkIntegrity(files)
                            isCacheReady = missing.isEmpty()
                            
                            val statusMsg = if (isCacheReady) getString(R.string.status_ready) else "Resource Update Required (${missing.size} files)"
                            safeUpdateUI { updateUI(isApkInstalled, isCacheReady, statusMsg) }
                        }
                    },
                    onFailure = {
                        safeUpdateUI { binding.textStatus.text = "Failed to file list" }
                    }
                )
            }
        }
    }

    private fun updateUI(apkInstalled: Boolean, cacheReady: Boolean, msg: String) {
        if (!isBindingValid()) return
        
        binding.textStatus.text = msg
         
        if (apkInstalled && cacheReady) {
            binding.btnAction.text = getString(R.string.btn_start)
            binding.btnAction.isEnabled = true
            binding.textInfo.visibility = View.GONE
        } else {
            binding.btnAction.text = if (!apkInstalled) getString(R.string.btn_download) else "Update Data"
            binding.btnAction.isEnabled = true
            binding.textInfo.visibility = View.VISIBLE
             
            if (!apkInstalled) {
                binding.textInfo.text = getString(R.string.info_not_installed)
            } else {
                binding.textInfo.text = "Game Data Missing/Outdated"
            }
        }
        binding.progressBar.visibility = View.GONE
    }

    private fun setupListeners() {
        binding.btnAction.setOnClickListener { onActionClick() }

        binding.btnInstagram.setOnClickListener { openUrl("https://instagram.com") }
        binding.btnTiktok.setOnClickListener { openUrl("https://tiktok.com") }
        binding.btnYoutube.setOnClickListener { openUrl("https://youtube.com") }
    }
    
    private fun onActionClick() {
        if (isApkInstalled && isCacheReady) {
            startGame()
        } else {
            startDownloadProcess()
        }
    }

    private fun startGame() {
        val prefs = requireContext().getSharedPreferences("noverra_prefs", Context.MODE_PRIVATE)
        val name = prefs.getString("player_name", "") ?: ""

        if (name.length < 3) {
            Toast.makeText(requireContext(), "Set valid player name in Stats menu first!", Toast.LENGTH_LONG).show()
            return
        }
        
        try {
            SAMPManager.launchGame(requireContext(), name, SERVER_IP, SERVER_PORT)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error launching game: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse(String.format("package:%s", requireContext().packageName))
                startActivityForResult(intent, 2296)
            } catch (e: Exception) {
                 val intent = Intent()
                 intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                 startActivityForResult(intent, 2296)
            }
        } else {
            // Android 10 and below
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 2296)
        }
    }

    private fun startDownloadProcess() {
        if (!isBindingValid()) return
        
        binding.progressBar.visibility = View.VISIBLE
        binding.btnAction.isEnabled = false
        
        GameRepository.fetchConfig { result ->
            if (!isBindingValid()) return@fetchConfig
            activity?.runOnUiThread {
                if (!isBindingValid()) return@runOnUiThread
                result.fold(
                    onSuccess = { config ->
                        clientConfig = config
                        if (!isApkInstalled) {
                            downloadApk(config.urlLauncher)
                        } else {
                            fetchAndDownloadFiles(config.urlCacheFiles)
                        }
                    },
                    onFailure = { e ->
                        resetUIState()
                        context?.let {
                            Toast.makeText(it, "Failed to fetch config: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
    
    private fun fetchAndDownloadFiles(url: String) {
        if (!isBindingValid()) return
        
        binding.textStatus.text = "Fetching file list..."
        GameRepository.fetchFileList(url) { result ->
            if (!isBindingValid()) return@fetchFileList
            activity?.runOnUiThread {
                if (!isBindingValid()) return@runOnUiThread
                result.fold(
                    onSuccess = { files ->
                        downloadMissingFiles(files)
                    },
                    onFailure = {
                        resetUIState()
                        context?.let {
                            Toast.makeText(it, "Failed fetch file list", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
    
    private fun downloadMissingFiles(files: List<FileEntry>) {
        if (DownloadManager.isDownloading) {
            Log.w(TAG, "Download already in progress")
            Toast.makeText(context, "Download already in progress", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show initial UI
        safeUpdateUI {
            binding.progressBar.visibility = View.VISIBLE
            binding.progressBar.max = 100
            binding.progressBar.progress = 0
            binding.btnAction.isEnabled = false
            binding.textStatus.text = "Starting download..."
        }
        
        // Start download using DownloadManager (runs in GlobalScope)
        DownloadManager.startDownload(files)
    }

    private fun downloadApk(url: String) {
        if (!isBindingValid()) return
        
        binding.textStatus.text = getString(R.string.status_downloading_apk)
        val request = android.app.DownloadManager.Request(Uri.parse(url))
            .setTitle("Noverra Launcher Update")
            .setDescription("Downloading SA-MP Launcher...")
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "launcher.apk")

        val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        try {
            dm.enqueue(request)
            Toast.makeText(requireContext(), "Download started. Please install APK once finished.", Toast.LENGTH_LONG).show()
            
            // Wait for user to install
            safeUpdateUI {
                binding.btnAction.isEnabled = true
                binding.progressBar.visibility = View.GONE
                binding.textStatus.text = "Waiting for installation..."
                binding.btnAction.text = "Check Install"
                binding.btnAction.setOnClickListener { checkStatus() }
            }
            
        } catch (e: Exception) {
            context?.let {
                Toast.makeText(it, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            resetUIState()
        }
    }
    
    private fun resetUIState() {
        if (!isBindingValid()) return
        
        binding.progressBar.visibility = View.GONE
        binding.btnAction.isEnabled = true
        checkStatus()
    }
    
    // Helper intent for URL
    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot open link", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Don't clear DownloadManager callbacks here - download should continue
        // Callbacks will be re-set when fragment view is recreated
        _binding = null
    }
    
    override fun onResume() {
        super.onResume()
        // Re-setup callbacks when returning to this fragment
        setupDownloadCallbacks()
        
        // If download is in progress, show the UI
        if (DownloadManager.isDownloading) {
            showDownloadingUI()
        } else {
            checkStatus()
        }
    }
}
