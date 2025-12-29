package com.noverra.launcher.ui.stats

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.noverra.launcher.R
import com.noverra.launcher.databinding.FragmentStatsBinding
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private val SERVER_IP = "192.168.111.168"
    private val SERVER_PORT = 13146

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        loadPlayerName()
        
        binding.btnSave.setOnClickListener {
            val name = binding.inputName.text.toString().trim()
            if (name.isNotEmpty()) {
                savePlayerName(name)
                android.widget.Toast.makeText(requireContext(), getString(R.string.msg_name_saved), android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRefresh.setOnClickListener {
            refreshStats()
        }

        refreshStats()
    }
    
    private fun loadPlayerName() {
        val prefs = requireContext().getSharedPreferences("noverra_prefs", android.content.Context.MODE_PRIVATE)
        val name = prefs.getString("player_name", "")
        binding.inputName.setText(name)
    }

    private fun savePlayerName(name: String) {
        val prefs = requireContext().getSharedPreferences("noverra_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("player_name", name).apply()
    }

    private fun refreshStats() {
        binding.textServerStatus.text = "Checking..."
        binding.btnRefresh.isEnabled = false

        Thread {
            queryServer()
        }.start()
    }

    private fun queryServer() {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.soTimeout = 3000

            val address = InetAddress.getByName(SERVER_IP)
            val ipParts = address.address // 4 bytes

            val buffer = ByteBuffer.allocate(11)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("SAMP".toByteArray()) // 0-3
            buffer.put(ipParts) // 4-7
            buffer.putShort(SERVER_PORT.toShort()) // 8-9
            buffer.put('i'.code.toByte()) // 10

            val packet = DatagramPacket(buffer.array(), buffer.capacity(), address, SERVER_PORT)
            socket.send(packet)

            val receiveData = ByteArray(1024)
            val receivePacket = DatagramPacket(receiveData, receiveData.size)
            socket.receive(receivePacket)

            // Parse Response
            // Header (11 bytes) + Password (1) + Players (2) + MaxPlayers (2)
            val response = ByteBuffer.wrap(receiveData)
            response.order(ByteOrder.LITTLE_ENDIAN)
            
            // Skip 11 bytes header
            if (response.capacity() > 11) {
                response.position(11)
                // val password = response.get() // Unused
                response.get() // Skip boolean
                val players = response.short.toInt()
                val maxPlayers = response.short.toInt()
                updateUI(true, players, maxPlayers)
            } else {
                updateUI(false, 0, 0)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            updateUI(false, 0, 0)
        } finally {
            socket?.close()
        }
    }

    private fun updateUI(isOnline: Boolean, players: Int, maxPlayers: Int) {
        Handler(Looper.getMainLooper()).post {
            if (_binding == null) return@post
            binding.btnRefresh.isEnabled = true
            if (isOnline) {
                binding.textServerStatus.text = getString(R.string.server_online)
                binding.textPlayers.text = "Players: $players / $maxPlayers"
            } else {
                binding.textServerStatus.text = getString(R.string.server_offline)
                binding.textPlayers.text = "Players: - / -"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
