package com.noverra.launcher.ui.home

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.noverra.launcher.R
import com.noverra.launcher.data.GameIntegrityManager
import com.noverra.launcher.data.GameRepository
import com.noverra.launcher.data.model.ClientConfig
import com.noverra.launcher.data.model.FileEntry
import com.noverra.launcher.databinding.FragmentHomeBinding
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.noverra.launcher.util.SAMPManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Hardcoded Server
    private val SERVER_IP = "192.168.111.168"
    private val SERVER_PORT = 13146
    
    // Status State
    private var isApkInstalled = false
    private var isCacheReady = false
    private var clientConfig: ClientConfig? = null

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
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        checkStatus()
    }

    private fun checkStatus() {
        if (isDetached || context == null) return
        
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
                if (isDetached) return@fetchConfig
                requireActivity().runOnUiThread {
                    result.fold(
                        onSuccess = { 
                            clientConfig = it
                            checkFilesIntegrity(it)
                        },
                        onFailure = { 
                            binding.textStatus.text = "Check Failed" 
                        }
                    )
                }
            }
        } else {
            checkFilesIntegrity(clientConfig!!)
        }
    }
    
    private fun checkFilesIntegrity(config: ClientConfig) {
        binding.textStatus.text = "Checking integrity..."
        GameRepository.fetchFileList(config.urlCacheFiles) { result ->
            if (isDetached) return@fetchFileList
            requireActivity().runOnUiThread {
                result.fold(
                    onSuccess = { files ->
                        lifecycleScope.launch {
                            val (correct, missing) = GameIntegrityManager.checkIntegrity(files)
                            isCacheReady = missing.isEmpty()
                            
                            val statusMsg = if (isCacheReady) getString(R.string.status_ready) else "Resource Update Required (${missing.size} files)"
                            updateUI(isApkInstalled, isCacheReady, statusMsg)
                        }
                    },
                    onFailure = {
                         binding.textStatus.text = "Failed to file list"
                    }
                )
            }
        }
    }

    private fun updateUI(apkInstalled: Boolean, cacheReady: Boolean, msg: String) {
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
        binding.progressBar.visibility = View.VISIBLE
        binding.btnAction.isEnabled = false
        
        GameRepository.fetchConfig { result ->
            if (isDetached) return@fetchConfig
            requireActivity().runOnUiThread {
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
                        Toast.makeText(activity, "Failed to fetch config: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
    
    private fun fetchAndDownloadFiles(url: String) {
         binding.textStatus.text = "Fetching file list..."
         GameRepository.fetchFileList(url) { result ->
            if (isDetached) return@fetchFileList
            requireActivity().runOnUiThread {
               result.fold(
                   onSuccess = { files ->
                        downloadMissingFiles(files)
                   },
                   onFailure = {
                       resetUIState()
                       Toast.makeText(activity, "Failed fetch file list", Toast.LENGTH_SHORT).show()
                   }
               )
            }
         }
    }
    
    private fun downloadMissingFiles(files: List<FileEntry>) {
        lifecycleScope.launch {
            val (_, missing) = GameIntegrityManager.checkIntegrity(files)
            if (missing.isEmpty()) {
                isCacheReady = true
                checkStatus()
                return@launch
            }

            // Show progress UI
            binding.progressBar.visibility = View.VISIBLE
            binding.progressBar.max = 100
            binding.progressBar.progress = 0

            var downloaded = 0
            var failed = 0
            val maxRetries = 3
            
            for ((index, entry) in missing.withIndex()) {
                if (isDetached) return@launch
                
                binding.textInfo.text = "Downloading: ${entry.name}"
                binding.progressBar.progress = 0

                var retryCount = 0
                var success = false
                
                // Retry logic untuk setiap file
                while (retryCount < maxRetries && !success) {
                    success = withContext(Dispatchers.IO) {
                        GameIntegrityManager.downloadFile(entry) { progress ->
                            lifecycleScope.launch(Dispatchers.Main) {
                                val percent = (progress * 100).toInt()
                                binding.progressBar.progress = percent
                            }
                        }
                    }
                    
                    if (!success) {
                        retryCount++
                        if (retryCount < maxRetries) {
                            binding.textInfo.text = "Retrying: ${entry.name} (Attempt $retryCount/$maxRetries)"
                            // Wait sebelum retry
                            delay(1000)
                        }
                    }
                }
                
                if (success) {
                    downloaded++
                } else {
                    failed++
                }
                
                // Update status setelah setiap file selesai
                binding.textStatus.text = "Downloading resources... ($downloaded/${missing.size})"
            }

            // Hide progress UI
            binding.progressBar.visibility = View.GONE

            if (failed == 0) {
                isCacheReady = true
                checkStatus()
                Toast.makeText(requireContext(), "All files downloaded successfully!", Toast.LENGTH_SHORT).show()
            } else {
                resetUIState()
                Toast.makeText(
                    requireContext(), 
                    "Download completed with errors: $downloaded/${missing.size} files. Failed: $failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun downloadApk(url: String) {
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
            binding.btnAction.isEnabled = true
            binding.progressBar.visibility = View.GONE
            binding.textStatus.text = "Waiting for installation..."
            binding.btnAction.text = "Check Install"
            binding.btnAction.setOnClickListener { checkStatus() }
            
        } catch (e: Exception) {
             Toast.makeText(requireContext(), "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
             resetUIState()
        }
    }
    
    private fun resetUIState() {
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
        _binding = null
    }
}
