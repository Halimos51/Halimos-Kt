package com.halimos.halimos

import android.content.Context
import android.os.Bundle
import android.util.Base64 as AndroidBase64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.gson.reflect.TypeToken
import com.halimos.halimos.databinding.FragmentLoginBinding
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val encryptionManager = EncryptionManager()
        val prefs = requireActivity().getSharedPreferences("halimos_prefs", Context.MODE_PRIVATE)
        val encryptedToken = prefs.getString("auth_token", null)

        if (!encryptedToken.isNullOrEmpty()) {
            try {
                val decryptedToken = encryptionManager.decrypt(encryptedToken, String::class.java)
                if (decryptedToken.isNotEmpty() && !isTokenExpired(decryptedToken)) {
                    findNavController().navigate(R.id.action_LoginFragment_to_FirstFragment)
                    return
                } else if (isTokenExpired(decryptedToken)) {
                    prefs.edit().remove("auth_token").apply()
                }
            } catch (e: Exception) {
                prefs.edit().remove("auth_token").apply()
            }
        }

        setupUrl()

        binding.btnOk.setOnClickListener {
            performLogin()
        }

        binding.btnCancel.setOnClickListener {
            requireActivity().finish()
        }
    }

    private fun isTokenExpired(token: String): Boolean {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return true
            val payload = String(AndroidBase64.decode(parts[1], AndroidBase64.URL_SAFE or AndroidBase64.NO_WRAP or AndroidBase64.NO_PADDING))
            val jsonObject = JSONObject(payload)
            if (jsonObject.has("exp")) {
                val exp = jsonObject.getLong("exp")
                val currentTime = System.currentTimeMillis() / 1000
                currentTime >= exp
            } else {
                false
            }
        } catch (e: Exception) {
            true
        }
    }

    private fun setupUrl() {
        val baseUrls = arrayOf(
            "http://Halimos-PC:1974",
            "http://halimos-pi:1974",
            "https://api.halimos.com"
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, baseUrls)
        binding.url.setAdapter(adapter)
    }

    private fun performLogin() {
        val baseUrlInput = binding.url.text.toString()
        val user = binding.username.text.toString()
        val plainPassword = binding.password.text.toString()
        val encryptionManager = EncryptionManager()

        if (baseUrlInput.isEmpty() || user.isEmpty() || plainPassword.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val password = encryptionManager.encrypt(plainPassword, String::class.java)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        val retrofitBaseUrl = if (baseUrlInput.endsWith("/")) baseUrlInput else "$baseUrlInput/"

        val retrofit = Retrofit.Builder()
            .baseUrl(retrofitBaseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        val service = retrofit.create(ApiService::class.java)
        
        val loginReq = LoginRequest(user, password)
        val encryptedRequest = encryptionManager.encrypt(loginReq, LoginRequest::class.java)

        val jsonStringBody = "\"$encryptedRequest\""
        val body = jsonStringBody.toRequestBody("application/json".toMediaTypeOrNull())

        service.login(body).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    var encryptedResponse = response.body()?.string()
                    if (encryptedResponse != null) {
                        try {
                            if (encryptedResponse.startsWith("\"") && encryptedResponse.endsWith("\"")) {
                                encryptedResponse = encryptedResponse.substring(1, encryptedResponse.length - 1)
                            }

                            val type: Type = object : TypeToken<ApiResponse<LoginData>>() {}.type
                            val apiResponse: ApiResponse<LoginData> = encryptionManager.decrypt(encryptedResponse, type)
                            
                            if (apiResponse.Success) {
                                val token = apiResponse.Data?.JWToken
                                if (!token.isNullOrEmpty()) {
                                    val encryptedToken = encryptionManager.encrypt(token, String::class.java)
                                    requireActivity().getSharedPreferences("halimos_prefs", Context.MODE_PRIVATE)
                                        .edit()
                                        .putString("auth_token", encryptedToken)
                                        .apply()
                                }

                                findNavController().navigate(R.id.action_LoginFragment_to_FirstFragment)
                            } else {
                                Toast.makeText(requireContext(), apiResponse.Message ?: "Login failed", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Decryption error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), "Login failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Toast.makeText(requireContext(), "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
